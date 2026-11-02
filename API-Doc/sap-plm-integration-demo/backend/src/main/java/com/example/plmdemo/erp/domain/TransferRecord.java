package com.example.plmdemo.erp.domain;

import java.time.Instant;
import java.util.List;

/** Immutable snapshot of one BOM transfer and its outcome. */
public record TransferRecord(
        String transferId,
        String idempotencyKey,
        String correlationId,
        String materialNumber,
        String revision,
        String plant,
        TransferStatus status,
        int attempts,
        String targetReference,
        List<String> messages,
        Instant createdAt,
        Instant updatedAt) {

    public TransferRecord {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public TransferRecord withStatus(TransferStatus newStatus, List<String> newMessages) {
        return new TransferRecord(transferId, idempotencyKey, correlationId, materialNumber, revision, plant,
                newStatus, attempts, targetReference, newMessages, createdAt, Instant.now());
    }

    public TransferRecord withAttempts(int newAttempts) {
        return new TransferRecord(transferId, idempotencyKey, correlationId, materialNumber, revision, plant,
                status, newAttempts, targetReference, messages, createdAt, Instant.now());
    }

    public TransferRecord succeeded(String reference, List<String> newMessages) {
        return new TransferRecord(transferId, idempotencyKey, correlationId, materialNumber, revision, plant,
                TransferStatus.SUCCEEDED, attempts, reference, newMessages, createdAt, Instant.now());
    }
}
