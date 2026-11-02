package com.demo.itemintegration.digikey.controller;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.demo.itemintegration.digikey.dto.DigiKeyProductDto;
import com.demo.itemintegration.digikey.dto.DigiKeySearchResponse;
import com.demo.itemintegration.digikey.service.DigiKeyService;

/**
 * Local, read-only DigiKey API consumed by the frontend. Independent of the Enterprise
 * {@code /api/items|parts|sites} endpoints.
 */
@RestController
@RequestMapping("/api/digikey/products")
public class DigiKeyController {

    /** DigiKey keyword search caps results at 50 per page. */
    private static final int MAX_LIMIT = 50;

    private final DigiKeyService service;

    public DigiKeyController(DigiKeyService service) {
        this.service = service;
    }

    /** One page of search results. A blank keyword uses the configured default keyword. */
    @GetMapping("/search")
    public DigiKeySearchResponse search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        int clamped = Math.max(1, Math.min(limit, MAX_LIMIT));
        return service.search(keyword, clamped, offset);
    }

    @GetMapping("/{productId}")
    public DigiKeyProductDto getProduct(@PathVariable @NotBlank String productId) {
        return service.getProduct(productId);
    }
}
