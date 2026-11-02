package com.gsr.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsr.onboarding.integration.email.EmailMessage;
import com.gsr.onboarding.integration.email.MockEmailService;

@SpringBootTest(properties = {
        "app.workflow.email-approval-required=true",
        "spring.datasource.url=jdbc:h2:mem:onboarding-approval;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailApprovalIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MockEmailService emailService;

    private String overallStatus(long id) throws Exception {
        String body = mvc.perform(get("/api/employees/{id}/workflow", id).with(OnboardingWorkflowIntegrationTest.HR))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("overallStatus").asText();
    }

    @Test
    void emailIsSentOnlyAfterHrApproval() throws Exception {
        String email = OnboardingWorkflowIntegrationTest.uniqueEmail();
        String created = mvc.perform(post("/api/employees").with(OnboardingWorkflowIntegrationTest.HR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OnboardingWorkflowIntegrationTest.employeeJson(email)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).path("employeeId").asLong();

        await().atMost(Duration.ofSeconds(10)).until(() -> "AWAITING_APPROVAL".equals(overallStatus(id)));
        assertThat(emailService.sentMessages()).noneMatch(m -> m.to().equals(email));
        mvc.perform(get("/api/employees/{id}/email-preview", id).with(OnboardingWorkflowIntegrationTest.HR))
                .andExpect(jsonPath("$.awaitingApproval").value(true))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mvc.perform(post("/api/employees/{id}/email/approve", id).with(OnboardingWorkflowIntegrationTest.HR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Line one\\r\\nBcc: attacker@example.com\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/employees/{id}/email/approve", id).with(OnboardingWorkflowIntegrationTest.HR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Welcome aboard, Priya!\",\"body\":\"Hi Priya,\\n\\nEdited by HR.\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.contentSource").value("EDITED"));

        await().atMost(Duration.ofSeconds(10)).until(() -> "COMPLETED".equals(overallStatus(id)));
        EmailMessage sent = emailService.sentMessages().stream().filter(m -> m.to().equals(email)).findFirst().orElseThrow();
        assertThat(sent.subject()).isEqualTo("Welcome aboard, Priya!");
        assertThat(sent.textBody()).contains("Edited by HR.");

        mvc.perform(post("/api/employees/{id}/email/approve", id).with(OnboardingWorkflowIntegrationTest.HR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_AWAITING_APPROVAL"));
    }
}
