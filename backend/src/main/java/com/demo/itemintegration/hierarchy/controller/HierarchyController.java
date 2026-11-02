package com.demo.itemintegration.hierarchy.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.demo.itemintegration.common.dto.PageResponse;
import com.demo.itemintegration.hierarchy.dto.HierarchyNodeDto;
import com.demo.itemintegration.hierarchy.service.HierarchyService;

/** Local, read-only Item Hierarchy API consumed by the frontend. */
@RestController
@RequestMapping("/api/item-hierarchy")
public class HierarchyController {

    private final HierarchyService hierarchyService;

    public HierarchyController(HierarchyService hierarchyService) {
        this.hierarchyService = hierarchyService;
    }

    /** One page of top-level products, each with its full BOM and sourced-part tree; {@code page} is zero-based. */
    @GetMapping
    public PageResponse<HierarchyNodeDto> getHierarchy(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return hierarchyService.getHierarchy(page, size);
    }

    /** One page of top-level product item numbers only (no tree); cheap enough for dashboard counts. */
    @GetMapping("/products")
    public PageResponse<String> getProducts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return hierarchyService.getProducts(page, size);
    }
}
