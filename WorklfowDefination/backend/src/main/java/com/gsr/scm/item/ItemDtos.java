package com.gsr.scm.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Request/response DTOs for the Items API. Entities are never serialized directly. */
public final class ItemDtos {

    private ItemDtos() {
    }

    public record ItemSummary(Long id, String itemNumber, String name, String description, String revision,
                              String approvedRevision, long partCount, LifecycleStatus lifecycleStatus,
                              LocalDateTime updatedAt, Long version) {
    }

    public record PartRef(Long id, String partNumber, String name, String partType, int quantity) {
    }

    public record PartSnapshot(Long partId, String partNumber, String name, String partType, int quantity) {
    }

    public record ChangeRequestDto(Long id, String number, ChangeRequest.Status status, String baseRevision,
                                   String targetRevision, String changeLog, String rejectionReason,
                                   LocalDateTime createdAt, String createdBy, LocalDateTime submittedAt,
                                   String submittedBy, LocalDateTime decidedAt, String decidedBy) {

        public static ChangeRequestDto of(ChangeRequest cr) {
            return cr == null ? null : new ChangeRequestDto(cr.getId(), cr.getNumber(), cr.getStatus(),
                    cr.getBaseRevision(), cr.getTargetRevision(), cr.getChangeLog(), cr.getRejectionReason(),
                    cr.getCreatedAt(), cr.getCreatedBy(), cr.getSubmittedAt(), cr.getSubmittedBy(),
                    cr.getDecidedAt(), cr.getDecidedBy());
        }
    }

    public record ItemRevisionDto(Long id, String revision, String name, String description,
                                  List<PartSnapshot> parts, LocalDateTime approvedAt, String approvedBy,
                                  Long changeRequestId, String workflowInstanceId) {
    }

    public record WorkflowRef(String id, String definitionKey, Integer definitionVersion, String status,
                              String currentStage, LocalDateTime startedAt) {
    }

    public record ItemDetail(Long id, String itemNumber, String name, String description,
                             LifecycleStatus lifecycleStatus, String revision, String approvedRevision,
                             LocalDateTime createdAt, LocalDateTime updatedAt, Long version,
                             List<PartRef> parts, ChangeRequestDto currentChangeRequest,
                             ItemRevisionDto lastApprovedRevision, WorkflowRef latestWorkflow,
                             boolean editable, boolean submittable) {
    }

    public record CreateItemRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*",
                    message = "may contain letters, digits, '.', '_' and '-'") String itemNumber,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            List<@NotNull Long> partIds) {
    }

    public record UpdateItemRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            /* null = leave the parts unchanged */
            List<@NotNull Long> partIds,
            @NotNull Long version) {
    }

    public record ReplacePartsRequest(@NotNull List<@NotNull Long> partIds, @NotNull Long version) {
    }

    public record SubmitRequest(String workflowKey, Long version) {
    }

    public record FieldChange(String field, String oldValue, String newValue) {
    }

    /** Net difference between the working revision and the last approved revision. */
    public record ChangeDiff(Long itemId, String itemNumber, String itemName, String baseRevision,
                             String targetRevision, List<FieldChange> fieldChanges, List<PartSnapshot> partsAdded,
                             List<PartSnapshot> partsRemoved, List<PartSnapshot> unchangedParts,
                             ChangeRequestDto changeRequest) {

        public boolean hasChanges() {
            return !fieldChanges.isEmpty() || !partsAdded.isEmpty() || !partsRemoved.isEmpty();
        }
    }
}
