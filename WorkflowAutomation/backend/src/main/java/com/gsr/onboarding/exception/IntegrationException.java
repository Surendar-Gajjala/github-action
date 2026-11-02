package com.gsr.onboarding.exception;

/**
 * Failure of an external integration (OpenAI, SMTP, GitHub). The message is safe to store and show
 * to HR users: it must never contain credentials or full email content.
 */
public class IntegrationException extends RuntimeException {

    private final boolean retryable;

    public IntegrationException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public IntegrationException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
