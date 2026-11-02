package com.demo.itemintegration.digikey.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw DigiKey product-detail response envelope ({@code GET .../productdetails}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DigiKeyProductDetailResponse(@JsonProperty("Product") DigiKeyExternalProduct product) {
}
