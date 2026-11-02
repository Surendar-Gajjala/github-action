// VERIFIED: plain Java.
package com.example.ectr.plugin.core.config;

/** Thrown when a rule XML file is missing, malformed, or contains values we do not understand. */
public class RuleConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RuleConfigurationException(String message) {
        super(message);
    }

    public RuleConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
