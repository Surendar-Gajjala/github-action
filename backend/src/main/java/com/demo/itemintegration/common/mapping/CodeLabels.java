package com.demo.itemintegration.common.mapping;

import static java.util.Map.entry;

import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Readable labels for status-like codes the hosted server returns
 * ({@code PRODN_APPROVED} → "Production Approved", {@code N_A} → "N/A").
 * Codes that are not listed are returned unchanged, so a new code still shows up
 * rather than disappearing.
 */
public final class CodeLabels {

    private static final Map<String, String> LABELS = Map.ofEntries(
            // Item status
            entry("PRODN_APPROVED", "Production Approved"),
            entry("UN_QUAL", "Unqualified"),
            entry("UNQUALIFIED", "Unqualified"),
            entry("CONDITIONAL", "Conditional"),
            entry("PRELIMINARY", "Preliminary"),
            entry("DESIGN", "Design"),
            entry("OBSOLETE", "Obsolete"),
            // Exemption status
            entry("APPROVED", "Approved"),
            entry("PENDING", "Pending"),
            entry("REJECTED", "Rejected"),
            // Shared / environmental compliance
            entry("N_A", "N/A"),
            entry("YES", "Yes"),
            entry("NO", "No"),
            entry("NO_DATA", "No Data"),
            entry("NOT_LOCKED", "Not Locked"),
            entry("LOCKED", "Locked"),
            entry("EXCL_ITEM", "Excluded Item"),
            entry("IMPLICIT", "Implicit"),
            // Risk and sourcing status
            entry("NOT_ASSESSED", "Not Assessed"),
            entry("UNKNOWN", "Unknown"));

    private CodeLabels() {
    }

    /** The label for a code, the trimmed original text when unknown, or {@code null} when blank. */
    public static String label(JsonNode node) {
        String value = ExternalValues.text(node);
        if (value == null) {
            return null;
        }
        return LABELS.getOrDefault(value.toUpperCase(Locale.ROOT), value);
    }
}
