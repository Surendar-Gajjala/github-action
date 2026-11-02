// ILLUSTRATIVE: in-memory stand-in for reading object data through the SAP ECTR / PlmConnector API.
// A real adapter would implement ObjectPropertyReader by calling the customer's ECTR SDK.
package com.example.ectr.plugin.mock;

import com.example.ectr.plugin.api.model.CadStructureNode;
import com.example.ectr.plugin.api.model.EngineeringObject;
import com.example.ectr.plugin.api.model.ObjectType;
import com.example.ectr.plugin.api.model.PropertyKey;
import com.example.ectr.plugin.api.port.ObjectPropertyReader;
import org.osgi.service.component.annotations.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Serves {@link SampleData}. Immutable after construction, therefore thread-safe. */
@Component(service = {ObjectPropertyReader.class, MockObjectRepository.class})
public final class MockObjectRepository implements ObjectPropertyReader {

    private final SampleData data = new SampleData();

    @Override
    public Optional<EngineeringObject> read(String objectId) {
        return Optional.ofNullable(data.objects.get(objectId));
    }

    @Override
    public CadStructureNode structure(String rootObjectId) {
        if (!data.objects.containsKey(rootObjectId)) {
            throw new NoSuchElementException("Object not found: " + rootObjectId);
        }
        return node(rootObjectId, 1);
    }

    @Override
    public List<String> findDrawingsFor(String objectId) {
        return data.objects.values().stream()
                .filter(o -> o.type() == ObjectType.DRAWING)
                .filter(o -> o.property(PropertyKey.REFERENCED_OBJECT).filter(objectId::equals).isPresent())
                .map(EngineeringObject::id)
                .toList();
    }

    /** All demo objects, for the shell's {@code ectr:list} command. */
    public Collection<EngineeringObject> all() {
        return data.objects.values();
    }

    private CadStructureNode node(String id, int quantity) {
        List<CadStructureNode> children = new ArrayList<>();
        for (SampleData.Link link : data.links) {
            if (link.parentId().equals(id)) {
                children.add(node(link.childId(), link.quantity()));
            }
        }
        return new CadStructureNode(id, quantity, children);
    }
}
