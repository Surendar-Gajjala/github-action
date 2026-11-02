package com.example.plmdemo.erp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under the "integration" prefix in application.yml.
 * Secrets (SAP user/password) are only ever read from environment variables.
 */
@ConfigurationProperties(prefix = "integration")
public record IntegrationProperties(Sap sap, Retry retry, Mapping mapping) {

    public record Sap(String mode, Mock mock, Real real) {
    }

    /** Behaviour of MockSapBomGateway. failureMode: NONE | TRANSIENT_ONCE | ALWAYS_TRANSIENT | PERMANENT */
    public record Mock(String failureMode) {
    }

    /** Placeholders for a real adapter. Not used in mock mode. */
    public record Real(String baseUrl, String client, String username, String password) {
    }

    public record Retry(int maxAttempts, long backoffMillis) {
    }

    /** Target-side defaults applied by BomMapper. These are demo values, not SAP defaults. */
    public record Mapping(String bomUsage, String defaultUnit) {
    }
}
