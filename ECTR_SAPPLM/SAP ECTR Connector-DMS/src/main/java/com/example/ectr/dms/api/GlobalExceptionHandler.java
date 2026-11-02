// VERIFIED: Spring 6 RFC 7807/9457 "problem details" error responses.
package com.example.ectr.dms.api;

import com.example.ectr.dms.files.InvalidFileException;
import com.example.ectr.dms.sap.BapiReturn;
import com.example.ectr.dms.service.IntegrationException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Turns every failure into a {@code application/problem+json} body with:
 * {@code type}, {@code title}, {@code status}, {@code detail}, {@code correlationId}, and where relevant
 * {@code transactionId}, {@code errors} (field validation) or {@code sapMessages}.
 * <p>
 * Stack traces and internal details never leave the service; they are logged with the correlation id.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_PREFIX = "urn:problem:ectr-dms:";

    @ExceptionHandler(IntegrationException.class)
    public ProblemDetail integration(IntegrationException e) {
        HttpStatus status = switch (e.kind()) {
            case SAP_REJECTED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case SAP_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case SAP_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        if (e.kind() == IntegrationException.Kind.INTERNAL) {
            LOG.error("Internal error, transaction {}", e.transactionId(), e);
        } else {
            LOG.warn("{} (transaction {}): {}", e.kind(), e.transactionId(), e.getMessage());
        }
        ProblemDetail problem = problem(status, e.kind().name().toLowerCase().replace('_', '-'), e.getMessage());
        if (e.transactionId() != null) {
            problem.setProperty("transactionId", e.transactionId());
        }
        if (!e.sapMessages().isEmpty()) {
            problem.setProperty("sapMessages", e.sapMessages().stream().map(GlobalExceptionHandler::sapMessage).toList());
        }
        return problem;
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "not-found", e.getMessage());
    }

    @ExceptionHandler(InvalidFileException.class)
    public ProblemDetail invalidFile(InvalidFileException e) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "invalid-file", e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    public ProblemDetail badRequest(RuntimeException e) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception e) {
        LOG.error("Unexpected error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal",
                "Unexpected error. Quote correlation id " + MDC.get(CorrelationIdFilter.MDC_KEY) + " to support.");
    }

    /** JSON body validation (@Valid): list every invalid field. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException e,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation", "The request contains invalid fields");
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    /** Validation of headers / path variables (method-level @Validated). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(@NonNull HandlerMethodValidationException e,
                                                                            @NonNull HttpHeaders headers,
                                                                            @NonNull HttpStatusCode status,
                                                                            @NonNull WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation", "The request contains invalid values");
        problem.setProperty("errors", e.getAllValidationResults().stream()
                .map(r -> Map.of("parameter", String.valueOf(r.getMethodParameter().getParameterName()),
                        "message", r.getResolvableErrors().isEmpty() ? "invalid"
                                : String.valueOf(r.getResolvableErrors().get(0).getDefaultMessage())))
                .toList());
        return ResponseEntity.badRequest().body(problem);
    }

    /** Adds type URI and correlation id to the problem details Spring creates itself (405, 413, 415, ...). */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, @NonNull HttpHeaders headers,
                                                          @NonNull HttpStatusCode statusCode,
                                                          @NonNull WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static ProblemDetail problem(HttpStatus status, String type, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + type));
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return problem;
    }

    private static Map<String, String> sapMessage(BapiReturn r) {
        return Map.of("type", r.type(), "id", r.id(), "number", r.number(), "message", r.message());
    }
}
