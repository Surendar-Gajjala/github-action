package com.demo.itemintegration.site.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.demo.itemintegration.common.dto.PageResponse;
import com.demo.itemintegration.external.ExternalApiClient;
import com.demo.itemintegration.external.ExternalQuery;
import com.demo.itemintegration.external.dto.ExternalQueryResponse;
import com.demo.itemintegration.external.dto.ExternalSiteRecord;
import com.demo.itemintegration.site.dto.SiteDto;
import com.demo.itemintegration.site.mapper.SiteMapper;

/**
 * Coordinates Site retrieval: one call to the hosted server per requested page, then
 * local in-memory mapping of every returned record. Nothing is persisted.
 */
@Service
public class SiteService {

    private static final Logger log = LoggerFactory.getLogger(SiteService.class);

    private final ExternalApiClient externalApiClient;
    private final SiteMapper siteMapper;

    public SiteService(ExternalApiClient externalApiClient, SiteMapper siteMapper) {
        this.externalApiClient = externalApiClient;
        this.siteMapper = siteMapper;
    }

    public PageResponse<SiteDto> getSites(int page, int size) {
        ExternalQueryResponse response = externalApiClient.execute(ExternalQuery.SITES, page, size);

        List<SiteDto> sites = response.records(ExternalSiteRecord.class).stream()
                .map(siteMapper::toSite)
                .map(siteMapper::toDto)
                .toList();

        log.debug("Retrieved {} sites (page {}, size {}) from hosted server", sites.size(), page, size);
        return PageResponse.of(sites, page, size, response.totalElements(), response.hasMore());
    }
}
