package com.example.plmdemo.erp.transfer;

/** Input to BomTransferService. idempotencyKey may be null; one is then derived from the business key. */
public record TransferCommand(String materialNumber, String revision, String plant,
                              String idempotencyKey, String correlationId) {

    public String effectiveIdempotencyKey() {
        return idempotencyKey != null && !idempotencyKey.isBlank()
                ? idempotencyKey
                : "BOM:" + materialNumber + ":" + revision + ":" + plant;
    }
}
