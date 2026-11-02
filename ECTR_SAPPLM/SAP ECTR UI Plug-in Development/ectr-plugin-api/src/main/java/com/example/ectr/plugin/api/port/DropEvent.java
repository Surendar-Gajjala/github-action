// VERIFIED: plain Java value type used by the ILLUSTRATIVE DropHandler port.
package com.example.ectr.plugin.api.port;

import java.util.List;
import java.util.Objects;

/**
 * A drag-and-drop gesture in the Object Browser, already converted to object ids by the adapter.
 *
 * @param sourceIds ids of the dragged objects (at least one)
 * @param targetId  id of the object the user dropped onto
 * @param action    requested action
 */
public record DropEvent(List<String> sourceIds, String targetId, DropAction action) {

    public DropEvent {
        sourceIds = List.copyOf(sourceIds);
        if (sourceIds.isEmpty()) {
            throw new IllegalArgumentException("A drop needs at least one source object");
        }
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(action, "action");
    }
}
