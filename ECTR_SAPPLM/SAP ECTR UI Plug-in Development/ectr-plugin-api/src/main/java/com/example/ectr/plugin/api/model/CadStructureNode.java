// VERIFIED: plain Java. Neutral CAD/BOM structure tree; not an ECTR API type.
package com.example.ectr.plugin.api.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * One node of a CAD assembly structure.
 *
 * @param objectId id of the engineering object at this position
 * @param quantity how many times the component occurs under its parent
 * @param children child positions (empty for a leaf part)
 */
public record CadStructureNode(String objectId, int quantity, List<CadStructureNode> children) {

    public CadStructureNode {
        Objects.requireNonNull(objectId, "objectId");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be >= 1 for " + objectId);
        }
        children = List.copyOf(children == null ? List.of() : children);
    }

    public static CadStructureNode leaf(String objectId, int quantity) {
        return new CadStructureNode(objectId, quantity, List.of());
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    /** Visits this node and then all descendants, depth first. */
    public void walk(Consumer<CadStructureNode> visitor) {
        visitor.accept(this);
        children.forEach(child -> child.walk(visitor));
    }

    /** All object ids in this structure, depth first, including this node. */
    public List<String> allObjectIds() {
        List<String> ids = new ArrayList<>();
        walk(node -> ids.add(node.objectId()));
        return ids;
    }
}
