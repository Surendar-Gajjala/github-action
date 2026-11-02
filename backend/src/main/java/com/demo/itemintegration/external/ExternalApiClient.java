package com.demo.itemintegration.external;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.demo.itemintegration.config.ExternalApiProperties;
import com.demo.itemintegration.external.ExternalApiException.Reason;
import com.demo.itemintegration.external.dto.ExternalGraphResponse;
import com.demo.itemintegration.external.dto.ExternalObjectResponse;
import com.demo.itemintegration.external.dto.ExternalQueryResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The only component that talks to the hosted server. It knows how to call it
 * (URL, Bearer authentication, query payloads, error translation) but nothing about
 * domain mapping. Shared by every entity (Items, Parts, ...).
 */
@Component
public class ExternalApiClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;
    private final ExternalApiProperties properties;
    private final URI queryUri;
    private final Map<ExternalQuery, String> queries = new EnumMap<>(ExternalQuery.class);
    private final URI graphUri;
    private final Map<GraphQuery, ObjectNode> graphQueries = new EnumMap<>(GraphQuery.class);
    private final URI objectBaseUri;

    public ExternalApiClient(RestClient externalApiRestClient, ExternalApiProperties properties) {
        this.restClient = externalApiRestClient;
        this.properties = properties;
        this.queryUri = toUri(properties.url());
        for (ExternalQuery query : ExternalQuery.values()) {
            queries.put(query, loadQuery(query.resource()));
        }
        this.graphUri = toUri(properties.graphUrl());
        for (GraphQuery query : GraphQuery.values()) {
            graphQueries.put(query, loadGraphTemplate(query.resource()));
        }
        this.objectBaseUri = toUri(properties.objectUrl());
    }

    /**
     * Runs one query-config payload for one page with exactly one authenticated request.
     * {@code page} is zero-based; both values override any paging in the configured URL.
     */
    public ExternalQueryResponse execute(ExternalQuery query, int page, int size) {
        URI pageUri = UriComponentsBuilder.fromUri(queryUri)
                .replaceQueryParam("page", page)
                .replaceQueryParam("size", size)
                .build(true)
                .toUri();
        return post(pageUri, queries.get(query), ExternalQueryResponse.class);
    }

    /**
     * Runs one graph-match payload for the given anchor values (item numbers or ids,
     * depending on the payload's {@code anchorProperty}) with exactly one authenticated
     * request to the graph endpoint.
     */
    public ExternalGraphResponse matchGraph(GraphQuery query, List<String> anchorValues) {
        ObjectNode payload = graphQueries.get(query).deepCopy();
        ArrayNode anchors = ((ObjectNode) payload.get("traversal")).putArray("anchorValues");
        anchorValues.forEach(anchors::add);
        return post(graphUri, payload.toString(), ExternalGraphResponse.class);
    }

    /**
     * Fetches one object (any entity) with all its properties in one authenticated
     * request. The hosted server answers an unknown id with an empty 200, so that is
     * returned as empty rather than as an error.
     */
    public Optional<ExternalObjectResponse> fetchObject(long objectId) {
        URI uri = UriComponentsBuilder.fromUri(objectBaseUri).pathSegment(Long.toString(objectId)).build(true).toUri();
        return Optional.ofNullable(call(() -> restClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(properties.token()))
                .retrieve()
                .body(ExternalObjectResponse.class)));
    }

    private <T> T post(URI uri, String jsonBody, Class<T> responseType) {
        T body = call(() -> restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(properties.token()))
                .body(jsonBody)
                .retrieve()
                .body(responseType));
        if (body == null) {
            log.warn("Hosted API returned an empty body");
            throw new ExternalApiException(Reason.INVALID_RESPONSE);
        }
        return body;
    }

    /** Runs one hosted request, translating every failure into a token-free {@link ExternalApiException}. */
    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            Reason reason = isTimeout(e) ? Reason.TIMEOUT : Reason.UNAVAILABLE;
            log.warn("Hosted API not reachable: {}", reason);
            throw new ExternalApiException(reason);
        } catch (RestClientException | HttpMessageConversionException e) {
            log.warn("Hosted API response could not be read: {}", e.getClass().getSimpleName());
            throw new ExternalApiException(Reason.INVALID_RESPONSE);
        }
    }

    private ExternalApiException translate(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        Reason reason = switch (status) {
            case 401 -> Reason.UNAUTHORIZED;
            case 403 -> Reason.FORBIDDEN;
            case 404 -> Reason.NOT_FOUND;
            case 429 -> Reason.RATE_LIMITED;
            default -> status >= 500 ? Reason.SERVER_ERROR : Reason.CLIENT_ERROR;
        };
        // Only the status is logged: never request headers or the upstream body.
        log.warn("Hosted API responded with status {} ({})", status, reason);
        Duration retryAfter = reason == Reason.RATE_LIMITED ? parseRetryAfter(e.getResponseHeaders()) : null;
        return new ExternalApiException(reason, status, retryAfter);
    }

    private static Duration parseRetryAfter(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            return null; // HTTP-date form is not forwarded
        }
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static ObjectNode loadGraphTemplate(String resource) {
        try {
            JsonNode template = JSON.readTree(loadQuery(resource));
            if (!(template instanceof ObjectNode object) || !(object.get("traversal") instanceof ObjectNode)) {
                throw new IllegalStateException("Hosted graph query " + resource + " has no traversal object");
            }
            return object;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse hosted graph query " + resource, e);
        }
    }

    /** Reads a query payload from the classpath and fails startup if it is missing or not valid JSON. */
    private static String loadQuery(String resource) {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            String query = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JSON.readTree(query);
            return query;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load hosted API query " + resource, e);
        }
    }

    /** Uses an already-encoded URL as-is; encodes it only if it is not a valid URI. */
    private static URI toUri(String url) {
        try {
            return URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return UriComponentsBuilder.fromUriString(url.trim()).encode().build().toUri();
        }
    }
}
