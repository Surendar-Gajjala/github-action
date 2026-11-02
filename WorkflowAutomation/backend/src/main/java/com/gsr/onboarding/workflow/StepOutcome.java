package com.gsr.onboarding.workflow;

import com.gsr.onboarding.domain.enums.StepStatus;

/**
 * Result of executing one workflow step.
 *
 * @param status            terminal status for this attempt
 * @param externalReference provider ID (email message ID, GitHub issue number) when available
 * @param message           error or informational note shown to HR users; never contains secrets
 */
public record StepOutcome(StepStatus status, String externalReference, String message) {

    public static StepOutcome success(String externalReference, String note) {
        return new StepOutcome(StepStatus.SUCCESS, externalReference, note);
    }

    public static StepOutcome failed(String message) {
        return new StepOutcome(StepStatus.FAILED, null, message);
    }

    public static StepOutcome uncertain(String message) {
        return new StepOutcome(StepStatus.FAILED_UNCERTAIN, null, message);
    }

    public static StepOutcome awaitingApproval(String message) {
        return new StepOutcome(StepStatus.AWAITING_APPROVAL, null, message);
    }
}
