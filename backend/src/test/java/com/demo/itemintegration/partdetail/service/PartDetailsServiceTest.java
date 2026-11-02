package com.demo.itemintegration.partdetail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.demo.itemintegration.external.ExternalApiClient;
import com.demo.itemintegration.external.dto.ExternalObjectResponse;
import com.demo.itemintegration.partdetail.PartNotFoundException;
import com.demo.itemintegration.partdetail.mapper.PartOverviewMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

class PartDetailsServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ExternalApiClient client = mock(ExternalApiClient.class);
    private final PartDetailsService service = new PartDetailsService(client, new PartOverviewMapper());

    @Test
    void overviewIsOneObjectCall() throws Exception {
        when(client.fetchObject(4332025900L)).thenReturn(Optional.of(json.readValue("""
                {"objectId": 4332025900, "objectTypeDbName": "part",
                 "properties": {"part_number": {"value": "Y5363689"}}}
                """, ExternalObjectResponse.class)));

        assertThat(service.getOverview(4332025900L).partNumber()).isEqualTo("Y5363689");

        verify(client, times(1)).fetchObject(4332025900L);
        verifyNoMoreInteractions(client);
    }

    @Test
    void unknownIdIsNotFound() {
        when(client.fetchObject(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOverview(1L)).isInstanceOf(PartNotFoundException.class);
    }

    @Test
    void nonPartObjectIsNotFound() throws Exception {
        when(client.fetchObject(4332025200L)).thenReturn(Optional.of(json.readValue("""
                {"objectId": 4332025200, "objectTypeDbName": "item", "properties": {}}
                """, ExternalObjectResponse.class)));

        assertThatThrownBy(() -> service.getOverview(4332025200L)).isInstanceOf(PartNotFoundException.class);
    }
}
