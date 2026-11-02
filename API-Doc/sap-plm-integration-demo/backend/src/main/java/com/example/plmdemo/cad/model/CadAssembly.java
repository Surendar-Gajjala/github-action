package com.example.plmdemo.cad.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Internal model of a CAD assembly export (one assembly with its component instances).
 * The JSON shape that produces it is the demo's own format, not an SAP ECTR or CAD vendor format.
 */
public record CadAssembly(
        String cadSystem,
        String fileName,
        String exportedBy,
        String partNumber,
        String revision,
        String plant,
        String changeNumber,
        String description,
        List<Component> components) {

    public CadAssembly {
        components = components == null ? List.of() : List.copyOf(components);
    }

    /** One placed instance in the CAD assembly. The same part can be placed several times. */
    public record Component(
            String instanceId,
            String partNumber,
            String revision,
            String description,
            BigDecimal quantity,
            String unit,
            String fileName,
            boolean excludeFromBom) {
    }
}
