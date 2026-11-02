package com.gsr.scm.workflow.worker;

import com.gsr.scm.audit.AuditService;
import com.gsr.scm.audit.WorkflowEventType;
import com.gsr.scm.config.Roles;
import com.gsr.scm.item.ChangeRequest;
import com.gsr.scm.item.Item;
import com.gsr.scm.item.ItemService;
import com.gsr.scm.lifecycle.LifecycleService;
import com.gsr.scm.lifecycle.LifecycleService.TransitionContext;
import com.gsr.scm.item.LifecycleStatus;
import com.gsr.scm.workflow.definition.ApprovalRules;
import com.gsr.scm.workflow.definition.WorkflowDefinitionService;
import com.gsr.scm.workflow.engine.JobContext;
import com.gsr.scm.workflow.instance.WorkflowInstance;
import com.gsr.scm.workflow.instance.WorkflowInstanceService;
import com.gsr.scm.workflow.task.ApprovalTask;
import com.gsr.scm.workflow.task.WorkflowTaskService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The only code path that can make an item APPROVED. Before releasing it re-checks, from the application's own
 * records, that every required approval was granted - the BPMN path alone is not trusted. Idempotent.
 */
@Component
public class ReleaseRevisionWorker implements WorkflowWorker {

    private final WorkflowInstanceService instances;
    private final WorkflowTaskService tasks;
    private final WorkflowDefinitionService definitions;
    private final ItemService items;
    private final LifecycleService lifecycle;
    private final WorkerSupport support;
    private final AuditService audit;

    public ReleaseRevisionWorker(WorkflowInstanceService instances, WorkflowTaskService tasks,
                                 WorkflowDefinitionService definitions, ItemService items, LifecycleService lifecycle,
                                 WorkerSupport support, AuditService audit) {
        this.instances = instances;
        this.tasks = tasks;
        this.definitions = definitions;
        this.items = items;
        this.lifecycle = lifecycle;
        this.support = support;
        this.audit = audit;
    }

    @Override
    public String jobType() {
        return "release-revision";
    }

    @Override
    public String description() {
        return "Verifies all required approvals and releases the working revision as APPROVED";
    }

    @Override
    @Transactional
    public Map<String, Object> execute(JobContext job) {
        WorkflowInstance w = instances.requireForJob(job);
        ChangeRequest cr = support.changeRequest(w);
        Item item = items.load(w.getItemId());
        if (cr.getStatus() == ChangeRequest.Status.APPROVED) {
            return Map.of("released", true, "releasedRevision", item.getApprovedRevision());
        }
        instances.markStage(w, job.elementId());
        if (cr.getStatus() != ChangeRequest.Status.SUBMITTED || item.getLifecycleStatus() != LifecycleStatus.IN_REVIEW) {
            throw new IllegalStateException("Cannot release: " + cr.getNumber() + " is " + cr.getStatus()
                    + " and item status is " + item.getLifecycleStatus());
        }

        ApprovalRules rules = definitions.rules(w.getDefinitionVersionId());
        List<ApprovalTask> all = tasks.forInstance(w.getId());
        List<String> problems = new ArrayList<>();
        all.stream().filter(t -> t.getStatus() == ApprovalTask.Status.OPEN)
                .forEach(t -> problems.add("'" + t.getName() + "' is still open"));
        all.stream().filter(t -> t.getStatus() == ApprovalTask.Status.REJECTED)
                .forEach(t -> problems.add("'" + t.getName() + "' was rejected by " + t.getDecisionBy()));
        Set<String> approvedGroups = all.stream().filter(t -> t.getStatus() == ApprovalTask.Status.APPROVED)
                .map(ApprovalTask::getCandidateGroup).collect(Collectors.toSet());
        if (approvedGroups.isEmpty()) {
            problems.add("no approval was granted");
        }
        Set<String> required = new LinkedHashSet<>(rules.requiredRoles());
        if (w.isManagerApprovalRequired()) {
            required.add(Roles.MANAGER);
        }
        required.stream().filter(r -> !approvedGroups.contains(r))
                .forEach(r -> problems.add("required " + r + " approval is missing"));
        if (!problems.isEmpty()) {
            // Not retryable in practice; surfaces as an incident so an administrator can investigate the process model.
            throw new IllegalStateException("Release of " + item.getItemNumber() + " rev " + item.getRevision()
                    + " blocked: " + String.join("; ", problems));
        }

        String approvers = all.stream().filter(t -> t.getStatus() == ApprovalTask.Status.APPROVED)
                .map(t -> t.getDecisionBy() + " (" + t.getCandidateGroup() + ")").collect(Collectors.joining(", "));
        String lastApprover = all.stream().filter(t -> t.getStatus() == ApprovalTask.Status.APPROVED)
                .reduce((a, b) -> b).map(ApprovalTask::getDecisionBy).orElse("engine");
        String previous = item.getApprovedRevision();

        lifecycle.transition(item, LifecycleStatus.APPROVED, new TransitionContext("engine",
                "Revision " + item.getRevision() + " released after approval by " + approvers,
                w.getBusinessKey(), w.getId(), cr.getId()));
        item.setApprovedRevision(item.getRevision());
        item.touch();
        items.snapshotApprovedRevision(item, lastApprover, cr.getId(), w.getId());
        cr.setStatus(ChangeRequest.Status.APPROVED);
        cr.setDecidedAt(LocalDateTime.now());
        cr.setDecidedBy(lastApprover);
        w.setStatus(WorkflowInstance.Status.COMPLETED);
        w.setCurrentStage("Released");
        w.setEndedAt(LocalDateTime.now());

        audit.record(WorkflowEventType.REVISION_RELEASED, "engine", e -> {
            WorkerSupport.describe(e, w);
            e.setToStatus(LifecycleStatus.APPROVED.name());
            e.setSummary("Revision " + item.getRevision() + " of " + item.getItemNumber() + " released"
                    + (previous == null ? "" : " (supersedes " + previous + ")") + "; approvals: " + approvers);
        });
        return Map.of("released", true, "releasedRevision", item.getRevision());
    }
}
