package com.gsr.scm.workflow.instance;

import com.gsr.scm.audit.AuditService;
import com.gsr.scm.audit.WorkflowEventDto;
import com.gsr.scm.audit.WorkflowEventRepository;
import com.gsr.scm.audit.WorkflowEventType;
import com.gsr.scm.common.Exceptions.BusinessRuleException;
import com.gsr.scm.common.Exceptions.NotFoundException;
import com.gsr.scm.common.PageResponse;
import com.gsr.scm.common.Pagination;
import com.gsr.scm.workflow.definition.WorkflowDefinitionService;
import com.gsr.scm.workflow.engine.JobContext;
import com.gsr.scm.workflow.engine.RetryableWorkflowException;
import com.gsr.scm.workflow.engine.WorkflowEngineAdapter;
import com.gsr.scm.workflow.instance.InstanceDtos.InstanceDetail;
import com.gsr.scm.workflow.instance.InstanceDtos.InstanceSummary;
import com.gsr.scm.workflow.task.ApprovalTaskRepository;
import com.gsr.scm.workflow.task.TaskDtos.TaskDto;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Read model and status bookkeeping for workflow instances. */
@Service
public class WorkflowInstanceService {

    public static final Set<String> SORTABLE = Set.of("startedAt", "updatedAt", "status", "definitionKey",
            "itemNumber", "businessKey");

    private final WorkflowInstanceRepository instances;
    private final ApprovalTaskRepository tasks;
    private final WorkflowEventRepository events;
    private final WorkflowDefinitionService definitions;
    private final WorkflowEngineAdapter engine;
    private final AuditService audit;

