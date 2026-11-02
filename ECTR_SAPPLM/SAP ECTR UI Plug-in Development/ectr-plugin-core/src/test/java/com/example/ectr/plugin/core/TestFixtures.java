package com.example.ectr.plugin.core;

import com.example.ectr.plugin.api.model.CadStructureNode;
import com.example.ectr.plugin.api.model.EngineeringObject;
import com.example.ectr.plugin.api.model.LocalizedText;
import com.example.ectr.plugin.api.model.ObjectType;
import com.example.ectr.plugin.api.model.PropertyKey;
import com.example.ectr.plugin.api.port.ObjectPropertyReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/** In-memory test data. Independent of the mock bundle so core tests have no module dependency. */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static EngineeringObject part(String id, String materialGroup) {
        Map<String, String> props = new LinkedHashMap<>();
        if (materialGroup != null) {
            props.put(PropertyKey.MATERIAL_GROUP, materialGroup);
        }
        props.put(PropertyKey.MATERIAL, "S355");
        props.put(PropertyKey.WEIGHT_KG, "1.25");
        props.put(PropertyKey.REVISION, "A");
        return new EngineeringObject(id, ObjectType.PART, props, bilingual(id));
    }

    public static EngineeringObject assembly(String id) {
        return new EngineeringObject(id, ObjectType.ASSEMBLY, Map.of(PropertyKey.REVISION, "A"), bilingual(id));
    }

    public static EngineeringObject drawing(String id, String referencedObject) {
        return new EngineeringObject(id, ObjectType.DRAWING,
                Map.of(PropertyKey.REFERENCED_OBJECT, referencedObject, PropertyKey.REVISION, "A"),
                LocalizedText.of("en", "Drawing " + id));
    }

    public static EngineeringObject folder(String id) {
        return new EngineeringObject(id, ObjectType.FOLDER, Map.of(), LocalizedText.of("en", id));
    }

    public static LocalizedText bilingual(String id) {
        return LocalizedText.of("en", "Item " + id).with("de", "Teil " + id);
    }

    /** Simple thread-unsafe reader for tests. */
    public static final class FakeReader implements ObjectPropertyReader {

        private final Map<String, EngineeringObject> objects = new HashMap<>();
        private final Map<String, List<CadStructureNode>> children = new HashMap<>();
        private final Map<String, Integer> quantities = new HashMap<>();
        private final Map<String, List<String>> drawings = new HashMap<>();

        public FakeReader add(EngineeringObject... list) {
            for (EngineeringObject o : list) {
                objects.put(o.id(), o);
                if (o.type() == ObjectType.DRAWING) {
                    o.property(PropertyKey.REFERENCED_OBJECT).ifPresent(ref ->
                            drawings.computeIfAbsent(ref, k -> new ArrayList<>()).add(o.id()));
                }
            }
            return this;
        }

        /** Declares {@code childId} (quantity {@code qty}) as a child of {@code parentId}. */
        public FakeReader link(String parentId, String childId, int qty) {
            children.computeIfAbsent(parentId, k -> new ArrayList<>()).add(new CadStructureNode(childId, qty, List.of()));
            quantities.put(parentId + ">" + childId, qty);
            return this;
        }

        /** Removes an object but keeps it in structures, to simulate a dangling reference. */
        public FakeReader remove(String id) {
            objects.remove(id);
            return this;
        }

        @Override
        public Optional<EngineeringObject> read(String objectId) {
            return Optional.ofNullable(objects.get(objectId));
        }

        @Override
        public CadStructureNode structure(String rootObjectId) {
            if (!objects.containsKey(rootObjectId)) {
                throw new NoSuchElementException(rootObjectId);
            }
            return build(rootObjectId, 1);
        }

        private CadStructureNode build(String id, int qty) {
            List<CadStructureNode> kids = children.getOrDefault(id, List.of()).stream()
                    .map(c -> build(c.objectId(), c.quantity()))
                    .toList();
            return new CadStructureNode(id, qty, kids);
        }

        @Override
        public List<String> findDrawingsFor(String objectId) {
            return drawings.getOrDefault(objectId, List.of());
        }
    }
}
