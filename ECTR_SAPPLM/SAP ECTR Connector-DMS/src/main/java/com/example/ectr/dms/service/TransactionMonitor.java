// VERIFIED: plain Java/Spring. In-memory only: state is lost on restart and not shared between
// instances. For production, back it with a database table (see docs/09-errors-timeouts-monitoring.md).
package com.example.ectr.dms.service;

import com.example.ectr.dms.domain.DocumentKey;
import com.example.ectr.dms.domain.TransactionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks every create request from receipt to its final state. Thread-safe. */
@Component
public class TransactionMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionMonitor.class);

    private final Map<String, TransactionRecord> byId = new ConcurrentHashMap<>();
    private final Map<String, String> idByIdempotencyKey = new ConcurrentHashMap<>();
    private final Clock clock;

    public TransactionMonitor() {
        this(Clock.systemUTC());
    }

    TransactionMonitor(Clock clock) {
        this.clock = clock;
    }

    /** Result of {@link #start}: either a new transaction or the existing one for the same idempotency key. */
    public record Started(TransactionRecord record, boolean replay) {
    }

    public Started start(String idempotencyKey) {
        String newId = UUID.randomUUID().toString();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String existing = idByIdempotencyKey.putIfAbsent(idempotencyKey, newId);
            if (existing != null) {
                return new Started(byId.get(existing), true);
            }
        }
        TransactionRecord record = new TransactionRecord(newId, idempotencyKey, TransactionState.RECEIVED, null,
                List.of(), List.of(new TransactionRecord.StateChange(TransactionState.RECEIVED, clock.instant())));
        byId.put(newId, record);
        return new Started(record, false);
    }

    public TransactionRecord update(String id, TransactionState state) {
        return update(id, state, null, List.of());
    }

    /**
     * Moves a transaction to a new state. A transaction in a terminal state is never changed again,
     * and TIMEOUT_OUTCOME_UNKNOWN never overwrites a newer, real state.
     */
    public TransactionRecord update(String id, TransactionState state, DocumentKey key, List<String> messages) {
        TransactionRecord updated = byId.computeIfPresent(id, (k, current) -> {
            if (current.state().isTerminal()) {
                return current;
            }
            List<TransactionRecord.StateChange> history = new ArrayList<>(current.history());
            history.add(new TransactionRecord.StateChange(state, clock.instant()));
            List<String> allMessages = new ArrayList<>(current.messages());
            allMessages.addAll(messages);
            return new TransactionRecord(current.id(), current.idempotencyKey(), state,
                    key != null ? key : current.documentKey(), allMessages, history);
        });
        if (updated != null) {
            LOG.info("Transaction {} -> {}{}", id, updated.state(),
                    updated.documentKey() == null ? "" : " (" + updated.documentKey() + ")");
        }
        return updated;
    }

    public Optional<TransactionRecord> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }
}
