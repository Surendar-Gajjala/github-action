// VERIFIED: plain Java. JCo-independent result of one RFC call.
package com.example.ectr.dms.sap;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @param exports export parameters: {@code String} or {@code Map<String, Object>} (structure)
 * @param tables  table parameters after the call
 */
public record RfcResult(Map<String, Object> exports, Map<String, List<Map<String, Object>>> tables) {

    public RfcResult {
        exports = Collections.unmodifiableMap(new LinkedHashMap<>(exports));
        Map<String, List<Map<String, Object>>> copy = new LinkedHashMap<>();
        tables.forEach((name, rows) -> copy.put(name, List.copyOf(rows)));
        tables = Collections.unmodifiableMap(copy);
    }

    public static RfcResult empty() {
        return new RfcResult(Map.of(), Map.of());
    }

    public String exportValue(String name) {
        Object value = exports.get(name);
        return value == null ? null : value.toString().trim();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> exportStructure(String name) {
        Object value = exports.get(name);
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    public List<Map<String, Object>> table(String name) {
        return tables.getOrDefault(name, List.of());
    }
}
