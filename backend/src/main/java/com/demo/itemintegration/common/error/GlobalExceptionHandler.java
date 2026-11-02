package com.demo.itemintegration.common.error;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.demo.itemintegration.external.ExternalApiException;
import com.demo.itemintegration.itemdetail.ItemNotFoundException;
import com.demo.itemintegration.partdetail.PartNotFoundException;

/**
 * Converts hosted-server failures into RFC 7807 problem responses for the frontend.
 * Responses carry a stable error code and a generic message only; no upstream
 * details, URLs, or credentials are exposed.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ProblemDetail> handleExternalApi(ExternalApiException e) {
        ErrorSpec spec = ErrorSpec.of(e.getReason());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(spec.status(), spec.message());
        problem.setTitle(spec.status().getReasonPhrase());
        problem.setProperty("code", spec.code());

        ResponseEntity.BodyBuilder response = ResponseEntity.status(spec.status());
        e.getRetryAfter().ifPresent(retryAfter ->
                response.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter.toSeconds())));
        return response.body(problem);
    }

    @ExceptionHandler(ItemNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleItemNotFound(ItemNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No item exists with this id.");
        problem.setTitle(HttpStatus.NOT_FOUND.getReasonPhrase());
        problem.setProperty("code", "ITEM_NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(PartNotFoundException.class)
    public ResponseEntity<ProblemDetail> handlePartNotFound(PartNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No part exists with this id.");
        problem.setTitle(HttpStatus.NOT_FOUND.getReasonPhrase());
        problem.setProperty("code", "PART_NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    private record ErrorSpec(HttpStatus status, String code, String message) {

        static ErrorSpec of(ExternalApiException.Reason reason) {
            return switch (reason) {
                case UNAUTHORIZED, FORBIDDEN -> new ErrorSpec(HttpStatus.BAD_GATEWAY,
                        "ITEM_SOURCE_ACCESS_DENIED", "The item data source rejected the request.");
                case NOT_FOUND -> new ErrorSpec(HttpStatus.BAD_GATEWAY,
                        "ITEM_SOURCE_NOT_FOUND", "The item data source endpoint could not be found.");
                case RATE_LIMITED -> new ErrorSpec(HttpStatus.TOO_MANY_REQUESTS,
                        "ITEM_SOURCE_RATE_LIMITED", "The item data source is rate limiting requests. Try again later.");
                case TIMEOUT -> new ErrorSpec(HttpStatus.GATEWAY_TIMEOUT,
                        "ITEM_SOURCE_TIMEOUT", "The item data source did not respond in time.");
                case UNAVAILABLE -> new ErrorSpec(HttpStatus.SERVICE_UNAVAILABLE,
                        "ITEM_SOURCE_UNAVAILABLE", "The item data source is currently unreachable.");
                case INVALID_RESPONSE -> new ErrorSpec(HttpStatus.BAD_GATEWAY,
                        "ITEM_SOURCE_INVALID_RESPONSE", "The item data source returned an unreadable response.");
                case SERVER_ERROR, CLIENT_ERROR -> new ErrorSpec(HttpStatus.BAD_GATEWAY,
                        "ITEM_SOURCE_ERROR", "The item data source failed to process the request.");
            };
        }
    }
}
