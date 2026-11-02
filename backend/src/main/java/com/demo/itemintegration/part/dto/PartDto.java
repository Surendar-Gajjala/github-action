package com.demo.itemintegration.part.dto;

import com.demo.itemintegration.part.model.LifecycleStatus;
import com.demo.itemintegration.part.model.SourcingType;
import com.demo.itemintegration.part.model.SupplyChainRisk;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Part as exposed by the local REST API. Uses internal field names only. */
@JsonPropertyOrder({"id", "partNumber", "manufacturer", "description", "z2PropertiesComparison",
        "countryOfOrigin", "sourcingType", "supplyChainRisk", "lifecycleStatus"})
public record PartDto(
        Long id,
        String partNumber,
        String manufacturer,
        String description,
        String z2PropertiesComparison,
        String countryOfOrigin,
        SourcingType sourcingType,
        SupplyChainRisk supplyChainRisk,
        LifecycleStatus lifecycleStatus) {
}
