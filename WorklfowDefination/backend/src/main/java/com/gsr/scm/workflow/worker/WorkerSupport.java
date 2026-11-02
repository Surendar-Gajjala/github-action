package com.gsr.scm.workflow.worker;

import com.gsr.scm.audit.WorkflowEvent;
import com.gsr.scm.common.Exceptions.NotFoundException;
import com.gsr.scm.item.ChangeRequest;
import com.gsr.scm.item.ItemRepositories.ChangeRequestRepository;
import com.gsr.scm.workflow.instance.WorkflowInstance;
import org.springframework.stereotype.Component;

/** Small helpers shared by the workers. */
@Component
public class WorkerSupport {

    private final ChangeRequestRepository changeRequests;

    public WorkerSupport(ChangeRequestRepository changeRequests) {
        this.changeRequests = changeRequests;
    }

    public ChangeRequest changeRequest(WorkflowInstance w) {
        return changeRequests.findById(w.getChangeRequestId())
                .orElseThrow(() -> NotFoundException.of("Change request", w.getChangeRequestId()));
    }

    public static void describe(WorkflowEvent e, WorkflowInstance w) {
        e.setItemId(w.getItemId());
        e.setItemNumber(w.getItemNumber());
        e.setChangeRequestId(w.getChangeRequestId());
        e.setBusinessKey(w.getBusinessKey());
        e.setWorkflowInstanceId(w.getId());
        e.setWorkflowDefinitionKey(w.getDefinitionKey());
    }
}
