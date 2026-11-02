package com.demo.itemintegration.site.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.demo.itemintegration.common.dto.PageResponse;
import com.demo.itemintegration.site.dto.SiteDto;
import com.demo.itemintegration.site.service.SiteService;

/** Local, read-only Site API consumed by the frontend. */
@RestController
@RequestMapping("/api/sites")
public class SiteController {

    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    /** One page of Sites; {@code page} is zero-based. */
    @GetMapping
    public PageResponse<SiteDto> getSites(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return siteService.getSites(page, size);
    }
}
