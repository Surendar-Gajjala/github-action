package com.gsr.scm.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

@Service
public class AuditService {

    private final WorkflowEventRepository repository;

    public AuditService(WorkflowEventRepository repository) {
        this.repository = repository;
    }

    /** Records an event as part of the caller's transaction (rolled back with the business change). */
    @Transactional(propagation = Propagation.MANDATORY)
    public WorkflowEvent record(WorkflowEventType action, String actor, Consumer<WorkflowEvent> details) {
        return repository.save(build(action, actor, details));
    }

    /** Records an event that must survive a rollback of the surrounding work (e.g. worker failures). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WorkflowEvent recordIndependently(WorkflowEventType action, String actor, Consumer<WorkflowEvent> details) {
        return repository.save(build(action, actor, details));
    }

    private static WorkflowEvent build(WorkflowEventType action, String actor, Consumer<WorkflowEvent> details) {
        WorkflowEvent event = new WorkflowEvent();
        event.setAction(action);
        event.setActor(actor);
        details.accept(event);
        if (event.getSummary() != null && event.getSummary().length() > 4000) {
            event.setSummary(event.getSummary().substring(0, 3997) + "...");
        }
        return event;
    }
}
