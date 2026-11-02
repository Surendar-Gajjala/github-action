// VERIFIED: plain Java. A JCo-independent description of one RFC function call, so the BAPI
// mapping can be unit-tested and executed by either the mock or the JCo client.
package com.example.ectr.dms.sap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One RFC call.
 *
 * @param functionName name of the RFC-enabled function module
 * @param imports      import parameters. Values are {@code String} (scalar field) or
 *                     {@code Map<String, Object>} (structure)
 * @param tables       table parameters: table name to rows (each row maps field name to value)
 */
public record RfcCall(String functionName, Map<String, Object> imports, Map<String, List<Map<String, Object>>> tables) {

    public RfcCall {
        Objects.requireNonNull(functionName, "functionName");
        imports = Collections.unmodifiableMap(new LinkedHashMap<>(imports));
        Map<String, List<Map<String, Object>>> copy = new LinkedHashMap<>();
        tables.forEach((name, rows) -> copy.put(name, List.copyOf(rows)));
        tables = Collections.unmodifiableMap(copy);
    }

    public static Builder function(String functionName) {
        return new Builder(functionName);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> importStructure(String name) {
        Object value = imports.get(name);
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    public String importValue(String name) {
        Object value = imports.get(name);
        return value instanceof String s ? s : null;
    }

    public List<Map<String, Object>> table(String name) {
        return tables.getOrDefault(name, List.of());
    }

    /** Fluent builder. {@code null} and blank values are skipped, so SAP applies its defaults. */
    public static final class Builder {
        private final String functionName;
        private final Map<String, Object> imports = new LinkedHashMap<>();
        private final Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();

        private Builder(String functionName) {
            this.functionName = functionName;
        }

        public Builder importValue(String name, String value) {
            if (value != null && !value.isBlank()) {
                imports.put(name, value);
            }
            return this;
        }

        public Builder importStructure(String name, Map<String, Object> fields) {
            imports.put(name, withoutBlanks(fields));
            return this;
        }

        public Builder tableRow(String table, Map<String, Object> row) {
            tables.computeIfAbsent(table, k -> new ArrayList<>()).add(withoutBlanks(row));
            return this;
        }

        public RfcCall build() {
            return new RfcCall(functionName, imports, tables);
        }

        private static Map<String, Object> withoutBlanks(Map<String, Object> fields) {
            Map<String, Object> result = new LinkedHashMap<>();
            fields.forEach((k, v) -> {
                if (v != null && !(v instanceof String s && s.isBlank())) {
                    result.put(k, v);
                }
            });
            return Collections.unmodifiableMap(result);
        }
    }
}
