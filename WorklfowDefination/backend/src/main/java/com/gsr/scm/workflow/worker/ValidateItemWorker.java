package com.gsr.scm.workflow.worker;

import com.gsr.scm.audit.AuditService;
import com.gsr.scm.audit.WorkflowEventType;
import com.gsr.scm.item.ChangeRequest;
import com.gsr.scm.item.Item;
import com.gsr.scm.item.ItemService;
import com.gsr.scm.item.LifecycleStatus;
import com.gsr.scm.workflow.engine.JobContext;
import com.gsr.scm.workflow.instance.WorkflowInstance;
import com.gsr.scm.workflow.instance.WorkflowInstanceService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Business validation of the item header. A failed validation routes the process to the rejection path. */
@Component
public class ValidateItemWorker implements WorkflowWorker {

    private final WorkflowInstanceService instances;
    private final ItemService items;
    private final WorkerSupport support;
    private final AuditService audit;

    public ValidateItemWorker(WorkflowInstanceService instances, ItemService items, WorkerSupport support,
                              AuditService audit) {
        this.instances = instances;
        this.items = items;
        this.support = support;
        this.audit = audit;
    }

    @Override
    public String jobType() {
        return "validate-item";
    }

    @Override
    public String description() {
        return "Validates the item header (status, name, number, submitted change request)";
    }

    @Override
    @Transactional
    public Map<String, Object> execute(JobContext job) {
        WorkflowInstance w = instances.requireForJob(job);
        instances.markStage(w, job.elementId());
        Item item = items.load(w.getItemId());
        ChangeRequest cr = support.changeRequest(w);

        List<String> problems = new ArrayList<>();
        if (item.getLifecycleStatus() != LifecycleStatus.IN_REVIEW) {
            problems.add("item status is " + item.getLifecycleStatus() + " (expected IN_REVIEW)");
        }
        if (cr.getStatus() != ChangeRequest.Status.SUBMITTED) {
            problems.add(cr.getNumber() + " is " + cr.getStatus() + " (expected SUBMITTED)");
        }
        if (item.getName() == null || item.getName().isBlank()) {
            problems.add("item name is missing");
        }
        if (item.getRevision() == null || item.getRevision().isBlank()) {
            problems.add("item revision is missing");
        }
        boolean passed = problems.isEmpty();
        String message = passed ? "Item validation passed" : "Item validation failed: " + String.join("; ", problems);
        audit.record(WorkflowEventType.VALIDATION_COMPLETED, "engine", e -> {
            WorkerSupport.describe(e, w);
            e.setSummary(message);
        });
        return Map.of(JobContext.VAR_VALIDATION_PASSED, passed, JobContext.VAR_VALIDATION_MESSAGE, message);
    }
}
