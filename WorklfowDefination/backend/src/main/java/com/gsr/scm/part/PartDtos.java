package com.gsr.scm.part;

import com.gsr.scm.item.LifecycleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class PartDtos {

    private PartDtos() {
    }

    public record PartSummary(Long id, String partNumber, String name, String description, String partType,
                              long associatedItemsCount, LocalDateTime createdAt, LocalDateTime updatedAt,
                              Long version) {
    }

    public record ItemRef(Long id, String itemNumber, String name, LifecycleStatus lifecycleStatus, String revision,
                          String approvedRevision) {
    }

    public record PartDetail(Long id, String partNumber, String name, String description, String partType,
                             LocalDateTime createdAt, LocalDateTime updatedAt, Long version, List<ItemRef> items) {
    }

    public record CreatePartRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*",
                    message = "may contain letters, digits, '.', '_' and '-'") String partNumber,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @Size(max = 64) String partType,
            List<@NotNull Long> itemIds) {
    }

    public record UpdatePartRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @Size(max = 64) String partType,
            @NotNull Long version) {
    }

    public record ReplaceItemsRequest(@NotNull List<@NotNull Long> itemIds) {
    }

    /** Result of an association change: which items received lifecycle change requests. */
    public record AssociationResult(PartDetail part, List<ItemRef> affectedItems) {
    }
}
