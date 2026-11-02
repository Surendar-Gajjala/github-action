package com.example.ectr.plugin.mock;

import com.example.ectr.plugin.api.model.CadStructureNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockObjectRepositoryTest {

    private final MockObjectRepository repository = new MockObjectRepository();

    @Test
    void structureContainsSubAssemblyChildren() {
        CadStructureNode root = repository.structure("ASM-100");
        assertTrue(root.allObjectIds().containsAll(List.of("SUB-110", "PRT-111", "PRT-113")));
        assertEquals(2, root.children().stream().filter(c -> c.objectId().equals("PRT-102")).findFirst()
                .orElseThrow().quantity());
    }

    @Test
    void partHasLeafStructure() {
        assertTrue(repository.structure("PRT-001").isLeaf());
    }

    @Test
    void drawingsAreFoundByReferencedObject() {
        assertEquals(List.of("DRW-101"), repository.findDrawingsFor("PRT-101"));
        assertEquals(List.of(), repository.findDrawingsFor("PRT-102"));
    }

    @Test
    void unknownIds() {
        assertTrue(repository.read("NOPE").isEmpty());
        assertThrows(NoSuchElementException.class, () -> repository.structure("NOPE"));
    }

    @Test
    void everyLinkedChildExists() {
        new SampleData().links.forEach(link ->
                assertTrue(repository.read(link.childId()).isPresent(), "missing " + link.childId()));
    }
}
