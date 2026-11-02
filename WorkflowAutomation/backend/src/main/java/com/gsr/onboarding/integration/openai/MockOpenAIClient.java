package com.gsr.onboarding.integration.openai;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.gsr.onboarding.config.AppProperties;
import com.gsr.onboarding.exception.IntegrationException;

/** Deterministic stand-in for OpenAI used in development and tests. Makes no network calls. */
@Component
@ConditionalOnProperty(name = "app.integrations.openai", havingValue = "mock", matchIfMissing = true)
public class MockOpenAIClient implements OpenAIClient {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    private final AtomicBoolean failing;
    private final AtomicInteger calls = new AtomicInteger();

    public MockOpenAIClient(AppProperties properties) {
        this.failing = new AtomicBoolean(properties.mock().openaiFail());
    }

    @Override
    public GeneratedOnboardingContent generateOnboardingContent(OnboardingPromptInput input) {
        calls.incrementAndGet();
        if (failing.get()) {
            throw new IntegrationException("Mock OpenAI is configured to fail (APP_MOCK_OPENAI_FAIL=true).", true);
        }
        String body = """
                Hi %1$s,

                Welcome to %2$s! We are delighted that you are joining the %3$s team as our new %4$s, starting on %5$s.

                Your first week is about settling in: meeting your teammates, getting your development environment ready and \
                learning how the %3$s team works. Your manager and onboarding buddy will guide you through each step, and the \
                checklist below will help you track your progress.

                If you have any questions before your first day, simply reply to this email.

                We look forward to working with you!

                The %2$s People Team""".formatted(
                input.firstName(), input.companyName(), input.department(), input.jobTitle(),
                input.joiningDate().format(DATE));
        List<String> checklist = List.of(
                "Complete HR paperwork and review the employee handbook",
                "Set up laptop, email and single sign-on",
                "Configure the " + input.department() + " development environment",
                "Request " + input.accessLevel() + " access to the required repository",
                "Meet your manager and onboarding buddy",
                "Join the " + input.department() + " team channels and stand-ups",
                "Review the team documentation and architecture overview");
        return new GeneratedOnboardingContent(
                "Welcome to " + input.companyName() + ", " + input.firstName() + "!",
                body,
                checklist,
                "Onboarding: " + input.firstName() + " (" + input.jobTitle() + ", " + input.department() + ")",
                input.firstName() + " joins " + input.department() + " as " + input.jobTitle() + " on "
                        + input.joiningDate() + ". Please help complete the onboarding tasks below.");
    }

    public void setFailing(boolean fail) {
        failing.set(fail);
    }

    public int callCount() {
        return calls.get();
    }
}
