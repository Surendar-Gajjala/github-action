package com.demo.itemintegration.part.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** Lifecycle status of a Part. */
public enum LifecycleStatus {
    ACTIVE("Active"),
    NRND("NRND"),
    LAST_TIME_BUY("LastTimeBuy"),
    OBSOLETE("Obsolete"),
    UNKNOWN("Unknown");

    private final String label;

    LifecycleStatus(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }
}
