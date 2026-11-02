package com.demo.itemintegration.digikey.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw DigiKey keyword-search response envelope; only the product list and count are used. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DigiKeyKeywordResponse(
        @JsonProperty("Products") List<DigiKeyExternalProduct> products,
        @JsonProperty("ProductsCount") Integer productsCount) {

    public List<DigiKeyExternalProduct> products() {
        return products == null ? List.of() : products;
    }
}
