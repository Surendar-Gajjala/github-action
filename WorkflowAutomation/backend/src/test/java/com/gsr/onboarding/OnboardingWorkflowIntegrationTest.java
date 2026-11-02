package com.gsr.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsr.onboarding.integration.email.MockEmailService;
import com.gsr.onboarding.integration.github.MockGitHubService;
import com.gsr.onboarding.integration.openai.MockOpenAIClient;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingWorkflowIntegrationTest {

    static final RequestPostProcessor HR = httpBasic("hr", "hr-password");
    static final RequestPostProcessor ADMIN = httpBasic("admin", "admin-password");

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MockEmailService emailService;
    @Autowired
    MockGitHubService gitHubService;
    @Autowired
    MockOpenAIClient openAIClient;

    @AfterEach
    void resetMocks() {
        emailService.setFailing(false);
        gitHubService.setFailing(false);
        openAIClient.setFailing(false);
    }

    static String employeeJson(String email) {
        return """
                {"firstName":"Priya","lastName":"Sharma","workEmail":"%s","jobTitle":"Backend Engineer",
                 "department":"Engineering","joiningDate":"%s","reportingManager":"Daniel Okafor",
                 "githubUsername":"priya-sharma","githubRepository":"acme-corp/payments-service","accessLevel":"WRITE"}
                """.formatted(email, LocalDate.now().plusDays(10));
    }

    static String uniqueEmail() {
        return "employee-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    long create(String email, String idempotencyKey) throws Exception {
        var request = post("/api/employees").with(HR).contentType(MediaType.APPLICATION_JSON).content(employeeJson(email));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        String body = mvc.perform(request)
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.employeeNumber").value(org.hamcrest.Matchers.matchesPattern("EMP-\\d{6}")))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("employeeId").asLong();
    }

    JsonNode workflow(long employeeId) throws Exception {
        String body = mvc.perform(get("/api/employees/{id}/workflow", employeeId).with(HR))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    String awaitStatus(long employeeId, String expected) {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100))
                .until(() -> expected.equals(workflow(employeeId).path("overallStatus").asText()));
        return expected;
    }

    long emailsSentTo(String email) {
        return emailService.sentMessages().stream().filter(m -> m.to().equals(email)).count();
    }

    @Test
    void submittedEmployeeIsOnboardedAutomatically() throws Exception {
        String email = uniqueEmail();
        long id = create(email, null);

        awaitStatus(id, "COMPLETED");

        JsonNode workflow = workflow(id);
        assertThat(workflow.path("steps")).hasSize(6);
        workflow.path("steps").forEach(step -> assertThat(step.path("status").asText()).isEqualTo("SUCCESS"));
        assertThat(workflow.path("history").size()).isGreaterThan(1);
        assertThat(emailsSentTo(email)).isEqualTo(1);

        mvc.perform(get("/api/employees/{id}/email-preview", id).with(HR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipient").value(email))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.checklist.length()").value(7));
        mvc.perform(get("/api/employees/{id}/github-issue", id).with(HR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.issueNumber").isNumber())
                .andExpect(jsonPath("$.issueUrl").value(org.hamcrest.Matchers.startsWith("https://github.com/acme-corp/onboarding/issues/")));
        mvc.perform(get("/api/employees").param("q", email).with(HR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].workflowStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].emailStatus").value("SUCCESS"));
    }

    @Test
    void repeatedRequestWithSameIdempotencyKeyDoesNotDuplicate() throws Exception {
        String email = uniqueEmail();
        String key = UUID.randomUUID().toString();
        long id = create(email, key);

        mvc.perform(post("/api/employees").with(HR).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(employeeJson(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(id))
                .andExpect(jsonPath("$.duplicateRequest").value(true));

        mvc.perform(post("/api/employees").with(HR).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(employeeJson(uniqueEmail())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        awaitStatus(id, "COMPLETED");
        assertThat(emailsSentTo(email)).isEqualTo(1);
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = uniqueEmail();
        create(email, null);

        mvc.perform(post("/api/employees").with(HR).contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson(email.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMPLOYEE"));
    }

    @Test
    void invalidRequestReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/employees").with(HR).contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":"Priya","lastName":"Sharma","workEmail":"not-an-email","jobTitle":"Engineer",
                         "department":"Engineering","joiningDate":"2001-01-01","reportingManager":"Dan",
                         "githubUsername":"-bad-","githubRepository":"no-slash","accessLevel":"WRITE"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='workEmail')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='joiningDate')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='githubUsername')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='githubRepository')]").exists());
    }

    @Test
    void failedGitHubStepCanBeRetriedWithoutResendingEmail() throws Exception {
        gitHubService.setFailing(true);
        String email = uniqueEmail();
        long id = create(email, null);

        awaitStatus(id, "FAILED");
        JsonNode failed = workflow(id);
        assertThat(failed.path("retryable").asBoolean()).isTrue();
        assertThat(failed.path("lastError").asText()).contains("Mock GitHub");
        assertThat(emailsSentTo(email)).isEqualTo(1);

        gitHubService.setFailing(false);
        mvc.perform(post("/api/employees/{id}/workflow/retry", id).with(HR)).andExpect(status().isAccepted());

        awaitStatus(id, "COMPLETED");
        JsonNode completed = workflow(id);
        assertThat(emailsSentTo(email)).isEqualTo(1);
        assertThat(completed.path("steps").get(3).path("attempts").asInt()).isEqualTo(1);
        assertThat(completed.path("steps").get(4).path("attempts").asInt()).isEqualTo(2);
    }

    @Test
    void openAiOutageFallsBackToTemplate() throws Exception {
        openAIClient.setFailing(true);
        long id = create(uniqueEmail(), null);

        awaitStatus(id, "COMPLETED");
        JsonNode generate = workflow(id).path("steps").get(2);
        assertThat(generate.path("externalReference").asText()).isEqualTo("source=FALLBACK");
        assertThat(generate.path("errorMessage").asText()).contains("fallback template used");
        mvc.perform(get("/api/employees/{id}/email-preview", id).with(HR))
                .andExpect(jsonPath("$.contentSource").value("FALLBACK"));
    }

    @Test
    void retryWithNothingFailedIsRejectedAndForceRequiresAdmin() throws Exception {
        long id = create(uniqueEmail(), null);
        awaitStatus(id, "COMPLETED");

        mvc.perform(post("/api/employees/{id}/workflow/retry", id).with(HR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTHING_TO_RETRY"));
        mvc.perform(post("/api/employees/{id}/workflow/retry", id).param("force", "true").with(HR))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/employees/{id}/workflow/retry", id).param("force", "true").with(ADMIN))
                .andExpect(status().isConflict());
    }

    @Test
    void securityRulesAreEnforced() throws Exception {
        mvc.perform(get("/api/employees")).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        mvc.perform(get("/api/employees").with(httpBasic("hr", "wrong"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/employees/{id}", 999999).with(HR)).andExpect(status().isNotFound());
    }
}
