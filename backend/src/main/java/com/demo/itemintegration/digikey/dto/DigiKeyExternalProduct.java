package com.demo.itemintegration.digikey.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One product exactly as the DigiKey Product Information V4 API names it. External field
 * names live only in this package. Unknown fields are ignored so schema additions never
 * break mapping; every field is nullable.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DigiKeyExternalProduct(
        @JsonProperty("ManufacturerProductNumber") String manufacturerProductNumber,
        @JsonProperty("Manufacturer") Manufacturer manufacturer,
        @JsonProperty("Description") Description description,
        @JsonProperty("DatasheetUrl") String datasheetUrl,
        @JsonProperty("ProductUrl") String productUrl,
        @JsonProperty("QuantityAvailable") Integer quantityAvailable,
        @JsonProperty("UnitPrice") Double unitPrice,
        @JsonProperty("ProductVariations") List<ProductVariation> productVariations) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Manufacturer(@JsonProperty("Name") String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Description(@JsonProperty("ProductDescription") String productDescription) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductVariation(
            @JsonProperty("DigiKeyProductNumber") String digiKeyProductNumber,
            @JsonProperty("StandardPricing") List<Pricing> standardPricing) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pricing(
            @JsonProperty("BreakQuantity") Integer breakQuantity,
            @JsonProperty("UnitPrice") Double unitPrice) {
    }
}
