package com.gsr.onboarding.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsr.onboarding.TestFixtures;
import com.gsr.onboarding.config.AppProperties;
import com.gsr.onboarding.exception.IntegrationException;
import com.gsr.onboarding.integration.github.GitHubIssue;
import com.gsr.onboarding.integration.github.HttpGitHubService;
import com.gsr.onboarding.integration.openai.GeneratedOnboardingContent;
import com.gsr.onboarding.integration.openai.HttpOpenAIClient;
import com.gsr.onboarding.integration.openai.OnboardingPromptInput;

class HttpClientsTest {

    private static final AppProperties PROPS =
            TestFixtures.properties(false, true, "https://api.openai.test/v1", "https://api.github.test");
    private static final OnboardingPromptInput INPUT = new OnboardingPromptInput("Priya", "Backend Engineer",
            "Engineering", LocalDate.of(2026, 11, 16), "WRITE", "Acme Corp");

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void openAiStructuredOutputIsParsed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpOpenAIClient client = new HttpOpenAIClient(builder, PROPS, mapper);
        String content = """
                {"emailSubject":"Welcome Priya","emailBody":"Hi Priya, welcome aboard the Engineering team!",\
                "checklist":["Set up laptop","Meet team","Read docs"],"githubIssueTitle":"Onboarding: Priya",\
                "githubIssueSummary":"Priya joins Engineering."}""";
        String response = mapperWrite(java.util.Map.of("choices", List.of(java.util.Map.of(
                "finish_reason", "stop", "message", java.util.Map.of("role", "assistant", "content", content)))));

        server.expect(requestTo("https://api.openai.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("@"))))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        GeneratedOnboardingContent result = client.generateOnboardingContent(INPUT);

        assertThat(result.emailSubject()).isEqualTo("Welcome Priya");
        assertThat(result.checklist()).hasSize(3);
        server.verify();
    }

    @Test
    void openAiServerErrorBecomesRetryableIntegrationException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpOpenAIClient client = new HttpOpenAIClient(builder, PROPS, mapper);
        server.expect(requestTo("https://api.openai.test/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.generateOnboardingContent(INPUT))
                .isInstanceOfSatisfying(IntegrationException.class, e -> {
                    assertThat(e.isRetryable()).isTrue();
                    assertThat(e.getMessage()).contains("503").doesNotContain("sk-test");
                });
    }

    @Test
    void gitHubFindsIssueByMarkerAndCreatesIssues() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpGitHubService service = new HttpGitHubService(builder, PROPS);

        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.github.test/repos/acme-corp/onboarding/issues?state=all")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer ghp-test"))
                .andRespond(withSuccess("""
                        [{"number":7,"html_url":"https://github.com/acme-corp/onboarding/pull/7","body":"<!-- onboarding-workflow:3 -->","pull_request":{}},
                         {"number":8,"html_url":"https://github.com/acme-corp/onboarding/issues/8","body":"<!-- onboarding-workflow:3 -->"}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.test/repos/acme-corp/onboarding/issues"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.title").value("Onboarding: Priya"))
                .andExpect(jsonPath("$.labels[0]").value("onboarding"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"number\":9,\"html_url\":\"https://github.com/acme-corp/onboarding/issues/9\"}"));

        assertThat(service.findIssueByMarker("acme-corp", "onboarding", "<!-- onboarding-workflow:3 -->"))
                .contains(new GitHubIssue(8, "https://github.com/acme-corp/onboarding/issues/8"));
        assertThat(service.createIssue("acme-corp", "onboarding", "Onboarding: Priya", "body", List.of("onboarding")))
                .isEqualTo(new GitHubIssue(9, "https://github.com/acme-corp/onboarding/issues/9"));
        server.verify();
    }

    @Test
    void gitHubForbiddenIsTranslated() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpGitHubService service = new HttpGitHubService(builder, PROPS);
        server.expect(requestTo("https://api.github.test/repos/acme-corp/onboarding/issues"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.createIssue("acme-corp", "onboarding", "t", "b", List.of()))
                .isInstanceOf(IntegrationException.class)
                .hasMessageContaining("HTTP 403")
                .hasMessageNotContaining("ghp-test");
    }

    private String mapperWrite(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
