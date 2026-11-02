// VERIFIED: plain Java.
package com.example.ectr.plugin.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A text held in several languages, such as a multilingual component description.
 * Keys are language-only locales (for example {@code en}, {@code de}).
 */
public record LocalizedText(Map<Locale, String> values) {

    public LocalizedText {
        Map<Locale, String> copy = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((locale, text) -> copy.put(normalize(locale), text));
        }
        values = Collections.unmodifiableMap(copy);
    }

    public static LocalizedText empty() {
        return new LocalizedText(Map.of());
    }

    public static LocalizedText of(String language, String text) {
        return new LocalizedText(Map.of(Locale.forLanguageTag(language), text));
    }

    public LocalizedText with(String language, String text) {
        Map<Locale, String> copy = new LinkedHashMap<>(values);
        copy.put(Locale.forLanguageTag(language), text);
        return new LocalizedText(copy);
    }

    /** Text in exactly this language, if it exists and is not blank. */
    public Optional<String> get(Locale locale) {
        String text = values.get(normalize(locale));
        return text == null || text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    /** Text in the requested language, else the fallback language, else any non-blank text. */
    public Optional<String> getOrFallback(Locale locale, Locale fallback) {
        return get(locale)
                .or(() -> get(fallback))
                .or(() -> values.values().stream().filter(t -> t != null && !t.isBlank()).findFirst());
    }

    public Set<Locale> languages() {
        return values.keySet();
    }

    private static Locale normalize(Locale locale) {
        return Locale.forLanguageTag(locale.getLanguage());
    }
}
