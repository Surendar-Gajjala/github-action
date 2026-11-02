package com.gsr.onboarding.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gsr.onboarding.config.AppProperties;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Health")
public class HealthController {

    private final HealthEndpoint healthEndpoint;
    private final AppProperties properties;

    public HealthController(HealthEndpoint healthEndpoint, AppProperties properties) {
        this.healthEndpoint = healthEndpoint;
        this.properties = properties;
    }

    @GetMapping("/api/health")
    @Operation(summary = "Application health check", description = "Public. Reports overall status and integration modes; never secrets.")
    public ResponseEntity<Map<String, Object>> health() {
        Status status = healthEndpoint.health().getStatus();
        AppProperties.Integrations integrations = properties.integrations();
        Map<String, Object> body = Map.of(
                "status", status.getCode(),
                "timestamp", Instant.now().toString(),
                "integrations", Map.of(
                        "openai", integrations.openai(),
                        "email", integrations.email(),
                        "github", integrations.github()),
                "emailApprovalRequired", properties.workflow().emailApprovalRequired());
        return ResponseEntity.status(Status.UP.equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
