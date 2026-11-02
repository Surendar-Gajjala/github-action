package com.demo.itemintegration.itemdetail.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.demo.itemintegration.external.ExternalApiClient;
import com.demo.itemintegration.external.GraphQuery;
import com.demo.itemintegration.external.dto.ExternalObjectResponse;
import com.demo.itemintegration.itemdetail.ItemNotFoundException;
import com.demo.itemintegration.itemdetail.dto.ItemOverviewDto;
import com.demo.itemintegration.itemdetail.mapper.ItemOverviewMapper;
import com.demo.itemintegration.itemdetail.mapper.ItemSourcesMapper;
import com.demo.itemintegration.part.dto.PartDto;

/**
 * Item details view (Overview, Sources). Each tab is exactly one hosted call, made
 * only when that tab is requested; nothing is persisted.
 */
@Service
public class ItemDetailsService {

    private static final String ITEM_TYPE = "item";

    private final ExternalApiClient externalApiClient;
    private final ItemOverviewMapper overviewMapper;
    private final ItemSourcesMapper sourcesMapper;

    public ItemDetailsService(ExternalApiClient externalApiClient, ItemOverviewMapper overviewMapper,
            ItemSourcesMapper sourcesMapper) {
        this.externalApiClient = externalApiClient;
        this.overviewMapper = overviewMapper;
        this.sourcesMapper = sourcesMapper;
    }

    /** All item properties, grouped for the Overview tab. Unknown ids and non-item objects are not found. */
    public ItemOverviewDto getOverview(long itemId) {
        ExternalObjectResponse object = externalApiClient.fetchObject(itemId)
                .filter(found -> ITEM_TYPE.equalsIgnoreCase(found.objectTypeDbName()))
                .orElseThrow(() -> new ItemNotFoundException(itemId));
        return overviewMapper.toOverview(object);
    }

    /** The item's sourced parts (item_sources). */
    public List<PartDto> getSources(long itemId) {
        return sourcesMapper.toSources(externalApiClient.matchGraph(GraphQuery.ITEM_SOURCES, List.of(Long.toString(itemId))));
    }
}
