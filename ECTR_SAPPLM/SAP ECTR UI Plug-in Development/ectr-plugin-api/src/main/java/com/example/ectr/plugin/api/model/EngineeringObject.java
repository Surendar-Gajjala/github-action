// VERIFIED: plain Java. Neutral snapshot of an ECTR object; not an ECTR API type.
package com.example.ectr.plugin.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of an engineering object's metadata at the moment a rule runs.
 *
 * @param id          the object's key as the adapter exposes it (for example a document key)
 * @param type        logical category used to select rules
 * @param properties  logical property name to value (see {@link PropertyKey})
 * @param description multilingual description
 */
public record EngineeringObject(String id, ObjectType type, Map<String, String> properties,
                                LocalizedText description) {

    public EngineeringObject {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties == null ? Map.of() : properties));
        description = description == null ? LocalizedText.empty() : description;
    }

    /** Property value, or empty when it is missing or blank. */
    public Optional<String> property(String key) {
        String value = properties.get(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }
}
