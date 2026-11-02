package com.gsr.onboarding.workflow.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsr.onboarding.TestFixtures;
import com.gsr.onboarding.domain.entity.OnboardingArtifact;
import com.gsr.onboarding.domain.enums.ArtifactStatus;
import com.gsr.onboarding.domain.enums.ArtifactType;
import com.gsr.onboarding.domain.enums.StepStatus;
import com.gsr.onboarding.exception.IntegrationException;
import com.gsr.onboarding.integration.github.GitHubIssue;
import com.gsr.onboarding.integration.github.GitHubService;
import com.gsr.onboarding.service.ArtifactMetadata;
import com.gsr.onboarding.service.ArtifactMetadataCodec;
import com.gsr.onboarding.service.EmailContentService;
import com.gsr.onboarding.workflow.StepOutcome;
import com.gsr.onboarding.workflow.WorkflowStepTxService;

class CreateGitHubIssueStepHandlerTest {

    private static final Long WORKFLOW_ID = 9L;

    private final WorkflowStepTxService tx = mock(WorkflowStepTxService.class);
    private final GitHubService gitHub = mock(GitHubService.class);
    private final ArtifactMetadataCodec codec = new ArtifactMetadataCodec(new ObjectMapper());
    private final CreateGitHubIssueStepHandler handler =
            new CreateGitHubIssueStepHandler(tx, gitHub, codec, TestFixtures.properties(false, true));

    private final OnboardingArtifact draft = new OnboardingArtifact() {
        @Override
        public Long getId() {
            return 21L;
        }
    };

    @BeforeEach
    void setUp() {
        draft.setArtifactType(ArtifactType.GITHUB_ISSUE);
        draft.setStatus(ArtifactStatus.DRAFT);
        draft.setSubject("Onboarding: Priya");
        draft.setContent(EmailContentService.marker(WORKFLOW_ID) + "\nbody");
        draft.setMetadata(codec.write(new ArtifactMetadata(List.of("a"), "acme-corp/onboarding", "acme-corp/app", null)));
        when(tx.findArtifact(WORKFLOW_ID, ArtifactType.GITHUB_ISSUE)).thenReturn(Optional.of(draft));
    }

    @Test
    void createsIssueWhenNoneExists() {
        when(gitHub.findIssueByMarker(any(), any(), any())).thenReturn(Optional.empty());
        when(gitHub.createIssue(eq("acme-corp"), eq("onboarding"), anyString(), anyString(), anyList()))
                .thenReturn(new GitHubIssue(55, "https://github.com/acme-corp/onboarding/issues/55"));

        StepOutcome outcome = handler.execute(WORKFLOW_ID);

        assertThat(outcome.status()).isEqualTo(StepStatus.SUCCESS);
        assertThat(outcome.externalReference()).isEqualTo("#55");
        verify(tx).markIssueCreated(eq(21L), any());
    }

    @Test
    void reusesExistingIssueFoundByMarker() {
        when(gitHub.findIssueByMarker("acme-corp", "onboarding", EmailContentService.marker(WORKFLOW_ID)))
                .thenReturn(Optional.of(new GitHubIssue(54, "https://github.com/acme-corp/onboarding/issues/54")));

        StepOutcome outcome = handler.execute(WORKFLOW_ID);

        assertThat(outcome.status()).isEqualTo(StepStatus.SUCCESS);
        assertThat(outcome.message()).contains("reused");
        verify(gitHub, never()).createIssue(any(), any(), any(), any(), any());
    }

    @Test
    void alreadyCreatedIssueIsNotRecreated() {
        draft.setStatus(ArtifactStatus.CREATED);
        draft.setExternalId("54");

        StepOutcome outcome = handler.execute(WORKFLOW_ID);

        assertThat(outcome.status()).isEqualTo(StepStatus.SUCCESS);
        verify(gitHub, never()).findIssueByMarker(any(), any(), any());
        verify(gitHub, never()).createIssue(any(), any(), any(), any(), any());
    }

    @Test
    void apiFailureIsRecorded() {
        when(gitHub.findIssueByMarker(any(), any(), any()))
                .thenThrow(new IntegrationException("GitHub rejected the token (HTTP 401) while listing issues.", false));

        StepOutcome outcome = handler.execute(WORKFLOW_ID);

        assertThat(outcome.status()).isEqualTo(StepStatus.FAILED);
        assertThat(outcome.message()).contains("HTTP 401");
    }
}
