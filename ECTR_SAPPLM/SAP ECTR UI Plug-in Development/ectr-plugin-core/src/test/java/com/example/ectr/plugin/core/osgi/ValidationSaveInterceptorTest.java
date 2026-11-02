package com.example.ectr.plugin.core.osgi;

import com.example.ectr.plugin.api.model.EngineeringObject;
import com.example.ectr.plugin.api.model.LocalizedText;
import com.example.ectr.plugin.api.model.ObjectType;
import com.example.ectr.plugin.api.port.EctrSession;
import com.example.ectr.plugin.api.port.SaveDecision;
import com.example.ectr.plugin.api.port.UserNotifier;
import com.example.ectr.plugin.core.TestFixtures;
import com.example.ectr.plugin.core.validation.RuleSet;
import com.example.ectr.plugin.core.validation.ValidationRule;
import com.example.ectr.plugin.core.validation.Violation;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The DS component is a plain class thanks to constructor injection, so no OSGi framework is needed. */
class ValidationSaveInterceptorTest {

    private final RecordingNotifier notifier = new RecordingNotifier();

    private ValidationSaveInterceptor interceptor(RuleRepository rules, Locale locale, boolean block) {
        EctrSession session = new EctrSession() {
            public String userId() { return "tester"; }
            public Locale locale() { return locale; }
        };
        ValidationSaveInterceptor.Config config = new ValidationSaveInterceptor.Config() {
            public boolean blockOnViolation() { return block; }
            public Class<? extends Annotation> annotationType() { return ValidationSaveInterceptor.Config.class; }
        };
        return new ValidationSaveInterceptor(rules, session, notifier, config);
    }

    private static EngineeringObject incompletePart() {
        return new EngineeringObject("PRT-9", ObjectType.PART, Map.of("REVISION", "A"), LocalizedText.of("en", "Pin"));
    }

    @Test
    void completeObjectIsSavedSilently() {
        SaveDecision decision = interceptor(RuleRepository.bundledDefaults(), Locale.ENGLISH, true)
                .beforeSave(TestFixtures.part("PRT-1", "STEEL"));
        assertTrue(decision.allowed());
        assertTrue(notifier.all.isEmpty());
    }

    @Test
    void incompleteObjectIsBlockedWithReadableMessage() {
        SaveDecision decision = interceptor(RuleRepository.bundledDefaults(), Locale.ENGLISH, true)
                .beforeSave(incompletePart());

        assertFalse(decision.allowed());
        assertEquals("Cannot save part PRT-9. Please complete the following:", decision.messages().get(0));
        assertTrue(decision.messages().contains("  - Field \"Material group\" is mandatory."), decision.messages().toString());
        assertEquals(1, notifier.errors.size());
    }

    @Test
    void messagesFollowSessionLanguage() {
        SaveDecision decision = interceptor(RuleRepository.bundledDefaults(), Locale.GERMAN, true)
                .beforeSave(incompletePart());
        assertTrue(decision.messages().contains("  - Das Feld „Warengruppe“ ist ein Pflichtfeld."),
                decision.messages().toString());
    }

    @Test
    void warningModeAllowsSaveButStillInformsUser() {
        SaveDecision decision = interceptor(RuleRepository.bundledDefaults(), Locale.ENGLISH, false)
                .beforeSave(incompletePart());
        assertTrue(decision.allowed());
        assertFalse(decision.messages().isEmpty());
        assertEquals(1, notifier.warnings.size());
    }

    @Test
    void crashingRuleBlocksSaveAndGivesIncidentReference() {
        ValidationRule broken = new ValidationRule() {
            public String id() { return "broken"; }
            public Optional<Violation> check(EngineeringObject object) { throw new IllegalStateException("boom"); }
        };
        RuleRepository rules = new RuleRepository(new RuleSet(Map.of(ObjectType.PART, List.of(broken))), List.of());

        SaveDecision decision = interceptor(rules, Locale.ENGLISH, true).beforeSave(incompletePart());

        assertFalse(decision.allowed());
        assertTrue(decision.messages().get(0).matches(".*reference ECTR-[0-9A-F]{8}\\.$"), decision.messages().get(0));
    }

    private static final class RecordingNotifier implements UserNotifier {
        final List<String> all = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        public void info(String message) { all.add(message); }
        public void warn(String message) { all.add(message); warnings.add(message); }
        public void error(String message) { all.add(message); errors.add(message); }
    }
}
