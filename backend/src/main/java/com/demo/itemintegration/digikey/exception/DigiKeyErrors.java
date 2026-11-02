package com.demo.itemintegration.digikey.exception;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import com.demo.itemintegration.digikey.exception.DigiKeyApiException.Reason;

/**
 * Translates low-level HTTP failures into token-free {@link DigiKeyApiException}s. Shared
 * by the auth service and API client so both map DigiKey errors the same way. Never logs
 * or includes the client secret, access token, or upstream body.
 */
public final class DigiKeyErrors {

    private DigiKeyErrors() {
    }

    public static DigiKeyApiException translate(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        return switch (status) {
            case 401, 403 -> new DigiKeyApiException(Reason.AUTH_FAILED);
            case 429 -> new DigiKeyApiException(Reason.RATE_LIMITED, retryAfter(e.getResponseHeaders()));
            default -> new DigiKeyApiException(status >= 500 ? Reason.SERVER_ERROR : Reason.CLIENT_ERROR);
        };
    }

    public static DigiKeyApiException translate(ResourceAccessException e) {
        return new DigiKeyApiException(isTimeout(e) ? Reason.TIMEOUT : Reason.UNAVAILABLE);
    }

    private static Duration retryAfter(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
