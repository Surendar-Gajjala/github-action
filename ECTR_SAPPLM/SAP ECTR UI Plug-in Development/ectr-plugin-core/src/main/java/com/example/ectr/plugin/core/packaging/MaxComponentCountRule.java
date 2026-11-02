// VERIFIED: plain Java.
package com.example.ectr.plugin.core.packaging;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SPLIT phase: a package with more than {@code max} entries is split into
 * {@code NAME-1}, {@code NAME-2}, ... keeping the original order. Drawings count as entries.
 */
public final class MaxComponentCountRule implements PackagingRule {

    public static final String ID = "max-components-per-package";

    private final int max;

    public MaxComponentCountRule(int max) {
        if (max < 1) {
            throw new IllegalArgumentException("max must be >= 1, was " + max);
        }
        this.max = max;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Phase phase() {
        return Phase.SPLIT;
    }

    @Override
    public void apply(PackagingContext context) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        context.packages().forEach((name, ids) -> {
            if (ids.size() <= max) {
                result.put(name, ids);
                return;
            }
            for (int start = 0, part = 1; start < ids.size(); start += max, part++) {
                result.put(name + "-" + part, new ArrayList<>(ids.subList(start, Math.min(start + max, ids.size()))));
            }
        });
        context.packages().clear();
        context.packages().putAll(result);
    }
}
