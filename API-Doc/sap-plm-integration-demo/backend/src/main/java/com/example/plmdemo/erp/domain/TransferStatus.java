package com.example.plmdemo.erp.domain;

public enum TransferStatus {
    RECEIVED,
    VALIDATION_FAILED,
    IN_PROGRESS,
    SUCCEEDED,
    /** SAP stayed unavailable after all retry attempts. Safe to resubmit later. */
    FAILED_TRANSIENT,
    /** SAP rejected the data. Fix the source data before resubmitting. */
    FAILED_PERMANENT;

    /** A new submission with the same idempotency key is treated as a duplicate in these states. */
    public boolean blocksResubmission() {
        return this == IN_PROGRESS || this == SUCCEEDED || this == RECEIVED;
    }
}
