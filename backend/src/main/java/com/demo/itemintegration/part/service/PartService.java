package com.demo.itemintegration.part.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.demo.itemintegration.common.dto.PageResponse;
import com.demo.itemintegration.external.ExternalApiClient;
import com.demo.itemintegration.external.ExternalQuery;
import com.demo.itemintegration.external.dto.ExternalPartRecord;
import com.demo.itemintegration.external.dto.ExternalQueryResponse;
import com.demo.itemintegration.part.dto.PartDto;
import com.demo.itemintegration.part.mapper.PartMapper;

/**
 * Coordinates Part retrieval: one call to the hosted server per requested page, then
 * local in-memory mapping of every returned record. Nothing is persisted.
 */
@Service
public class PartService {

    private static final Logger log = LoggerFactory.getLogger(PartService.class);

    private final ExternalApiClient externalApiClient;
    private final PartMapper partMapper;

    public PartService(ExternalApiClient externalApiClient, PartMapper partMapper) {
        this.externalApiClient = externalApiClient;
        this.partMapper = partMapper;
    }

    public PageResponse<PartDto> getParts(int page, int size) {
        ExternalQueryResponse response = externalApiClient.execute(ExternalQuery.PARTS, page, size);

        List<PartDto> parts = response.records(ExternalPartRecord.class).stream()
                .map(partMapper::toPart)
                .map(partMapper::toDto)
                .toList();

        log.debug("Retrieved {} parts (page {}, size {}) from hosted server", parts.size(), page, size);
        return PageResponse.of(parts, page, size, response.totalElements(), response.hasMore());
    }
}
