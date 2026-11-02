package com.example.plmdemo.cad.model;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates a CAD assembly export and groups its instances per part.
 * Used by both demo paths:
 *   cad/ (ECTR path)  -> document structure (BoD) + material BOM in the SAP backend
 *   erp/ (PLM path)   -> engineering BOM in the external PLM
 *
 * Rules (demo rules):
 *  - a missing quantity counts as 1, a missing unit as EA
 *  - instances of the same part number are summed (2 x BEARING-201 -> 2)
 *  - excludeFromBom instances (skeletons, reference geometry) stay in the document
 *    structure but are not counted for the BOM
 */
@Component
public class CadStructureMapper {

    public static final String DEFAULT_UNIT = "EA";

    private static final class Acc {
        String revision;
        String description;
        String fileName;
        String unit;
        BigDecimal structureQty = BigDecimal.ZERO;
        BigDecimal bomQty = BigDecimal.ZERO;
        final List<String> instanceIds = new ArrayList<>();
        final List<String> bomInstanceIds = new ArrayList<>();
    }

    public CadStructure analyze(CadAssembly cad) {
        List<String> violations = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        String header = normalize(cad.partNumber());
        if (header.isEmpty()) violations.add("Assembly part number is missing");
        if (isBlank(cad.revision())) violations.add("Assembly revision is missing");
        if (isBlank(cad.plant())) violations.add("Plant is missing");
        if (cad.components().isEmpty()) violations.add("Assembly has no components");

        Map<String, Acc> parts = new LinkedHashMap<>();
        Set<String> instanceIds = new HashSet<>();
        int excluded = 0;

        for (CadAssembly.Component c : cad.components()) {
            String part = normalize(c.partNumber());
            String label = c.instanceId() != null ? c.instanceId() : part;

            if (c.instanceId() != null && !instanceIds.add(c.instanceId())) {
                violations.add("Instance ID " + c.instanceId() + " is used more than once");
            }
            if (part.isEmpty()) {
                violations.add("Instance " + label + " has no part number");
                continue;
            }
            if (part.equals(header)) {
                violations.add("Instance " + label + " references the assembly itself (" + part + ")");
                continue;
            }
            BigDecimal qty = c.quantity() == null ? BigDecimal.ONE : c.quantity();
            if (qty.signum() <= 0) {
                violations.add("Instance " + label + " (" + part + ") has invalid quantity " + qty);
                continue;
            }
            String unit = isBlank(c.unit()) ? DEFAULT_UNIT : c.unit().trim().toUpperCase(Locale.ROOT);

            Acc acc = parts.computeIfAbsent(part, k -> new Acc());
            if (acc.unit != null && !acc.unit.equals(unit)) {
                violations.add("Part " + part + " is used with different units (" + acc.unit + " and " + unit + ")");
                continue;
            }
            acc.unit = unit;
            if (acc.revision == null) acc.revision = c.revision();
            if (acc.description == null) acc.description = c.description();
            if (acc.fileName == null) acc.fileName = c.fileName();
            acc.structureQty = acc.structureQty.add(qty);
            acc.instanceIds.add(label);
            if (c.excludeFromBom()) {
                excluded++;
                notes.add(label + " (" + part + ") excluded from BOM (excludeFromBom=true)");
            } else {
                acc.bomQty = acc.bomQty.add(qty);
                acc.bomInstanceIds.add(label);
            }
        }

        if (violations.isEmpty() && parts.values().stream().noneMatch(a -> a.bomQty.signum() > 0)) {
            violations.add("No BOM-relevant components left after exclusions");
        }
        if (!violations.isEmpty()) {
            throw new CadValidationException(violations);
        }

        List<CadStructure.Line> lines = new ArrayList<>();
        parts.forEach((part, a) -> {
            lines.add(new CadStructure.Line(part, a.revision, a.description, a.fileName, a.unit,
                    a.structureQty.stripTrailingZeros(), a.bomQty.stripTrailingZeros(), a.instanceIds));
            if (a.bomInstanceIds.size() > 1) {
                notes.add(part + ": " + a.bomInstanceIds.size() + " CAD instances (" + String.join(", ", a.bomInstanceIds)
                        + ") -> one BOM item, quantity " + a.bomQty.stripTrailingZeros().toPlainString() + " " + a.unit);
            }
        });
        return new CadStructure(cad, lines, notes, cad.components().size(), excluded);
    }

    static String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
