package com.demo.itemintegration.config;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Shared HTTP infrastructure for talking to the hosted server. Future external
 * domain clients (Parts, Sites, BOM, ...) can reuse this {@link RestClient}.
 */
@Configuration
public class ExternalApiClientConfig {

    @Bean
    RestClient externalApiRestClient(RestClient.Builder builder, ExternalApiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder.requestFactory(requestFactory).build();
    }
}
