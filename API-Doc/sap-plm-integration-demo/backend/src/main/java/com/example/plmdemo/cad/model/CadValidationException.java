package com.example.plmdemo.cad.model;

import java.util.List;

public class CadValidationException extends RuntimeException {

    private final List<String> violations;

    public CadValidationException(List<String> violations) {
        super("CAD data cannot be imported: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
