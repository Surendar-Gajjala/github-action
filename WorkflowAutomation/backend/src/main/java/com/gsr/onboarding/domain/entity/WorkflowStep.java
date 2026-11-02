package com.gsr.onboarding.domain.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.gsr.onboarding.domain.enums.StepName;
import com.gsr.onboarding.domain.enums.StepStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "workflow_steps")
public class WorkflowStep extends BaseEntity {

    @Column(name = "workflow_id", nullable = false, updatable = false)
    private Long workflowId;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_name", nullable = false, updatable = false, length = 40)
    private StepName stepName;

    @Column(name = "step_order", nullable = false, updatable = false)
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StepStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "external_reference", length = 500)
    private String externalReference;

    public static WorkflowStep pending(Long workflowId, StepName name) {
        WorkflowStep step = new WorkflowStep();
        step.setWorkflowId(workflowId);
        step.setStepName(name);
        step.setStepOrder(name.order());
        step.setStatus(StepStatus.PENDING);
        return step;
    }
}
