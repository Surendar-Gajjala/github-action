package com.gsr.onboarding.workflow;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.gsr.onboarding.config.AppProperties;
import com.gsr.onboarding.domain.entity.WorkflowStep;
import com.gsr.onboarding.domain.enums.StepName;
import com.gsr.onboarding.domain.enums.StepStatus;
import com.gsr.onboarding.domain.enums.WorkflowStatus;

/** Step dependencies and overall-status rules. */
@Component
public class WorkflowPolicy {

    private final AppProperties.Workflow properties;

    public WorkflowPolicy(AppProperties appProperties) {
        this.properties = appProperties.workflow();
    }

    public Set<StepName> dependencies(StepName step) {
        return switch (step) {
            case VALIDATE_DETAILS -> EnumSet.noneOf(StepName.class);
            case SAVE_RECORD -> EnumSet.of(StepName.VALIDATE_DETAILS);
            case GENERATE_CONTENT -> EnumSet.of(StepName.SAVE_RECORD);
            case SEND_EMAIL -> EnumSet.of(StepName.GENERATE_CONTENT);
            // With github-after-email=false the issue is created even if the email is pending approval or failed.
            case CREATE_GITHUB_ISSUE -> properties.githubAfterEmail()
                    ? EnumSet.of(StepName.GENERATE_CONTENT, StepName.SEND_EMAIL)
                    : EnumSet.of(StepName.GENERATE_CONTENT);
            case COMPLETE_WORKFLOW -> EnumSet.complementOf(EnumSet.of(StepName.COMPLETE_WORKFLOW));
        };
    }

    public boolean isReady(WorkflowStep step, Map<StepName, StepStatus> statuses) {
        if (step.getStatus() != StepStatus.PENDING) {
            return false;
        }
        return dependencies(step.getStepName()).stream()
                .allMatch(dependency -> {
                    StepStatus status = statuses.get(dependency);
                    return status != null && status.isDone();
                });
    }

    public Optional<WorkflowStep> nextRunnable(List<WorkflowStep> steps) {
        Map<StepName, StepStatus> statuses = statuses(steps);
        return steps.stream()
                .sorted((a, b) -> Integer.compare(a.getStepOrder(), b.getStepOrder()))
                .filter(step -> isReady(step, statuses))
                .findFirst();
    }

    public WorkflowStatus overallStatus(List<WorkflowStep> steps) {
        if (steps.stream().allMatch(step -> step.getStatus().isDone())) {
            return WorkflowStatus.COMPLETED;
        }
        if (steps.stream().anyMatch(step -> step.getStatus() == StepStatus.IN_PROGRESS)
                || nextRunnable(steps).isPresent()) {
            return WorkflowStatus.IN_PROGRESS;
        }
        if (steps.stream().anyMatch(step -> step.getStatus() == StepStatus.AWAITING_APPROVAL)) {
            return WorkflowStatus.AWAITING_APPROVAL;
        }
        if (steps.stream().anyMatch(step -> step.getStatus().isFailure())) {
            return WorkflowStatus.FAILED;
        }
        return WorkflowStatus.IN_PROGRESS;
    }

    public boolean isRetryable(WorkflowStep step) {
        return step.getStatus() == StepStatus.FAILED && step.getAttempts() < properties.maxAttempts();
    }

    public int maxAttempts() {
        return properties.maxAttempts();
    }

    private static Map<StepName, StepStatus> statuses(List<WorkflowStep> steps) {
        Map<StepName, StepStatus> statuses = new EnumMap<>(StepName.class);
        steps.forEach(step -> statuses.put(step.getStepName(), step.getStatus()));
        return statuses;
    }
}
