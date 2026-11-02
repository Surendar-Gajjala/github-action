package com.gsr.onboarding.service;

import java.util.List;

/**
 * JSON document stored in {@code onboarding_artifacts.metadata}.
 *
 * @param checklist           personalised onboarding checklist
 * @param repository          repository where the onboarding issue is created ({@code owner/repo})
 * @param requestedRepository repository the employee needs access to
 * @param generationNote      why fallback content was used, if it was
 */
public record ArtifactMetadata(
        List<String> checklist,
        String repository,
        String requestedRepository,
        String generationNote) {

    public ArtifactMetadata {
        checklist = checklist == null ? List.of() : List.copyOf(checklist);
    }
}
