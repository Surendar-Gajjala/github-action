// VERIFIED: plain Java.
package com.example.ectr.dms.domain;

/** Life cycle of one document-creation request, as shown by GET /api/v1/transactions/{id}. */
public enum TransactionState {
    /** Request accepted and validated, waiting for a free SAP worker. */
    RECEIVED(false),
    /** BAPI_DOCUMENT_CREATE2 is running. */
    SAP_CALL_STARTED(false),
    /** SAP created the document in the current LUW; commit not yet done. */
    SAP_DOCUMENT_CREATED(false),
    /** BAPI_TRANSACTION_COMMIT succeeded: the document exists in SAP. */
    COMMITTED(true),
    /** SAP refused the request (RETURN type E/A); rolled back. */
    REJECTED_BY_SAP(true),
    /** SAP unreachable or connection broken. Nothing was committed by this request. */
    COMMUNICATION_FAILED(true),
    /**
     * Our timeout expired before SAP answered. The SAP call may still finish; the state is updated
     * if it does. Until then the outcome is unknown: check the document in SAP before retrying.
     */
    TIMEOUT_OUTCOME_UNKNOWN(false),
    /** Unexpected error in the middleware. */
    FAILED(true);

    private final boolean terminal;

    TransactionState(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
