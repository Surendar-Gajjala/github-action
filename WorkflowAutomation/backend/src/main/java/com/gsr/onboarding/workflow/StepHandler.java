package com.gsr.onboarding.workflow;

import com.gsr.onboarding.domain.enums.StepName;

/**
 * Executes one asynchronous workflow step. Implementations run outside any database transaction
 * and must be idempotent: re-running a step after a crash or retry must not repeat a completed side effect.
 */
public interface StepHandler {

    StepName step();

    StepOutcome execute(Long workflowId);
}
