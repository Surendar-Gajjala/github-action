package com.demo.itemintegration.external;

/**
 * The query-config payloads the application sends to the hosted server. Every entity
 * uses the same endpoint and token; only the payload (which entity and which
 * properties to select) differs. Payload files live in {@code resources/external-queries}.
 */
public enum ExternalQuery {
    ITEMS("external-queries/item-query.json"),
    PARTS("external-queries/part-query.json"),
    SITES("external-queries/site-query.json"),
    /** Top-level products of the item_bom relationship (the Item Hierarchy roots). */
    HIERARCHY_ANCHORS("external-queries/item-hierarchy-anchors.json");

    private final String resource;

    ExternalQuery(String resource) {
        this.resource = resource;
    }

    String resource() {
        return resource;
    }
}
