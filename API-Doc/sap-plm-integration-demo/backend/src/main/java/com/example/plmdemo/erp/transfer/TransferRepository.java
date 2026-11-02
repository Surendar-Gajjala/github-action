package com.example.plmdemo.erp.transfer;

import com.example.plmdemo.erp.domain.TransferRecord;

import java.util.Optional;
import java.util.function.UnaryOperator;

/** Stores transfer status. Swap the in-memory version for a database table in a real setup. */
public interface TransferRepository {

    /**
     * Atomically registers a new transfer for the idempotency key, unless an existing one blocks it.
     *
     * @return the record now stored for the key and whether it was newly created
     */
    Registration register(String idempotencyKey, UnaryOperator<TransferRecord> createOrReplace);

    TransferRecord save(TransferRecord record);

    Optional<TransferRecord> findById(String transferId);

    record Registration(TransferRecord record, boolean created) {
    }
}
