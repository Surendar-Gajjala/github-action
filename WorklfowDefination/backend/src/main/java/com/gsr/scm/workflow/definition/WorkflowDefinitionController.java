package com.gsr.scm.workflow.definition;

import com.gsr.scm.config.CurrentUser;
import com.gsr.scm.workflow.definition.DefinitionDtos.CreateDefinitionRequest;
import com.gsr.scm.workflow.definition.DefinitionDtos.DefinitionDetail;
import com.gsr.scm.workflow.definition.DefinitionDtos.DefinitionSummary;
import com.gsr.scm.workflow.definition.DefinitionDtos.SaveDraftRequest;
import com.gsr.scm.workflow.definition.DefinitionDtos.ValidateRequest;
import com.gsr.scm.workflow.definition.DefinitionDtos.VersionDetail;
import com.gsr.scm.workflow.definition.DefinitionDtos.VersionDto;
import com.gsr.scm.workflow.definition.DefinitionDtos.WorkerInfo;
import com.gsr.scm.workflow.worker.WorkflowWorker;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/workflows/definitions")
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService definitions;
    private final List<WorkflowWorker> workers;

    public WorkflowDefinitionController(WorkflowDefinitionService definitions, List<WorkflowWorker> workers) {
        this.definitions = definitions;
        this.workers = workers;
    }

    @GetMapping
    public List<DefinitionSummary> list() {
        return definitions.list();
    }

    @GetMapping("/{key}")
    public DefinitionDetail get(@PathVariable String key) {
        return definitions.get(key);
    }

    @PostMapping
    public ResponseEntity<DefinitionDetail> create(@Valid @RequestBody CreateDefinitionRequest req) {
        DefinitionDetail created = definitions.create(req, CurrentUser.username());
        return ResponseEntity.created(URI.create("/api/workflows/definitions/" + req.key())).body(created);
    }

    /** Saves (creates or updates) the draft version. */
    @PutMapping("/{key}")
    public DefinitionDetail saveDraft(@PathVariable String key, @Valid @RequestBody SaveDraftRequest req) {
        return definitions.saveDraft(key, req, CurrentUser.username());
    }

    @PostMapping("/{key}/publish")
    public DefinitionDetail publish(@PathVariable String key) {
        return definitions.publish(key, CurrentUser.username());
    }

    @PostMapping("/{key}/activate")
    public DefinitionDetail activate(@PathVariable String key) {
        return definitions.setActive(key, true, CurrentUser.username());
    }

    @PostMapping("/{key}/deactivate")
    public DefinitionDetail deactivate(@PathVariable String key) {
        return definitions.setActive(key, false, CurrentUser.username());
    }

    @GetMapping("/{key}/versions")
    public List<VersionDto> versions(@PathVariable String key) {
        return definitions.versions(key);
    }

    @GetMapping("/{key}/versions/{version}")
    public VersionDetail version(@PathVariable String key, @PathVariable int version) {
        return definitions.version(key, version);
    }

    @PostMapping("/validate")
    public BpmnValidator.Result validate(@Valid @RequestBody ValidateRequest req) {
        return definitions.validate(req.bpmnXml(), req.key());
    }

    /** Job types with a registered worker, for the designer's service-task picker. */
    @GetMapping("/workers")
    public List<WorkerInfo> workers() {
        return workers.stream().map(w -> new WorkerInfo(w.jobType(), w.description()))
                .sorted(Comparator.comparing(WorkerInfo::jobType)).toList();
    }
}
