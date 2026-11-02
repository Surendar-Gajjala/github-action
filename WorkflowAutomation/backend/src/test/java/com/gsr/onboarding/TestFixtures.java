package com.gsr.onboarding;

import java.time.LocalDate;
import java.util.List;

import com.gsr.onboarding.config.AppProperties;
import com.gsr.onboarding.config.AppProperties.IntegrationMode;
import com.gsr.onboarding.domain.entity.Employee;
import com.gsr.onboarding.domain.enums.AccessLevel;

/** Shared builders for unit tests. */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static AppProperties properties(boolean approvalRequired, boolean fallbackEnabled) {
        return properties(approvalRequired, fallbackEnabled, "https://api.openai.test/v1", "https://api.github.test");
    }

    public static AppProperties properties(boolean approvalRequired, boolean fallbackEnabled,
                                           String openAiUrl, String gitHubUrl) {
        return new AppProperties(
                new AppProperties.Integrations(IntegrationMode.MOCK, IntegrationMode.MOCK, IntegrationMode.MOCK),
                new AppProperties.Workflow(approvalRequired, true, 5, 1000),
                new AppProperties.OpenAi("sk-test", "gpt-4o-mini", openAiUrl, 5, fallbackEnabled,
                        "classpath:templates/fallback-welcome.txt", "You write onboarding content for {company}.",
                        "Acme Corp"),
                new AppProperties.Mail("onboarding@example.com", "Onboarding Team"),
                new AppProperties.GitHub("ghp-test", gitHubUrl, "acme-corp", "onboarding", 5, List.of("onboarding")),
                new AppProperties.Security("hr", "hr-password", "admin", "admin-password", List.of("http://localhost:5173")),
                new AppProperties.Mock(false, false, false));
    }

    public static Employee employee() {
        Employee employee = new Employee();
        employee.setEmployeeNumber("EMP-001234");
        employee.setFirstName("Priya");
        employee.setLastName("Sharma");
        employee.setEmail("priya.sharma@example.com");
        employee.setJobTitle("Backend Engineer");
        employee.setDepartment("Engineering");
        employee.setJoiningDate(LocalDate.now().plusDays(14));
        employee.setReportingManager("Daniel Okafor");
        employee.setGithubUsername("priya-sharma");
        employee.setGithubRepository("acme-corp/payments-service");
        employee.setAccessLevel(AccessLevel.WRITE);
        return employee;
    }
}
