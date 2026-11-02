package com.example.plmdemo.erp.sap;

/** Temporary SAP-side failure. Safe to retry. */
public class SapTransientException extends RuntimeException {

    public SapTransientException(String message) {
        super(message);
    }

    public SapTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
