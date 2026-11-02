package com.gsr.onboarding.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Pattern;

import jakarta.validation.constraints.Size;

import com.gsr.onboarding.domain.enums.ArtifactStatus;
import com.gsr.onboarding.domain.enums.ContentSource;
import com.gsr.onboarding.domain.enums.StepStatus;

/** Payloads for the generated email and GitHub issue. */
public final class ArtifactResponses {

    private ArtifactResponses() {
    }

    public record EmailPreviewResponse(
            boolean available,
            String recipient,
            String subject,
            String body,
            List<String> checklist,
            ArtifactStatus status,
            ContentSource contentSource,
            StepStatus stepStatus,
            boolean approvalRequired,
            boolean awaitingApproval,
            Instant sentAt) {

        public static EmailPreviewResponse notAvailable(StepStatus stepStatus, boolean approvalRequired) {
            return new EmailPreviewResponse(false, null, null, null, List.of(), null, null, stepStatus,
                    approvalRequired, false, null);
        }
    }

    /** Optional edits applied by HR when approving the email. Omitted fields keep the generated text. */
    public record EmailApprovalRequest(
            @Size(min = 1, max = 200) @Pattern(regexp = ValidationPatterns.SINGLE_LINE, message = "must be a single line")
            String subject,
            @Size(min = 1, max = 8000)
            String body) {
    }

    public record GitHubIssueResponse(
            boolean available,
            String repository,
            String requestedRepository,
            String issueTitle,
            String issueBody,
            List<String> checklist,
            Integer issueNumber,
            String issueUrl,
            ArtifactStatus status,
            StepStatus stepStatus,
            String errorMessage) {

        public static GitHubIssueResponse notAvailable(StepStatus stepStatus, String errorMessage) {
            return new GitHubIssueResponse(false, null, null, null, null, List.of(), null, null, null,
                    stepStatus, errorMessage);
        }
    }
}
