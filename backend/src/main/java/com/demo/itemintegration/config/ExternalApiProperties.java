package com.demo.itemintegration.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Single, central source of hosted-server connection settings.
 * <p>
 * Populated from environment variables (see {@code application.yml}); the application
 * refuses to start if the URL or token is missing. The token is a secret and is
 * masked in {@link #toString()} so it cannot leak through logging.
 */
@Validated
@ConfigurationProperties(prefix = "external.api")
public record ExternalApiProperties(
        @NotBlank(message = "EXTERNAL_API_URL must be set") String url,
        @NotBlank(message = "EXTERNAL_API_GRAPH_URL must be set") String graphUrl,
        @NotBlank(message = "EXTERNAL_API_OBJECT_URL must be set") String objectUrl,
        @NotBlank(message = "EXTERNAL_API_TOKEN must be set") String token,
        @NotNull @DefaultValue("5s") Duration connectTimeout,
        @NotNull @DefaultValue("30s") Duration readTimeout) {

    @Override
    public String toString() {
        return "ExternalApiProperties[url=" + url
                + ", graphUrl=" + graphUrl
                + ", objectUrl=" + objectUrl
                + ", token=******"
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
