package com.gsr.onboarding.config;

import java.time.Duration;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** HTTP clients for external integrations, each with explicit connect and read timeouts. */
@Configuration
public class RestClientConfig {

    public static final String OPENAI_CLIENT = "openAiRestClientBuilder";
    public static final String GITHUB_CLIENT = "gitHubRestClientBuilder";

    @Bean(OPENAI_CLIENT)
    RestClient.Builder openAiRestClientBuilder(AppProperties properties) {
        return builderWithTimeouts(Duration.ofSeconds(properties.openai().timeoutSeconds()));
    }

    @Bean(GITHUB_CLIENT)
    RestClient.Builder gitHubRestClientBuilder(AppProperties properties) {
        return builderWithTimeouts(Duration.ofSeconds(properties.github().timeoutSeconds()));
    }

    private static RestClient.Builder builderWithTimeouts(Duration readTimeout) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(ClientHttpRequestFactories.get(settings));
    }
}
