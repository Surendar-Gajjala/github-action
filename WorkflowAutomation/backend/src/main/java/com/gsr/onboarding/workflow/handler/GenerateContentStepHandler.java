package com.gsr.onboarding.workflow.handler;

import org.springframework.stereotype.Component;

import com.gsr.onboarding.domain.entity.Employee;
import com.gsr.onboarding.domain.enums.StepName;
import com.gsr.onboarding.exception.IntegrationException;
import com.gsr.onboarding.service.EmailContentService;
import com.gsr.onboarding.service.EmailContentService.ContentResult;
import com.gsr.onboarding.workflow.StepHandler;
import com.gsr.onboarding.workflow.StepOutcome;
import com.gsr.onboarding.workflow.WorkflowStepTxService;

/** Generates the welcome email, checklist and issue content (OpenAI, or the fallback template). */
@Component
public class GenerateContentStepHandler implements StepHandler {

    private final WorkflowStepTxService tx;
    private final EmailContentService contentService;

    public GenerateContentStepHandler(WorkflowStepTxService tx, EmailContentService contentService) {
        this.tx = tx;
        this.contentService = contentService;
    }

    @Override
    public StepName step() {
        return StepName.GENERATE_CONTENT;
    }

    @Override
    public StepOutcome execute(Long workflowId) {
        Employee employee = tx.loadEmployee(workflowId);
        ContentResult result;
        try {
            result = contentService.generate(employee); // network call: no transaction open
        } catch (IntegrationException e) {
            return StepOutcome.failed(e.getMessage());
        }
        tx.saveGeneratedArtifacts(workflowId, employee, result);
        return StepOutcome.success("source=" + result.source(), result.note());
    }
}
