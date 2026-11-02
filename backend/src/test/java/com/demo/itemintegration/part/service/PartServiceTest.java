package com.demo.itemintegration.part.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.demo.itemintegration.common.dto.PageResponse;
import com.demo.itemintegration.external.ExternalApiClient;
import com.demo.itemintegration.external.ExternalQuery;
import com.demo.itemintegration.external.dto.ExternalQueryResponse;
import com.demo.itemintegration.part.dto.PartDto;
import com.demo.itemintegration.part.mapper.PartMapper;
import com.demo.itemintegration.part.model.SourcingType;
import com.fasterxml.jackson.databind.ObjectMapper;

class PartServiceTest {

    @Test
    void mapsOnePageFromASinglePartsQuery() throws Exception {
        ExternalQueryResponse response = new ObjectMapper().readValue("""
                {"totalElements": 10994, "hasMore": true,
                 "results": [{"part_number": "P-1", "part__sourcing_type": "OTS", "part__id": 1},
                             {"part_number": "P-2", "part__sourcing_type": "UNKNOWN", "part__id": 2}]}
                """, ExternalQueryResponse.class);
        ExternalApiClient client = mock(ExternalApiClient.class);
        when(client.execute(ExternalQuery.PARTS, 0, 25)).thenReturn(response);

        PageResponse<PartDto> parts = new PartService(client, new PartMapper()).getParts(0, 25);

        verify(client, times(1)).execute(ExternalQuery.PARTS, 0, 25);
        assertThat(parts.count()).isEqualTo(2);
        assertThat(parts.objects().get(0).partNumber()).isEqualTo("P-1");
        assertThat(parts.objects().get(0).sourcingType()).isEqualTo(SourcingType.OFF_THE_SHELF);
        assertThat(parts.totalObjects()).isEqualTo(10_994L);
        assertThat(parts.totalPages()).isEqualTo(440);
        assertThat(parts.hasMore()).isTrue();
    }
}
