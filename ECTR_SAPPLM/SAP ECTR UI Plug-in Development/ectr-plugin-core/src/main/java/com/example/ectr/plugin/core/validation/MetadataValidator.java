// VERIFIED: plain Java.
package com.example.ectr.plugin.core.validation;

import com.example.ectr.plugin.api.model.EngineeringObject;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Runs the rules for an object's type and collects every violation (not just the first). */
public final class MetadataValidator {

    private final RuleSet ruleSet;

    public MetadataValidator(RuleSet ruleSet) {
        this.ruleSet = Objects.requireNonNull(ruleSet, "ruleSet");
    }

    public ValidationResult validate(EngineeringObject object) {
        List<Violation> violations = ruleSet.rulesFor(object.type()).stream()
                .map(rule -> rule.check(object))
                .flatMap(Optional::stream)
                .toList();
        return new ValidationResult(violations);
    }

    public ValidationResult validateAll(Collection<EngineeringObject> objects) {
        return objects.stream()
                .map(this::validate)
                .reduce(ValidationResult.ok(), ValidationResult::merge);
    }
}
