package com.example.ectr.plugin.core.packaging;

import com.example.ectr.plugin.core.TestFixtures.FakeReader;
import com.example.ectr.plugin.core.config.RuleConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static com.example.ectr.plugin.core.TestFixtures.assembly;
import static com.example.ectr.plugin.core.TestFixtures.drawing;
import static com.example.ectr.plugin.core.TestFixtures.part;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagingRuleEngineTest {

    private FakeReader reader;

    @BeforeEach
    void setUp() {
        // ASM-1
        //  ├─ P-1 (STEEL)  x1     + drawing D-1
        //  ├─ P-2 (STEEL)  x2
        //  ├─ P-3 (RUBBER) x4
        //  └─ SUB-1        x2     + drawing D-SUB
        //       ├─ P-4 (STEEL) x3
        //       └─ P-5 (no group) x1
        reader = new FakeReader()
                .add(assembly("ASM-1"), assembly("SUB-1"),
                        part("P-1", "STEEL"), part("P-2", "STEEL"), part("P-3", "RUBBER"),
                        part("P-4", "STEEL"), part("P-5", null),
                        drawing("D-1", "P-1"), drawing("D-SUB", "SUB-1"))
                .link("ASM-1", "P-1", 1).link("ASM-1", "P-2", 2).link("ASM-1", "P-3", 4)
                .link("ASM-1", "SUB-1", 2)
                .link("SUB-1", "P-4", 3).link("SUB-1", "P-5", 1);
    }

    @Test
    void groupsByMaterialGroupAndAddsDrawings() {
        PackagingRuleEngine engine = new PackagingRuleEngine(reader,
                List.of(new SameMaterialGroupRule(), new IncludeDrawingsRule()));

        PackagingPlan plan = engine.plan("ASM-1");

        assertEquals(Map.of(
                "MG-STEEL", List.of("P-1", "P-2", "P-4", "D-1"),
                "MG-RUBBER", List.of("P-3"),
                "DOCUMENTATION", List.of("D-SUB"),
                PackagingRuleEngine.UNASSIGNED, List.of("P-5")), plan.packages());
        assertEquals(1, plan.warnings().size());
        assertEquals("packaging.missingMaterialGroup", plan.warnings().get(0).key());
    }

    @Test
    void splitsLargePackagesAfterDrawingsWereAdded() {
        // Configured in the "wrong" order on purpose: the engine sorts by phase.
        PackagingRuleEngine engine = new PackagingRuleEngine(reader,
                List.of(new MaxComponentCountRule(3), new IncludeDrawingsRule(), new SameMaterialGroupRule()));

        PackagingPlan plan = engine.plan("ASM-1");

        assertEquals(List.of("P-1", "P-2", "P-4"), plan.packages().get("MG-STEEL-1"));
        assertEquals(List.of("D-1"), plan.packages().get("MG-STEEL-2"));
        assertTrue(plan.packages().containsKey("MG-RUBBER"));
    }

    @Test
    void withoutRulesEverythingIsUnassigned() {
        PackagingPlan plan = new PackagingRuleEngine(reader, List.of()).plan("ASM-1");
        assertEquals(1, plan.packageCount());
        assertEquals(7, plan.packages().get(PackagingRuleEngine.UNASSIGNED).size());
    }

    @Test
    void quantitiesAreMultipliedAlongTheStructure() {
        PackagingContext[] captured = new PackagingContext[1];
        PackagingRule capture = new PackagingRule() {
            public String id() { return "capture"; }
            public Phase phase() { return Phase.GROUP; }
            public void apply(PackagingContext context) { captured[0] = context; }
        };
        new PackagingRuleEngine(reader, List.of(capture)).plan("ASM-1");

        assertEquals(4, captured[0].quantity("P-3"));
        assertEquals(6, captured[0].quantity("P-4")); // 2 x SUB-1, 3 each
    }

    @Test
    void unreadableComponentIsSkippedWithWarning() {
        reader.remove("P-3");
        PackagingPlan plan = new PackagingRuleEngine(reader, List.of(new SameMaterialGroupRule())).plan("ASM-1");
        assertTrue(plan.warnings().stream().anyMatch(w -> w.key().equals("packaging.unknownObject")));
        assertTrue(plan.packages().values().stream().noneMatch(ids -> ids.contains("P-3")));
    }

    @Test
    void unknownRootIsAnError() {
        PackagingRuleEngine engine = new PackagingRuleEngine(reader, List.of());
        assertThrows(NoSuchElementException.class, () -> engine.plan("NOPE"));
    }

    @Test
    void bundledPackagingRulesLoad() {
        List<PackagingRule> rules = PackagingRulesLoader.load("");
        assertEquals(List.of(SameMaterialGroupRule.ID, IncludeDrawingsRule.ID, MaxComponentCountRule.ID),
                rules.stream().map(PackagingRule::id).toList());
    }

    @Test
    void packagingLoaderRejectsBadMax() {
        String xml = "<packaging-rules><rule id=\"max-components-per-package\" max=\"0\"/></packaging-rules>";
        assertThrows(RuleConfigurationException.class, () -> PackagingRulesLoader.load(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "test.xml"));
    }

    @Test
    void packagingLoaderRejectsUnknownRule() {
        String xml = "<packaging-rules><rule id=\"group-by-colour\"/></packaging-rules>";
        assertThrows(RuleConfigurationException.class, () -> PackagingRulesLoader.load(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "test.xml"));
    }
}
