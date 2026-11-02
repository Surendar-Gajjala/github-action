package com.demo.itemintegration.digikey.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

import com.demo.itemintegration.digikey.auth.DigiKeyAuthService;
import com.demo.itemintegration.digikey.config.DigiKeyClientConfig;
import com.demo.itemintegration.digikey.config.DigiKeyProperties;
import com.demo.itemintegration.digikey.dto.DigiKeyKeywordResponse;
import com.demo.itemintegration.digikey.dto.DigiKeyProductDetailResponse;
import com.demo.itemintegration.digikey.dto.DigiKeySearchRequest;
import com.demo.itemintegration.digikey.exception.DigiKeyApiException;
import com.demo.itemintegration.digikey.exception.DigiKeyApiException.Reason;
import com.demo.itemintegration.digikey.exception.DigiKeyErrors;

/**
 * The only component that talks to the DigiKey Product Information V4 API. Knows how to
 * call it (bearer token from {@link DigiKeyAuthService}, the required {@code X-DIGIKEY-*}
 * headers, error translation) but nothing about domain mapping. Never logs the token.
 */
@Component
public class DigiKeyApiClient {

    private static final Logger log = LoggerFactory.getLogger(DigiKeyApiClient.class);
    private static final String KEYWORD_PATH = "/products/v4/search/keyword";

    private final RestClient restClient;
    private final DigiKeyAuthService authService;
    private final DigiKeyProperties properties;

    public DigiKeyApiClient(@Qualifier(DigiKeyClientConfig.DIGIKEY_REST_CLIENT) RestClient restClient,
            DigiKeyAuthService authService, DigiKeyProperties properties) {
        this.restClient = restClient;
        this.authService = authService;
        this.properties = properties;
    }

    /** One keyword search (POST). */
    public DigiKeyKeywordResponse searchKeyword(DigiKeySearchRequest request) {
        String token = authService.getAccessToken();
        return call(() -> restClient.post()
                .uri(KEYWORD_PATH)
                .headers(headers -> applyHeaders(headers, token))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(DigiKeyKeywordResponse.class));
    }

    /** One product-detail lookup by DigiKey (or manufacturer) product number (GET). */
    public DigiKeyProductDetailResponse getProduct(String productNumber) {
        String token = authService.getAccessToken();
        String encoded = UriUtils.encodePathSegment(productNumber, StandardCharsets.UTF_8);
        return call(() -> restClient.get()
                .uri("/products/v4/search/{pn}/productdetails", encoded)
                .headers(headers -> applyHeaders(headers, token))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(DigiKeyProductDetailResponse.class));
    }

    private void applyHeaders(HttpHeaders headers, String token) {
        headers.setBearerAuth(token);
        headers.set("X-DIGIKEY-Client-Id", properties.clientId());
        headers.set("X-DIGIKEY-Locale-Site", properties.localeSite());
        headers.set("X-DIGIKEY-Locale-Language", properties.localeLanguage());
        headers.set("X-DIGIKEY-Locale-Currency", properties.localeCurrency());
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        T body;
        try {
            body = request.get();
        } catch (RestClientResponseException e) {
            log.warn("DigiKey API responded with status {}", e.getStatusCode().value());
            throw DigiKeyErrors.translate(e);
        } catch (ResourceAccessException e) {
            throw DigiKeyErrors.translate(e);
        }
        if (body == null) {
            throw new DigiKeyApiException(Reason.INVALID_RESPONSE);
        }
        return body;
    }
}
