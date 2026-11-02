package com.example.ectr.plugin.core.validation;

import com.example.ectr.plugin.api.model.EngineeringObject;
import com.example.ectr.plugin.api.model.LocalizedText;
import com.example.ectr.plugin.api.model.ObjectType;
import com.example.ectr.plugin.api.model.PropertyKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationRulesTest {

    private static EngineeringObject partWith(String key, String value) {
        Map<String, String> props = new HashMap<>();
        props.put(key, value);
        return new EngineeringObject("P1", ObjectType.PART, props, LocalizedText.of("en", "Bolt"));
    }

    @Test
    void mandatoryPassesWhenValuePresent() {
        assertTrue(new MandatoryFieldRule(PropertyKey.MATERIAL).check(partWith(PropertyKey.MATERIAL, "S355")).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void mandatoryFailsWhenMissingOrBlank(String value) {
        Optional<Violation> v = new MandatoryFieldRule(PropertyKey.MATERIAL).check(partWith(PropertyKey.MATERIAL, value));
        assertTrue(v.isPresent());
        assertEquals("mandatory:MATERIAL", v.get().ruleId());
        assertEquals(PropertyKey.MATERIAL, v.get().propertyKey());
        assertEquals("validation.mandatory", v.get().message().key());
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "B", "ZZ"})
    void patternAcceptsMatchingValues(String revision) {
        PatternRule rule = new PatternRule(PropertyKey.REVISION, Pattern.compile("[A-Z]{1,2}"), "A..ZZ");
        assertTrue(rule.check(partWith(PropertyKey.REVISION, revision)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "ABC", "1", "A1"})
    void patternRejectsNonMatchingValues(String revision) {
        PatternRule rule = new PatternRule(PropertyKey.REVISION, Pattern.compile("[A-Z]{1,2}"), "A..ZZ");
        Optional<Violation> v = rule.check(partWith(PropertyKey.REVISION, revision));
        assertTrue(v.isPresent());
        assertEquals(revision, v.get().message().args().get(1));
    }

    @Test
    void patternIgnoresEmptyValueBecauseMandatoryHandlesIt() {
        PatternRule rule = new PatternRule(PropertyKey.REVISION, Pattern.compile("[A-Z]"), null);
        assertTrue(rule.check(partWith(PropertyKey.REVISION, "")).isEmpty());
    }

    @Test
    void languagesRuleReportsFirstMissingLanguage() {
        RequiredLanguagesRule rule = new RequiredLanguagesRule(List.of(Locale.ENGLISH, Locale.GERMAN));
        Optional<Violation> v = rule.check(partWith("X", "y"));
        assertTrue(v.isPresent());
        assertEquals(List.of("de"), v.get().message().args());
    }

    @Test
    void languagesRuleTreatsBlankTranslationAsMissing() {
        RequiredLanguagesRule rule = new RequiredLanguagesRule(List.of(Locale.GERMAN));
        EngineeringObject obj = new EngineeringObject("P1", ObjectType.PART, Map.of(),
                LocalizedText.of("en", "Bolt").with("de", "  "));
        assertTrue(rule.check(obj).isPresent());
    }

    @Test
    void languagesRuleMatchesRegionalLocalesByLanguage() {
        RequiredLanguagesRule rule = new RequiredLanguagesRule(List.of(Locale.GERMAN));
        EngineeringObject obj = new EngineeringObject("P1", ObjectType.PART, Map.of(),
                new LocalizedText(Map.of(Locale.GERMANY, "Schraube")));
        assertTrue(rule.check(obj).isEmpty());
    }
}
