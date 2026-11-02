package com.gsr.scm.workflow;

import com.gsr.scm.audit.WorkflowEventDto;
import com.gsr.scm.audit.WorkflowEventRepository;
import com.gsr.scm.audit.WorkflowEventType;
import com.gsr.scm.common.PageResponse;
import com.gsr.scm.common.Pagination;
import com.gsr.scm.config.CurrentUser;
import com.gsr.scm.item.LifecycleStatus;
import com.gsr.scm.lifecycle.LifecycleService;
import com.gsr.scm.lifecycle.LifecycleTransition;
import com.gsr.scm.workflow.engine.WorkflowEngineAdapter;
import com.gsr.scm.workflow.engine.WorkflowEngineAdapter.EngineInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Lifecycle configuration, global workflow history, engine info and current-user endpoints. */
@RestController
@RequestMapping("/api")
public class WorkflowAdminController {

    private final LifecycleService lifecycle;
    private final WorkflowEventRepository events;
    private final WorkflowEngineAdapter engine;

    public WorkflowAdminController(LifecycleService lifecycle, WorkflowEventRepository events,
                                   WorkflowEngineAdapter engine) {
        this.lifecycle = lifecycle;
        this.events = events;
        this.engine = engine;
    }

    public record TransitionDto(Long id, LifecycleStatus fromStatus, LifecycleStatus toStatus, boolean enabled,
                                boolean system, String description, LocalDateTime updatedAt, String updatedBy) {
        static TransitionDto of(LifecycleTransition t) {
            return new TransitionDto(t.getId(), t.getFromStatus(), t.getToStatus(), t.isEnabled(), t.isSystem(),
                    t.getDescription(), t.getUpdatedAt(), t.getUpdatedBy());
        }
    }

    public record TransitionRequest(@NotNull LifecycleStatus fromStatus, @NotNull LifecycleStatus toStatus,
                                    boolean enabled, @Size(max = 500) String description) {
    }

    public record Me(String username, Set<String> roles) {
    }

    @GetMapping("/workflows/lifecycle-transitions")
    public List<TransitionDto> transitions() {
        return lifecycle.list().stream().map(TransitionDto::of).toList();
    }

    @PutMapping("/workflows/lifecycle-transitions")
    public TransitionDto upsertTransition(@Valid @RequestBody TransitionRequest req) {
        return TransitionDto.of(lifecycle.upsert(req.fromStatus(), req.toStatus(), req.enabled(), req.description(),
                CurrentUser.username()));
    }

    @DeleteMapping("/workflows/lifecycle-transitions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTransition(@PathVariable Long id) {
        lifecycle.delete(id, CurrentUser.username());
    }

    @GetMapping("/workflows/history")
    public PageResponse<WorkflowEventDto> history(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) WorkflowEventType action,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "50") int size,
                                                  @RequestParam(required = false) String sort) {
        return PageResponse.of(events.search(Pagination.likePattern(q), action,
                        Pagination.of(page, size, sort, Set.of("timestamp", "action", "actor"), "timestamp,desc")),
                WorkflowEventDto::of);
    }

    @GetMapping("/workflows/engine")
    public EngineInfo engine() {
        return engine.info();
    }

    @GetMapping("/me")
    public Me me() {
        return new Me(CurrentUser.username(), CurrentUser.roles());
    }
}
