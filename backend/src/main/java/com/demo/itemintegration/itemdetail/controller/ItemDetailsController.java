package com.demo.itemintegration.itemdetail.controller;

import java.util.List;

import jakarta.validation.constraints.Positive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.demo.itemintegration.itemdetail.dto.ItemOverviewDto;
import com.demo.itemintegration.itemdetail.service.ItemDetailsService;
import com.demo.itemintegration.part.dto.PartDto;

/** Local, read-only item details API: one endpoint per tab (Overview, Sources) of the item details view. */
@RestController
@RequestMapping("/api/items/{id}")
public class ItemDetailsController {

    private final ItemDetailsService itemDetailsService;

    public ItemDetailsController(ItemDetailsService itemDetailsService) {
        this.itemDetailsService = itemDetailsService;
    }

    @GetMapping("/overview")
    public ItemOverviewDto getOverview(@PathVariable @Positive long id) {
        return itemDetailsService.getOverview(id);
    }

    @GetMapping("/sources")
    public List<PartDto> getSources(@PathVariable @Positive long id) {
        return itemDetailsService.getSources(id);
    }
}
