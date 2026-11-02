package com.demo.itemintegration.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/** Properties of a part node in the graph-match response (the {@code select.part} list). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalHierarchyPartRecord(
        @JsonProperty("part_number") JsonNode partNumber,
        @JsonProperty("manufacturer") JsonNode manufacturer) {
}
