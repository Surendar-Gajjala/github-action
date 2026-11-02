package com.demo.itemintegration.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.demo.itemintegration.config.ExternalApiProperties;
import com.demo.itemintegration.external.ExternalApiException.Reason;
import com.demo.itemintegration.external.GraphQuery;
import com.demo.itemintegration.external.ExternalQuery;
import com.demo.itemintegration.external.dto.ExternalGraphResponse;
import com.demo.itemintegration.external.dto.ExternalItemRecord;
import com.demo.itemintegration.external.dto.ExternalObjectResponse;
import com.demo.itemintegration.external.dto.ExternalPartRecord;
import com.demo.itemintegration.external.dto.ExternalSiteRecord;
import com.demo.itemintegration.external.dto.ExternalQueryResponse;

class ExternalApiClientTest {

    private static final String URL = "https://hosted.example.com/api/items?q=all";
    private static final String PAGED_URL = URL + "&page=0&size=25";
    private static final String GRAPH_URL = "https://hosted.example.com/graph/match/execute";
    private static final String OBJECT_URL = "https://hosted.example.com/objects/partial";
    private static final String TOKEN = "s3cr3t-token";

    private MockRestServiceServer server;
    private ExternalApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ExternalApiProperties properties =
                new ExternalApiProperties(URL, GRAPH_URL, OBJECT_URL, TOKEN, Duration.ofSeconds(1), Duration.ofSeconds(1));
        client = new ExternalApiClient(builder.build(), properties);
    }

    @Test
    void sendsOneAuthenticatedQueryRequestAndParsesResponse() {
        server.expect(once(), requestTo(PAGED_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from.entity").value("item"))
                .andExpect(jsonPath("$.select.length()").value(13))
                .andExpect(jsonPath("$.select[12].alias").value("item__id"))
                .andExpect(jsonPath("$.output.format").value("table"))
                .andRespond(withSuccess("""
                        {"zql": "SELECT", "properties": ["item_number"], "objects": {},
                         "results": [{"item_number": "A"}, {"item_number": "B"}]}
                        """, MediaType.APPLICATION_JSON));

        ExternalQueryResponse response = client.execute(ExternalQuery.ITEMS, 0, 25);

        assertThat(response.records(ExternalItemRecord.class)).hasSize(2);
        server.verify();
    }

    @Test
    void parsesHostedServerEnvelope() {
        server.expect(requestTo(PAGED_URL)).andRespond(withSuccess("""
                {"zql": "SELECT ...", "properties": ["item_number", "item__odm__active"],
                 "objects": [{"id": 1, "name": "item", "dbName": "item"}],
                 "results": [{"item_number": "ITM-1", "item__odm__active": "[\\"true\\"]"}],
                 "objectGraph": null, "pageNumber": 0, "pageSize": 25, "totalElements": 100,
                 "exactTotal": true, "hasMore": true, "queryInfo": {"executionTimeMs": 12}}
                """, MediaType.APPLICATION_JSON));

        ExternalQueryResponse response = client.execute(ExternalQuery.ITEMS, 0, 25);

        assertThat(response.records(ExternalItemRecord.class)).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(100L);
        assertThat(response.hasMore()).isTrue();
    }

    @Test
    void partsQuerySendsPartPayloadToSameEndpoint() {
        server.expect(once(), requestTo(PAGED_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(jsonPath("$.from.entity").value("part"))
                .andExpect(jsonPath("$.select.length()").value(9))
                .andExpect(jsonPath("$.select[8].alias").value("part__id"))
                .andExpect(jsonPath("$.context.tableId").value("objecttype-view-part"))
                .andRespond(withSuccess("{\"results\": [{\"part_number\": \"P-1\"}]}", MediaType.APPLICATION_JSON));

        ExternalQueryResponse response = client.execute(ExternalQuery.PARTS, 0, 25);

        assertThat(response.records(ExternalPartRecord.class).get(0).partNumber().asText()).isEqualTo("P-1");
        server.verify();
    }

    @Test
    void sitesQuerySendsSitePayloadToSameEndpoint() {
        server.expect(once(), requestTo(PAGED_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(jsonPath("$.from.entity").value("site"))
                .andExpect(jsonPath("$.select.length()").value(16))
                .andExpect(jsonPath("$.select[15].alias").value("site__id"))
                .andExpect(jsonPath("$.context.tableId").value("objecttype-view-site"))
                .andRespond(withSuccess("{\"results\": [], \"totalElements\": 0}", MediaType.APPLICATION_JSON));

        ExternalQueryResponse response = client.execute(ExternalQuery.SITES, 0, 25);

        assertThat(response.records(ExternalSiteRecord.class)).isEmpty();
        server.verify();
    }

    @Test
    void hierarchyAnchorsQueryUsesQueryEndpoint() {
        server.expect(once(), requestTo(PAGED_URL))
                .andExpect(jsonPath("$.distinct").value(true))
                .andExpect(jsonPath("$.where.and[0].topOfRelationship").value("item_bom"))
                .andRespond(withSuccess("{\"results\": [{\"anchor\": \"903239\"}]}", MediaType.APPLICATION_JSON));

        client.execute(ExternalQuery.HIERARCHY_ANCHORS, 0, 25);

        server.verify();
    }

    @Test
    void matchGraphPostsAnchorsToGraphEndpoint() {
        server.expect(once(), requestTo(GRAPH_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(jsonPath("$.traversal.anchorValues.length()").value(2))
                .andExpect(jsonPath("$.traversal.anchorValues[0]").value("903239"))
                .andExpect(jsonPath("$.traversal.anchorValues[1]").value("903240"))
                .andExpect(jsonPath("$.traversal.relationshipTypes[1]").value("item_sources"))
                .andExpect(jsonPath("$.traversal.maxDepth").value(6))
                .andExpect(jsonPath("$.select.item.length()").value(25))
                .andExpect(jsonPath("$.select.part[1]").value("manufacturer"))
                .andRespond(withSuccess("""
                        {"nodes": [{"id": 1, "type": "item", "properties": {"item_number": "903239"}}],
                         "edges": [], "zql": "SELECT ...", "queryInfo": {"executionTimeMs": 5}}
                        """, MediaType.APPLICATION_JSON));

        ExternalGraphResponse response = client.matchGraph(GraphQuery.ITEM_HIERARCHY, List.of("903239", "903240"));

        assertThat(response.nodes()).hasSize(1);
        assertThat(response.edges()).isEmpty();
        server.verify();
    }

    @Test
    void itemSourcesPayloadAnchorsOnTheItemId() {
        server.expect(once(), requestTo(GRAPH_URL))
                .andExpect(jsonPath("$.traversal.relationshipTypes[0]").value("item_sources"))
                .andExpect(jsonPath("$.traversal.direction").value("DESCENDANTS"))
                .andExpect(jsonPath("$.traversal.anchorProperty").value("id"))
                .andExpect(jsonPath("$.traversal.anchorValues[0]").value("4332025200"))
                .andRespond(withSuccess("{\"nodes\": [], \"edges\": []}", MediaType.APPLICATION_JSON));

        client.matchGraph(GraphQuery.ITEM_SOURCES, List.of("4332025200"));

        server.verify();
    }

    @Test
    void fetchObjectGetsOneObjectWithBearerToken() {
        server.expect(once(), requestTo(OBJECT_URL + "/4332025200"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess("""
                        {"objectId": 4332025200, "objectTypeDbName": "item",
                         "properties": {"item_number": {"id": 1, "value": "A93548-290", "property_id": 2}}}
                        """, MediaType.APPLICATION_JSON));

        ExternalObjectResponse object = client.fetchObject(4332025200L).orElseThrow();

        assertThat(object.objectTypeDbName()).isEqualTo("item");
        assertThat(object.value("item_number").asText()).isEqualTo("A93548-290");
        server.verify();
    }

    @Test
    void fetchObjectTreatsEmptyBodyAsNotFound() {
        // The hosted server answers an unknown id with 200 and no body.
        server.expect(requestTo(OBJECT_URL + "/1")).andRespond(withSuccess());

        assertThat(client.fetchObject(1L)).isEmpty();
    }

    @Test
    void fetchObjectTranslatesErrors() {
        server.expect(requestTo(OBJECT_URL + "/5")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.fetchObject(5L))
                .isInstanceOfSatisfying(ExternalApiException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.FORBIDDEN));
    }

    @Test
    void matchGraphTranslatesErrors() {
        server.expect(requestTo(GRAPH_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.matchGraph(GraphQuery.ITEM_HIERARCHY, List.of("903239")))
                .isInstanceOfSatisfying(ExternalApiException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.UNAUTHORIZED));
    }

    @Test
    void requestedPageReplacesPagingInConfiguredUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer pagedServer = MockRestServiceServer.bindTo(builder).build();
        ExternalApiClient pagedClient = new ExternalApiClient(builder.build(), new ExternalApiProperties(
                "https://hosted.example.com/execute?page=0&size=25", GRAPH_URL, OBJECT_URL, TOKEN, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        pagedServer.expect(once(), requestTo("https://hosted.example.com/execute?page=3&size=50"))
                .andRespond(withSuccess("{\"results\": []}", MediaType.APPLICATION_JSON));

        pagedClient.execute(ExternalQuery.ITEMS, 3, 50);

        pagedServer.verify();
    }

    @Test
    void parsesTableFormatRows() {
        server.expect(requestTo(PAGED_URL)).andRespond(withSuccess("""
                {"properties": ["item_number", "item__availability_risk", "item__id"],
                 "results": [["ITM-1", "HIGH", 1], ["ITM-2", null, 2]]}
                """, MediaType.APPLICATION_JSON));

        ExternalQueryResponse response = client.execute(ExternalQuery.ITEMS, 0, 25);

        assertThat(response.records(ExternalItemRecord.class)).hasSize(2);
        assertThat(response.records(ExternalItemRecord.class).get(1).itemNumber().asText()).isEqualTo("ITM-2");
        assertThat(response.records(ExternalItemRecord.class).get(1).id().asInt()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({
            "401, UNAUTHORIZED", "403, FORBIDDEN", "404, NOT_FOUND", "429, RATE_LIMITED",
            "500, SERVER_ERROR", "503, SERVER_ERROR", "400, CLIENT_ERROR"
    })
    void translatesUpstreamErrorStatuses(int status, Reason expected) {
        server.expect(requestTo(PAGED_URL)).andRespond(withStatus(HttpStatus.valueOf(status))
                .body("upstream says token " + TOKEN + " is bad"));

        assertThatThrownBy(() -> client.execute(ExternalQuery.ITEMS, 0, 25))
                .isInstanceOfSatisfying(ExternalApiException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(expected);
                    assertThat(e.getMessage()).doesNotContain(TOKEN);
                });
    }

    @Test
    void forwardsRetryAfterOnRateLimit() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "30");
        server.expect(requestTo(PAGED_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        assertThatThrownBy(() -> client.execute(ExternalQuery.ITEMS, 0, 25))
                .isInstanceOfSatisfying(ExternalApiException.class,
                        e -> assertThat(e.getRetryAfter()).contains(Duration.ofSeconds(30)));
    }

    @Test
    void translatesTimeout() {
        server.expect(requestTo(PAGED_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.execute(ExternalQuery.ITEMS, 0, 25))
                .isInstanceOfSatisfying(ExternalApiException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.TIMEOUT));
    }

    @Test
    void translatesConnectionFailure() {
        server.expect(requestTo(PAGED_URL)).andRespond(withException(new ConnectException("Connection refused")));

        assertThatThrownBy(() -> client.execute(ExternalQuery.ITEMS, 0, 25))
                .isInstanceOfSatisfying(ExternalApiException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE));
    }

    @Test
    void translatesUnreadableBody() {
        server.expect(requestTo(PAGED_URL)).andRespond(withSuccess("<html>not json</html>", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.execute(ExternalQuery.ITEMS, 0, 25))
                .isInstanceOfSatisfying(ExternalApiException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.INVALID_RESPONSE));
    }

    @Test
    void propertiesNeverPrintToken() {
        ExternalApiProperties properties =
                new ExternalApiProperties(URL, GRAPH_URL, OBJECT_URL, TOKEN, Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThat(properties.toString()).doesNotContain(TOKEN);
    }
}
