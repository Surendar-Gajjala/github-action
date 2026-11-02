// VERIFIED: plain Java value type used by the ILLUSTRATIVE SaveInterceptor port.
package com.example.ectr.plugin.api.port;

import java.util.List;

/**
 * Result of a pre-save check.
 *
 * @param allowed  {@code false} means the host must not save the object
 * @param messages user-facing, already localized messages (may be non-empty even when allowed)
 */
public record SaveDecision(boolean allowed, List<String> messages) {

    public SaveDecision {
        messages = List.copyOf(messages);
    }

    public static SaveDecision allow() {
        return new SaveDecision(true, List.of());
    }

    public static SaveDecision reject(List<String> messages) {
        return new SaveDecision(false, messages);
    }
}
