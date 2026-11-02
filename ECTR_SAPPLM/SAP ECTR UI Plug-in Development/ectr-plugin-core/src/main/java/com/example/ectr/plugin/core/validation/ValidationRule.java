// VERIFIED: plain Java.
package com.example.ectr.plugin.core.validation;

import com.example.ectr.plugin.api.model.EngineeringObject;

import java.util.Optional;

/** A single metadata check. Implementations must be stateless and thread-safe. */
public interface ValidationRule {

    /** Stable identifier, for example {@code mandatory:MATERIAL_GROUP}. */
    String id();

    /** Returns a violation if the object breaks this rule, otherwise empty. */
    Optional<Violation> check(EngineeringObject object);
}
