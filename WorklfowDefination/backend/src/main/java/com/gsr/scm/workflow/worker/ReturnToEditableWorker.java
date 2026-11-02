package com.gsr.scm.workflow.worker;

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

import java.util.Map;

/**
 * Returns a rejected item to DRAFT (never approved) or CHANGE_PENDING (has an approved revision) and opens a new
 * change request for the rework. The previously approved revision stays the released one. Idempotent.
 */
@Component
public class ReturnToEditableWorker implements WorkflowWorker {

    private final WorkflowInstanceService instances;
    private final ItemService items;
    private final LifecycleService lifecycle;
    private final WorkerSupport support;

    public ReturnToEditableWorker(WorkflowInstanceService instances, ItemService items, LifecycleService lifecycle,
                                  WorkerSupport support) {
        this.instances = instances;
        this.items = items;
        this.lifecycle = lifecycle;
        this.support = support;
    }

    @Override
    public String jobType() {
        return "return-to-editable";
    }

    @Override
    public String description() {
        return "Returns a rejected item to DRAFT or CHANGE_PENDING for rework";
    }

    @Override
    @Transactional
    public Map<String, Object> execute(JobContext job) {
        WorkflowInstance w = instances.requireForJob(job);
        instances.markStage(w, job.elementId());
        Item item = items.load(w.getItemId());
        if (item.getLifecycleStatus() != LifecycleStatus.REJECTED) {
            return Map.of();
        }
        ChangeRequest rejected = support.changeRequest(w);
        LifecycleStatus target = item.getApprovedRevision() != null
                ? LifecycleStatus.CHANGE_PENDING : LifecycleStatus.DRAFT;
        lifecycle.transition(item, target, new TransitionContext("engine",
                "Returned for rework after rejection of " + rejected.getNumber(), w.getBusinessKey(), w.getId(),
                rejected.getId()));
        item.touch();
        items.reopenAfterRejection(item, rejected, "engine");
        return Map.of("returnedTo", target.name());
    }
}
