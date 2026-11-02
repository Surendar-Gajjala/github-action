package com.example.plmdemo.erp.domain;

import java.util.List;

/**
 * Internal domain model of an engineering BOM from the source PLM.
 * Independent of any SAP-specific transport format.
 */
public record EngineeringBom(
        String sourceSystem,
        String materialNumber,
        String revision,
        String plant,
        ReleaseState releaseState,
        String changeNumber,
        List<BomItem> items) {

    public EngineeringBom {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public EngineeringBom withReleaseState(ReleaseState newState) {
        return new EngineeringBom(sourceSystem, materialNumber, revision, plant, newState, changeNumber, items);
    }
}
