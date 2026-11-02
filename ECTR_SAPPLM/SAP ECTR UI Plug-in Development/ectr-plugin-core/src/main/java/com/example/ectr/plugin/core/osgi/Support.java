// VERIFIED: plain Java.
package com.example.ectr.plugin.core.osgi;

import com.example.ectr.plugin.api.port.EctrSession;

import java.util.Locale;
import java.util.UUID;

/** Small helpers shared by the components in this package. */
final class Support {

    private static final System.Logger LOG = System.getLogger(Support.class.getName());

    private Support() {
    }

    /**
     * Short id shown to the user and written to the log with the stack trace,
     * so support can find the matching log entry.
     */
    static String newIncidentReference() {
        return "ECTR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    /** The session language, or English if the session cannot tell us. */
    static Locale safeLocale(EctrSession session) {
        try {
            Locale locale = session.locale();
            return locale == null ? Locale.ENGLISH : locale;
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot read session locale, using English", e);
            return Locale.ENGLISH;
        }
    }
}
