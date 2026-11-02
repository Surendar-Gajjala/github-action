// Compiled only with profile "jco". Active only when the Spring profile "jco" is set.
package com.example.ectr.dms.sap.jco;

import com.example.ectr.dms.sap.RfcClient;
import com.sap.conn.jco.ext.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Registers the destination provider and exposes the JCo-based {@link RfcClient}. */
@Configuration
@Profile("jco")
@EnableConfigurationProperties(JcoProperties.class)
public class JcoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(JcoConfiguration.class);

    @Bean
    public RfcClient rfcClient(JcoProperties properties) {
        // JCo allows only ONE DestinationDataProvider per JVM; registering twice throws IllegalStateException.
        if (!Environment.isDestinationDataProviderRegistered()) {
            Environment.registerDestinationDataProvider(new SpringDestinationDataProvider(properties));
        }
        LOG.info("SAP JCo enabled: {}", properties); // toString masks the password
        return new JcoRfcClient(properties.destinationName());
    }
}
