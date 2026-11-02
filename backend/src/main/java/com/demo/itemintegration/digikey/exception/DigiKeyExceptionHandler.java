package com.demo.itemintegration.digikey.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts {@link DigiKeyApiException}s into RFC 7807 problem responses for the frontend.
 * Dedicated to the DigiKey module and independent of the Enterprise
 * {@code GlobalExceptionHandler}. Responses carry a stable code and a generic message
 * only — never the client secret, access token, or upstream body.
 */
@RestControllerAdvice(basePackages = "com.demo.itemintegration.digikey")
public class DigiKeyExceptionHandler {

    @ExceptionHandler(DigiKeyApiException.class)
    public ResponseEntity<ProblemDetail> handle(DigiKeyApiException e) {
        ErrorSpec spec = ErrorSpec.of(e.getReason());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(spec.status(), spec.message());
        problem.setTitle(spec.status().getReasonPhrase());
        problem.setProperty("code", spec.code());

        ResponseEntity.BodyBuilder response = ResponseEntity.status(spec.status());
        e.getRetryAfter().ifPresent(retryAfter ->
                response.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter.toSeconds())));
        return response.body(problem);
    }

    private record ErrorSpec(HttpStatus status, String code, String message) {

        static ErrorSpec of(DigiKeyApiException.Reason reason) {
            return switch (reason) {
                case NOT_CONFIGURED -> new ErrorSpec(HttpStatus.SERVICE_UNAVAILABLE,
                        "DIGIKEY_NOT_CONFIGURED", "DigiKey is not configured. Set the DigiKey client credentials.");
                case AUTH_FAILED -> new ErrorSpec(HttpStatus.BAD_GATEWAY,
                        "DIGIKEY_AUTH_FAILED", "DigiKey rejected the credentials.");
                case RATE_LIMITED -> new ErrorSpec(HttpStatus.TOO_MANY_REQUESTS,
                        "DIGIKEY_RATE_LIMITED", "DigiKey is rate limiting requests. Try again later.");
                case TIMEOUT -> new ErrorSpec(HttpStatus.GATEWAY_TIMEOUT,
                        "DIGIKEY_TIMEOUT", "DigiKey did not respond in time.");
                case UNAVAILABLE -> new ErrorSpec(HttpStatus.SERVICE_UNAVAILABLE,
                        "DIGIKEY_UNAVAILABLE", "DigiKey is currently unreachable.");
                case INVALID_RESPONSE -> new ErrorSpec(HttpStatus.BAD_GATEWAY,
                        "DIGIKEY_INVALID_RESPONSE", "DigiKey returned an unreadable response.");
                case SERVER_ERROR, CLIENT_ERROR -> new ErrorSpec(HttpStatus.BAD_GATEWAY,
                        "DIGIKEY_ERROR", "DigiKey failed to process the request.");
            };
        }
    }
}
