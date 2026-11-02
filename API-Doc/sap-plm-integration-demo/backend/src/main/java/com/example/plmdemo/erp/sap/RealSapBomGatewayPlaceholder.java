package com.example.plmdemo.erp.sap;

import com.example.plmdemo.erp.config.IntegrationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * PLACEHOLDER for a real SAP adapter. Active only when integration.sap.mode=real.
 * It deliberately does nothing and fails every call.
 *
 * To implement:
 *  1. Choose the interface your SAP and PLM versions actually support. Candidates, depending on
 *     release and licensing: an OData/REST API, IDoc via middleware, BAPI/RFC via SAP JCo, or an
 *     SAP PLM integration add-on. Do not assume one is available. Check with your SAP Basis/PLM team.
 *  2. Translate SapBomRequest into that interface's documented payload schema.
 *  3. Map technical errors (timeouts, 5xx, locks) to SapTransientException and business
 *     rejections to SapPermanentException.
 *  4. Pass the idempotency key and correlation ID if the target interface supports it.
 *  5. Read credentials only from environment variables or a secrets manager (see IntegrationProperties.Real).
 */
@Component
@ConditionalOnProperty(name = "integration.sap.mode", havingValue = "real")
public class RealSapBomGatewayPlaceholder implements SapBomGateway {

    private final IntegrationProperties.Real config;

    public RealSapBomGatewayPlaceholder(IntegrationProperties properties) {
        this.config = properties.sap().real();
    }

    @Override
    public SapBomResponse submitBom(SapBomRequest request) {
        throw new SapPermanentException("Real SAP adapter is not implemented. Configured target: "
                + (config.baseUrl() == null || config.baseUrl().isBlank() ? "<none>" : config.baseUrl())
                + ". Implement RealSapBomGatewayPlaceholder for your SAP interface or use integration.sap.mode=mock.");
    }

    @Override
    public String mode() {
        return "REAL-PLACEHOLDER";
    }
}
