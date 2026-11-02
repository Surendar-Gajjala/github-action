package com.gsr.scm.workflow.task;

import com.gsr.scm.audit.AuditService;
import com.gsr.scm.audit.WorkflowEventType;
import com.gsr.scm.common.Exceptions.BusinessRuleException;
import com.gsr.scm.common.Exceptions.ConflictException;
import com.gsr.scm.common.Exceptions.ForbiddenException;
import com.gsr.scm.common.Exceptions.NotFoundException;
import com.gsr.scm.common.PageResponse;
import com.gsr.scm.common.Pagination;
import com.gsr.scm.item.ChangeRequest;
import com.gsr.scm.item.Item;
import com.gsr.scm.item.ItemDtos.ChangeDiff;
import com.gsr.scm.item.ItemRepositories.ChangeRequestRepository;
import com.gsr.scm.item.ItemService;
import com.gsr.scm.workflow.definition.ApprovalRules;
import com.gsr.scm.workflow.definition.BpmnModel;
import com.gsr.scm.workflow.definition.WorkflowDefinitionService;
import com.gsr.scm.workflow.engine.JobContext;
import com.gsr.scm.workflow.engine.WorkflowEngineAdapter;
import com.gsr.scm.workflow.instance.WorkflowInstance;
import com.gsr.scm.workflow.instance.WorkflowInstanceRepository;
import com.gsr.scm.workflow.instance.WorkflowInstanceService;
import com.gsr.scm.workflow.task.TaskDtos.TaskDto;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Approval task inbox. Tasks are created when the engine activates a user-task job; deciding a task completes that
 * job with {@code lastDecision} so the process can branch. Authorization is enforced here, per task.
 */
@Service
public class WorkflowTaskService {

    public static final Set<String> SORTABLE = Set.of("createdAt", "dueDate", "name", "itemNumber", "status",
            "candidateGroup");

    private final ApprovalTaskRepository tasks;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowInstanceService instances;
    private final WorkflowDefinitionService definitions;
    private final ItemService items;
    private final ChangeRequestRepository changeRequests;
    private final WorkflowEngineAdapter engine;
    private final AuditService audit;

