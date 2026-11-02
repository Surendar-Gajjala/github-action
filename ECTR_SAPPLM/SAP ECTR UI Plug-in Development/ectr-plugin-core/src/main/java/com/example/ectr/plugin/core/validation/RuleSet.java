// VERIFIED: plain Java.
package com.example.ectr.plugin.core.validation;

import com.example.ectr.plugin.api.model.ObjectType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Validation rules per object type. Immutable once built; safe to share between threads. */
public final class RuleSet {

    private final Map<ObjectType, List<ValidationRule>> rules;

    public RuleSet(Map<ObjectType, List<ValidationRule>> rules) {
        EnumMap<ObjectType, List<ValidationRule>> copy = new EnumMap<>(ObjectType.class);
        rules.forEach((type, list) -> copy.put(type, List.copyOf(list)));
        this.rules = copy;
    }

    /** Rules for a type; an empty list when the type has no rules configured. */
    public List<ValidationRule> rulesFor(ObjectType type) {
        return rules.getOrDefault(type, List.of());
    }
}
