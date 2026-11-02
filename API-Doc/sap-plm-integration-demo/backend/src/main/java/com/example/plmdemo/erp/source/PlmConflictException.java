package com.example.plmdemo.erp.source;

/** The PLM object is in a state that does not allow the requested change (mapped to HTTP 409). */
public class PlmConflictException extends RuntimeException {

    public PlmConflictException(String message) {
        super(message);
    }
}
