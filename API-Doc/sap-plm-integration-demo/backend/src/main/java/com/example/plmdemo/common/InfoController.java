package com.example.plmdemo.common;

import com.example.plmdemo.erp.sap.SapBomGateway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * GET /api/v1/info: which back ends the demo pages are talking to.
 *   gatewayMode    ERP path SAP gateway (MOCK or REAL-PLACEHOLDER)
 *   ectrBackend    CAD path ECTR + SAP backend (always MOCK in this demo)
 */
@RestController
public class InfoController {

    private final SapBomGateway gateway;

    public InfoController(SapBomGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/api/v1/info")
    public Map<String, String> info() {
        return Map.of("gatewayMode", gateway.mode(), "ectrBackend", "MOCK");
    }
}
