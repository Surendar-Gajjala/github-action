package com.gsr.onboarding.dto;

import java.time.Instant;
import java.util.List;

import com.gsr.onboarding.domain.enums.StepName;
import com.gsr.onboarding.domain.enums.StepStatus;
import com.gsr.onboarding.domain.enums.WorkflowStatus;

public record WorkflowResponse(
        Long workflowId,
        Long employeeId,
        WorkflowStatus overallStatus,
        StepName currentStep,
        boolean emailApprovalRequired,
        Instant startedAt,
        Instant completedAt,
        String lastError,
        boolean retryable,
        boolean requiresForcedRetry,
        List<StepResponse> steps,
        List<HistoryEntry> history) {

    public record StepResponse(
            StepName name,
            String label,
            int order,
            StepStatus status,
            int attempts,
            Instant startedAt,
            Instant completedAt,
            String errorMessage,
            String externalReference) {
    }

    /** Audit trail entry for the workflow execution history. */
    public record HistoryEntry(Instant at, String actor, String action, String details) {
    }
}
