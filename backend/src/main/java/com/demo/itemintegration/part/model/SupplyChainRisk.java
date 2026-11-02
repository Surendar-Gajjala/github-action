package com.demo.itemintegration.part.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** Supply chain risk of a Part. */
public enum SupplyChainRisk {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH"),
    NOT_ASSESSED("Not Assessed");

    private final String label;

    SupplyChainRisk(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }
}
