package com.demo.itemintegration.item.model;

import java.util.List;

/**
 * The application's internal Item. Held in memory only; never persisted.
 * ODM names and active flags are positional lists (index i of each describes the same ODM)
 * and are never null.
 */
public record Item(
        Long id,
        String itemNumber,
        String description,
        String revision,
        String businessUnit,
        Boolean isProduct,
        StructureRole structureRole,
        List<String> odmName,
        List<Boolean> odmActive,
        AvailabilityRisk availabilityRisk,
        String usedInProducts,
        Integer productFamiliesImpacted,
        String itemStatusName) {

    public Item {
        odmName = odmName == null ? List.of() : List.copyOf(odmName);
        odmActive = odmActive == null ? List.of() : List.copyOf(odmActive);
    }
}
