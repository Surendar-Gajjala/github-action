// VERIFIED: Spring Boot type-safe configuration.
package com.example.ectr.dms.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Settings under {@code app.*} in application.yml. Invalid values stop the application at startup. */
@Validated
@ConfigurationProperties("app")
public record IntegrationProperties(@Valid @NotNull Sap sap, @Valid @NotNull Storage storage) {

    /**
     * @param callTimeout   maximum time a REST caller waits for SAP
     * @param workerThreads parallel SAP calls; keep at or below the JCo pool capacity / peak limit
     * @param queueCapacity requests waiting for a worker before the service answers 503
     */
    public record Sap(@DefaultValue("30s") @NotNull Duration callTimeout,
                      @DefaultValue("4") @Min(1) int workerThreads,
                      @DefaultValue("50") @Min(0) int queueCapacity) {
    }

    /**
     * @param root         directory for received original files
     * @param allowedTypes file kinds accepted for upload (PDF, DXF)
     */
    public record Storage(@DefaultValue("./data/originals") @NotNull Path root,
                          @DefaultValue({"PDF", "DXF"}) @NotEmpty List<String> allowedTypes) {
    }
}
