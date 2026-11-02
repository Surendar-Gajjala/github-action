// VERIFIED: plain Java port. Implementations: MockRfcClient (default) and JcoRfcClient (profile "jco").
package com.example.ectr.dms.sap;

import java.util.function.Function;

/** Executes RFC calls against an SAP system. Implementations must be thread-safe. */
public interface RfcClient {

    /**
     * Runs {@code work} in one <b>stateful</b> session: every call made through the session uses the
     * same backend connection and logical unit of work, so a BAPI and the following
     * BAPI_TRANSACTION_COMMIT belong together. Anything not committed when the session ends is
     * discarded by SAP.
     *
     * @throws SapCommunicationException if SAP cannot be reached or the connection breaks
     */
    <T> T inSession(Function<RfcSession, T> work);

    /**
     * Cheap connectivity check for health monitoring.
     *
     * @throws SapCommunicationException if SAP cannot be reached
     */
    void ping();

    /** One open session. Valid only inside {@link #inSession}. */
    interface RfcSession {
        RfcResult call(RfcCall call);
    }
}
