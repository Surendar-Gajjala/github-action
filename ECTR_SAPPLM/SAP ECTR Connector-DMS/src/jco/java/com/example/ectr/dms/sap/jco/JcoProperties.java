// Compiled only with profile "jco". Values come from environment variables (application-jco.yml).
package com.example.ectr.dms.sap.jco;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

/**
 * Connection to one SAP application server.
 * For message-server (load-balanced) logon, add mshost/r3name/group and the matching
 * DestinationDataProvider.JCO_MSHOST / JCO_R3NAME / JCO_GROUP properties.
 */
@Validated
@Profile("jco") // keeps @ConfigurationPropertiesScan from validating it when running with the mock
@ConfigurationProperties("sap.jco")
public record JcoProperties(@DefaultValue("ECTR_DMS") @NotBlank String destinationName,
                            @NotBlank String ashost,
                            @DefaultValue("00") @NotBlank String sysnr,
                            @NotBlank String client,
                            @NotBlank String user,
                            @NotBlank String password,
                            @DefaultValue("EN") @NotBlank String language,
                            @DefaultValue("3") int poolCapacity,
                            @DefaultValue("10") int peakLimit) {

    /** Never print the password, not even by accident in a log statement. */
    @Override
    public String toString() {
        return "JcoProperties[destination=" + destinationName + ", ashost=" + ashost + ", sysnr=" + sysnr
                + ", client=" + client + ", user=" + user + ", password=****, language=" + language
                + ", poolCapacity=" + poolCapacity + ", peakLimit=" + peakLimit + "]";
    }
}
