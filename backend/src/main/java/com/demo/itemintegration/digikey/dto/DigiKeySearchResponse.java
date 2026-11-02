package com.demo.itemintegration.digikey.dto;

import java.util.List;

/** One page of DigiKey search results as exposed by the local API. */
public record DigiKeySearchResponse(
        List<DigiKeyProductDto> products,
        Integer totalCount,
        int limit,
        int offset) {
}
