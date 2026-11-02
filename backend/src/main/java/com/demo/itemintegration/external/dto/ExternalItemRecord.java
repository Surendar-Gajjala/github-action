package com.demo.itemintegration.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * One Item row exactly as the hosted server names it. External field names live only
 * here. Values are kept as raw JSON so the mapper can normalise inconsistent types
 * (null, "", "[]", arrays, numbers-as-strings) without failing the whole request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalItemRecord(
        @JsonProperty("item_number") JsonNode itemNumber,
        @JsonProperty("description") JsonNode description,
        @JsonProperty("item__revision") JsonNode revision,
        @JsonProperty("item__business_unit") JsonNode businessUnit,
        @JsonProperty("item__is_product") JsonNode isProduct,
        @JsonProperty("item__structure_role") JsonNode structureRole,
        @JsonProperty("item__odm__name") JsonNode odmName,
        @JsonProperty("item__odm__active") JsonNode odmActive,
        @JsonProperty("item__availability_risk") JsonNode availabilityRisk,
        @JsonProperty("item__used_in_products") JsonNode usedInProducts,
        @JsonProperty("item__product_families_impacted") JsonNode productFamiliesImpacted,
        @JsonProperty("item__item_status__name") JsonNode itemStatusName,
        @JsonProperty("item__id") JsonNode id) {
}
