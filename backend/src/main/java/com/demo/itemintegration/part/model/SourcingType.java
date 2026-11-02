package com.demo.itemintegration.part.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** How a Part is sourced. */
public enum SourcingType {
    OFF_THE_SHELF("Off-the-shelf"),
    CUSTOM("Custom"),
    CONFLICT("Conflict"),
    UNKNOWN("Unknown");

    private final String label;

    SourcingType(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }
}
