package com.gsr.onboarding.domain.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.gsr.onboarding.domain.enums.StepName;
import com.gsr.onboarding.domain.enums.WorkflowStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "onboarding_workflows")
public class OnboardingWorkflow extends BaseEntity {

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_status", nullable = false, length = 30)
    private WorkflowStatus overallStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", length = 40)
    private StepName currentStep;

    /** Snapshot of the approval policy at the time the workflow started. */
    @Column(name = "email_approval_required", nullable = false)
    private boolean emailApprovalRequired;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;
}
