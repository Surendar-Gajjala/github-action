package com.example.plmdemo.cad.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of analysing a CAD assembly: one line per distinct part.
 *
 *  - structureQuantity counts ALL instances (what the document structure / BoD mirrors)
 *  - bomQuantity counts only BOM-relevant instances (excludeFromBom=false)
 */
public record CadStructure(
        CadAssembly assembly,
        List<Line> lines,
        List<String> notes,
        int cadInstances,
        int excludedInstances) {

    public CadStructure {
        lines = List.copyOf(lines);
        notes = List.copyOf(notes);
    }

    public record Line(
            String partNumber,
            String revision,
            String description,
            String fileName,
            String unit,
            BigDecimal structureQuantity,
            BigDecimal bomQuantity,
            List<String> instanceIds) {

        public Line {
            instanceIds = List.copyOf(instanceIds);
        }

        public boolean bomRelevant() {
            return bomQuantity.signum() > 0;
        }
    }

    public List<Line> bomLines() {
        return lines.stream().filter(Line::bomRelevant).toList();
    }

    /** Normalized (trimmed, upper-case) assembly part number. */
    public String partNumber() {
        return CadStructureMapper.normalize(assembly.partNumber());
    }
}
