package com.gsr.onboarding.workflow;

/** Published when a workflow has new runnable work; handled after the publishing transaction commits. */
public record WorkflowScheduledEvent(Long workflowId) {
}
