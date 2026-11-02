package com.demo.itemintegration.hierarchy.service;

import static com.demo.itemintegration.common.mapping.ExternalValues.text;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.demo.itemintegration.common.dto.PageResponse;
import com.demo.itemintegration.external.ExternalApiClient;
import com.demo.itemintegration.external.ExternalQuery;
import com.demo.itemintegration.external.GraphQuery;
import com.demo.itemintegration.external.dto.ExternalGraphResponse;
import com.demo.itemintegration.external.dto.ExternalQueryResponse;
import com.demo.itemintegration.hierarchy.dto.HierarchyNodeDto;
import com.demo.itemintegration.hierarchy.mapper.HierarchyTreeBuilder;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Coordinates the Item Hierarchy for one page of top-level products with two hosted
 * calls: the anchors query (which products are on this page), then one graph traversal
 * for all of them. The tree is built in memory; nothing is persisted.
 */
@Service
public class HierarchyService {

    private static final Logger log = LoggerFactory.getLogger(HierarchyService.class);

    private final ExternalApiClient externalApiClient;
    private final HierarchyTreeBuilder treeBuilder;

    public HierarchyService(ExternalApiClient externalApiClient, HierarchyTreeBuilder treeBuilder) {
        this.externalApiClient = externalApiClient;
        this.treeBuilder = treeBuilder;
    }

    public PageResponse<HierarchyNodeDto> getHierarchy(int page, int size) {
        ExternalQueryResponse anchorsPage = externalApiClient.execute(ExternalQuery.HIERARCHY_ANCHORS, page, size);
        List<String> anchors = anchorsOf(anchorsPage);

        List<HierarchyNodeDto> roots = anchors.isEmpty()
                ? List.of()
                : treeBuilder.build(anchors, externalApiClient.matchGraph(GraphQuery.ITEM_HIERARCHY, anchors));

        log.debug("Built hierarchy for {} products (page {}, size {})", roots.size(), page, size);
        return PageResponse.of(roots, page, size, anchorsPage.totalElements(), anchorsPage.hasMore());
    }

    /**
     * One page of top-level product item numbers (the hierarchy roots) without
     * traversing their trees: a single anchors query, used for counts and lists.
     */
    public PageResponse<String> getProducts(int page, int size) {
        ExternalQueryResponse anchorsPage = externalApiClient.execute(ExternalQuery.HIERARCHY_ANCHORS, page, size);
        return PageResponse.of(anchorsOf(anchorsPage), page, size, anchorsPage.totalElements(), anchorsPage.hasMore());
    }

    private static List<String> anchorsOf(ExternalQueryResponse anchorsPage) {
        return anchorsPage.records(AnchorRecord.class).stream()
                .map(record -> text(record.anchor()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /** One row of the anchors query ({@code item.item_number AS anchor}). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnchorRecord(JsonNode anchor) {
    }
}
