// ILLUSTRATIVE port. A real implementation reads the logged-on ECTR user and UI language.
package com.example.ectr.plugin.api.port;

import java.util.Locale;

/** Facts about the current client session that the plug-in needs. */
public interface EctrSession {

    String userId();

    /** UI language used to localize messages and pick description texts. */
    Locale locale();
}
