package com.gsr.scm.workflow.worker;

import com.gsr.scm.audit.AuditService;
import com.gsr.scm.audit.WorkflowEventType;
import com.gsr.scm.item.ChangeRequest;
import com.gsr.scm.item.Item;
import com.gsr.scm.item.ItemService;
import com.gsr.scm.item.LifecycleStatus;
import com.gsr.scm.lifecycle.LifecycleService;
import com.gsr.scm.lifecycle.LifecycleService.TransitionContext;
import com.gsr.scm.workflow.engine.JobContext;
import com.gsr.scm.workflow.instance.WorkflowInstance;
import com.gsr.scm.workflow.instance.WorkflowInstanceService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/** Records the rejection reason, marks the change request and item REJECTED. Idempotent. */
@Component
public class RecordRejectionWorker implements WorkflowWorker {

    private final WorkflowInstanceService instances;
    private final ItemService items;
    private final LifecycleService lifecycle;
    private final WorkerSupport support;
    private final AuditService audit;

    public RecordRejectionWorker(WorkflowInstanceService instances, ItemService items, LifecycleService lifecycle,
                                 WorkerSupport support, AuditService audit) {
        this.instances = instances;
        this.items = items;
        this.lifecycle = lifecycle;
        this.support = support;
        this.audit = audit;
    }

    @Override
    public String jobType() {
        return "record-rejection";
    }

    @Override
    public String description() {
        return "Records the rejection reason and moves the item to REJECTED";
    }

    @Override
    @Transactional
    public Map<String, Object> execute(JobContext job) {
        WorkflowInstance w = instances.requireForJob(job);
        ChangeRequest cr = support.changeRequest(w);
        if (cr.getStatus() == ChangeRequest.Status.REJECTED) {
            return Map.of();
        }
        instances.markStage(w, job.elementId());
        Item item = items.load(w.getItemId());

        String reason = firstNonBlank(job.string(JobContext.VAR_REJECTION_REASON),
                job.flag(JobContext.VAR_VALIDATION_PASSED) ? null : job.string(JobContext.VAR_VALIDATION_MESSAGE),
                "Rejected by workflow");
        String decidedBy = firstNonBlank(job.string(JobContext.VAR_LAST_DECISION_BY), "engine", null);

        if (item.getLifecycleStatus() == LifecycleStatus.IN_REVIEW) {
            lifecycle.transition(item, LifecycleStatus.REJECTED, new TransitionContext(decidedBy,
                    "Rejected: " + reason, w.getBusinessKey(), w.getId(), cr.getId()));
            item.touch();
        }
        cr.setStatus(ChangeRequest.Status.REJECTED);
        cr.setRejectionReason(reason);
        cr.setDecidedAt(LocalDateTime.now());
        cr.setDecidedBy(decidedBy);
        w.setStatus(WorkflowInstance.Status.REJECTED);

        audit.record(WorkflowEventType.REQUEST_REJECTED, decidedBy, e -> {
            WorkerSupport.describe(e, w);
            e.setToStatus(LifecycleStatus.REJECTED.name());
            e.setSummary(cr.getNumber() + " rejected: " + reason);
        });
        return Map.of("rejectionRecorded", true);
    }

    private static String firstNonBlank(String a, String b, String c) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return c;
    }
}
