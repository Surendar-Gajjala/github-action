package com.gsr.scm.workflow.instance;

import com.gsr.scm.audit.WorkflowEventDto;
import com.gsr.scm.workflow.task.TaskDtos.TaskDto;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public final class InstanceDtos {

    private InstanceDtos() {
    }

    public record InstanceSummary(String id, String engine, Long engineInstanceKey, String definitionKey,
                                  String definitionName, int definitionVersion, String businessKey, Long itemId,
                                  String itemNumber, Long changeRequestId, String currentStage,
                                  WorkflowInstance.Status status, boolean managerApprovalRequired, String startedBy,
                                  LocalDateTime startedAt, LocalDateTime updatedAt, LocalDateTime endedAt,
                                  String incidentMessage) {

        public static InstanceSummary of(WorkflowInstance w) {
            return new InstanceSummary(w.getId(), w.getEngine(), w.getEngineInstanceKey(), w.getDefinitionKey(),
                    w.getDefinitionName(), w.getDefinitionVersion(), w.getBusinessKey(), w.getItemId(),
                    w.getItemNumber(), w.getChangeRequestId(), w.getCurrentStage(), w.getStatus(),
                    w.isManagerApprovalRequired(), w.getStartedBy(), w.getStartedAt(), w.getUpdatedAt(),
                    w.getEndedAt(), w.getIncidentMessage());
        }
    }

    public record InstanceDetail(InstanceSummary instance, List<TaskDto> tasks, List<WorkflowEventDto> history) {
    }

    public record StartInstanceRequest(@NotNull Long itemId, String workflowKey, Long version) {
    }
}
