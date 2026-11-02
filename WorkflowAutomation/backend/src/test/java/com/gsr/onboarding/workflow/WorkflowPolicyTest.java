package com.gsr.onboarding.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gsr.onboarding.TestFixtures;
import com.gsr.onboarding.domain.entity.WorkflowStep;
import com.gsr.onboarding.domain.enums.StepName;
import com.gsr.onboarding.domain.enums.StepStatus;
import com.gsr.onboarding.domain.enums.WorkflowStatus;

class WorkflowPolicyTest {

    private final WorkflowPolicy policy = new WorkflowPolicy(TestFixtures.properties(false, true));

    private static List<WorkflowStep> steps(StepStatus... statuses) {
        List<WorkflowStep> steps = new ArrayList<>();
        StepName[] names = StepName.values();
        for (int i = 0; i < names.length; i++) {
            WorkflowStep step = WorkflowStep.pending(1L, names[i]);
            step.setStatus(statuses[i]);
            steps.add(step);
        }
        return steps;
    }

    @Test
    void nextRunnableFollowsStepOrder() {
        var steps = steps(StepStatus.SUCCESS, StepStatus.SUCCESS, StepStatus.PENDING,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING);

        assertThat(policy.nextRunnable(steps)).get().extracting(WorkflowStep::getStepName)
                .isEqualTo(StepName.GENERATE_CONTENT);
        assertThat(policy.overallStatus(steps)).isEqualTo(WorkflowStatus.IN_PROGRESS);
    }

    @Test
    void failedStepBlocksDependentsAndFailsWorkflow() {
        var steps = steps(StepStatus.SUCCESS, StepStatus.SUCCESS, StepStatus.SUCCESS,
                StepStatus.FAILED, StepStatus.PENDING, StepStatus.PENDING);

        assertThat(policy.nextRunnable(steps)).isEmpty();
        assertThat(policy.overallStatus(steps)).isEqualTo(WorkflowStatus.FAILED);
    }

    @Test
    void awaitingApprovalIsReported() {
        var steps = steps(StepStatus.SUCCESS, StepStatus.SUCCESS, StepStatus.SUCCESS,
                StepStatus.AWAITING_APPROVAL, StepStatus.PENDING, StepStatus.PENDING);

        assertThat(policy.overallStatus(steps)).isEqualTo(WorkflowStatus.AWAITING_APPROVAL);
    }

    @Test
    void allSuccessfulStepsCompleteWorkflow() {
        var steps = steps(StepStatus.SUCCESS, StepStatus.SUCCESS, StepStatus.SUCCESS,
                StepStatus.SUCCESS, StepStatus.SUCCESS, StepStatus.SUCCESS);

        assertThat(policy.overallStatus(steps)).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void retryLimitedByMaxAttempts() {
        WorkflowStep step = WorkflowStep.pending(1L, StepName.SEND_EMAIL);
        step.setStatus(StepStatus.FAILED);
        step.setAttempts(4);
        assertThat(policy.isRetryable(step)).isTrue();
        step.setAttempts(5);
        assertThat(policy.isRetryable(step)).isFalse();
        step.setStatus(StepStatus.FAILED_UNCERTAIN);
        step.setAttempts(1);
        assertThat(policy.isRetryable(step)).isFalse();
    }
}
