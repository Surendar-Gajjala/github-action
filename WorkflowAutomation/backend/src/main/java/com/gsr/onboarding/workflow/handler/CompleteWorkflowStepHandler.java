package com.gsr.onboarding.workflow.handler;

import org.springframework.stereotype.Component;

import com.gsr.onboarding.domain.enums.StepName;
import com.gsr.onboarding.workflow.StepHandler;
import com.gsr.onboarding.workflow.StepOutcome;

/** Final marker step; becomes runnable only after every other step succeeded. */
@Component
public class CompleteWorkflowStepHandler implements StepHandler {

    @Override
    public StepName step() {
        return StepName.COMPLETE_WORKFLOW;
    }

    @Override
    public StepOutcome execute(Long workflowId) {
        return StepOutcome.success(null, null);
    }
}
