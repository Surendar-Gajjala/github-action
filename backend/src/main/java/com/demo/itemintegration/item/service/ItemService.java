package com.demo.itemintegration.item.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.demo.itemintegration.common.dto.PageResponse;
import com.demo.itemintegration.external.ExternalApiClient;
import com.demo.itemintegration.external.ExternalQuery;
import com.demo.itemintegration.external.dto.ExternalItemRecord;
import com.demo.itemintegration.external.dto.ExternalQueryResponse;
import com.demo.itemintegration.item.dto.ItemDto;
import com.demo.itemintegration.item.mapper.ItemMapper;

/**
 * Coordinates Item retrieval: one call to the hosted server per requested page, then
 * local in-memory mapping of every returned record. Nothing is persisted.
 */
@Service
public class ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);

    private final ExternalApiClient externalApiClient;
    private final ItemMapper itemMapper;

    public ItemService(ExternalApiClient externalApiClient, ItemMapper itemMapper) {
        this.externalApiClient = externalApiClient;
        this.itemMapper = itemMapper;
    }

    public PageResponse<ItemDto> getItems(int page, int size) {
        ExternalQueryResponse response = externalApiClient.execute(ExternalQuery.ITEMS, page, size);

        List<ItemDto> items = response.records(ExternalItemRecord.class).stream()
                .map(itemMapper::toItem)
                .map(itemMapper::toDto)
                .toList();

        log.debug("Retrieved {} items (page {}, size {}) from hosted server", items.size(), page, size);
        return PageResponse.of(items, page, size, response.totalElements(), response.hasMore());
    }
}
