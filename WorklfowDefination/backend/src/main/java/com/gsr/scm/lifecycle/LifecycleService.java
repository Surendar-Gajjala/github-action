package com.gsr.scm.lifecycle;

import com.gsr.scm.audit.AuditService;
import com.gsr.scm.audit.WorkflowEventType;
import com.gsr.scm.common.Exceptions.BusinessRuleException;
import com.gsr.scm.common.Exceptions.LifecycleTransitionException;
import com.gsr.scm.common.Exceptions.NotFoundException;
import com.gsr.scm.item.Item;
import com.gsr.scm.item.LifecycleStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Single authority for Item lifecycle state changes. Every change is checked against the configured transition table
 * and audited, regardless of whether it was triggered by a user edit or by a workflow worker.
 */
@Service
public class LifecycleService {

    private final LifecycleTransitionRepository repository;
    private final AuditService audit;

    public LifecycleService(LifecycleTransitionRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public record TransitionContext(String actor, String reason, String businessKey, String workflowInstanceId,
                                    Long changeRequestId) {

        public static TransitionContext of(String actor, String reason) {
            return new TransitionContext(actor, reason, null, null, null);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void transition(Item item, LifecycleStatus target, TransitionContext ctx) {
        LifecycleStatus from = item.getLifecycleStatus();
        if (!isAllowed(from, target)) {
            throw new LifecycleTransitionException(
                    "Lifecycle transition %s → %s is not permitted for item %s".formatted(from, target, item.getItemNumber()));
        }
        item.applyTransition(target, new TransitionPermit(target));
        audit.record(WorkflowEventType.LIFECYCLE_TRANSITION, ctx.actor(), e -> {
            e.setItemId(item.getId());
            e.setItemNumber(item.getItemNumber());
            e.setFromStatus(from.name());
            e.setToStatus(target.name());
            e.setBusinessKey(ctx.businessKey());
            e.setWorkflowInstanceId(ctx.workflowInstanceId());
            e.setChangeRequestId(ctx.changeRequestId());
            e.setSummary(ctx.reason());
        });
    }

    @Transactional(readOnly = true)
    public boolean isAllowed(LifecycleStatus from, LifecycleStatus to) {
        return from != to && repository.existsByFromStatusAndToStatusAndEnabledTrue(from, to);
    }

    @Transactional(readOnly = true)
    public List<LifecycleTransition> list() {
        return repository.findAllByOrderByFromStatusAscToStatusAsc();
    }

    @Transactional
    public LifecycleTransition upsert(LifecycleStatus from, LifecycleStatus to, boolean enabled, String description,
                                      String actor) {
        if (from == to) {
            throw new BusinessRuleException("A transition must change the status");
        }
        LifecycleTransition t = repository.findByFromStatusAndToStatus(from, to)
                .orElseGet(() -> new LifecycleTransition(from, to, description, false));
        boolean changed = t.getId() == null || t.isEnabled() != enabled;
        t.setEnabled(enabled);
        if (description != null) {
            t.setDescription(description);
        }
        t.setUpdatedAt(LocalDateTime.now());
        t.setUpdatedBy(actor);
        LifecycleTransition saved = repository.save(t);
        if (changed) {
            audit.record(WorkflowEventType.LIFECYCLE_CONFIG_CHANGED, actor, e -> {
                e.setFromStatus(from.name());
                e.setToStatus(to.name());
                e.setSummary("Transition %s → %s %s".formatted(from, to, enabled ? "enabled" : "disabled"));
            });
        }
        return saved;
    }

    @Transactional
    public void delete(Long id, String actor) {
        LifecycleTransition t = repository.findById(id).orElseThrow(() -> NotFoundException.of("Transition", id));
        if (t.isSystem()) {
            throw new BusinessRuleException("System transitions cannot be deleted; disable them instead");
        }
        repository.delete(t);
        audit.record(WorkflowEventType.LIFECYCLE_CONFIG_CHANGED, actor, e -> {
            e.setFromStatus(t.getFromStatus().name());
            e.setToStatus(t.getToStatus().name());
            e.setSummary("Transition %s → %s deleted".formatted(t.getFromStatus(), t.getToStatus()));
        });
    }
}
