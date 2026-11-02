// VERIFIED: plain Java.
package com.example.ectr.dms.sap;

/** SAP could not be reached, the logon failed, or the connection broke during a call. */
public class SapCommunicationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SapCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }

    public SapCommunicationException(String message) {
        super(message);
    }
}
