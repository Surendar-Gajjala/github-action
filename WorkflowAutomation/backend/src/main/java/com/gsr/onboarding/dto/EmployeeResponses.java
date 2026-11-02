package com.gsr.onboarding.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.gsr.onboarding.domain.enums.AccessLevel;
import com.gsr.onboarding.domain.enums.StepStatus;
import com.gsr.onboarding.domain.enums.WorkflowStatus;

/** Response payloads describing employees. */
public final class EmployeeResponses {

    private EmployeeResponses() {
    }

    /** Returned by POST /api/employees. Processing continues asynchronously. */
    public record EmployeeCreatedResponse(
            Long employeeId,
            String employeeNumber,
            Long workflowId,
            WorkflowStatus workflowStatus,
            boolean duplicateRequest) {
    }

    public record EmployeeResponse(
            Long id,
            String employeeNumber,
            String firstName,
            String lastName,
            String fullName,
            String email,
            String jobTitle,
            String department,
            LocalDate joiningDate,
            String reportingManager,
            String githubUsername,
            String githubRepository,
            AccessLevel accessLevel,
            String createdBy,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** Row in the employee history table. */
    public record EmployeeSummaryResponse(
            Long id,
            String employeeNumber,
            String fullName,
            String email,
            String department,
            LocalDate joiningDate,
            WorkflowStatus workflowStatus,
            StepStatus emailStatus,
            StepStatus githubIssueStatus,
            boolean retryable,
            Instant createdAt) {
    }

    public record EmployeeDetailResponse(EmployeeResponse employee, WorkflowResponse workflow) {
    }
}
