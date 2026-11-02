package com.demo.itemintegration.digikey.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.demo.itemintegration.digikey.dto.DigiKeyExternalProduct;
import com.demo.itemintegration.digikey.dto.DigiKeyExternalProduct.Pricing;
import com.demo.itemintegration.digikey.dto.DigiKeyExternalProduct.ProductVariation;
import com.demo.itemintegration.digikey.dto.DigiKeyProductDto;
import com.demo.itemintegration.digikey.dto.PriceBreak;

/**
 * Boundary between DigiKey's external product shape and the internal
 * {@link DigiKeyProductDto}. Null-tolerant throughout: a missing field maps to {@code null}
 * (or an empty price-break list) rather than failing the whole response.
 */
@Component
public class DigiKeyProductMapper {

    public DigiKeyProductDto toDto(DigiKeyExternalProduct external, String currency) {
        ProductVariation primary = firstVariation(external);
        return new DigiKeyProductDto(
                primary == null ? null : primary.digiKeyProductNumber(),
                external.manufacturer() == null ? null : external.manufacturer().name(),
                external.manufacturerProductNumber(),
                external.description() == null ? null : external.description().productDescription(),
                external.quantityAvailable(),
                external.unitPrice(),
                currency,
                external.datasheetUrl(),
                external.productUrl(),
                priceBreaks(primary));
    }

    private static ProductVariation firstVariation(DigiKeyExternalProduct external) {
        List<ProductVariation> variations = external.productVariations();
        return variations == null || variations.isEmpty() ? null : variations.get(0);
    }

    private static List<PriceBreak> priceBreaks(ProductVariation variation) {
        if (variation == null || variation.standardPricing() == null) {
            return List.of();
        }
        return variation.standardPricing().stream()
                .filter(p -> p != null)
                .map((Pricing p) -> new PriceBreak(p.breakQuantity(), p.unitPrice()))
                .toList();
    }
}
