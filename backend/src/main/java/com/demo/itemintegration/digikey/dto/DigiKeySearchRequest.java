package com.demo.itemintegration.digikey.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Request body sent to the DigiKey keyword-search endpoint. */
public record DigiKeySearchRequest(
        @JsonProperty("Keywords") String keywords,
        @JsonProperty("Limit") int limit,
        @JsonProperty("Offset") int offset) {
}
