package com.demo.itemintegration.site.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** What a Site is used for. */
public enum SiteType {
    FABRICATION("Fabrication"),
    IC_ASSEMBLY("IC Assembly"),
    FINAL_ASSEMBLY("Final Assembly"),
    TEST("Test"),
    PACKAGING("Packaging"),
    WAREHOUSE("Warehouse"),
    HQ("HQ"),
    OFFICE("Office");

    private final String label;

    SiteType(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }
}
