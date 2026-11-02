package com.gsr.onboarding.integration.email;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.gsr.onboarding.config.AppProperties;
import com.gsr.onboarding.exception.IntegrationException;

/** Records messages in memory instead of sending them. Never contacts an SMTP server. */
@Component
@ConditionalOnProperty(name = "app.integrations.email", havingValue = "mock", matchIfMissing = true)
public class MockEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(MockEmailService.class);
    private static final int MAX_RETAINED = 100;

    private final AtomicBoolean failing;
    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

    public MockEmailService(AppProperties properties) {
        this.failing = new AtomicBoolean(properties.mock().emailFail());
    }

    @Override
    public String send(EmailMessage message) {
        if (failing.get()) {
            throw new IntegrationException("Mock SMTP is configured to fail (APP_MOCK_EMAIL_FAIL=true).", true);
        }
        if (sent.size() >= MAX_RETAINED) {
            sent.remove(0);
        }
        sent.add(message);
        String messageId = "<mock-" + UUID.randomUUID() + "@onboarding.local>";
        log.info("[MOCK] Email not sent to {}; recorded locally as {}", EmailMasking.mask(message.to()), messageId);
        return messageId;
    }

    public void setFailing(boolean fail) {
        failing.set(fail);
    }

    public List<EmailMessage> sentMessages() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }
}
