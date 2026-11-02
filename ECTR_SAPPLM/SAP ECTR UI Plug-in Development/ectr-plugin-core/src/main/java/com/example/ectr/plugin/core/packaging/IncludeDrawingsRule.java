// VERIFIED: plain Java.
package com.example.ectr.plugin.core.packaging;

import com.example.ectr.plugin.api.model.PropertyKey;

/**
 * ENRICH phase: each drawing goes into the same package as the component it documents.
 * Drawings of the assembly itself or of sub-assemblies go into package {@value #DOCUMENTATION}.
 */
public final class IncludeDrawingsRule implements PackagingRule {

    public static final String ID = "include-drawings";
    public static final String DOCUMENTATION = "DOCUMENTATION";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Phase phase() {
        return Phase.ENRICH;
    }

    @Override
    public void apply(PackagingContext context) {
        context.drawings().values().forEach(drawing -> {
            String target = drawing.property(PropertyKey.REFERENCED_OBJECT)
                    .flatMap(context::packageOf)
                    .orElse(DOCUMENTATION);
            context.addToPackage(target, drawing.id());
        });
    }
}
