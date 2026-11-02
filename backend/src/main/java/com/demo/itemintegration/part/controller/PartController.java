package com.demo.itemintegration.part.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.demo.itemintegration.common.dto.PageResponse;
import com.demo.itemintegration.part.dto.PartDto;
import com.demo.itemintegration.part.service.PartService;

/** Local, read-only Part API consumed by the frontend. */
@RestController
@RequestMapping("/api/parts")
public class PartController {

    private final PartService partService;

    public PartController(PartService partService) {
        this.partService = partService;
    }

    /** One page of Parts; {@code page} is zero-based. */
    @GetMapping
    public PageResponse<PartDto> getParts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return partService.getParts(page, size);
    }
}
