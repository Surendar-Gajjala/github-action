package com.example.ectr.plugin.core.dnd;

import com.example.ectr.plugin.api.port.DropAction;
import com.example.ectr.plugin.api.port.DropEvent;
import com.example.ectr.plugin.core.TestFixtures.FakeReader;
import com.example.ectr.plugin.core.i18n.LocalizableMessage;
import com.example.ectr.plugin.core.packaging.PackagingRuleEngine;
import com.example.ectr.plugin.core.packaging.PackagingRulesLoader;
import com.example.ectr.plugin.core.validation.MetadataValidator;
import com.example.ectr.plugin.core.validation.RuleSetLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.example.ectr.plugin.core.TestFixtures.assembly;
import static com.example.ectr.plugin.core.TestFixtures.drawing;
import static com.example.ectr.plugin.core.TestFixtures.folder;
import static com.example.ectr.plugin.core.TestFixtures.part;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragDropControllerTest {

    private FakeReader reader;
    private DragDropController controller;

    @BeforeEach
    void setUp() {
        reader = new FakeReader()
                .add(folder("FLD"), assembly("ASM-OK"), assembly("ASM-BAD"), assembly("ASM-TARGET"),
                        part("P-1", "STEEL"), part("P-2", "STEEL"), part("P-BAD", null),
                        drawing("D-1", "P-1"))
                .link("ASM-OK", "P-1", 1).link("ASM-OK", "P-2", 1)
                .link("ASM-BAD", "P-1", 1).link("ASM-BAD", "P-BAD", 1)
                .link("ASM-TARGET", "ASM-OK", 1);
        controller = new DragDropController(reader,
                new MetadataValidator(RuleSetLoader.load("")),
                new PackagingRuleEngine(reader, PackagingRulesLoader.load("")));
    }

    private static List<String> keys(DragDropResult result) {
        return result.messages().stream().map(LocalizableMessage::key).toList();
    }

    @Test
    void validAssemblyOntoFolderCreatesPackagingPlan() {
        DragDropResult result = controller.handle(new DropEvent(List.of("ASM-OK"), "FLD", DropAction.COPY));

        assertTrue(result.accepted());
        assertEquals(1, result.plans().size());
        assertEquals(List.of("P-1", "P-2", "D-1"), result.plans().get(0).packages().get("MG-STEEL"));
        assertEquals("drop.packagingCreated", keys(result).get(0));
    }

    @Test
    void assemblyWithIncompletePartIsRejectedWithDetails() {
        DragDropResult result = controller.handle(new DropEvent(List.of("ASM-BAD"), "FLD", DropAction.COPY));

        assertFalse(result.accepted());
        assertTrue(result.plans().isEmpty());
        assertEquals(List.of("drop.invalidSource", "drop.violationLine"), keys(result));
        assertEquals("P-BAD", result.messages().get(0).args().get(0));
    }

    @Test
    void partOntoFolderIsRejected() {
        DragDropResult result = controller.handle(new DropEvent(List.of("P-1"), "FLD", DropAction.COPY));
        assertFalse(result.accepted());
        assertEquals(List.of("drop.onlyAssembliesToFolder"), keys(result));
    }

    @Test
    void validPartOntoAssemblyIsAccepted() {
        DragDropResult result = controller.handle(new DropEvent(List.of("P-1", "P-2"), "ASM-TARGET", DropAction.LINK));
        assertTrue(result.accepted());
        assertEquals(List.of("drop.linkAccepted"), keys(result));
        assertEquals(2, result.messages().get(0).args().get(0));
    }

    @Test
    void incompletePartOntoAssemblyIsRejected() {
        DragDropResult result = controller.handle(new DropEvent(List.of("P-BAD"), "ASM-TARGET", DropAction.LINK));
        assertFalse(result.accepted());
    }

    @Test
    void droppingParentIntoOwnChildIsRejectedAsCycle() {
        DragDropResult result = controller.handle(new DropEvent(List.of("ASM-TARGET"), "ASM-OK", DropAction.LINK));
        assertFalse(result.accepted());
        assertEquals(List.of("drop.cycle"), keys(result));
    }

    @Test
    void droppingOntoItselfIsRejected() {
        DragDropResult result = controller.handle(new DropEvent(List.of("ASM-OK"), "ASM-OK", DropAction.LINK));
        assertEquals(List.of("drop.cycle"), keys(result));
    }

    @Test
    void droppingOntoPartIsRejected() {
        DragDropResult result = controller.handle(new DropEvent(List.of("P-2"), "P-1", DropAction.MOVE));
        assertEquals(List.of("drop.unsupportedTarget"), keys(result));
    }

    @Test
    void unknownObjectsAreRejected() {
        assertEquals(List.of("drop.unknownObject"),
                keys(controller.handle(new DropEvent(List.of("P-1"), "NOPE", DropAction.COPY))));
        assertEquals(List.of("drop.unknownObject"),
                keys(controller.handle(new DropEvent(List.of("NOPE"), "FLD", DropAction.COPY))));
    }
}
