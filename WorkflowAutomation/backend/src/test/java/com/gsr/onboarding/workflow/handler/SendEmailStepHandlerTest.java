package com.gsr.onboarding.workflow.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsr.onboarding.domain.entity.OnboardingArtifact;
import com.gsr.onboarding.domain.entity.OnboardingWorkflow;
import com.gsr.onboarding.domain.enums.ArtifactStatus;
import com.gsr.onboarding.domain.enums.ArtifactType;
import com.gsr.onboarding.domain.enums.StepStatus;
import com.gsr.onboarding.exception.IntegrationException;
import com.gsr.onboarding.integration.email.EmailService;
import com.gsr.onboarding.service.ArtifactMetadataCodec;
import com.gsr.onboarding.service.EmailContentService;
import com.gsr.onboarding.workflow.StepOutcome;
import com.gsr.onboarding.workflow.WorkflowStepTxService;

class SendEmailStepHandlerTest {

    private static final Long WORKFLOW_ID = 7L;

    private final WorkflowStepTxService tx = mock(WorkflowStepTxService.class);
    private final EmailService emailService = mock(EmailService.class);
    private final EmailContentService contentService = mock(EmailContentService.class);
    private final SendEmailStepHandler handler = new SendEmailStepHandler(tx, emailService, contentService,
            new ArtifactMetadataCodec(new ObjectMapper()));

    private final OnboardingWorkflow workflow = new OnboardingWorkflow();
    private final OnboardingArtifact email = new OnboardingArtifact() {
        @Override
        public Long getId() {
            return 11L;
        }
    };

    @BeforeEach
    void setUp() {
        email.setArtifactType(ArtifactType.WELCOME_EMAIL);
        email.setRecipient("priya.sharma@example.com");
        email.setSubject("Welcome");
        email.setContent("Hi Priya");
        when(tx.loadWorkflow(WORKFLOW_ID)).thenReturn(workflow);
        when(tx.findArtifact(WORKFLOW_ID, ArtifactType.WELCOME_EMAIL)).thenReturn(Optional.of(email));
        when(tx.transitionArtifact(eq(11L), anyCollection(), eq(ArtifactStatus.SENDING))).thenReturn(true);
        when(contentService.renderPlainText(any(), any())).thenReturn("text");
        when(contentService.renderHtml(any(), any())).thenReturn("<p>html</p>");
    }

    @Test
    void alreadySentEmailIsNotResent() {
        email.setStatus(ArtifactStatus.SENT);
        email.setExternalId("<id@mail>");

        StepOutcome outcome = handler.execute(WORKFLOW_ID);

        assertThat(outcome.status()).isEqualTo(StepStatus.SUCCESS);
        verify(emailService, never()).send(any());
    }

    @Test
    void interruptedSendIsReportedAsUncertainWithoutResending() {
        email.setStatus(ArtifactStatus.SENDING);

        StepOutcome outcome = handler.execute(WORKFLOW_ID);

        assertThat(outcome.status()).isEqualTo(StepStatus.FAILED_UNCERTAIN);
        verify(emailService, never()).send(any());
    }

    @Test
    void draftWaitsForApprovalWhenRequired() {
        workflow.setEmailApprovalRequired(true);
        email.setStatus(ArtifactStatus.DRAFT);

        StepOutcome outcome = handler.execute(WORKFLOW_ID);

        assertThat(outcome.status()).isEqualTo(StepStatus.AWAITING_APPROVAL);
        verify(emailService, never()).send(any());
    }

    @Test
    void sendsDraftWhenApprovalNotRequired() {
        email.setStatus(ArtifactStatus.DRAFT);
        when(emailService.send(any())).thenReturn("<msg-1@mail>");

        StepOutcome outcome = handler.execute(WORKFLOW_ID);

        assertThat(outcome.status()).isEqualTo(StepStatus.SUCCESS);
        assertThat(outcome.externalReference()).isEqualTo("<msg-1@mail>");
        verify(tx).markEmailSent(11L, "<msg-1@mail>");
    }

    @Test
    void smtpFailureMarksArtifactSendFailed() {
        email.setStatus(ArtifactStatus.APPROVED);
        when(emailService.send(any())).thenThrow(new IntegrationException("SMTP server rejected the message.", true));

        StepOutcome outcome = handler.execute(WORKFLOW_ID);

        assertThat(outcome.status()).isEqualTo(StepStatus.FAILED);
        assertThat(outcome.message()).contains("SMTP");
        verify(tx).transitionArtifact(11L, EnumSet.of(ArtifactStatus.SENDING), ArtifactStatus.SEND_FAILED);
        verify(tx, never()).markEmailSent(any(), any());
    }
}
