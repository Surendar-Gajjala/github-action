package com.gsr.scm.workflow.definition;

import com.gsr.scm.config.Roles;
import com.gsr.scm.item.ItemDtos.ChangeDiff;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Approval requirements attached to a workflow definition version.
 * <ul>
 *   <li>{@code managerApproval} is evaluated by the backend when an instance starts and passed to the process as the
 *       boolean variable {@code managerApprovalRequired}, so BPMN gateways can branch on it.</li>
 *   <li>{@code requiredRoles} are verified again by the release-revision worker before a revision is released.</li>
 * </ul>
 */
public record ApprovalRules(
        @NotNull ManagerApproval managerApproval,
        @Min(0) @Max(1000) int partChangeThreshold,
        @NotNull List<String> requiredRoles,
        @Min(1) @Max(365) Integer dueInDays,
        boolean preventSelfApproval) {

    public enum ManagerApproval {
        ALWAYS,
        NEVER,
        WHEN_PARTS_REMOVED,
        WHEN_PREVIOUSLY_APPROVED,
        WHEN_PART_CHANGES_EXCEED
    }

    public ApprovalRules {
        requiredRoles = requiredRoles == null ? List.of() : List.copyOf(requiredRoles);
    }

    public static ApprovalRules defaults() {
        return new ApprovalRules(ManagerApproval.WHEN_PART_CHANGES_EXCEED, 2, List.of(Roles.ENGINEERING), 5, true);
    }

    public boolean managerApprovalRequired(ChangeDiff diff, boolean previouslyApproved) {
        int partChanges = diff.partsAdded().size() + diff.partsRemoved().size();
        return switch (managerApproval) {
            case ALWAYS -> true;
            case NEVER -> false;
            case WHEN_PARTS_REMOVED -> !diff.partsRemoved().isEmpty();
            case WHEN_PREVIOUSLY_APPROVED -> previouslyApproved;
            case WHEN_PART_CHANGES_EXCEED -> partChanges > partChangeThreshold;
        };
    }
}
