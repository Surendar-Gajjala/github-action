// ILLUSTRATIVE: settings of the simulated SAP system only.
package com.example.ectr.dms.sap.mock;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * @param latency              artificial delay of BAPI_DOCUMENT_CREATE2, to test timeouts
 * @param documentTypes        document types the fake system "knows"; others are rejected like in SAP
 * @param failCommunication    {@code true} = every call fails as if SAP were down
 */
@ConfigurationProperties("mock.sap")
public record MockSapProperties(@DefaultValue("0ms") Duration latency,
                                @DefaultValue({"DRW", "CAD", "DES"}) List<String> documentTypes,
                                @DefaultValue("false") boolean failCommunication) {
}
