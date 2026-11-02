package com.demo.itemintegration.item.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.demo.itemintegration.common.dto.PageResponse;
import com.demo.itemintegration.item.dto.ItemDto;
import com.demo.itemintegration.item.service.ItemService;

/** Local, read-only Item API consumed by the frontend. */
@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /** One page of Items; {@code page} is zero-based. */
    @GetMapping
    public PageResponse<ItemDto> getItems(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return itemService.getItems(page, size);
    }
}
