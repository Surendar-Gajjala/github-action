package com.demo.itemintegration.partdetail.service;

import org.springframework.stereotype.Service;

import com.demo.itemintegration.external.ExternalApiClient;
import com.demo.itemintegration.partdetail.PartNotFoundException;
import com.demo.itemintegration.partdetail.dto.PartOverviewDto;
import com.demo.itemintegration.partdetail.mapper.PartOverviewMapper;

/**
 * Part details view (Overview). The tab is exactly one hosted call, made only when the
 * tab is requested; nothing is persisted.
 */
@Service
public class PartDetailsService {

    private static final String PART_TYPE = "part";

    private final ExternalApiClient externalApiClient;
    private final PartOverviewMapper overviewMapper;

    public PartDetailsService(ExternalApiClient externalApiClient, PartOverviewMapper overviewMapper) {
        this.externalApiClient = externalApiClient;
        this.overviewMapper = overviewMapper;
    }

    /** All part properties, grouped for the Overview tab. Unknown ids and non-part objects are not found. */
    public PartOverviewDto getOverview(long partId) {
        return externalApiClient.fetchObject(partId)
                .filter(found -> PART_TYPE.equalsIgnoreCase(found.objectTypeDbName()))
                .map(overviewMapper::toOverview)
                .orElseThrow(() -> new PartNotFoundException(partId));
    }
}
