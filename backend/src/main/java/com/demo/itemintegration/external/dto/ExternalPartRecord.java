package com.demo.itemintegration.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * One Part row exactly as the hosted server names it (the aliases in
 * {@code part-query.json}). External field names live only here. Values are kept as
 * raw JSON so the mapper can normalise inconsistent types without failing the request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalPartRecord(
        @JsonProperty("part_number") JsonNode partNumber,
        @JsonProperty("manufacturer") JsonNode manufacturer,
        @JsonProperty("description") JsonNode description,
        @JsonProperty("z2_properties_comparison") JsonNode z2PropertiesComparison,
        @JsonProperty("part__country_of_origin") JsonNode countryOfOrigin,
        @JsonProperty("part__sourcing_type") JsonNode sourcingType,
        @JsonProperty("part__supply_chain_risk") JsonNode supplyChainRisk,
        @JsonProperty("part__lifecycle__status") JsonNode lifecycleStatus,
        @JsonProperty("part__id") JsonNode id) {
}
