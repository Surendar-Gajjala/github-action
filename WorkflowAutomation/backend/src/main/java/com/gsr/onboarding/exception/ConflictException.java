package com.gsr.onboarding.exception;

/** Request conflicts with current state: duplicates, ineligible retries, invalid approval state. */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
