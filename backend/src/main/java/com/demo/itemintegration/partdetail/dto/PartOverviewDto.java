package com.demo.itemintegration.partdetail.dto;

import java.util.List;

import com.demo.itemintegration.itemdetail.dto.OverviewSection;

/**
 * Overview tab of the part details view: the identifying fields for the page header,
 * then every shown property grouped into labelled sections (see {@link OverviewSection},
 * shared with the item overview). Values are display-ready; {@code null} means no value.
 */
public record PartOverviewDto(
        Long id,
        String partNumber,
        String manufacturer,
        String description,
        List<OverviewSection> sections) {
}
