package com.demo.itemintegration.digikey.config;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Dedicated HTTP client for the DigiKey API, pointed at {@code digikey.base-url} and kept
 * entirely separate from the Enterprise {@code RestClient}. Built from the shared
 * {@link RestClient.Builder} (general-purpose infrastructure only).
 */
@Configuration
public class DigiKeyClientConfig {

    /** Qualifier so this client is never confused with the Enterprise {@code externalApiRestClient}. */
    public static final String DIGIKEY_REST_CLIENT = "digiKeyRestClient";

    @Bean(DIGIKEY_REST_CLIENT)
    RestClient digiKeyRestClient(RestClient.Builder builder, DigiKeyProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder.clone().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }
}
