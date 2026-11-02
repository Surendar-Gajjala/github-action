package com.gsr.onboarding.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.gsr.onboarding.domain.entity.AuditEvent;
import com.gsr.onboarding.domain.entity.WorkflowStep;
import com.gsr.onboarding.dto.WorkflowResponse.HistoryEntry;
import com.gsr.onboarding.dto.WorkflowResponse.StepResponse;

@Mapper
public interface WorkflowMapper {

    @Mapping(target = "name", source = "stepName")
    @Mapping(target = "label", expression = "java(step.getStepName().label())")
    @Mapping(target = "order", source = "stepOrder")
    StepResponse toStepResponse(WorkflowStep step);

    List<StepResponse> toStepResponses(List<WorkflowStep> steps);

    @Mapping(target = "at", source = "createdAt")
    HistoryEntry toHistoryEntry(AuditEvent event);

    List<HistoryEntry> toHistoryEntries(List<AuditEvent> events);
}
