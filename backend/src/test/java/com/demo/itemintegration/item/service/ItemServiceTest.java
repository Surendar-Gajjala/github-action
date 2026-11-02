package com.demo.itemintegration.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.demo.itemintegration.external.ExternalApiClient;
import com.demo.itemintegration.external.dto.ExternalItemRecord;
import com.demo.itemintegration.external.dto.ExternalQueryResponse;
import com.demo.itemintegration.common.dto.PageResponse;
import com.demo.itemintegration.external.ExternalQuery;
import com.demo.itemintegration.item.dto.ItemDto;
import com.demo.itemintegration.item.mapper.ItemMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class ItemServiceTest {

    @Test
    void mapsLargeDatasetFromASingleExternalCall() throws Exception {
        ObjectMapper json = new ObjectMapper();
        ObjectNode body = json.createObjectNode();
        body.put("totalElements", 25_000).put("hasMore", true);
        ArrayNode results = body.putArray("results");
        for (int i = 1; i <= 10_000; i++) {
            results.addObject().put("item__id", i).put("item_number", "ITM-" + i);
        }
        ExternalQueryResponse response = json.treeToValue(body, ExternalQueryResponse.class);

        ExternalApiClient client = mock(ExternalApiClient.class);
        when(client.execute(ExternalQuery.ITEMS, 0, 10_000)).thenReturn(response);

        PageResponse<ItemDto> items = new ItemService(client, new ItemMapper()).getItems(0, 10_000);

        verify(client, times(1)).execute(ExternalQuery.ITEMS, 0, 10_000);
        assertThat(items.count()).isEqualTo(10_000);
        assertThat(items.objects().get(9_999).itemNumber()).isEqualTo("ITM-10000");
        assertThat(items.totalObjects()).isEqualTo(25_000L);
        assertThat(items.totalPages()).isEqualTo(3);
        assertThat(items.hasMore()).isTrue();
    }

    @Test
    void lastPageReportsNoMore() {
        ExternalApiClient client = mock(ExternalApiClient.class);
        when(client.execute(ExternalQuery.ITEMS, 2, 25)).thenReturn(
                new ExternalQueryResponse(null, null, null, java.util.List.of(), null, 2, 25, 60L, false));

        PageResponse<ItemDto> page = new ItemService(client, new ItemMapper()).getItems(2, 25);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.hasMore()).isFalse();
    }
}
