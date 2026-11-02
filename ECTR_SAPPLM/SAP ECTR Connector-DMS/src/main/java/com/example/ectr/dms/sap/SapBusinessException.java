// VERIFIED: plain Java.
package com.example.ectr.dms.sap;

import java.util.List;

/** SAP was reached but refused the request (a BAPI RETURN message of type E or A). */
public class SapBusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<BapiReturn> messages;

    public SapBusinessException(String functionName, List<BapiReturn> messages) {
        super(functionName + " returned: " + messages.stream().map(BapiReturn::toString).toList());
        this.messages = List.copyOf(messages);
    }

    public List<BapiReturn> messages() {
        return messages;
    }
}
