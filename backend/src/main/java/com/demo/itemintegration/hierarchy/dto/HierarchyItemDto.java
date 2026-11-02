package com.demo.itemintegration.hierarchy.dto;

import com.demo.itemintegration.item.model.StructureRole;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Item columns of the Item Hierarchy grid (the table config's "grid" section), with
 * internal names. Status-like values are readable labels (see
 * {@link com.demo.itemintegration.common.mapping.CodeLabels}); type, make/buy, design
 * group and project are shown as the hosted codes.
 */
public record HierarchyItemDto(
        String itemNumber,
        String description,
        String revision,
        String itemType,
        String itemStatus,
        String makeBuy,
        String designGroup,
        Boolean restricted,
        String exemptionStatus,
        String project,
        String sourceNotes,
        String collection,
        String collectionLock,
        String euRohs,
        String euRohsLock,
        String ecExemptions,
        String pwbLeadHalogen,
        String pwbLeadHalogenLock,
        String plasticLeadHalogen,
        String plasticLeadHalogenLock,
        String pnr,
        String pnrLock,
        String businessUnit,
        @JsonProperty("isProduct") Boolean isProduct,
        StructureRole structureRole) {
}
