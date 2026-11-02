package com.example.plmdemo.erp.api.dto;

import com.example.plmdemo.erp.domain.TransferRecord;

import java.time.Instant;
import java.util.List;

/** External response contract. gatewayMode tells the caller whether a real SAP system was involved. */
public record TransferResponseDto(
        String transferId,
        String status,
        boolean duplicate,
        String gatewayMode,
        String materialNumber,
        String revision,
        String plant,
        String targetReference,
        int attempts,
        List<String> messages,
        String idempotencyKey,
        String correlationId,
        Instant createdAt,
        Instant updatedAt) {

    public static TransferResponseDto from(TransferRecord r, boolean duplicate, String gatewayMode) {
        return new TransferResponseDto(r.transferId(), r.status().name(), duplicate, gatewayMode,
                r.materialNumber(), r.revision(), r.plant(), r.targetReference(), r.attempts(),
                r.messages(), r.idempotencyKey(), r.correlationId(), r.createdAt(), r.updatedAt());
    }
}
