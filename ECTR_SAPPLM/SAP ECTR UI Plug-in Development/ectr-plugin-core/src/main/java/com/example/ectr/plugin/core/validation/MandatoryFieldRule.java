// VERIFIED: plain Java.
package com.example.ectr.plugin.core.validation;

import com.example.ectr.plugin.api.model.EngineeringObject;
import com.example.ectr.plugin.core.i18n.LocalizableMessage;
import com.example.ectr.plugin.core.i18n.LocalizableMessage.FieldLabel;

import java.util.Objects;
import java.util.Optional;

/** The property must be present and not blank (whitespace-only counts as missing). */
public final class MandatoryFieldRule implements ValidationRule {

    private final String propertyKey;

    public MandatoryFieldRule(String propertyKey) {
        this.propertyKey = Objects.requireNonNull(propertyKey, "propertyKey");
    }

    @Override
    public String id() {
        return "mandatory:" + propertyKey;
    }

    @Override
    public Optional<Violation> check(EngineeringObject object) {
        if (object.property(propertyKey).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(new Violation(object.id(), id(), propertyKey,
                LocalizableMessage.of("validation.mandatory", new FieldLabel(propertyKey))));
    }
}
