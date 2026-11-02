// VERIFIED: plain Java (java.util.ResourceBundle). Works inside an OSGi bundle because the
// bundle's own class loader is passed explicitly.
package com.example.ectr.plugin.core.i18n;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Translates {@link LocalizableMessage}s using {@code messages*.properties} in this bundle.
 * <p>
 * Lookup order is the requested language, then the base file (English). The JVM default
 * locale is deliberately ignored so results do not depend on the workstation's OS settings.
 */
public final class Messages {

    static final String BUNDLE = "com.example.ectr.plugin.core.i18n.messages";

    private static final ResourceBundle.Control NO_DEFAULT_LOCALE =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private final Locale locale;
    private final ResourceBundle bundle;

    public Messages(Locale locale) {
        this.locale = locale;
        this.bundle = ResourceBundle.getBundle(BUNDLE, locale, Messages.class.getClassLoader(), NO_DEFAULT_LOCALE);
    }

    public Locale locale() {
        return locale;
    }

    public String format(LocalizableMessage message) {
        Object[] args = message.args().stream().map(this::translateArg).toArray();
        return format(message.key(), args);
    }

    public String format(String key, Object... args) {
        String pattern;
        try {
            pattern = bundle.getString(key);
        } catch (MissingResourceException e) {
            // A missing translation must never hide the actual problem from the user.
            return key + " " + List.of(args);
        }
        return new MessageFormat(pattern, locale).format(args);
    }

    private Object translateArg(Object arg) {
        if (arg instanceof LocalizableMessage nested) {
            return format(nested);
        }
        if (arg instanceof LocalizableMessage.FieldLabel field) {
            return lookupOrDefault("field." + field.propertyKey(), field.propertyKey());
        }
        if (arg instanceof LocalizableMessage.TypeLabel type) {
            return lookupOrDefault("type." + type.type().name(), type.type().name());
        }
        return arg;
    }

    private String lookupOrDefault(String key, String fallback) {
        return bundle.containsKey(key) ? bundle.getString(key) : fallback;
    }
}
