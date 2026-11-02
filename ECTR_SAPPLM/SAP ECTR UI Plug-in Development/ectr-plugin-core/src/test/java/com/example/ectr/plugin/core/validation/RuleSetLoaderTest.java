package com.example.ectr.plugin.core.validation;

import com.example.ectr.plugin.api.model.EngineeringObject;
import com.example.ectr.plugin.api.model.LocalizedText;
import com.example.ectr.plugin.api.model.ObjectType;
import com.example.ectr.plugin.api.model.PropertyKey;
import com.example.ectr.plugin.core.TestFixtures;
import com.example.ectr.plugin.core.config.RuleConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleSetLoaderTest {

    private static RuleSet parse(String xml) {
        return RuleSetLoader.load(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "test.xml");
    }

    @Test
    void bundledDefaultLoadsAndAcceptsCompletePart() {
        MetadataValidator validator = new MetadataValidator(RuleSetLoader.load(""));
        assertTrue(validator.validate(TestFixtures.part("P1", "STEEL")).isValid());
    }

    @Test
    void bundledDefaultReportsEveryMissingField() {
        MetadataValidator validator = new MetadataValidator(RuleSetLoader.load(""));
        EngineeringObject incomplete = new EngineeringObject("P2", ObjectType.PART,
                Map.of(PropertyKey.REVISION, "a1"), LocalizedText.of("en", "Bracket"));

        ValidationResult result = validator.validate(incomplete);

        assertEquals(
                java.util.List.of("mandatory:MATERIAL_GROUP", "mandatory:MATERIAL", "mandatory:WEIGHT_KG",
                        "pattern:REVISION", "description-languages"),
                result.violations().stream().map(Violation::ruleId).toList());
    }

    @Test
    void typesWithoutRulesAreAlwaysValid() {
        MetadataValidator validator = new MetadataValidator(RuleSetLoader.load(""));
        assertTrue(validator.validate(TestFixtures.folder("F1")).isValid());
    }

    @Test
    void externalFileOverridesBundledDefault(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("rules.xml");
        Files.writeString(file, """
                <validation-rules>
                  <object-type name="part"><mandatory property="CAD_FILE"/></object-type>
                </validation-rules>
                """);
        MetadataValidator validator = new MetadataValidator(RuleSetLoader.load(file.toString()));
        ValidationResult result = validator.validate(TestFixtures.part("P1", "STEEL"));
        assertFalse(result.isValid());
        assertEquals("mandatory:CAD_FILE", result.violations().get(0).ruleId());
    }

    @Test
    void missingExternalFileIsReported() {
        RuleConfigurationException e = assertThrows(RuleConfigurationException.class,
                () -> RuleSetLoader.load("does/not/exist.xml"));
        assertTrue(e.getMessage().contains("not found"), e.getMessage());
    }

    @Test
    void unknownRuleElementIsRejected() {
        RuleConfigurationException e = assertThrows(RuleConfigurationException.class, () -> parse("""
                <validation-rules>
                  <object-type name="PART"><mandatroy property="X"/></object-type>
                </validation-rules>"""));
        assertTrue(e.getMessage().contains("mandatroy"), e.getMessage());
    }

    @Test
    void unknownObjectTypeIsRejected() {
        assertThrows(RuleConfigurationException.class, () -> parse("""
                <validation-rules><object-type name="WIDGET"/></validation-rules>"""));
    }

    @Test
    void duplicateObjectTypeIsRejected() {
        assertThrows(RuleConfigurationException.class, () -> parse("""
                <validation-rules><object-type name="PART"/><object-type name="PART"/></validation-rules>"""));
    }

    @Test
    void invalidRegexIsRejected() {
        RuleConfigurationException e = assertThrows(RuleConfigurationException.class, () -> parse("""
                <validation-rules>
                  <object-type name="PART"><pattern property="REVISION" regex="[A-Z"/></object-type>
                </validation-rules>"""));
        assertTrue(e.getMessage().contains("invalid regex"), e.getMessage());
    }

    @Test
    void missingAttributeIsRejected() {
        assertThrows(RuleConfigurationException.class, () -> parse("""
                <validation-rules><object-type name="PART"><mandatory/></object-type></validation-rules>"""));
    }

    @Test
    void doctypeIsRejectedToPreventXxe() {
        assertThrows(RuleConfigurationException.class, () -> parse("""
                <?xml version="1.0"?>
                <!DOCTYPE validation-rules [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <validation-rules>&xxe;</validation-rules>"""));
    }

    @Test
    void wrongRootElementIsRejected() {
        assertThrows(RuleConfigurationException.class, () -> parse("<packaging-rules/>"));
    }
}
