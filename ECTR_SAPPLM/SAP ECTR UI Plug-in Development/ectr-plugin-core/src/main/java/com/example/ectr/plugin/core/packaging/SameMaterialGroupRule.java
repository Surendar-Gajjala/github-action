// VERIFIED: plain Java.
package com.example.ectr.plugin.core.packaging;

import com.example.ectr.plugin.api.model.PropertyKey;
import com.example.ectr.plugin.core.i18n.LocalizableMessage;

/**
 * GROUP phase: one package per material group, named {@code MG-<group>}.
 * Components without a material group are left unassigned and reported as a warning.
 */
public final class SameMaterialGroupRule implements PackagingRule {

    public static final String ID = "group-by-material-group";
    static final String PREFIX = "MG-";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Phase phase() {
        return Phase.GROUP;
    }

    @Override
    public void apply(PackagingContext context) {
        context.components().values().forEach(component -> component.property(PropertyKey.MATERIAL_GROUP)
                .ifPresentOrElse(
                        group -> context.addToPackage(PREFIX + group, component.id()),
                        () -> context.warn(LocalizableMessage.of("packaging.missingMaterialGroup",
                                component.id(), PackagingRuleEngine.UNASSIGNED))));
    }
}
