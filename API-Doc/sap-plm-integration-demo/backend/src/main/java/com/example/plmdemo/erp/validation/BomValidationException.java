package com.example.plmdemo.erp.validation;

import java.util.List;

public class BomValidationException extends RuntimeException {

    private final List<String> violations;

    public BomValidationException(List<String> violations) {
        super("BOM validation failed: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
