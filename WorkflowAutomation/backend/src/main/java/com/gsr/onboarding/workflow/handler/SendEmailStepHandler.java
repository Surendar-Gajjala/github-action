package com.gsr.onboarding.workflow.handler;

import java.util.EnumSet;

import org.springframework.stereotype.Component;

import com.gsr.onboarding.domain.entity.OnboardingArtifact;
import com.gsr.onboarding.domain.entity.OnboardingWorkflow;
import com.gsr.onboarding.domain.enums.ArtifactStatus;
import com.gsr.onboarding.domain.enums.ArtifactType;
import com.gsr.onboarding.domain.enums.StepName;
import com.gsr.onboarding.exception.IntegrationException;
import com.gsr.onboarding.integration.email.EmailMessage;
import com.gsr.onboarding.integration.email.EmailService;
import com.gsr.onboarding.service.ArtifactMetadata;
import com.gsr.onboarding.service.ArtifactMetadataCodec;
import com.gsr.onboarding.service.EmailContentService;
import com.gsr.onboarding.workflow.StepHandler;
import com.gsr.onboarding.workflow.StepOutcome;
import com.gsr.onboarding.workflow.WorkflowStepTxService;

/**
 * Sends the welcome email at most once. The artifact is moved to SENDING before contacting SMTP;
 * a SENDING artifact found on a later attempt means the outcome is unknown, so it is never resent automatically.
 */
@Component
public class SendEmailStepHandler implements StepHandler {

    private static final EnumSet<ArtifactStatus> SENDABLE =
            EnumSet.of(ArtifactStatus.DRAFT, ArtifactStatus.APPROVED, ArtifactStatus.SEND_FAILED);

    private final WorkflowStepTxService tx;
    private final EmailService emailService;
    private final EmailContentService contentService;
    private final ArtifactMetadataCodec metadataCodec;

    public SendEmailStepHandler(WorkflowStepTxService tx, EmailService emailService,
                                EmailContentService contentService, ArtifactMetadataCodec metadataCodec) {
        this.tx = tx;
        this.emailService = emailService;
        this.contentService = contentService;
        this.metadataCodec = metadataCodec;
    }

    @Override
    public StepName step() {
        return StepName.SEND_EMAIL;
    }

    @Override
    public StepOutcome execute(Long workflowId) {
        OnboardingWorkflow workflow = tx.loadWorkflow(workflowId);
        OnboardingArtifact email = tx.findArtifact(workflowId, ArtifactType.WELCOME_EMAIL).orElse(null);
        if (email == null) {
            return StepOutcome.failed("Welcome email content has not been generated yet.");
        }
        switch (email.getStatus()) {
            case SENT:
                return StepOutcome.success(email.getExternalId(), "Email was already sent; it was not sent again.");
            case SENDING:
                return StepOutcome.uncertain("A previous send attempt was interrupted, so delivery is unknown. "
                        + "An administrator must confirm a forced retry to avoid a duplicate email.");
            case DRAFT:
                if (workflow.isEmailApprovalRequired()) {
                    return StepOutcome.awaitingApproval("Waiting for an HR user to review and approve the email.");
                }
                break;
            default:
                break;
        }
        if (!tx.transitionArtifact(email.getId(), SENDABLE, ArtifactStatus.SENDING)) {
            return StepOutcome.failed("The email changed state while sending was starting. Retry the step.");
        }
        ArtifactMetadata metadata = metadataCodec.read(email.getMetadata());
        EmailMessage message = new EmailMessage(
                email.getRecipient(),
                email.getSubject(),
                contentService.renderPlainText(email.getContent(), metadata.checklist()),
                contentService.renderHtml(email.getContent(), metadata.checklist()),
                "onboarding-workflow-" + workflowId);
        String messageId;
        try {
            messageId = emailService.send(message); // network call: no transaction open
        } catch (IntegrationException e) {
            tx.transitionArtifact(email.getId(), EnumSet.of(ArtifactStatus.SENDING), ArtifactStatus.SEND_FAILED);
            return StepOutcome.failed(e.getMessage());
        }
        tx.markEmailSent(email.getId(), messageId);
        return StepOutcome.success(messageId, null);
    }
}
