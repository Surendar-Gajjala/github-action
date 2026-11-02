package com.gsr.scm.workflow;

import com.gsr.scm.audit.AuditService;
import com.gsr.scm.audit.WorkflowEventType;
import com.gsr.scm.common.Exceptions.BusinessRuleException;
import com.gsr.scm.common.Exceptions.ConflictException;
import com.gsr.scm.common.Exceptions.EngineUnavailableException;
import com.gsr.scm.item.ChangeRequest;
import com.gsr.scm.item.Item;
import com.gsr.scm.item.ItemDtos.ChangeDiff;
import com.gsr.scm.item.ItemRepositories.ChangeRequestRepository;
import com.gsr.scm.item.ItemService;
import com.gsr.scm.item.LifecycleStatus;
import com.gsr.scm.lifecycle.LifecycleService;
import com.gsr.scm.lifecycle.LifecycleService.TransitionContext;
import com.gsr.scm.workflow.definition.WorkflowDefinitionService;
import com.gsr.scm.workflow.definition.WorkflowDefinitionService.ResolvedDefinition;
import com.gsr.scm.workflow.engine.JobContext;
import com.gsr.scm.workflow.engine.WorkflowEngineAdapter;
import com.gsr.scm.workflow.instance.InstanceDtos.InstanceSummary;
import com.gsr.scm.workflow.instance.WorkflowInstance;
import com.gsr.scm.workflow.instance.WorkflowInstanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Entry point for submitting an item's change request to the lifecycle workflow.
 * <p>
 * Duplicate-start protection is layered: the item's optimistic lock (two concurrent submits cannot both move it to
 * IN_REVIEW), the change request status (only OPEN can be submitted), and unique constraints on
 * {@code workflow_instances.change_request_id} and {@code business_key}.
 */
@Service
public class WorkflowService {

    private final ItemService items;
    private final ChangeRequestRepository changeRequests;
    private final WorkflowInstanceRepository instances;
    private final WorkflowDefinitionService definitions;
    private final LifecycleService lifecycle;
    private final WorkflowEngineAdapter engine;
    private final AuditService audit;

    public WorkflowService(ItemService items, ChangeRequestRepository changeRequests,
                           WorkflowInstanceRepository instances, WorkflowDefinitionService definitions,
                           LifecycleService lifecycle, WorkflowEngineAdapter engine, AuditService audit) {
        this.items = items;
        this.changeRequests = changeRequests;
        this.instances = instances;
        this.definitions = definitions;
        this.lifecycle = lifecycle;
        this.engine = engine;
        this.audit = audit;
    }

