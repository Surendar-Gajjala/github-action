package com.gsr.onboarding.domain.enums;

public enum StepStatus {
    PENDING,
    IN_PROGRESS,
    SUCCESS,
    FAILED,
    /** The outcome of an external side effect is unknown (e.g. interrupted while sending email). Needs an admin decision. */
    FAILED_UNCERTAIN,
    AWAITING_APPROVAL,
    SKIPPED;

    public boolean isDone() {
        return this == SUCCESS || this == SKIPPED;
    }

    public boolean isFailure() {
        return this == FAILED || this == FAILED_UNCERTAIN;
    }
}
