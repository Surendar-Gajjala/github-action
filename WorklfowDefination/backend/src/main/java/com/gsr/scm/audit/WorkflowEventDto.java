package com.gsr.scm.audit;

import java.time.LocalDateTime;

public record WorkflowEventDto(Long id, LocalDateTime timestamp, String actor, WorkflowEventType action, Long itemId,
                               String itemNumber, Long changeRequestId, String businessKey,
                               String workflowInstanceId, String workflowDefinitionKey, String fromStatus,
                               String toStatus, String summary) {

    public static WorkflowEventDto of(WorkflowEvent e) {
        return new WorkflowEventDto(e.getId(), e.getTimestamp(), e.getActor(), e.getAction(), e.getItemId(),
                e.getItemNumber(), e.getChangeRequestId(), e.getBusinessKey(), e.getWorkflowInstanceId(),
                e.getWorkflowDefinitionKey(), e.getFromStatus(), e.getToStatus(), e.getSummary());
    }
}
