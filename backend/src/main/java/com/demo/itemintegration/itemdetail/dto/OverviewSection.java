package com.demo.itemintegration.itemdetail.dto;

import java.util.List;

/** One labelled group of {@link OverviewField}s in an object overview (item or part). */
public record OverviewSection(String title, List<OverviewField> fields) {
}
