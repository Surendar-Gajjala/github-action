package com.demo.itemintegration.digikey.exception;

import java.time.Duration;
import java.util.Optional;

/**
 * Failure while talking to DigiKey (or missing configuration). Messages are fixed,
 * generic strings: they never contain the client secret or access token.
 */
public class DigiKeyApiException extends RuntimeException {

    public enum Reason {
        NOT_CONFIGURED,
        AUTH_FAILED,
        RATE_LIMITED,
        SERVER_ERROR,
        CLIENT_ERROR,
        TIMEOUT,
        UNAVAILABLE,
        INVALID_RESPONSE
    }

    private final Reason reason;
    private final Duration retryAfter;

    public DigiKeyApiException(Reason reason) {
        this(reason, null);
    }

    public DigiKeyApiException(Reason reason, Duration retryAfter) {
        super("DigiKey API call failed: " + reason);
        this.reason = reason;
        this.retryAfter = retryAfter;
    }

    public Reason getReason() {
        return reason;
    }

    public Optional<Duration> getRetryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
