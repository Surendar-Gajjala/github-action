package com.demo.itemintegration.hierarchy.dto;

import java.math.BigDecimal;
import java.util.List;

import com.demo.itemintegration.hierarchy.model.NodeKind;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One row of the Item Hierarchy tree.
 * <ul>
 *   <li>{@code key}: unique per occurrence (the path of node ids from the root), because
 *       a shared item or part appears once under every parent that uses it</li>
 *   <li>{@code qty}: quantity on the item_bom edge from the parent; {@code null} for
 *       top-level products and for sourced parts</li>
 *   <li>exactly one of {@code item} / {@code part} is set, matching {@code kind}</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HierarchyNodeDto(
        String key,
        NodeKind kind,
        Long id,
        BigDecimal qty,
        HierarchyItemDto item,
        HierarchyPartDto part,
        List<HierarchyNodeDto> children) {
}
