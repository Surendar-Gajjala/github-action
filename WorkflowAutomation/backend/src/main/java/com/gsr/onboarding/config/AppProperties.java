package com.gsr.onboarding.config;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed application configuration bound from the {@code app.*} namespace.
 * Secrets (API keys, tokens, passwords) are always supplied through environment variables.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Integrations integrations,
        @Valid @NotNull Workflow workflow,
        @Valid @NotNull OpenAi openai,
        @Valid @NotNull Mail mail,
        @Valid @NotNull GitHub github,
        @Valid @NotNull Security security,
        @Valid @NotNull Mock mock) {

    public enum IntegrationMode { MOCK, LIVE }

    public record Integrations(
            @NotNull IntegrationMode openai,
            @NotNull IntegrationMode email,
            @NotNull IntegrationMode github) {
    }

    public record Workflow(
            boolean emailApprovalRequired,
            boolean githubAfterEmail,
            @Min(1) int maxAttempts,
            @Min(100) long pollIntervalMs) {
    }

    public record OpenAi(
            String apiKey,
            @NotBlank String model,
            @NotBlank String baseUrl,
            @Min(1) int timeoutSeconds,
            boolean fallbackEnabled,
            @NotBlank String fallbackTemplate,
            @NotBlank String systemPrompt,
            @NotBlank String companyName) {

        @Override
        public String toString() {
            // Never expose the API key through logs or actuator output.
            return "OpenAi[model=" + model + ", baseUrl=" + baseUrl + ", apiKeyConfigured=" + hasApiKey() + "]";
        }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Mail(@NotBlank String from, String fromName) {
    }

    public record GitHub(
            String token,
            @NotBlank String apiUrl,
            String defaultOwner,
            String defaultRepository,
            @Min(1) int timeoutSeconds,
            List<String> labels) {

        @Override
        public String toString() {
            return "GitHub[apiUrl=" + apiUrl + ", defaultOwner=" + defaultOwner
                    + ", defaultRepository=" + defaultRepository + ", tokenConfigured=" + hasToken() + "]";
        }

        public boolean hasToken() {
            return token != null && !token.isBlank();
        }
    }

    public record Security(
            @NotBlank String hrUsername,
            @NotBlank String hrPassword,
            @NotBlank String adminUsername,
            @NotBlank String adminPassword,
            List<String> allowedOrigins) {

        @Override
        public String toString() {
            return "Security[hrUsername=" + hrUsername + ", adminUsername=" + adminUsername + "]";
        }
    }

    /** Failure injection switches for mock integrations (development and testing only). */
    public record Mock(boolean openaiFail, boolean emailFail, boolean githubFail) {
    }
}