    public WorkflowInstanceService(WorkflowInstanceRepository instances, ApprovalTaskRepository tasks,
                                   WorkflowEventRepository events, WorkflowDefinitionService definitions,
                                   @Lazy WorkflowEngineAdapter engine, AuditService audit) {
        this.instances = instances;
        this.tasks = tasks;
        this.events = events;
        this.definitions = definitions;
        this.engine = engine;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<InstanceSummary> list(String q, WorkflowInstance.Status status, String definitionKey,
                                              boolean activeOnly, Pageable pageable) {
        EnumSet<WorkflowInstance.Status> statuses = status != null ? EnumSet.of(status)
                : activeOnly ? EnumSet.of(WorkflowInstance.Status.STARTING, WorkflowInstance.Status.RUNNING,
                WorkflowInstance.Status.INCIDENT)
                : EnumSet.allOf(WorkflowInstance.Status.class);
        return PageResponse.of(instances.search(Pagination.likePattern(q), statuses,
                definitionKey == null || definitionKey.isBlank() ? null : definitionKey, pageable), InstanceSummary::of);
    }

    @Transactional(readOnly = true)
    public InstanceDetail get(String id) {
        WorkflowInstance w = load(id);
        List<TaskDto> taskDtos = tasks.findByWorkflowInstanceIdOrderByCreatedAtAsc(id).stream()
                .map(t -> TaskDto.of(t, w)).toList();
        return new InstanceDetail(InstanceSummary.of(w), taskDtos, history(id));
    }

    @Transactional(readOnly = true)
    public List<WorkflowEventDto> history(String id) {
        load(id);
        return events.findByWorkflowInstanceIdOrderByTimestampAscIdAsc(id).stream().map(WorkflowEventDto::of).toList();
    }

    @Transactional(readOnly = true)
    public List<InstanceSummary> forItem(Long itemId) {
        return instances.findByItemIdOrderByStartedAtDesc(itemId).stream().map(InstanceSummary::of).toList();
    }

    // ------------------------------------------------------------------ used by workers / engine callbacks

    /** Finds the application instance for an engine job; retryable if the starting transaction is not visible yet. */
    @Transactional(propagation = Propagation.MANDATORY)
    public WorkflowInstance requireForJob(JobContext job) {
        Optional<WorkflowInstance> found = Optional.ofNullable(job.workflowInstanceId()).flatMap(instances::findById)
                .or(() -> instances.findByEngineInstanceKey(job.engineInstanceKey()));
        WorkflowInstance w = found.orElseThrow(() -> new RetryableWorkflowException(
                "No workflow instance found for engine instance " + job.engineInstanceKey() + " (not committed yet?)"));
        if (w.getEngineInstanceKey() == null) {
            w.setEngineInstanceKey(job.engineInstanceKey());
        }
        if (w.getStatus() == WorkflowInstance.Status.STARTING) {
            w.setStatus(WorkflowInstance.Status.RUNNING);
        }
        return w;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markStage(WorkflowInstance w, String elementId) {
        w.setCurrentElementId(elementId);
        w.setCurrentStage(definitions.model(w.getDefinitionVersionId()).displayName(elementId));
        if (w.getStatus() == WorkflowInstance.Status.INCIDENT) {
            w.setStatus(WorkflowInstance.Status.RUNNING);
            w.setIncidentMessage(null);
        }
    }

    /** Records that a job exhausted its retries. Runs in its own transaction: the failed work was rolled back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markIncident(JobContext job, String message) {
        Optional<WorkflowInstance> found = Optional.ofNullable(job.workflowInstanceId()).flatMap(instances::findById)
                .or(() -> instances.findByEngineInstanceKey(job.engineInstanceKey()));
        found.ifPresent(w -> {
            w.setStatus(WorkflowInstance.Status.INCIDENT);
            w.setIncidentMessage(truncate(message));
            w.setLastFailedJobKey(job.jobKey());
            w.setCurrentElementId(job.elementId());
            w.setCurrentStage(definitions.model(w.getDefinitionVersionId()).displayName(job.elementId()));
        });
        audit.record(WorkflowEventType.WORKFLOW_FAILED, "engine", e -> {
            found.ifPresent(w -> {
                e.setItemId(w.getItemId());
                e.setItemNumber(w.getItemNumber());
                e.setWorkflowInstanceId(w.getId());
                e.setBusinessKey(w.getBusinessKey());
                e.setChangeRequestId(w.getChangeRequestId());
                e.setWorkflowDefinitionKey(w.getDefinitionKey());
            });
            e.setSummary("Job '" + job.jobType() + "' at element " + job.elementId()
                    + " failed with no retries left: " + message);
        });
    }

    /** Called when the engine reports the process reached an end event. */
    @Transactional
    public void markEnded(long engineInstanceKey) {
        instances.findByEngineInstanceKey(engineInstanceKey).ifPresent(w -> {
            if (w.getEndedAt() == null) {
                w.setEndedAt(LocalDateTime.now());
            }
            if (w.getStatus() == WorkflowInstance.Status.RUNNING || w.getStatus() == WorkflowInstance.Status.STARTING) {
                w.setStatus(WorkflowInstance.Status.COMPLETED);
            }
            w.setCurrentStage(w.getStatus() == WorkflowInstance.Status.REJECTED ? "Ended (rejected)" : "Ended");
            audit.record(WorkflowEventType.WORKFLOW_COMPLETED, "engine", e -> {
                e.setItemId(w.getItemId());
                e.setItemNumber(w.getItemNumber());
                e.setWorkflowInstanceId(w.getId());
                e.setBusinessKey(w.getBusinessKey());
                e.setChangeRequestId(w.getChangeRequestId());
                e.setWorkflowDefinitionKey(w.getDefinitionKey());
                e.setSummary("Process instance reached an end event (" + w.getStatus() + ")");
            });
        });
    }

    @Transactional
    public InstanceSummary retry(String id, String actor) {
        WorkflowInstance w = load(id);
        if (w.getStatus() != WorkflowInstance.Status.INCIDENT) {
            throw new BusinessRuleException("Only instances with an incident can be retried (status is " + w.getStatus() + ")");
        }
        engine.retry(w.getEngineInstanceKey(), w.getLastFailedJobKey());
        w.setStatus(WorkflowInstance.Status.RUNNING);
        w.setIncidentMessage(null);
        audit.record(WorkflowEventType.WORKFLOW_RETRIED, actor, e -> {
            e.setItemId(w.getItemId());
            e.setItemNumber(w.getItemNumber());
            e.setWorkflowInstanceId(w.getId());
            e.setBusinessKey(w.getBusinessKey());
            e.setSummary("Incident resolved and job retried at " + w.getCurrentStage());
        });
        return InstanceSummary.of(w);
    }

    public WorkflowInstance load(String id) {
        return instances.findById(id).orElseThrow(() -> NotFoundException.of("Workflow instance", id));
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 2000 ? s : s.substring(0, 1997) + "...";
    }
}
