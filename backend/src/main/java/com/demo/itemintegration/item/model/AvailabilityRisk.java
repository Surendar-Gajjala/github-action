package com.demo.itemintegration.item.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** Supply availability risk of an Item. */
public enum AvailabilityRisk {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH"),
    NOT_ASSESSED("NOT ASSESSED");

    private final String label;

    AvailabilityRisk(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }
}
