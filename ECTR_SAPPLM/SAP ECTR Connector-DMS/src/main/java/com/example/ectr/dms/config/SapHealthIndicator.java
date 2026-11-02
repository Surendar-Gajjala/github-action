// VERIFIED: Spring Boot Actuator health contributor. Shown under GET /actuator/health (component "sap").
package com.example.ectr.dms.config;

import com.example.ectr.dms.sap.SapDocumentGateway;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Reports DOWN when SAP cannot be reached, so load balancers and monitoring notice it. */
@Component("sap")
public class SapHealthIndicator implements HealthIndicator {

    private final SapDocumentGateway gateway;

    public SapHealthIndicator(SapDocumentGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Health health() {
        try {
            gateway.ping();
            return Health.up().build();
        } catch (RuntimeException e) {
            // Message only: no hostnames or credentials in the health output.
            return Health.down().withDetail("error", e.getClass().getSimpleName()).build();
        }
    }
}