    public WorkflowTaskService(ApprovalTaskRepository tasks, WorkflowInstanceRepository instanceRepository,
                               WorkflowInstanceService instances, WorkflowDefinitionService definitions,
                               ItemService items, ChangeRequestRepository changeRequests,
                               @Lazy WorkflowEngineAdapter engine, AuditService audit) {
        this.tasks = tasks;
        this.instanceRepository = instanceRepository;
        this.instances = instances;
        this.definitions = definitions;
        this.items = items;
        this.changeRequests = changeRequests;
        this.engine = engine;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<TaskDto> list(String q, ApprovalTask.Status status, boolean mine, Set<String> userRoles,
                                      Pageable pageable) {
        EnumSet<ApprovalTask.Status> statuses = status == null ? EnumSet.allOf(ApprovalTask.Status.class)
                : EnumSet.of(status);
        Set<String> groups = userRoles.isEmpty() ? Set.of("-") : userRoles;
        Page<ApprovalTask> page = tasks.search(Pagination.likePattern(q), statuses, !mine, groups, pageable);
        Map<String, WorkflowInstance> byId = instanceRepository.findAllById(
                        page.getContent().stream().map(ApprovalTask::getWorkflowInstanceId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(WorkflowInstance::getId, Function.identity()));
        return PageResponse.of(page, t -> TaskDto.of(t, byId.get(t.getWorkflowInstanceId())));
    }

    @Transactional(readOnly = true)
    public TaskDto get(Long id) {
        ApprovalTask t = load(id);
        return TaskDto.of(t, instanceRepository.findById(t.getWorkflowInstanceId()).orElse(null));
    }

    /** Item and parts changes under review for this task. */
    @Transactional(readOnly = true)
    public ChangeDiff changes(Long taskId) {
        return items.diff(load(taskId).getItemId());
    }

    // ------------------------------------------------------------------ engine callback

    /** Idempotently registers an activated user-task job as an inbox task. */
    @Transactional
    public ApprovalTask registerTask(JobContext job) {
        return tasks.findByEngineJobKey(job.jobKey()).orElseGet(() -> {
            WorkflowInstance w = instances.requireForJob(job);
            BpmnModel model = definitions.model(w.getDefinitionVersionId());
            BpmnModel.Node node = model.node(job.elementId()).orElse(null);
            String group = candidateGroup(job, node);
            Item item = items.load(w.getItemId());

            ApprovalTask t = new ApprovalTask();
            t.setWorkflowInstanceId(w.getId());
            t.setEngineJobKey(job.jobKey());
            t.setEngineInstanceKey(job.engineInstanceKey());
            t.setElementId(job.elementId());
            t.setName(model.displayName(job.elementId()));
            t.setCandidateGroup(group);
            t.setItemId(item.getId());
            t.setItemNumber(item.getItemNumber());
            t.setItemName(item.getName());
            t.setChangeRequestId(w.getChangeRequestId());
            t.setDueDate(dueDate(job, node, definitions.rules(w.getDefinitionVersionId())));
            tasks.save(t);
            instances.markStage(w, job.elementId());
            audit.record(WorkflowEventType.APPROVAL_REQUESTED, "engine", e -> {
                e.setItemId(item.getId());
                e.setItemNumber(item.getItemNumber());
                e.setChangeRequestId(w.getChangeRequestId());
                e.setBusinessKey(w.getBusinessKey());
                e.setWorkflowInstanceId(w.getId());
                e.setWorkflowDefinitionKey(w.getDefinitionKey());
                e.setSummary("'" + t.getName() + "' assigned to role " + group
                        + (t.getDueDate() == null ? "" : ", due " + t.getDueDate().toLocalDate()));
            });
            return t;
        });
    }

    // ------------------------------------------------------------------ decisions

    @Transactional
    public TaskDto approve(Long taskId, String comment, String user, Set<String> roles) {
        ApprovalTask t = authorize(taskId, user, roles);
        decide(t, ApprovalTask.Status.APPROVED, comment, user);
        Map<String, Object> vars = decisionVariables(t, "APPROVED", user);
        vars.put("lastDecisionComment", comment == null ? "" : comment);
        engine.completeUserTask(t.getEngineJobKey(), vars);
        return get(taskId);
    }

    @Transactional
    public TaskDto reject(Long taskId, String reason, String user, Set<String> roles) {
        if (reason == null || reason.trim().length() < 5) {
            throw new BusinessRuleException("A rejection reason of at least 5 characters is required");
        }
        ApprovalTask t = authorize(taskId, user, roles);
        decide(t, ApprovalTask.Status.REJECTED, reason.trim(), user);
        Map<String, Object> vars = decisionVariables(t, "REJECTED", user);
        vars.put(JobContext.VAR_REJECTION_REASON, reason.trim());
        engine.completeUserTask(t.getEngineJobKey(), vars);
        return get(taskId);
    }

    private ApprovalTask authorize(Long taskId, String user, Set<String> roles) {
        ApprovalTask t = load(taskId);
        if (t.getStatus() != ApprovalTask.Status.OPEN) {
            throw new ConflictException("Task " + taskId + " was already " + t.getStatus().name().toLowerCase()
                    + " by " + t.getDecisionBy());
        }
        if (!roles.contains(t.getCandidateGroup())) {
            throw new ForbiddenException("Task '" + t.getName() + "' requires role " + t.getCandidateGroup());
        }
        WorkflowInstance w = instances.load(t.getWorkflowInstanceId());
        ApprovalRules rules = definitions.rules(w.getDefinitionVersionId());
        if (rules.preventSelfApproval() && t.getChangeRequestId() != null) {
            ChangeRequest cr = changeRequests.findById(t.getChangeRequestId()).orElse(null);
            if (cr != null && user.equals(cr.getSubmittedBy())) {
                throw new ForbiddenException("Segregation of duties: you submitted " + cr.getNumber()
                        + " and cannot decide its approval");
            }
        }
        return t;
    }

    private void decide(ApprovalTask t, ApprovalTask.Status decision, String comment, String user) {
        t.setStatus(decision);
        t.setDecisionBy(user);
        t.setDecisionComment(comment);
        t.setCompletedAt(LocalDateTime.now());
        // Flush now: the @Version check (and row lock) rejects a concurrent decision before the engine is called.
        tasks.saveAndFlush(t);
        WorkflowInstance w = instances.load(t.getWorkflowInstanceId());
        audit.record(WorkflowEventType.APPROVAL_COMPLETED, user, e -> {
            e.setItemId(t.getItemId());
            e.setItemNumber(t.getItemNumber());
            e.setChangeRequestId(t.getChangeRequestId());
            e.setBusinessKey(w.getBusinessKey());
            e.setWorkflowInstanceId(w.getId());
            e.setWorkflowDefinitionKey(w.getDefinitionKey());
            e.setSummary("'" + t.getName() + "' " + decision.name().toLowerCase() + " by " + user + " ("
                    + t.getCandidateGroup() + ")" + (comment == null || comment.isBlank() ? "" : ": " + comment));
        });
    }

    private static Map<String, Object> decisionVariables(ApprovalTask t, String decision, String user) {
        Map<String, Object> vars = new HashMap<>();
        vars.put(JobContext.VAR_LAST_DECISION, decision);
        vars.put(JobContext.VAR_LAST_DECISION_BY, user);
        vars.put(t.getElementId() + "Decision", decision);
        return vars;
    }

    private static String candidateGroup(JobContext job, BpmnModel.Node node) {
        String header = job.headers() == null ? null : job.headers().get(JobContext.HEADER_CANDIDATE_GROUPS);
        if (header != null && !header.isBlank()) {
            // Camunda sends a JSON array, e.g. ["ENGINEERING"]
            String first = header.replaceAll("[\\[\\]\"]", "").split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        if (node != null && !node.candidateGroups().isEmpty()) {
            return node.candidateGroups().get(0);
        }
        throw new IllegalStateException("User task " + job.elementId() + " has no candidate group");
    }

    private static LocalDateTime dueDate(JobContext job, BpmnModel.Node node, ApprovalRules rules) {
        String raw = job.headers() == null ? null : job.headers().get(JobContext.HEADER_DUE_DATE);
        if (raw == null && node != null) {
            raw = node.dueDate();
        }
        if (raw != null && !raw.isBlank() && !raw.startsWith("=")) {
            try {
                return OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // fall through to the rule-based due date
            }
        }
        return rules.dueInDays() == null ? null : LocalDateTime.now().plusDays(rules.dueInDays());
    }

    private ApprovalTask load(Long id) {
        return tasks.findById(id).orElseThrow(() -> NotFoundException.of("Task", id));
    }

    /** Used by the release worker to verify approvals. */
    @Transactional(readOnly = true)
    public List<ApprovalTask> forInstance(String workflowInstanceId) {
        return tasks.findByWorkflowInstanceIdOrderByCreatedAtAsc(workflowInstanceId);
    }
}
