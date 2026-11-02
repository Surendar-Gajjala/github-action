package com.example.plmdemo.erp.sap;

/** SAP rejected the request. Retrying with the same data will not help. */
public class SapPermanentException extends RuntimeException {

    public SapPermanentException(String message) {
        super(message);
    }

    public SapPermanentException(String message, Throwable cause) {
        super(message, cause);
    }
}
