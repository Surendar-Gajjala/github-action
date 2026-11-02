package com.demo.itemintegration.external.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Raw response of the hosted graph-match endpoint: a flat list of nodes and the edges
 * between them. {@code zql} and {@code queryInfo} are external metadata and are not
 * exposed by the local API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalGraphResponse(
        List<ExternalGraphNode> nodes,
        List<ExternalGraphEdge> edges,
        JsonNode zql,
        JsonNode queryInfo) {

    public List<ExternalGraphNode> nodes() {
        return nodes == null ? List.of() : nodes;
    }

    public List<ExternalGraphEdge> edges() {
        return edges == null ? List.of() : edges;
    }

    /** A graph node; {@code type} is the entity ("item" or "part"). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExternalGraphNode(Long id, String type, JsonNode properties) {
    }

    /** A relationship between two nodes; {@code type} is "item_bom" or "item_sources". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExternalGraphEdge(Long id, String type, Long fromNodeId, Long toNodeId, JsonNode properties) {
    }
}
