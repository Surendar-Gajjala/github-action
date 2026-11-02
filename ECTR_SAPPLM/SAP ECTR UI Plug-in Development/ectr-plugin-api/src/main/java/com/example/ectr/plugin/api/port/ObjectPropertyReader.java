// ILLUSTRATIVE port. A real implementation calls the customer's ECTR SDK / PlmConnector API.
package com.example.ectr.plugin.api.port;

import com.example.ectr.plugin.api.model.CadStructureNode;
import com.example.ectr.plugin.api.model.EngineeringObject;

import java.util.List;
import java.util.Optional;

/**
 * Read access to engineering object metadata and structures.
 * Implementations must be thread-safe; DS may call them from several threads.
 */
public interface ObjectPropertyReader {

    /** Reads one object, or returns empty when no object has this id. */
    Optional<EngineeringObject> read(String objectId);

    /**
     * Reads the CAD structure below an object. A part with no children returns a leaf node.
     *
     * @throws java.util.NoSuchElementException if no object has this id
     */
    CadStructureNode structure(String rootObjectId);

    /** Ids of drawings that document the given object (empty if none). */
    List<String> findDrawingsFor(String objectId);
}
