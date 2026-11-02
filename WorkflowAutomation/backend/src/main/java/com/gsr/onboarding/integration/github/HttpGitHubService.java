package com.gsr.onboarding.integration.github;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.gsr.onboarding.config.AppProperties;
import com.gsr.onboarding.config.RestClientConfig;
import com.gsr.onboarding.exception.IntegrationException;

/**
 * GitHub REST API client authenticated with a fine-grained personal access token
 * that only needs "Issues: read and write" on the onboarding repository.
 */
@Component
@ConditionalOnProperty(name = "app.integrations.github", havingValue = "live")
public class HttpGitHubService implements GitHubService {

    private static final Logger log = LoggerFactory.getLogger(HttpGitHubService.class);
    private static final String API_VERSION = "2022-11-28";

    private final RestClient restClient;
    private final AppProperties.GitHub properties;

    public HttpGitHubService(@Qualifier(RestClientConfig.GITHUB_CLIENT) RestClient.Builder builder,
                             AppProperties appProperties) {
        this.properties = appProperties.github();
        this.restClient = builder
                .baseUrl(properties.apiUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", API_VERSION)
                .defaultHeader(HttpHeaders.USER_AGENT, "onboarding-workflow-automation")
                .build();
    }

    @Override
    public Optional<GitHubIssue> findIssueByMarker(String owner, String repository, String marker) {
        requireToken();
        try {
            JsonNode issues = restClient.get()
                    .uri("/repos/{owner}/{repo}/issues?state=all&sort=created&direction=desc&per_page=100",
                            owner, repository)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
                    .retrieve()
                    .body(JsonNode.class);
            if (issues == null || !issues.isArray()) {
                return Optional.empty();
            }
            for (JsonNode issue : issues) {
                if (issue.has("pull_request")) {
                    continue;
                }
                if (issue.path("body").asText("").contains(marker)) {
                    return Optional.of(new GitHubIssue(issue.path("number").asInt(), issue.path("html_url").asText()));
                }
            }
            return Optional.empty();
        } catch (RestClientResponseException e) {
            throw translate(e, "listing issues");
        } catch (ResourceAccessException e) {
            throw new IntegrationException("GitHub API is unreachable or timed out.", true, e);
        }
    }

    @Override
    public GitHubIssue createIssue(String owner, String repository, String title, String body, List<String> labels) {
        requireToken();
        Map<String, Object> request = new HashMap<>();
        request.put("title", title);
        request.put("body", body);
        if (labels != null && !labels.isEmpty()) {
            request.put("labels", labels);
        }
        try {
            JsonNode created = restClient.post()
                    .uri("/repos/{owner}/{repo}/issues", owner, repository)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            if (created == null || !created.hasNonNull("number")) {
                throw new IntegrationException("GitHub API returned an unexpected response.", true);
            }
            GitHubIssue issue = new GitHubIssue(created.path("number").asInt(), created.path("html_url").asText());
            log.info("Created GitHub onboarding issue #{} in {}/{}", issue.number(), owner, repository);
            return issue;
        } catch (RestClientResponseException e) {
            throw translate(e, "creating the issue");
        } catch (ResourceAccessException e) {
            throw new IntegrationException("GitHub API is unreachable or timed out.", true, e);
        }
    }

    private void requireToken() {
        if (!properties.hasToken()) {
            throw new IntegrationException("GitHub token is not configured (GITHUB_TOKEN).", false);
        }
    }

    private static IntegrationException translate(RestClientResponseException e, String action) {
        int status = e.getStatusCode().value();
        String message = switch (status) {
            case 401 -> "GitHub rejected the token (HTTP 401) while " + action + ".";
            case 403 -> "GitHub denied access or rate limited the request (HTTP 403) while " + action
                    + ". The token needs Issues read/write permission.";
            case 404 -> "GitHub repository not found or not accessible with this token (HTTP 404).";
            case 410 -> "Issues are disabled for the GitHub repository (HTTP 410).";
            case 422 -> "GitHub rejected the issue content (HTTP 422).";
            default -> "GitHub API returned HTTP " + status + " while " + action + ".";
        };
        boolean retryable = status == 403 || status == 429 || e.getStatusCode().is5xxServerError();
        return new IntegrationException(message, retryable, e);
    }
}
