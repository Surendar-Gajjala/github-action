package com.demo.itemintegration.itemdetail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.demo.itemintegration.external.ExternalApiClient;
import com.demo.itemintegration.external.GraphQuery;
import com.demo.itemintegration.external.dto.ExternalGraphResponse;
import com.demo.itemintegration.external.dto.ExternalObjectResponse;
import com.demo.itemintegration.itemdetail.ItemNotFoundException;
import com.demo.itemintegration.itemdetail.mapper.ItemOverviewMapper;
import com.demo.itemintegration.itemdetail.mapper.ItemSourcesMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

class ItemDetailsServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ExternalApiClient client = mock(ExternalApiClient.class);
    private final ItemDetailsService service =
            new ItemDetailsService(client, new ItemOverviewMapper(), new ItemSourcesMapper());

    @Test
    void overviewIsOneObjectCall() throws Exception {
        when(client.fetchObject(4332025200L)).thenReturn(Optional.of(json.readValue("""
                {"objectId": 4332025200, "objectTypeDbName": "item",
                 "properties": {"item_number": {"value": "A93548-290"}}}
                """, ExternalObjectResponse.class)));

        assertThat(service.getOverview(4332025200L).itemNumber()).isEqualTo("A93548-290");

        verify(client, times(1)).fetchObject(4332025200L);
        verifyNoMoreInteractions(client);
    }

    @Test
    void unknownIdIsNotFound() {
        when(client.fetchObject(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOverview(1L)).isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void nonItemObjectIsNotFound() throws Exception {
        when(client.fetchObject(4332092700L)).thenReturn(Optional.of(json.readValue("""
                {"objectId": 4332092700, "objectTypeDbName": "part", "properties": {}}
                """, ExternalObjectResponse.class)));

        assertThatThrownBy(() -> service.getOverview(4332092700L)).isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void sourcesAreOneGraphCallAnchoredOnTheId() throws Exception {
        ExternalGraphResponse empty = json.readValue("{\"nodes\": [], \"edges\": []}", ExternalGraphResponse.class);
        when(client.matchGraph(GraphQuery.ITEM_SOURCES, List.of("4332025200"))).thenReturn(empty);

        assertThat(service.getSources(4332025200L)).isEmpty();

        verify(client, times(1)).matchGraph(GraphQuery.ITEM_SOURCES, List.of("4332025200"));
        verifyNoMoreInteractions(client);
    }
}
