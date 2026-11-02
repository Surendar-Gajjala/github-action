package com.gsr.scm.workflow.task;

import com.gsr.scm.workflow.instance.WorkflowInstance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class TaskDtos {

    private TaskDtos() {
    }

    public record TaskDto(Long id, String name, String elementId, ApprovalTask.Status status, String candidateGroup,
                          Long itemId, String itemNumber, String itemName, Long changeRequestId,
                          String workflowInstanceId, Long engineInstanceKey, String definitionKey,
                          Integer definitionVersion, String currentStage, LocalDateTime createdAt,
                          LocalDateTime dueDate, LocalDateTime completedAt, String decisionBy,
                          String decisionComment, boolean overdue) {

        public static TaskDto of(ApprovalTask t, WorkflowInstance w) {
            boolean overdue = t.getStatus() == ApprovalTask.Status.OPEN && t.getDueDate() != null
                    && t.getDueDate().isBefore(LocalDateTime.now());
            return new TaskDto(t.getId(), t.getName(), t.getElementId(), t.getStatus(), t.getCandidateGroup(),
                    t.getItemId(), t.getItemNumber(), t.getItemName(), t.getChangeRequestId(),
                    t.getWorkflowInstanceId(), t.getEngineInstanceKey(),
                    w == null ? null : w.getDefinitionKey(), w == null ? null : w.getDefinitionVersion(),
                    w == null ? null : w.getCurrentStage(), t.getCreatedAt(), t.getDueDate(), t.getCompletedAt(),
                    t.getDecisionBy(), t.getDecisionComment(), overdue);
        }
    }

    public record ApproveRequest(@Size(max = 2000) String comment) {
    }

    public record RejectRequest(
            @NotBlank(message = "A rejection reason is required")
            @Size(min = 5, max = 2000, message = "The rejection reason must be between 5 and 2000 characters")
            String reason) {
    }
}
