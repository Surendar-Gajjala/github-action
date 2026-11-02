package com.demo.itemintegration.partdetail.controller;

import jakarta.validation.constraints.Positive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.demo.itemintegration.partdetail.dto.PartOverviewDto;
import com.demo.itemintegration.partdetail.service.PartDetailsService;

/** Local, read-only part details API: the Overview tab of the part details view. */
@RestController
@RequestMapping("/api/parts/{id}")
public class PartDetailsController {

    private final PartDetailsService partDetailsService;

    public PartDetailsController(PartDetailsService partDetailsService) {
        this.partDetailsService = partDetailsService;
    }

    @GetMapping("/overview")
    public PartOverviewDto getOverview(@PathVariable @Positive long id) {
        return partDetailsService.getOverview(id);
    }
}
