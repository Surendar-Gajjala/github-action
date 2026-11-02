package com.demo.itemintegration.part.model;

/**
 * The application's internal Part. Held in memory only; never persisted.
 * {@code id} is a Long because hosted identifiers exceed the int range.
 */
public record Part(
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
