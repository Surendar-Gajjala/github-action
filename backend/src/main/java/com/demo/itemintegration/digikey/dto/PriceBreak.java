package com.demo.itemintegration.digikey.dto;

/** One quantity price break exposed by the local API. */
public record PriceBreak(Integer breakQuantity, Double unitPrice) {
}
