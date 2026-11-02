package com.example.plmdemo.erp.transfer;

import com.example.plmdemo.erp.domain.TransferRecord;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/** In-memory store. Data is lost on restart, which is fine for the demo. */
@Repository
public class InMemoryTransferRepository implements TransferRepository {

    private final Map<String, TransferRecord> byId = new ConcurrentHashMap<>();
    private final Map<String, String> idByIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public synchronized Registration register(String idempotencyKey, UnaryOperator<TransferRecord> createOrReplace) {
        TransferRecord existing = Optional.ofNullable(idByIdempotencyKey.get(idempotencyKey))
                .map(byId::get)
                .orElse(null);
        if (existing != null && existing.status().blocksResubmission()) {
            return new Registration(existing, false);
        }
        TransferRecord created = createOrReplace.apply(existing);
        byId.put(created.transferId(), created);
        idByIdempotencyKey.put(idempotencyKey, created.transferId());
        return new Registration(created, true);
    }

    @Override
    public TransferRecord save(TransferRecord record) {
        byId.put(record.transferId(), record);
        return record;
    }

    @Override
    public Optional<TransferRecord> findById(String transferId) {
        return Optional.ofNullable(byId.get(transferId));
    }
}
