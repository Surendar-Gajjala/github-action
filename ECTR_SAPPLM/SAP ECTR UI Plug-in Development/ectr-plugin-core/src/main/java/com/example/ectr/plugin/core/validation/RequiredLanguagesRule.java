// VERIFIED: plain Java.
package com.example.ectr.plugin.core.validation;

import com.example.ectr.plugin.api.model.EngineeringObject;
import com.example.ectr.plugin.core.i18n.LocalizableMessage;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The multilingual description must exist in each listed language.
 * Reports the first missing language; the user fixes one at a time and saves again.
 */
public final class RequiredLanguagesRule implements ValidationRule {

    private final List<Locale> languages;

    public RequiredLanguagesRule(List<Locale> languages) {
        if (languages.isEmpty()) {
            throw new IllegalArgumentException("At least one language is required");
        }
        this.languages = List.copyOf(languages);
    }

    @Override
    public String id() {
        return "description-languages";
    }

    @Override
    public Optional<Violation> check(EngineeringObject object) {
        return languages.stream()
                .filter(lang -> object.description().get(lang).isEmpty())
                .findFirst()
                .map(missing -> new Violation(object.id(), id(), null,
                        LocalizableMessage.of("validation.language", missing.getLanguage())));
    }
}
