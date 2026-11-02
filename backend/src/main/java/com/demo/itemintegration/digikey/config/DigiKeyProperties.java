package com.demo.itemintegration.digikey.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Central configuration for the DigiKey integration — completely independent of the
 * Enterprise ({@code external.api}) configuration and token.
 * <p>
 * Client id / secret are intentionally NOT {@code @NotBlank}: the application must start
 * without DigiKey credentials (the DigiKey endpoints then fail at call time with
 * {@code DIGIKEY_NOT_CONFIGURED}). The secret is masked in {@link #toString()}.
 */
@Validated
@ConfigurationProperties(prefix = "digikey")
public record DigiKeyProperties(
        @NotBlank(message = "DIGIKEY_BASE_URL must be set") String baseUrl,
        @DefaultValue("") String clientId,
        @DefaultValue("") String clientSecret,
        @NotBlank @DefaultValue("IN") String localeSite,
        @NotBlank @DefaultValue("en") String localeLanguage,
        @NotBlank @DefaultValue("INR") String localeCurrency,
        @NotBlank @DefaultValue("resistor") String defaultKeyword,
        @NotNull @DefaultValue("5s") Duration connectTimeout,
        @NotNull @DefaultValue("30s") Duration readTimeout) {

    /** True only when both OAuth credentials are present. */
    public boolean hasCredentials() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    @Override
    public String toString() {
        return "DigiKeyProperties[baseUrl=" + baseUrl
                + ", clientId=" + (clientId == null || clientId.isBlank() ? "(unset)" : "******")
                + ", clientSecret=******"
                + ", localeSite=" + localeSite
                + ", localeLanguage=" + localeLanguage
                + ", localeCurrency=" + localeCurrency
                + ", defaultKeyword=" + defaultKeyword
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
