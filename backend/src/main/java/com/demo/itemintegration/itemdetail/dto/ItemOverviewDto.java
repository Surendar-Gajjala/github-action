package com.demo.itemintegration.itemdetail.dto;

import java.util.List;

/**
 * Overview tab of the item details view: the identifying fields for the page header,
 * then every shown property grouped into labelled sections (see {@link OverviewSection}).
 * Values are display-ready (codes become readable labels, booleans Yes/No); {@code null}
 * means no value.
 */
public record ItemOverviewDto(
        Long id,
        String itemNumber,
        String description,
        String revision,
        String itemType,
        String itemStatus,
        List<OverviewSection> sections) {
}
