package com.gsr.onboarding.integration.email;

import com.gsr.onboarding.exception.IntegrationException;

public interface EmailService {

    /**
     * Sends the message and returns the provider message ID.
     *
     * @throws IntegrationException when delivery fails
     */
    String send(EmailMessage message);
}
