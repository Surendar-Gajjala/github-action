package com.gsr.onboarding.workflow.handler;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.gsr.onboarding.config.AppProperties;
import com.gsr.onboarding.domain.entity.OnboardingArtifact;
import com.gsr.onboarding.domain.enums.ArtifactStatus;
import com.gsr.onboarding.domain.enums.ArtifactType;
import com.gsr.onboarding.domain.enums.StepName;
import com.gsr.onboarding.exception.IntegrationException;
import com.gsr.onboarding.integration.github.GitHubIssue;
import com.gsr.onboarding.integration.github.GitHubService;
import com.gsr.onboarding.service.ArtifactMetadataCodec;
import com.gsr.onboarding.service.EmailContentService;
import com.gsr.onboarding.workflow.StepHandler;
import com.gsr.onboarding.workflow.StepOutcome;
import com.gsr.onboarding.workflow.WorkflowStepTxService;

/**
 * Creates the onboarding issue exactly once. Before creating, it looks for an issue carrying this
 * workflow's hidden marker, so a retry after a lost response reuses the existing issue.
 */
@Component
public class CreateGitHubIssueStepHandler implements StepHandler {

    private final WorkflowStepTxService tx;
    private final GitHubService gitHubService;
    private final ArtifactMetadataCodec metadataCodec;
    private final List<String> labels;

    public CreateGitHubIssueStepHandler(WorkflowStepTxService tx, GitHubService gitHubService,
                                        ArtifactMetadataCodec metadataCodec, AppProperties properties) {
        this.tx = tx;
        this.gitHubService = gitHubService;
        this.metadataCodec = metadataCodec;
        this.labels = properties.github().labels() == null ? List.of() : properties.github().labels();
    }

    @Override
    public StepName step() {
        return StepName.CREATE_GITHUB_ISSUE;
    }

    @Override
    public StepOutcome execute(Long workflowId) {
        OnboardingArtifact issueDraft = tx.findArtifact(workflowId, ArtifactType.GITHUB_ISSUE).orElse(null);
        if (issueDraft == null) {
            return StepOutcome.failed("GitHub issue content has not been generated yet.");
        }
        if (issueDraft.getStatus() == ArtifactStatus.CREATED) {
            return StepOutcome.success("#" + issueDraft.getExternalId(), "Issue was already created; it was not created again.");
        }
        String repository = metadataCodec.read(issueDraft.getMetadata()).repository();
        String[] parts = repository == null ? new String[0] : repository.split("/", 2);
        if (parts.length != 2) {
            return StepOutcome.failed("GitHub repository is not configured (GITHUB_DEFAULT_OWNER / GITHUB_DEFAULT_REPOSITORY).");
        }
        try {
            Optional<GitHubIssue> existing = gitHubService.findIssueByMarker(parts[0], parts[1],
                    EmailContentService.marker(workflowId));
            GitHubIssue issue = existing.orElseGet(() -> gitHubService.createIssue(parts[0], parts[1],
                    issueDraft.getSubject(), issueDraft.getContent(), labels));
            tx.markIssueCreated(issueDraft.getId(), issue);
            return StepOutcome.success("#" + issue.number(),
                    existing.isPresent() ? "An existing onboarding issue for this workflow was found and reused." : null);
        } catch (IntegrationException e) {
            tx.transitionArtifact(issueDraft.getId(), EnumSet.of(ArtifactStatus.DRAFT), ArtifactStatus.FAILED);
            return StepOutcome.failed(e.getMessage());
        }
    }
}