    @Transactional
    public InstanceSummary submitItem(Long itemId, String workflowKey, Long expectedVersion, String actor) {
        Item item = items.load(itemId);
        if (expectedVersion != null && !expectedVersion.equals(item.getVersion())) {
            throw new ConflictException("Item " + item.getItemNumber() + " was modified (version " + item.getVersion()
                    + "). Reload before submitting.");
        }
        LifecycleStatus status = item.getLifecycleStatus();
        if (status == LifecycleStatus.IN_REVIEW) {
            throw new ConflictException("Item " + item.getItemNumber() + " is already in review");
        }
        if (status == LifecycleStatus.APPROVED) {
            throw new BusinessRuleException("Item " + item.getItemNumber()
                    + " is approved and has no pending change to submit");
        }
        if (status == LifecycleStatus.REJECTED) {
            throw new BusinessRuleException("Item " + item.getItemNumber()
                    + " was rejected; edit it to start a new change request before resubmitting");
        }
        ChangeRequest cr = changeRequests.findFirstByItemIdAndStatusOrderByIdDesc(itemId, ChangeRequest.Status.OPEN)
                .orElseThrow(() -> new BusinessRuleException("Item " + item.getItemNumber() + " has no open change request"));
        if (instances.existsByChangeRequestId(cr.getId())) {
            throw new ConflictException("A workflow was already started for " + cr.getNumber());
        }
        ChangeDiff diff = items.diff(itemId);
        if (item.getApprovedRevision() != null && !diff.hasChanges()) {
            throw new BusinessRuleException("There are no changes compared to approved revision "
                    + item.getApprovedRevision());
        }

        ResolvedDefinition def = definitions.resolveForSubmission(workflowKey, item.getApprovedRevision() != null);
        boolean managerRequired = def.rules().managerApprovalRequired(diff, item.getApprovedRevision() != null);
        String businessKey = "ITEM-" + item.getId() + "-CR-" + cr.getId();
        String instanceId = UUID.randomUUID().toString();

        lifecycle.transition(item, LifecycleStatus.IN_REVIEW, new TransitionContext(actor,
                "Submitted " + cr.getNumber() + " for approval via " + def.definition().getWorkflowKey(),
                businessKey, instanceId, cr.getId()));
        item.touch();
        cr.setStatus(ChangeRequest.Status.SUBMITTED);
        cr.setSubmittedAt(LocalDateTime.now());
        cr.setSubmittedBy(actor);

        WorkflowInstance w = new WorkflowInstance();
        w.setId(instanceId);
        w.setEngine(engine.info().engine());
        w.setDefinitionKey(def.definition().getWorkflowKey());
        w.setDefinitionName(def.definition().getName());
        w.setDefinitionVersion(def.version().getVersionNumber());
        w.setDefinitionVersionId(def.version().getId());
        w.setBusinessKey(businessKey);
        w.setItemId(item.getId());
        w.setItemNumber(item.getItemNumber());
        w.setChangeRequestId(cr.getId());
        w.setManagerApprovalRequired(managerRequired);
        w.setStartedBy(actor);
        w.setCurrentStage("Starting");
        instances.saveAndFlush(w);
        changeRequests.saveAndFlush(cr);

        audit.record(WorkflowEventType.ITEM_SUBMITTED, actor, e -> {
            e.setItemId(item.getId());
            e.setItemNumber(item.getItemNumber());
            e.setChangeRequestId(cr.getId());
            e.setBusinessKey(businessKey);
            e.setWorkflowInstanceId(instanceId);
            e.setWorkflowDefinitionKey(w.getDefinitionKey());
            e.setSummary("%s submitted for revision %s: %d field change(s), %d part(s) added, %d removed".formatted(
                    cr.getNumber(), item.getRevision(), diff.fieldChanges().size(), diff.partsAdded().size(),
                    diff.partsRemoved().size()));
        });

        Map<String, Object> vars = new HashMap<>();
        vars.put(JobContext.VAR_WORKFLOW_INSTANCE_ID, instanceId);
        vars.put(JobContext.VAR_ITEM_ID, item.getId());
        vars.put(JobContext.VAR_CHANGE_REQUEST_ID, cr.getId());
        vars.put(JobContext.VAR_BUSINESS_KEY, businessKey);
        vars.put(JobContext.VAR_ACTOR, actor);
        vars.put("itemNumber", item.getItemNumber());
        vars.put("revision", item.getRevision());
        vars.put(JobContext.VAR_MANAGER_APPROVAL_REQUIRED, managerRequired);

        long engineKey;
        try {
            engineKey = engine.startInstance(def.version().getEngineProcessDefinitionKey(), vars);
        } catch (EngineUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // Rolls back the status change, change request submission and instance row.
            throw new EngineUnavailableException("The workflow engine could not start the process: " + e.getMessage(), e);
        }
        w.setEngineInstanceKey(engineKey);
        if (w.getStatus() == WorkflowInstance.Status.STARTING) {
            w.setStatus(WorkflowInstance.Status.RUNNING);
        }
        audit.record(WorkflowEventType.WORKFLOW_STARTED, actor, e -> {
            e.setItemId(item.getId());
            e.setItemNumber(item.getItemNumber());
            e.setChangeRequestId(cr.getId());
            e.setBusinessKey(businessKey);
            e.setWorkflowInstanceId(instanceId);
            e.setWorkflowDefinitionKey(w.getDefinitionKey());
            e.setSummary("Started %s v%d on %s (engine instance %d); manager approval %s".formatted(
                    w.getDefinitionKey(), w.getDefinitionVersion(), w.getEngine(), engineKey,
                    managerRequired ? "required" : "not required"));
        });
        return InstanceSummary.of(w);
    }
}
