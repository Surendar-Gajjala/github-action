// VERIFIED: plain Java value type used by the ILLUSTRATIVE DropHandler port.
package com.example.ectr.plugin.api.port;

import java.util.List;

/**
 * What the plug-in decided about a drop.
 *
 * @param accepted {@code false} means the host should cancel the drop
 * @param messages user-facing, localized messages
 */
public record DropOutcome(boolean accepted, List<String> messages) {

    public DropOutcome {
        messages = List.copyOf(messages);
    }
}
