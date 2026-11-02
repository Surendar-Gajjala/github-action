package com.gsr.scm.workflow.definition;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class DefinitionDtos {

    private DefinitionDtos() {
    }

    public record DefinitionSummary(String key, String name, String description, WorkflowDefinition.Type type,
                                    boolean active, Integer latestVersion,
                                    WorkflowDefinitionVersion.Status latestStatus, Integer publishedVersion,
                                    boolean hasDraft, LocalDateTime updatedAt, String updatedBy) {
    }

    public record VersionDto(Long id, int versionNumber, WorkflowDefinitionVersion.Status status, String engine,
                             Long engineProcessDefinitionKey, Integer engineVersion, LocalDateTime createdAt,
                             String createdBy, LocalDateTime updatedAt, LocalDateTime publishedAt,
                             String publishedBy) {

        static VersionDto of(WorkflowDefinitionVersion v) {
            return new VersionDto(v.getId(), v.getVersionNumber(), v.getStatus(), v.getEngine(),
                    v.getEngineProcessDefinitionKey(), v.getEngineVersion(), v.getCreatedAt(), v.getCreatedBy(),
                    v.getUpdatedAt(), v.getPublishedAt(), v.getPublishedBy());
        }
    }

    public record VersionDetail(VersionDto meta, String bpmnXml, ApprovalRules rules) {
    }

    public record DefinitionDetail(DefinitionSummary definition, VersionDetail current, List<VersionDto> versions) {
    }

    public record CreateDefinitionRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Z][A-Z0-9_]*",
                    message = "must be UPPER_SNAKE_CASE, e.g. ITEM_LIFECYCLE_APPROVAL") String key,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotNull WorkflowDefinition.Type type,
            String bpmnXml,
            @Valid ApprovalRules rules) {
    }

    public record SaveDraftRequest(
            @Size(max = 200) String name,
            @Size(max = 2000) String description,
            WorkflowDefinition.Type type,
            @NotBlank String bpmnXml,
            @NotNull @Valid ApprovalRules rules) {
    }

    public record ValidateRequest(@NotBlank String bpmnXml, String key) {
    }

    public record WorkerInfo(String jobType, String description) {
    }
}
