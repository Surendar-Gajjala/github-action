package com.example.plmdemo.cad.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CadStructureMapperTest {

    private final CadStructureMapper mapper = new CadStructureMapper();

    private static CadAssembly.Component comp(String id, String part, String qty, String unit, boolean excluded) {
        return new CadAssembly.Component(id, part, "A", part + " desc", qty == null ? null : new BigDecimal(qty), unit,
                part.toLowerCase() + ".prt", excluded);
    }

    private static CadAssembly motor(List<CadAssembly.Component> components) {
        return new CadAssembly("DEMO-CAD", "motor-100.asm", "designer01",
                "MOTOR-100", "D", "1000", "ECN-5001", "Motor", components);
    }

    @Test
    void groupsInstancesAndSeparatesStructureFromBom() {
        CadStructure s = mapper.analyze(motor(List.of(
                comp("i-001", "SHAFT-101", "1", "EA", false),
                comp("i-002", "BEARING-201", "1", "EA", false),
                comp("i-003", "bearing-201", null, null, false),
                comp("i-004", "SKEL-100", "1", "EA", true))));

        assertThat(s.partNumber()).isEqualTo("MOTOR-100");
        assertThat(s.lines()).extracting(CadStructure.Line::partNumber)
                .containsExactly("SHAFT-101", "BEARING-201", "SKEL-100");
        assertThat(s.bomLines()).extracting(CadStructure.Line::partNumber)
                .containsExactly("SHAFT-101", "BEARING-201");

        CadStructure.Line bearing = s.lines().get(1);
        assertThat(bearing.structureQuantity()).isEqualByComparingTo("2");
        assertThat(bearing.bomQuantity()).isEqualByComparingTo("2");
        assertThat(bearing.instanceIds()).containsExactly("i-002", "i-003");

        CadStructure.Line skeleton = s.lines().get(2);
        assertThat(skeleton.structureQuantity()).isEqualByComparingTo("1");
        assertThat(skeleton.bomRelevant()).isFalse();

        assertThat(s.cadInstances()).isEqualTo(4);
        assertThat(s.excludedInstances()).isEqualTo(1);
        assertThat(s.notes()).anyMatch(n -> n.contains("SKEL-100") && n.contains("excluded"))
                .anyMatch(n -> n.startsWith("BEARING-201: 2 CAD instances"));
    }

    @Test
    void invalidCadDataIsRejectedWithAllProblems() {
        assertThatThrownBy(() -> mapper.analyze(motor(List.of(
                comp("i-001", "SHAFT-101", "0", "EA", false),
                comp("i-002", "BEARING-201", "1", "EA", false),
                comp("i-002", "BEARING-201", "1", "KG", false),
                comp("i-004", "MOTOR-100", "1", "EA", false)))))
                .isInstanceOfSatisfying(CadValidationException.class, e -> assertThat(e.getViolations())
                        .anyMatch(v -> v.contains("invalid quantity 0"))
                        .anyMatch(v -> v.contains("used more than once"))
                        .anyMatch(v -> v.contains("different units"))
                        .anyMatch(v -> v.contains("references the assembly itself")));
    }

    @Test
    void onlyExcludedComponentsIsRejected() {
        assertThatThrownBy(() -> mapper.analyze(motor(List.of(
                comp("i-004", "SKEL-100", "1", "EA", true)))))
                .hasMessageContaining("No BOM-relevant components");
    }
}
