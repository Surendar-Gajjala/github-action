package com.gsr.scm.workflow.instance;

import com.gsr.scm.audit.WorkflowEventDto;
import com.gsr.scm.common.PageResponse;
import com.gsr.scm.common.Pagination;
import com.gsr.scm.config.CurrentUser;
import com.gsr.scm.workflow.WorkflowService;
import com.gsr.scm.workflow.instance.InstanceDtos.InstanceDetail;
import com.gsr.scm.workflow.instance.InstanceDtos.InstanceSummary;
import com.gsr.scm.workflow.instance.InstanceDtos.StartInstanceRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workflows/instances")
public class WorkflowInstanceController {

    private final WorkflowInstanceService instances;
    private final WorkflowService workflow;

    public WorkflowInstanceController(WorkflowInstanceService instances, WorkflowService workflow) {
        this.instances = instances;
        this.workflow = workflow;
    }

    /** Starts the lifecycle workflow for an item's open change request (same as submit-for-approval). */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public InstanceSummary start(@Valid @RequestBody StartInstanceRequest req) {
        return workflow.submitItem(req.itemId(), req.workflowKey(), req.version(), CurrentUser.username());
    }

    @GetMapping
    public PageResponse<InstanceSummary> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) WorkflowInstance.Status status,
                                              @RequestParam(required = false) String definitionKey,
                                              @RequestParam(defaultValue = "false") boolean activeOnly,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String sort) {
        return instances.list(q, status, definitionKey, activeOnly,
                Pagination.of(page, size, sort, WorkflowInstanceService.SORTABLE, "startedAt,desc"));
    }

    @GetMapping("/{id}")
    public InstanceDetail get(@PathVariable String id) {
        return instances.get(id);
    }

    @GetMapping("/{id}/history")
    public List<WorkflowEventDto> history(@PathVariable String id) {
        return instances.history(id);
    }

    @PostMapping("/{id}/retry")
    public InstanceSummary retry(@PathVariable String id) {
        return instances.retry(id, CurrentUser.username());
    }
}
