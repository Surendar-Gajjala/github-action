// VERIFIED: plain Java.
package com.example.ectr.plugin.core.validation;

import com.example.ectr.plugin.api.model.EngineeringObject;
import com.example.ectr.plugin.core.i18n.LocalizableMessage;
import com.example.ectr.plugin.core.i18n.LocalizableMessage.FieldLabel;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * If the property has a value, the whole value must match the regular expression.
 * An empty value passes; combine with {@link MandatoryFieldRule} to require it.
 */
public final class PatternRule implements ValidationRule {

    private final String propertyKey;
    private final Pattern pattern;
    private final String example;

    public PatternRule(String propertyKey, Pattern pattern, String example) {
        this.propertyKey = Objects.requireNonNull(propertyKey, "propertyKey");
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.example = example == null || example.isBlank() ? pattern.pattern() : example;
    }

    @Override
    public String id() {
        return "pattern:" + propertyKey;
    }

    @Override
    public Optional<Violation> check(EngineeringObject object) {
        return object.property(propertyKey)
                .filter(value -> !pattern.matcher(value).matches())
                .map(value -> new Violation(object.id(), id(), propertyKey,
                        LocalizableMessage.of("validation.pattern", new FieldLabel(propertyKey), value, example)));
    }
}
