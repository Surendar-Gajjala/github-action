// VERIFIED: plain Java.
package com.example.ectr.plugin.core.packaging;

/**
 * One step of assembly packaging. Rules mutate the shared {@link PackagingContext}.
 * The engine runs rules ordered by {@link Phase}; within a phase it keeps the configured order.
 */
public interface PackagingRule {

    enum Phase {
        /** Put components into packages. */
        GROUP,
        /** Add related objects (drawings, documents) to existing packages. */
        ENRICH,
        /** Reshape packages (split, merge) after their content is final. */
        SPLIT
    }

    String id();

    Phase phase();

    void apply(PackagingContext context);
}
