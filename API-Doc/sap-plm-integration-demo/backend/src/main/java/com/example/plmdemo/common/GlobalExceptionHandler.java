package com.example.plmdemo.common;

import com.example.plmdemo.cad.ectr.EctrSessionNotFoundException;
import com.example.plmdemo.cad.ectr.EctrStateException;
import com.example.plmdemo.cad.model.CadValidationException;
import com.example.plmdemo.erp.source.PlmConflictException;
import com.example.plmdemo.erp.transfer.BomNotFoundException;
import com.example.plmdemo.erp.validation.BomValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/** Turns exceptions into a consistent JSON error body that always carries the correlation ID. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponseDto> invalidInput(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Request body is invalid", details, req);
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ErrorResponseDto> invalidHeader(InvalidRequestException e, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", e.getMessage(), List.of(), req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponseDto> unreadable(HttpMessageNotReadableException e, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Request body is not valid JSON", List.of(), req);
    }

    @ExceptionHandler(BomNotFoundException.class)
    ResponseEntity<ErrorResponseDto> notFound(BomNotFoundException e, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "BOM_NOT_FOUND", e.getMessage(), List.of(), req);
    }

    @ExceptionHandler(BomValidationException.class)
    ResponseEntity<ErrorResponseDto> validation(BomValidationException e, HttpServletRequest req) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "BOM_VALIDATION_FAILED",
                "BOM cannot be transferred", e.getViolations(), req);
    }

    @ExceptionHandler(CadValidationException.class)
    ResponseEntity<ErrorResponseDto> cadValidation(CadValidationException e, HttpServletRequest req) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "CAD_VALIDATION_FAILED",
                "CAD data cannot be imported into PLM", e.getViolations(), req);
    }

    @ExceptionHandler(PlmConflictException.class)
    ResponseEntity<ErrorResponseDto> plmConflict(PlmConflictException e, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "PLM_CONFLICT", e.getMessage(), List.of(), req);
    }

    @ExceptionHandler(EctrStateException.class)
    ResponseEntity<ErrorResponseDto> ectrState(EctrStateException e, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "ECTR_STEP_ORDER", e.getMessage(), List.of(), req);
    }

    @ExceptionHandler(EctrSessionNotFoundException.class)
    ResponseEntity<ErrorResponseDto> ectrSessionNotFound(EctrSessionNotFoundException e, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "ECTR_SESSION_NOT_FOUND", e.getMessage(), List.of(), req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponseDto> noSuchEndpoint(NoResourceFoundException e, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "No endpoint " + req.getMethod() + " " + req.getRequestURI(),
                List.of(), req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponseDto> unexpected(Exception e, HttpServletRequest req) {
        log.error("Unexpected error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Unexpected error; see server log with the correlation ID", List.of(), req);
    }

    private static ResponseEntity<ErrorResponseDto> error(HttpStatus status, String code, String message,
                                                          List<String> details, HttpServletRequest req) {
        String correlationId = (String) req.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        return ResponseEntity.status(status).body(new ErrorResponseDto(code, message, details, correlationId));
    }
}
