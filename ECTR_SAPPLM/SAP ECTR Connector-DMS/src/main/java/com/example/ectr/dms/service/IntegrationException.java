// VERIFIED: plain Java.
package com.example.ectr.dms.service;

import com.example.ectr.dms.sap.BapiReturn;

import java.util.List;

/** A create/read request that could not be completed. The API layer maps {@link Kind} to an HTTP status. */
public class IntegrationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Kind {
        /** SAP refused the data (BAPI RETURN E/A) - HTTP 422. */
        SAP_REJECTED,
        /** SAP not reachable or service overloaded - HTTP 503. */
        SAP_UNAVAILABLE,
        /** SAP did not answer in time; outcome unknown - HTTP 504. */
        SAP_TIMEOUT,
        /** Bug or unexpected state in the middleware - HTTP 500. */
        INTERNAL
    }

    private final Kind kind;
    private final String transactionId;
    private final transient List<BapiReturn> sapMessages;

    public IntegrationException(Kind kind, String transactionId, String message, List<BapiReturn> sapMessages,
                                Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.transactionId = transactionId;
        this.sapMessages = List.copyOf(sapMessages);
    }

    public Kind kind() {
        return kind;
    }

    /** May be {@code null} for read requests, which have no transaction. */
    public String transactionId() {
        return transactionId;
    }

    public List<BapiReturn> sapMessages() {
        return sapMessages;
    }
}
