package com.demo.itemintegration.external.dto;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Raw response envelope returned by the hosted server for any query-config payload
 * (Items, Parts, ...).
 * <p>
 * {@code zql}, {@code properties}, {@code objects}, {@code queryInfo} and the paging fields
 * are external metadata and are not exposed by the local API. {@code results} holds the
 * entity rows. A row may be a JSON object keyed by external field name (what the hosted
 * server returns today), or a positional array whose columns are named by
 * {@code properties}; {@link #records(Class)} handles both shapes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalQueryResponse(
        JsonNode zql,
        JsonNode properties,
        JsonNode objects,
        List<JsonNode> results,
        JsonNode queryInfo,
        Integer pageNumber,
        Integer pageSize,
        Long totalElements,
        Boolean hasMore) {

    private static final ObjectMapper ROW_READER = new ObjectMapper();

    /**
     * The rows from {@code results} as external record DTOs of {@code recordType}.
     * Rows that are neither objects nor arrays are skipped.
     */
    public <T> List<T> records(Class<T> recordType) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<String> columns = propertyNames();
        List<T> records = new ArrayList<>(results.size());
        for (JsonNode row : results) {
            ObjectNode object = asObject(row, columns);
            if (object != null) {
                records.add(ROW_READER.convertValue(object, recordType));
            }
        }
        return records;
    }

    /** Column names from {@code properties}: plain strings, or objects carrying a name. */
    List<String> propertyNames() {
        List<String> names = new ArrayList<>();
        if (properties == null || !properties.isArray()) {
            return names;
        }
        for (JsonNode property : properties) {
            if (property.isTextual()) {
                names.add(property.asText());
            } else if (property.isObject()) {
                JsonNode name = property.hasNonNull("name") ? property.get("name") : property.get("field");
                names.add(name == null ? null : name.asText());
            } else {
                names.add(null);
            }
        }
        return names;
    }

    private static ObjectNode asObject(JsonNode row, List<String> columns) {
        if (row instanceof ObjectNode object) {
            return object;
        }
        if (row == null || !row.isArray()) {
            return null;
        }
        ObjectNode object = ROW_READER.createObjectNode();
        Iterator<JsonNode> values = row.elements();
        for (int i = 0; values.hasNext() && i < columns.size(); i++) {
            JsonNode value = values.next();
            String column = columns.get(i);
            if (column != null) {
                object.set(column, value);
            }
        }
        return object;
    }
}
