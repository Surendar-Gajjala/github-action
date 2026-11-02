// ILLUSTRATIVE: invented demo data. Ids, values and structures do not come from any SAP system.
package com.example.ectr.plugin.mock;

import com.example.ectr.plugin.api.model.EngineeringObject;
import com.example.ectr.plugin.api.model.LocalizedText;
import com.example.ectr.plugin.api.model.ObjectType;
import com.example.ectr.plugin.api.model.PropertyKey;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo data set used by the mock host.
 * <pre>
 * FLD-PACK  folder "Packaging"
 * ASM-100   Brake caliper assembly                  (complete, used for packaging)
 *   PRT-101  Caliper housing      x1  CAST          + DRW-101
 *   PRT-102  Piston               x2  MACHINED
 *   PRT-103  Piston seal          x2  SEAL
 *   PRT-104  Dust boot            x2  SEAL
 *   PRT-105  Bleed screw          x1  FASTENER
 *   PRT-106  Washer               x2  FASTENER
 *   SUB-110  Bracket sub-assembly x1                + DRW-110
 *     PRT-111 Bracket             x1  CAST
 *     PRT-112 Guide pin           x2  FASTENER
 *     PRT-113 Bolt M12            x2  FASTENER
 *   (DRW-100 documents ASM-100 itself)
 * ASM-200   Wheel hub assembly                       (PRT-202 incomplete, packaging is refused)
 *   PRT-201  Hub                  x1  CAST
 *   PRT-202  Wheel bearing        x1  (no material group, no German text)
 * PRT-001   Spacer   (missing weight, bad revision, no German text: save is blocked)
 * PRT-002   Shim     (complete: save is allowed)
 * </pre>
 */
final class SampleData {

    record Link(String parentId, String childId, int quantity) {
    }

    final Map<String, EngineeringObject> objects = new LinkedHashMap<>();
    final List<Link> links;

    SampleData() {
        add(new EngineeringObject("FLD-PACK", ObjectType.FOLDER, Map.of(),
                text("Packaging", "Verpackung")));

        add(assembly("ASM-100", "Brake caliper assembly", "Bremssattel-Baugruppe"));
        add(part("PRT-101", "Caliper housing", "Bremssattelgehäuse", "CAST", "EN-GJS-400-15", "2.8"));
        add(part("PRT-102", "Piston", "Kolben", "MACHINED", "42CrMo4", "0.35"));
        add(part("PRT-103", "Piston seal", "Kolbendichtung", "SEAL", "EPDM", "0.01"));
        add(part("PRT-104", "Dust boot", "Staubmanschette", "SEAL", "EPDM", "0.02"));
        add(part("PRT-105", "Bleed screw", "Entlüftungsschraube", "FASTENER", "C45", "0.01"));
        add(part("PRT-106", "Washer", "Unterlegscheibe", "FASTENER", "Cu", "0.005"));
        add(assembly("SUB-110", "Bracket sub-assembly", "Halter-Unterbaugruppe"));
        add(part("PRT-111", "Bracket", "Halter", "CAST", "EN-GJS-500-7", "1.2"));
        add(part("PRT-112", "Guide pin", "Führungsbolzen", "FASTENER", "C45", "0.06"));
        add(part("PRT-113", "Bolt M12", "Schraube M12", "FASTENER", "10.9", "0.05"));
        add(drawing("DRW-100", "ASM-100"));
        add(drawing("DRW-101", "PRT-101"));
        add(drawing("DRW-110", "SUB-110"));

        add(assembly("ASM-200", "Wheel hub assembly", "Radnaben-Baugruppe"));
        add(part("PRT-201", "Hub", "Nabe", "CAST", "EN-GJS-500-7", "3.4"));
        add(new EngineeringObject("PRT-202", ObjectType.PART,
                Map.of(PropertyKey.MATERIAL, "100Cr6", PropertyKey.WEIGHT_KG, "0.9", PropertyKey.REVISION, "A"),
                LocalizedText.of("en", "Wheel bearing")));

        add(new EngineeringObject("PRT-001", ObjectType.PART,
                Map.of(PropertyKey.MATERIAL_GROUP, "MACHINED", PropertyKey.MATERIAL, "S235", PropertyKey.REVISION, "a"),
                LocalizedText.of("en", "Spacer")));
        add(part("PRT-002", "Shim", "Passscheibe", "MACHINED", "S235", "0.02"));

        links = List.of(
                new Link("ASM-100", "PRT-101", 1),
                new Link("ASM-100", "PRT-102", 2),
                new Link("ASM-100", "PRT-103", 2),
                new Link("ASM-100", "PRT-104", 2),
                new Link("ASM-100", "PRT-105", 1),
                new Link("ASM-100", "PRT-106", 2),
                new Link("ASM-100", "SUB-110", 1),
                new Link("SUB-110", "PRT-111", 1),
                new Link("SUB-110", "PRT-112", 2),
                new Link("SUB-110", "PRT-113", 2),
                new Link("ASM-200", "PRT-201", 1),
                new Link("ASM-200", "PRT-202", 1));
    }

    private void add(EngineeringObject object) {
        objects.put(object.id(), object);
    }

    private static LocalizedText text(String en, String de) {
        return LocalizedText.of("en", en).with("de", de);
    }

    private static EngineeringObject part(String id, String en, String de, String group, String material, String kg) {
        return new EngineeringObject(id, ObjectType.PART, Map.of(
                PropertyKey.MATERIAL_GROUP, group,
                PropertyKey.MATERIAL, material,
                PropertyKey.WEIGHT_KG, kg,
                PropertyKey.REVISION, "A",
                PropertyKey.CAD_FILE, id.toLowerCase() + ".prt"), text(en, de));
    }

    private static EngineeringObject assembly(String id, String en, String de) {
        return new EngineeringObject(id, ObjectType.ASSEMBLY, Map.of(
                PropertyKey.REVISION, "A",
                PropertyKey.CAD_FILE, id.toLowerCase() + ".asm"), text(en, de));
    }

    private static EngineeringObject drawing(String id, String referencedObject) {
        return new EngineeringObject(id, ObjectType.DRAWING, Map.of(
                PropertyKey.REFERENCED_OBJECT, referencedObject,
                PropertyKey.REVISION, "A",
                PropertyKey.CAD_FILE, id.toLowerCase() + ".drw"), text("Drawing of " + referencedObject,
                "Zeichnung von " + referencedObject));
    }
}
