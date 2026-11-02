package com.demo.itemintegration.hierarchy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.demo.itemintegration.common.dto.PageResponse;
import com.demo.itemintegration.external.ExternalApiClient;
import com.demo.itemintegration.external.ExternalQuery;
import com.demo.itemintegration.external.GraphQuery;
import com.demo.itemintegration.external.dto.ExternalGraphResponse;
import com.demo.itemintegration.external.dto.ExternalQueryResponse;
import com.demo.itemintegration.hierarchy.dto.HierarchyNodeDto;
import com.demo.itemintegration.hierarchy.mapper.HierarchyMapper;
import com.demo.itemintegration.hierarchy.mapper.HierarchyTreeBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

class HierarchyServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ExternalApiClient client = mock(ExternalApiClient.class);
    private final HierarchyService service =
            new HierarchyService(client, new HierarchyTreeBuilder(new HierarchyMapper()));

    @Test
    void usesOneAnchorsQueryAndOneGraphCallPerPage() throws Exception {
        when(client.execute(ExternalQuery.HIERARCHY_ANCHORS, 0, 2)).thenReturn(json.readValue("""
                {"results": [{"anchor": "903239"}, {"anchor": "903240"}], "totalElements": 292, "hasMore": true}
                """, ExternalQueryResponse.class));
        when(client.matchGraph(GraphQuery.ITEM_HIERARCHY, List.of("903239", "903240"))).thenReturn(json.readValue("""
                {"nodes": [{"id": 1, "type": "item", "properties": {"item_number": "903239"}},
                           {"id": 2, "type": "item", "properties": {"item_number": "903240"}},
                           {"id": 3, "type": "item", "properties": {"item_number": "E70293-013"}}],
                 "edges": [{"id": 10, "type": "item_bom", "fromNodeId": 1, "toNodeId": 3,
                            "properties": {"qty_per_bom.qty": "2"}}]}
                """, ExternalGraphResponse.class));

        PageResponse<HierarchyNodeDto> page = service.getHierarchy(0, 2);

        verify(client, times(1)).execute(ExternalQuery.HIERARCHY_ANCHORS, 0, 2);
        verify(client, times(1)).matchGraph(GraphQuery.ITEM_HIERARCHY, List.of("903239", "903240"));
        assertThat(page.objects()).extracting(n -> n.item().itemNumber()).containsExactly("903239", "903240");
        assertThat(page.objects().get(0).children()).hasSize(1);
        assertThat(page.totalObjects()).isEqualTo(292L);
        assertThat(page.totalPages()).isEqualTo(146);
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    void productsUsesOnlyTheAnchorsQuery() throws Exception {
        when(client.execute(ExternalQuery.HIERARCHY_ANCHORS, 0, 1)).thenReturn(json.readValue("""
                {"results": [{"anchor": "903239"}], "totalElements": 292, "hasMore": true}
                """, ExternalQueryResponse.class));

        PageResponse<String> products = service.getProducts(0, 1);

        verify(client, times(1)).execute(ExternalQuery.HIERARCHY_ANCHORS, 0, 1);
        verify(client, never()).matchGraph(any(), any());
        assertThat(products.objects()).containsExactly("903239");
        assertThat(products.totalObjects()).isEqualTo(292L);
    }

    @Test
    void skipsGraphCallWhenPageHasNoAnchors() throws Exception {
        when(client.execute(ExternalQuery.HIERARCHY_ANCHORS, 20, 25)).thenReturn(json.readValue("""
                {"results": [], "totalElements": 292, "hasMore": false}
                """, ExternalQueryResponse.class));

        PageResponse<HierarchyNodeDto> page = service.getHierarchy(20, 25);

        verify(client, never()).matchGraph(any(), any());
        assertThat(page.objects()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }
}
