package com.example.ectr.plugin.core.i18n;

import com.example.ectr.plugin.api.model.ObjectType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesTest {

    @Test
    void englishFieldLabelIsInserted() {
        String text = new Messages(Locale.ENGLISH).format(
                LocalizableMessage.of("validation.mandatory", new LocalizableMessage.FieldLabel("MATERIAL_GROUP")));
        assertEquals("Field \"Material group\" is mandatory.", text);
    }

    @Test
    void germanTextsAreUsedForGerman() {
        String text = new Messages(Locale.GERMANY).format(
                LocalizableMessage.of("save.blocked", new LocalizableMessage.TypeLabel(ObjectType.PART), "PRT-1"));
        assertEquals("Bauteil PRT-1 kann nicht gesichert werden. Bitte ergänzen Sie:", text);
    }

    @Test
    void unsupportedLanguageFallsBackToEnglishNotJvmDefault() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN);
            String text = new Messages(Locale.FRENCH).format("drop.unknownObject", "X");
            assertEquals("Object X was not found.", text);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void nestedMessagesAreTranslated() {
        LocalizableMessage inner = LocalizableMessage.of("validation.language", "de");
        String text = new Messages(Locale.ENGLISH).format(LocalizableMessage.of("drop.violationLine", "P-1", inner));
        assertEquals("  - P-1: The description is missing in language \"de\".", text);
    }

    @Test
    void choiceFormatHandlesSingularAndPlural() {
        Messages en = new Messages(Locale.ENGLISH);
        assertEquals("1 object can be added to assembly A.", en.format("drop.linkAccepted", 1, "A"));
        assertEquals("3 objects can be added to assembly A.", en.format("drop.linkAccepted", 3, "A"));
    }

    @Test
    void missingKeyShowsKeyInsteadOfFailing() {
        assertTrue(new Messages(Locale.ENGLISH).format("no.such.key", "A").startsWith("no.such.key"));
    }

    @Test
    void germanBundleHasEveryEnglishKey() throws IOException {
        Set<String> missing = new TreeSet<>(load("messages.properties").stringPropertyNames());
        missing.removeAll(load("messages_de.properties").stringPropertyNames());
        assertTrue(missing.isEmpty(), "Keys missing in messages_de.properties: " + missing);
    }

    private static Properties load(String file) throws IOException {
        String path = "/" + Messages.BUNDLE.replace('.', '/').replace("messages", "") + file;
        try (InputStream in = MessagesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            Properties properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return properties;
        }
    }
}
