// VERIFIED: plain Java.
package com.example.ectr.dms.service;

import com.example.ectr.dms.domain.DocumentKey;
import com.example.ectr.dms.domain.TransactionState;

import java.time.Instant;
import java.util.List;

/**
 * Immutable snapshot of one document-creation transaction.
 *
 * @param id             transaction id returned to the caller
 * @param idempotencyKey optional key supplied by the caller to make retries safe
 * @param state          current state
 * @param documentKey    created document, once known
 * @param messages       user-facing error or info messages
 * @param history        all state changes with timestamps
 */
public record TransactionRecord(String id, String idempotencyKey, TransactionState state, DocumentKey documentKey,
                                List<String> messages, List<StateChange> history) {

    public TransactionRecord {
        messages = List.copyOf(messages);
        history = List.copyOf(history);
    }

    public record StateChange(TransactionState state, Instant at) {
    }
}
