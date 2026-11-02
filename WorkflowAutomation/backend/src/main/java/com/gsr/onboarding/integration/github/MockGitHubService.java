package com.gsr.onboarding.integration.github;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.gsr.onboarding.config.AppProperties;
import com.gsr.onboarding.exception.IntegrationException;

/** In-memory GitHub stand-in. Never calls the GitHub API; issue URLs are placeholders. */
@Component
@ConditionalOnProperty(name = "app.integrations.github", havingValue = "mock", matchIfMissing = true)
public class MockGitHubService implements GitHubService {

    private static final Logger log = LoggerFactory.getLogger(MockGitHubService.class);

    public record MockIssue(int number, String title, String body, String htmlUrl) {
    }

    private final AtomicBoolean failing;
    private final AtomicInteger nextNumber = new AtomicInteger(100);
    private final Map<String, List<MockIssue>> issuesByRepository = new ConcurrentHashMap<>();

    public MockGitHubService(AppProperties properties) {
        this.failing = new AtomicBoolean(properties.mock().githubFail());
    }

    @Override
    public Optional<GitHubIssue> findIssueByMarker(String owner, String repository, String marker) {
        return issuesByRepository.getOrDefault(key(owner, repository), List.of()).stream()
                .filter(issue -> issue.body().contains(marker))
                .findFirst()
                .map(issue -> new GitHubIssue(issue.number(), issue.htmlUrl()));
    }

    @Override
    public GitHubIssue createIssue(String owner, String repository, String title, String body, List<String> labels) {
        if (failing.get()) {
            throw new IntegrationException("Mock GitHub is configured to fail (APP_MOCK_GITHUB_FAIL=true).", true);
        }
        int number = nextNumber.incrementAndGet();
        String url = "https://github.com/" + owner + "/" + repository + "/issues/" + number;
        issuesByRepository.computeIfAbsent(key(owner, repository), k -> new CopyOnWriteArrayList<>())
                .add(new MockIssue(number, title, body, url));
        log.info("[MOCK] GitHub issue not created; recorded locally as {}/{}#{}", owner, repository, number);
        return new GitHubIssue(number, url);
    }

    public void setFailing(boolean fail) {
        failing.set(fail);
    }

    public int createdCount() {
        return issuesByRepository.values().stream().mapToInt(List::size).sum();
    }

    public List<MockIssue> issues(String owner, String repository) {
        return List.copyOf(issuesByRepository.getOrDefault(key(owner, repository), List.of()));
    }

    private static String key(String owner, String repository) {
        return owner + "/" + repository;
    }
}
