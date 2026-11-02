// VERIFIED: plain Java.
package com.example.ectr.plugin.core.packaging;

import com.example.ectr.plugin.core.i18n.LocalizableMessage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of packaging one assembly.
 *
 * @param rootId   the assembly that was packaged
 * @param packages package name to object ids, in creation order
 * @param warnings things the user should know (for example components without a material group)
 */
public record PackagingPlan(String rootId, Map<String, List<String>> packages, List<LocalizableMessage> warnings) {

    public PackagingPlan {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        packages.forEach((name, ids) -> copy.put(name, List.copyOf(ids)));
        packages = Collections.unmodifiableMap(copy);
        warnings = List.copyOf(warnings);
    }

    public int packageCount() {
        return packages.size();
    }
}
