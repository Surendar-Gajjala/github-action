package com.gsr.scm.workflow.worker;

import com.gsr.scm.audit.AuditService;
import com.gsr.scm.audit.WorkflowEventType;
import com.gsr.scm.item.ItemDtos.PartRef;
import com.gsr.scm.item.ItemService;
import com.gsr.scm.workflow.engine.JobContext;
import com.gsr.scm.workflow.instance.WorkflowInstance;
import com.gsr.scm.workflow.instance.WorkflowInstanceService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates the bill of materials. Combines its result with an earlier validation result, if any. */
@Component
public class ValidatePartRelationshipsWorker implements WorkflowWorker {

    private final WorkflowInstanceService instances;
    private final ItemService items;
    private final AuditService audit;

    public ValidatePartRelationshipsWorker(WorkflowInstanceService instances, ItemService items, AuditService audit) {
        this.instances = instances;
        this.items = items;
        this.audit = audit;
    }

    @Override
    public String jobType() {
        return "validate-part-relationships";
    }

    @Override
    public String description() {
        return "Validates the item's part relationships (at least one part, no duplicates)";
    }

    @Override
    @Transactional
    public Map<String, Object> execute(JobContext job) {
        WorkflowInstance w = instances.requireForJob(job);
        instances.markStage(w, job.elementId());
        List<PartRef> parts = items.partRefs(w.getItemId());

        List<String> problems = new ArrayList<>();
        if (parts.isEmpty()) {
            problems.add("the item has no parts");
        }
        Set<String> seen = new HashSet<>();
        parts.stream().map(PartRef::partNumber).filter(n -> !seen.add(n))
                .forEach(n -> problems.add("part " + n + " is listed twice"));
        parts.stream().filter(p -> p.quantity() < 1)
                .forEach(p -> problems.add("part " + p.partNumber() + " has quantity " + p.quantity()));

        boolean previous = !job.variables().containsKey(JobContext.VAR_VALIDATION_PASSED)
                || job.flag(JobContext.VAR_VALIDATION_PASSED);
        String previousMessage = job.string(JobContext.VAR_VALIDATION_MESSAGE);
        boolean passed = previous && problems.isEmpty();
        String own = problems.isEmpty() ? "Part relationships valid (" + parts.size() + " parts)"
                : "Part relationship validation failed: " + String.join("; ", problems);
        String message = !previous && previousMessage != null ? previousMessage + "; " + own : own;
        audit.record(WorkflowEventType.VALIDATION_COMPLETED, "engine", e -> {
            WorkerSupport.describe(e, w);
            e.setSummary(own);
        });
        return Map.of(JobContext.VAR_VALIDATION_PASSED, passed, JobContext.VAR_VALIDATION_MESSAGE, message);
    }
}
