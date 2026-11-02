package com.gsr.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import com.gsr.onboarding.TestFixtures;
import com.gsr.onboarding.domain.entity.Employee;
import com.gsr.onboarding.domain.enums.ContentSource;
import com.gsr.onboarding.exception.IntegrationException;
import com.gsr.onboarding.integration.openai.GeneratedOnboardingContent;
import com.gsr.onboarding.integration.openai.OpenAIClient;

class EmailContentServiceTest {

    private final OpenAIClient openAIClient = mock(OpenAIClient.class);
    private final Employee employee = TestFixtures.employee();

    private EmailContentService service(boolean fallbackEnabled) {
        return new EmailContentService(openAIClient, Validation.buildDefaultValidatorFactory().getValidator(),
                TestFixtures.properties(false, fallbackEnabled), new DefaultResourceLoader());
    }

    private static GeneratedOnboardingContent content(String body) {
        return new GeneratedOnboardingContent("Welcome to Acme Corp, Priya!", body,
                List.of("- [ ] Set up laptop", "2. Meet the team", "Review documentation", "Meet the team"),
                "Onboarding: Priya", "Priya joins Engineering.");
    }

    @Test
    void acceptsValidAiOutputAndNormalisesChecklist() {
        when(openAIClient.generateOnboardingContent(any())).thenReturn(
                content("Hi Priya,\n\nWelcome to the Engineering team. We are glad to have you with us."));

        EmailContentService.ContentResult result = service(true).generate(employee);

        assertThat(result.source()).isEqualTo(ContentSource.AI);
        assertThat(result.note()).isNull();
        assertThat(result.content().checklist())
                .containsExactly("Set up laptop", "Meet the team", "Review documentation");
    }

    @Test
    void rejectsAiOutputWithLinksAndUsesFallback() {
        when(openAIClient.generateOnboardingContent(any())).thenReturn(
                content("Hi Priya, please log in at https://evil.example.com to confirm your account details today."));

        EmailContentService.ContentResult result = service(true).generate(employee);

        assertThat(result.source()).isEqualTo(ContentSource.FALLBACK);
        assertThat(result.note()).contains("links");
        assertThat(result.content().emailBody()).contains("Hi Priya").contains("Daniel Okafor").doesNotContain("{{");
    }

    @Test
    void usesFallbackWhenOpenAiIsUnavailable() {
        when(openAIClient.generateOnboardingContent(any()))
                .thenThrow(new IntegrationException("OpenAI API is unreachable or timed out.", true));

        EmailContentService.ContentResult result = service(true).generate(employee);

        assertThat(result.source()).isEqualTo(ContentSource.FALLBACK);
        assertThat(result.note()).contains("unreachable");
        assertThat(result.content().checklist()).isEqualTo(EmailContentService.FALLBACK_CHECKLIST);
    }

    @Test
    void propagatesFailureWhenFallbackDisabled() {
        when(openAIClient.generateOnboardingContent(any()))
                .thenThrow(new IntegrationException("OpenAI API returned HTTP 500.", true));

        assertThatThrownBy(() -> service(false).generate(employee))
                .isInstanceOf(IntegrationException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void htmlRenderingEscapesContent() {
        String html = service(true).renderHtml("Hi <b>Priya</b>\n\nSecond & last", List.of("<script>x</script>"));

        assertThat(html).contains("Hi &lt;b&gt;Priya&lt;/b&gt;").contains("Second &amp; last")
                .contains("&lt;script&gt;").doesNotContain("<script>");
    }

    @Test
    void issueBodyCarriesMarkerAndNeutralisesMentions() {
        String body = service(true).buildIssueBody(employee, 42L, "Ping @octocat <!-- fake -->", List.of("Meet @team"));

        assertThat(body).startsWith(EmailContentService.marker(42L));
        assertThat(body).doesNotContain("@octocat").doesNotContain("<!-- fake");
        assertThat(body).contains("- [ ] Development environment setup").contains("does not grant any access");
    }
}
