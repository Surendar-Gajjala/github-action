// VERIFIED: plain Java.
package com.example.ectr.plugin.core.validation;

import java.util.ArrayList;
import java.util.List;

/** All violations found for one object or a set of objects. */
public record ValidationResult(List<Violation> violations) {

    public ValidationResult {
        violations = List.copyOf(violations);
    }

    public static ValidationResult ok() {
        return new ValidationResult(List.of());
    }

    public boolean isValid() {
        return violations.isEmpty();
    }

    public ValidationResult merge(ValidationResult other) {
        List<Violation> all = new ArrayList<>(violations);
        all.addAll(other.violations);
        return new ValidationResult(all);
    }
}
