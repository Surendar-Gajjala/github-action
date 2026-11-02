package com.demo.itemintegration.digikey.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.demo.itemintegration.digikey.client.DigiKeyApiClient;
import com.demo.itemintegration.digikey.config.DigiKeyProperties;
import com.demo.itemintegration.digikey.dto.DigiKeyKeywordResponse;
import com.demo.itemintegration.digikey.dto.DigiKeyProductDetailResponse;
import com.demo.itemintegration.digikey.dto.DigiKeyProductDto;
import com.demo.itemintegration.digikey.dto.DigiKeySearchRequest;
import com.demo.itemintegration.digikey.dto.DigiKeySearchResponse;
import com.demo.itemintegration.digikey.exception.DigiKeyApiException;
import com.demo.itemintegration.digikey.exception.DigiKeyApiException.Reason;
import com.demo.itemintegration.digikey.mapper.DigiKeyProductMapper;

/**
 * Coordinates DigiKey retrieval: one DigiKey call per request, then in-memory mapping to
 * the internal model. A blank keyword falls back to the configured default keyword.
 * Nothing is persisted.
 */
@Service
public class DigiKeyService {

    private final DigiKeyApiClient client;
    private final DigiKeyProductMapper mapper;
    private final DigiKeyProperties properties;

    public DigiKeyService(DigiKeyApiClient client, DigiKeyProductMapper mapper, DigiKeyProperties properties) {
        this.client = client;
        this.mapper = mapper;
        this.properties = properties;
    }

    public DigiKeySearchResponse search(String keyword, int limit, int offset) {
        String effective = keyword == null || keyword.isBlank() ? properties.defaultKeyword() : keyword.trim();
        DigiKeyKeywordResponse response = client.searchKeyword(new DigiKeySearchRequest(effective, limit, offset));

        List<DigiKeyProductDto> products = response.products().stream()
                .map(product -> mapper.toDto(product, properties.localeCurrency()))
                .toList();
        return new DigiKeySearchResponse(products, response.productsCount(), limit, offset);
    }

    public DigiKeyProductDto getProduct(String productNumber) {
        DigiKeyProductDetailResponse response = client.getProduct(productNumber);
        if (response.product() == null) {
            throw new DigiKeyApiException(Reason.INVALID_RESPONSE);
        }
        return mapper.toDto(response.product(), properties.localeCurrency());
    }
}
