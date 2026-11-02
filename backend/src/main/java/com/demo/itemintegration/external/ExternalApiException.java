package com.demo.itemintegration.external;

import java.time.Duration;
import java.util.Optional;

/**
 * Failure while communicating with the hosted server. Messages are fixed, generic
 * strings: they never contain the token, request headers, or the upstream body.
 */
public class ExternalApiException extends RuntimeException {

    public enum Reason {
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        RATE_LIMITED,
        SERVER_ERROR,
        CLIENT_ERROR,
        TIMEOUT,
        UNAVAILABLE,
        INVALID_RESPONSE
    }

    private final Reason reason;
    private final Integer upstreamStatus;
    private final Duration retryAfter;

    public ExternalApiException(Reason reason, Integer upstreamStatus, Duration retryAfter) {
        super("Hosted API call failed: " + reason
                + (upstreamStatus != null ? " (upstream status " + upstreamStatus + ")" : ""));
        this.reason = reason;
        this.upstreamStatus = upstreamStatus;
        this.retryAfter = retryAfter;
    }

    public ExternalApiException(Reason reason) {
        this(reason, null, null);
    }

    public Reason getReason() {
        return reason;
    }

    public Optional<Integer> getUpstreamStatus() {
        return Optional.ofNullable(upstreamStatus);
    }

    public Optional<Duration> getRetryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
