package com.demo.itemintegration.digikey.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.demo.itemintegration.digikey.config.DigiKeyClientConfig;
import com.demo.itemintegration.digikey.config.DigiKeyProperties;
import com.demo.itemintegration.digikey.exception.DigiKeyApiException;
import com.demo.itemintegration.digikey.exception.DigiKeyApiException.Reason;
import com.demo.itemintegration.digikey.exception.DigiKeyErrors;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Obtains and caches a DigiKey OAuth access token using the 2-legged
 * (client_credentials) flow. The token is reused until shortly before it expires; the
 * client secret and token are never logged. Requires DigiKey credentials — without them
 * callers get {@link Reason#NOT_CONFIGURED}.
 */
@Service
public class DigiKeyAuthService {

    private static final Logger log = LoggerFactory.getLogger(DigiKeyAuthService.class);
    private static final String TOKEN_PATH = "/v1/oauth2/token";
    /** Refresh this long before the real expiry to avoid using a just-expired token. */
    private static final Duration EXPIRY_BUFFER = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final DigiKeyProperties properties;
    private final AtomicReference<CachedToken> cache = new AtomicReference<>();

    public DigiKeyAuthService(@Qualifier(DigiKeyClientConfig.DIGIKEY_REST_CLIENT) RestClient restClient,
            DigiKeyProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /** A valid access token, from cache when possible, otherwise freshly requested. */
    public synchronized String getAccessToken() {
        if (!properties.hasCredentials()) {
            throw new DigiKeyApiException(Reason.NOT_CONFIGURED);
        }
        CachedToken current = cache.get();
        if (current != null && Instant.now().isBefore(current.expiresAt())) {
            return current.token();
        }
        return requestToken().token();
    }

    private CachedToken requestToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());

        TokenResponse response;
        try {
            response = restClient.post()
                    .uri(TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("DigiKey token request failed with status {}", e.getStatusCode().value());
            throw DigiKeyErrors.translate(e);
        } catch (ResourceAccessException e) {
            throw DigiKeyErrors.translate(e);
        }

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new DigiKeyApiException(Reason.INVALID_RESPONSE);
        }

        long expiresIn = response.expiresIn() == null ? 600L : response.expiresIn();
        Instant expiresAt = Instant.now().plusSeconds(expiresIn).minus(EXPIRY_BUFFER);
        CachedToken token = new CachedToken(response.accessToken(), expiresAt);
        cache.set(token);
        log.debug("Obtained a new DigiKey access token (expires in {}s)", expiresIn);
        return token;
    }

    private record CachedToken(String token, Instant expiresAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("token_type") String tokenType) {
    }
}
