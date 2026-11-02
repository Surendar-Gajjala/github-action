// VERIFIED: plain Java.
package com.example.ectr.dms.api;

/** Requested document, file or transaction does not exist. Mapped to HTTP 404. */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}
