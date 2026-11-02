package com.gsr.scm.workflow.task;

import com.gsr.scm.common.PageResponse;
import com.gsr.scm.common.Pagination;
import com.gsr.scm.config.CurrentUser;
import com.gsr.scm.item.ItemDtos.ChangeDiff;
import com.gsr.scm.workflow.task.TaskDtos.ApproveRequest;
import com.gsr.scm.workflow.task.TaskDtos.RejectRequest;
import com.gsr.scm.workflow.task.TaskDtos.TaskDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows/tasks")
public class WorkflowTaskController {

    private final WorkflowTaskService tasks;

    public WorkflowTaskController(WorkflowTaskService tasks) {
        this.tasks = tasks;
    }

    /**
     * @param mine when true (default) only tasks whose candidate group matches one of the caller's roles
     */
    @GetMapping
    public PageResponse<TaskDto> list(@RequestParam(required = false) String q,
                                      @RequestParam(required = false, defaultValue = "OPEN") ApprovalTask.Status status,
                                      @RequestParam(defaultValue = "true") boolean mine,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @RequestParam(required = false) String sort) {
        return tasks.list(q, status, mine, CurrentUser.roles(),
                Pagination.of(page, size, sort, WorkflowTaskService.SORTABLE, "createdAt,desc"));
    }

    @GetMapping("/{taskId}")
    public TaskDto get(@PathVariable Long taskId) {
        return tasks.get(taskId);
    }

    @GetMapping("/{taskId}/changes")
    public ChangeDiff changes(@PathVariable Long taskId) {
        return tasks.changes(taskId);
    }

    @PostMapping("/{taskId}/approve")
    public TaskDto approve(@PathVariable Long taskId, @Valid @RequestBody(required = false) ApproveRequest req) {
        return tasks.approve(taskId, req == null ? null : req.comment(), CurrentUser.username(), CurrentUser.roles());
    }

    @PostMapping("/{taskId}/reject")
    public TaskDto reject(@PathVariable Long taskId, @Valid @RequestBody RejectRequest req) {
        return tasks.reject(taskId, req.reason(), CurrentUser.username(), CurrentUser.roles());
    }
}
