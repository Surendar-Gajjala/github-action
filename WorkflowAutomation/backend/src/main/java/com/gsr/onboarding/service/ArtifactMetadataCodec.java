package com.gsr.onboarding.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ArtifactMetadataCodec {

    private static final ArtifactMetadata EMPTY = new ArtifactMetadata(List.of(), null, null, null);

    private final ObjectMapper objectMapper;

    public ArtifactMetadataCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(ArtifactMetadata metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise artifact metadata", e);
        }
    }

    public ArtifactMetadata read(String json) {
        if (json == null || json.isBlank()) {
            return EMPTY;
        }
        try {
            return objectMapper.readValue(json, ArtifactMetadata.class);
        } catch (JsonProcessingException e) {
            return EMPTY;
        }
    }
}
