// VERIFIED: plain Java.
package com.example.ectr.plugin.core.i18n;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A message that is not yet translated: a resource-bundle key plus its arguments.
 * Rules return these so that the same result can be shown in any UI language.
 * Arguments of type {@link FieldLabel}, {@link TypeLabel} or a nested {@code LocalizableMessage}
 * are translated as well.
 */
public record LocalizableMessage(String key, List<Object> args) {

    public LocalizableMessage {
        Objects.requireNonNull(key, "key");
        args = List.copyOf(args);
    }

    public static LocalizableMessage of(String key, Object... args) {
        return new LocalizableMessage(key, Arrays.asList(args));
    }

    /** Argument placeholder that is replaced by the localized label of a property key. */
    public record FieldLabel(String propertyKey) {
    }

    /** Argument placeholder that is replaced by the localized name of an object type. */
    public record TypeLabel(Enum<?> type) {
    }
}
