package com.demo.itemintegration.external;

/**
 * Graph-match payloads sent to the hosted graph endpoint. Each payload fixes the
 * relationships, direction and selected properties; the client fills in
 * {@code traversal.anchorValues}. Payload files live in {@code resources/external-queries}.
 */
public enum GraphQuery {
    /** item_bom + item_sources below top-level products, anchored on item_number (Item Hierarchy). */
    ITEM_HIERARCHY("external-queries/item-hierarchy-graph.json"),
    /** item_sources from one item to its sourced parts, anchored on the item id (Sources tab). */
    ITEM_SOURCES("external-queries/item-sources-graph.json");

    private final String resource;

    GraphQuery(String resource) {
        this.resource = resource;
    }

    String resource() {
        return resource;
    }
}
