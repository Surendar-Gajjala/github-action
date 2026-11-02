package com.demo.itemintegration.item.dto;

import java.util.List;

import com.demo.itemintegration.item.model.AvailabilityRisk;
import com.demo.itemintegration.item.model.StructureRole;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Item as exposed by the local REST API. Uses internal field names only. */
@JsonPropertyOrder({"id", "itemNumber", "description", "revision", "businessUnit", "isProduct",
        "structureRole", "odmName", "odmActive", "availabilityRisk", "usedInProducts",
        "productFamiliesImpacted", "itemStatusName"})
public record ItemDto(
        Long id,
        String itemNumber,
        String description,
        String revision,
        String businessUnit,
        @JsonProperty("isProduct") Boolean isProduct,
        StructureRole structureRole,
        List<String> odmName,
        List<Boolean> odmActive,
        AvailabilityRisk availabilityRisk,
        String usedInProducts,
        Integer productFamiliesImpacted,
        String itemStatusName) {
}
