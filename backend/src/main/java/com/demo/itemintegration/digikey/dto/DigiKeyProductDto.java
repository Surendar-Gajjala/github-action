package com.demo.itemintegration.digikey.dto;

import java.util.List;

/** A DigiKey product as exposed by the local API. Internal field names only. */
public record DigiKeyProductDto(
        String digiKeyPartNumber,
        String manufacturer,
        String manufacturerPartNumber,
        String description,
        Integer quantityAvailable,
        Double unitPrice,
        String currency,
        String datasheetUrl,
        String productUrl,
        List<PriceBreak> priceBreaks) {
}
