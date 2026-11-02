package com.demo.itemintegration.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Properties of a part node returned by the item-sources graph traversal
 * ({@code item-sources-graph.json}). The graph endpoint names properties plainly
 * ({@code sourcing_type}), unlike the Parts query aliases ({@code part__sourcing_type}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalSourcePartRecord(
        @JsonProperty("part_number") JsonNode partNumber,
        @JsonProperty("manufacturer") JsonNode manufacturer,
        @JsonProperty("description") JsonNode description,
        @JsonProperty("z2_properties_comparison") JsonNode z2PropertiesComparison,
        @JsonProperty("country_of_origin") JsonNode countryOfOrigin,
        @JsonProperty("sourcing_type") JsonNode sourcingType,
        @JsonProperty("supply_chain_risk") JsonNode supplyChainRisk,
        @JsonProperty("lifecycle.status") JsonNode lifecycleStatus) {
}
