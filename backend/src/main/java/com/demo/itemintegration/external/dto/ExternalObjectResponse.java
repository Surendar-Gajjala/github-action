package com.demo.itemintegration.external.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Raw response of the hosted partial-object endpoint: one object with every property,
 * keyed by external property name (e.g. {@code "environmental_compliance.eu_rohs"}).
 * Each property is wrapped with metadata; only its {@code value} is used locally.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalObjectResponse(
        Long objectId,
        String objectTypeDbName,
        Map<String, ExternalObjectProperty> properties) {

    public Map<String, ExternalObjectProperty> properties() {
        return properties == null ? Map.of() : properties;
    }

    /** The raw value of a property; {@code null} when the property is absent. */
    public JsonNode value(String externalName) {
        ExternalObjectProperty property = properties().get(externalName);
        return property == null ? null : property.value();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExternalObjectProperty(JsonNode value) {
    }
}
