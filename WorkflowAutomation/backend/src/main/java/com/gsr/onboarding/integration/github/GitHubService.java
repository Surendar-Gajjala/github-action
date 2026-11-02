package com.gsr.onboarding.integration.github;

import java.util.List;
import java.util.Optional;

import com.gsr.onboarding.exception.IntegrationException;

/**
 * Creates onboarding issues. This service only manages issues: it never changes repository
 * collaborators or permissions. Access provisioning requires a separate, explicitly authorised process.
 */
public interface GitHubService {

    /**
     * Finds a recently created issue whose body contains the given idempotency marker.
     * Used to avoid duplicate issues when a previous attempt succeeded but its result was not saved.
     */
    Optional<GitHubIssue> findIssueByMarker(String owner, String repository, String marker);

    /** @throws IntegrationException when the GitHub API call fails */
    GitHubIssue createIssue(String owner, String repository, String title, String body, List<String> labels);
}
