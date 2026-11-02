package com.demo.itemintegration.site.service;

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
import com.demo.itemintegration.site.dto.SiteDto;
import com.demo.itemintegration.site.mapper.SiteMapper;
import com.demo.itemintegration.site.model.SiteType;
import com.fasterxml.jackson.databind.ObjectMapper;

class SiteServiceTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void mapsOnePageFromASingleSitesQuery() throws Exception {
        ExternalQueryResponse response = json.readValue("""
                {"totalElements": 2, "hasMore": false,
                 "results": [{"site_name": "Penang", "site_type": "TEST", "site__id": 1},
                             {"site_name": "Austin", "site_type": "HQ", "site__id": 2}]}
                """, ExternalQueryResponse.class);
        ExternalApiClient client = mock(ExternalApiClient.class);
        when(client.execute(ExternalQuery.SITES, 0, 25)).thenReturn(response);

        PageResponse<SiteDto> sites = new SiteService(client, new SiteMapper()).getSites(0, 25);

        verify(client, times(1)).execute(ExternalQuery.SITES, 0, 25);
        assertThat(sites.count()).isEqualTo(2);
        assertThat(sites.objects().get(1).siteType()).isEqualTo(SiteType.HQ);
        assertThat(sites.totalObjects()).isEqualTo(2L);
        assertThat(sites.hasMore()).isFalse();
    }

    @Test
    void emptyHostedResultGivesEmptyPage() throws Exception {
        // What the hosted server returns today: no Site rows.
        ExternalQueryResponse response = json.readValue("""
                {"zql": "SELECT ...", "properties": ["site_id"], "objects": [{"id": 4332013843, "name": "Site"}],
                 "results": [], "pageNumber": 0, "pageSize": 25, "totalElements": 0, "hasMore": false}
                """, ExternalQueryResponse.class);
        ExternalApiClient client = mock(ExternalApiClient.class);
        when(client.execute(ExternalQuery.SITES, 0, 25)).thenReturn(response);

        PageResponse<SiteDto> sites = new SiteService(client, new SiteMapper()).getSites(0, 25);

        assertThat(sites.count()).isZero();
        assertThat(sites.objects()).isEmpty();
        assertThat(sites.totalObjects()).isZero();
        assertThat(sites.totalPages()).isEqualTo(1);
        assertThat(sites.hasMore()).isFalse();
    }
}
