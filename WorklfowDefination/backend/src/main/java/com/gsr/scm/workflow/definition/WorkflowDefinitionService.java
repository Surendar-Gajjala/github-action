package com.gsr.scm.workflow.definition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsr.scm.audit.AuditService;
import com.gsr.scm.audit.WorkflowEventType;
import com.gsr.scm.common.Exceptions.BusinessRuleException;
import com.gsr.scm.common.Exceptions.ConflictException;
import com.gsr.scm.common.Exceptions.EngineUnavailableException;
import com.gsr.scm.common.Exceptions.NotFoundException;
import com.gsr.scm.workflow.definition.DefinitionDtos.CreateDefinitionRequest;
import com.gsr.scm.workflow.definition.DefinitionDtos.DefinitionDetail;
import com.gsr.scm.workflow.definition.DefinitionDtos.DefinitionSummary;
import com.gsr.scm.workflow.definition.DefinitionDtos.SaveDraftRequest;
import com.gsr.scm.workflow.definition.DefinitionDtos.VersionDetail;
import com.gsr.scm.workflow.definition.DefinitionDtos.VersionDto;
import com.gsr.scm.workflow.definition.WorkflowDefinitionRepositories.WorkflowDefinitionRepository;
import com.gsr.scm.workflow.definition.WorkflowDefinitionRepositories.WorkflowDefinitionVersionRepository;
import com.gsr.scm.workflow.definition.WorkflowDefinitionVersion.Status;
import com.gsr.scm.workflow.engine.WorkflowEngineAdapter;
import com.gsr.scm.workflow.engine.WorkflowEngineAdapter.DeploymentResult;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Workflow definitions: drafts, validation, versioned publishing (deployment to the engine) and activation.
 */
@Service
public class WorkflowDefinitionService {

    private final WorkflowDefinitionRepository definitions;
    private final WorkflowDefinitionVersionRepository versions;
    private final BpmnValidator validator;
    private final WorkflowEngineAdapter engine;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Map<Long, BpmnModel> modelCache = new ConcurrentHashMap<>();

    public WorkflowDefinitionService(WorkflowDefinitionRepository definitions,
                                     WorkflowDefinitionVersionRepository versions, BpmnValidator validator,
                                     WorkflowEngineAdapter engine, AuditService audit, ObjectMapper mapper) {
        this.definitions = definitions;
        this.versions = versions;
        this.validator = validator;
        this.engine = engine;
        this.audit = audit;
        this.mapper = mapper;
    }

    public record ResolvedDefinition(WorkflowDefinition definition, WorkflowDefinitionVersion version,
                                     ApprovalRules rules) {
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<DefinitionSummary> list() {
        return definitions.findAllByOrderByWorkflowKeyAsc().stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public DefinitionDetail get(String key) {
        WorkflowDefinition def = load(key);
        List<WorkflowDefinitionVersion> all = versions.findByWorkflowKeyOrderByVersionNumberDesc(key);
        WorkflowDefinitionVersion current = all.stream().filter(v -> v.getStatus() == Status.DRAFT).findFirst()
                .or(() -> all.stream().filter(v -> v.getStatus() == Status.PUBLISHED).findFirst())
                .orElse(all.isEmpty() ? null : all.get(0));
        return new DefinitionDetail(summary(def), current == null ? null : detail(current),
                all.stream().map(VersionDto::of).toList());
    }

    @Transactional(readOnly = true)
    public List<VersionDto> versions(String key) {
        load(key);
        return versions.findByWorkflowKeyOrderByVersionNumberDesc(key).stream().map(VersionDto::of).toList();
    }

    @Transactional(readOnly = true)
    public VersionDetail version(String key, int versionNumber) {
        return versions.findByWorkflowKeyAndVersionNumber(key, versionNumber).map(this::detail)
                .orElseThrow(() -> new NotFoundException("Workflow " + key + " has no version " + versionNumber));
    }

    public BpmnValidator.Result validate(String xml, String key) {
        return validator.validate(xml, key);
    }

    /** Parsed model of a definition version (cached; published versions are immutable). */
    @Transactional(readOnly = true)
    public BpmnModel model(Long versionId) {
        BpmnModel cached = modelCache.get(versionId);
        if (cached != null) {
            return cached;
        }
        WorkflowDefinitionVersion v = versions.findById(versionId)
                .orElseThrow(() -> NotFoundException.of("Workflow definition version", versionId));
        BpmnModel model = BpmnParser.parse(v.getBpmnXml());
        if (v.getStatus() != Status.DRAFT) {
            modelCache.put(versionId, model);
        }
        return model;
    }

    @Transactional(readOnly = true)
    public ApprovalRules rules(Long versionId) {
        return versions.findById(versionId).map(v -> readRules(v.getRulesJson()))
                .orElseThrow(() -> NotFoundException.of("Workflow definition version", versionId));
    }

    /**
     * Picks the published, active definition used for an item submission. An explicit key wins; otherwise
     * a first approval uses ITEM_LIFECYCLE, a change to an approved item prefers ITEM_PART_CHANGE.
     */
    @Transactional(readOnly = true)
    public ResolvedDefinition resolveForSubmission(String workflowKey, boolean previouslyApproved) {
        if (workflowKey != null && !workflowKey.isBlank()) {
            WorkflowDefinition def = load(workflowKey);
            if (!def.isActive()) {
                throw new BusinessRuleException("Workflow " + workflowKey + " is inactive");
            }
            return publishedVersion(def).orElseThrow(() -> new BusinessRuleException(
                    "Workflow " + workflowKey + " has no published version. Publish it in the Workflow tab first."));
        }
        List<WorkflowDefinition.Type> preference = previouslyApproved
                ? List.of(WorkflowDefinition.Type.ITEM_PART_CHANGE, WorkflowDefinition.Type.ITEM_LIFECYCLE)
                : List.of(WorkflowDefinition.Type.ITEM_LIFECYCLE);
        for (WorkflowDefinition.Type type : preference) {
            for (WorkflowDefinition def : definitions.findByWorkflowTypeAndActiveTrue(type)) {
                Optional<ResolvedDefinition> resolved = publishedVersion(def);
                if (resolved.isPresent()) {
                    return resolved.get();
                }
            }
        }
        throw new BusinessRuleException("No active, published lifecycle workflow is available (" + preference
                + "). Publish one in the Workflow tab before submitting.");
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public DefinitionDetail create(CreateDefinitionRequest req, String actor) {
        if (definitions.existsByWorkflowKey(req.key())) {
            throw new ConflictException("Workflow key " + req.key() + " already exists");
        }
        WorkflowDefinition def = new WorkflowDefinition();
        def.setWorkflowKey(req.key());
        def.setName(req.name().trim());
        def.setDescription(req.description());
        def.setWorkflowType(req.type());
        def.setUpdatedBy(actor);
        definitions.save(def);

        String xml = req.bpmnXml() == null || req.bpmnXml().isBlank() ? template(req.key(), req.name()) : req.bpmnXml();
        WorkflowDefinitionVersion v = new WorkflowDefinitionVersion();
        v.setWorkflowKey(req.key());
        v.setVersionNumber(1);
        v.setBpmnXml(xml);
        v.setRulesJson(writeRules(req.rules() == null ? ApprovalRules.defaults() : req.rules()));
        v.setCreatedBy(actor);
        versions.save(v);
        audit.record(WorkflowEventType.DEFINITION_SAVED, actor, e -> {
            e.setWorkflowDefinitionKey(req.key());
            e.setSummary("Workflow definition " + req.key() + " created (draft v1)");
        });
        return get(req.key());
    }

    /** Saves the editable draft, creating a new draft version on top of the latest one if needed. */
    @Transactional
    public DefinitionDetail saveDraft(String key, SaveDraftRequest req, String actor) {
        WorkflowDefinition def = load(key);
        if (req.name() != null && !req.name().isBlank()) {
            def.setName(req.name().trim());
        }
        if (req.description() != null) {
            def.setDescription(req.description());
        }
        if (req.type() != null) {
            def.setWorkflowType(req.type());
        }
        def.setUpdatedAt(LocalDateTime.now());
        def.setUpdatedBy(actor);

        WorkflowDefinitionVersion draft = versions.findFirstByWorkflowKeyAndStatus(key, Status.DRAFT)
                .orElseGet(() -> {
                    int next = versions.findFirstByWorkflowKeyOrderByVersionNumberDesc(key)
                            .map(v -> v.getVersionNumber() + 1).orElse(1);
                    WorkflowDefinitionVersion v = new WorkflowDefinitionVersion();
                    v.setWorkflowKey(key);
                    v.setVersionNumber(next);
                    v.setCreatedBy(actor);
                    return v;
                });
        draft.setBpmnXml(req.bpmnXml());
        draft.setRulesJson(writeRules(req.rules()));
        draft.setUpdatedAt(LocalDateTime.now());
        versions.save(draft);
        audit.record(WorkflowEventType.DEFINITION_SAVED, actor, e -> {
            e.setWorkflowDefinitionKey(key);
            e.setSummary("Draft v" + draft.getVersionNumber() + " of " + key + " saved");
        });
        return get(key);
    }

    /** Validates the draft, deploys it to the engine and makes it the published version. */
    @Transactional
    public DefinitionDetail publish(String key, String actor) {
        WorkflowDefinition def = load(key);
        WorkflowDefinitionVersion draft = versions.findFirstByWorkflowKeyAndStatus(key, Status.DRAFT)
                .orElseThrow(() -> new BusinessRuleException(
                        "Workflow " + key + " has no draft to publish. Edit the definition to create a new draft."));
        BpmnValidator.Result result = validator.validate(draft.getBpmnXml(), key);
        if (!result.valid()) {
            throw new BusinessRuleException("The BPMN definition is invalid and cannot be published", result.errors());
        }
        DeploymentResult deployment;
        try {
            deployment = engine.deploy(key, draft.getBpmnXml());
        } catch (EngineUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EngineUnavailableException("Deployment to the workflow engine failed: " + e.getMessage(), e);
        }
        versions.findFirstByWorkflowKeyAndStatus(key, Status.PUBLISHED).ifPresent(previous -> {
            previous.setStatus(Status.SUPERSEDED);
            versions.saveAndFlush(previous);
        });
        draft.setStatus(Status.PUBLISHED);
        draft.setEngine(engine.info().engine());
        draft.setEngineProcessDefinitionKey(deployment.processDefinitionKey());
        draft.setEngineVersion(deployment.version());
        draft.setPublishedAt(LocalDateTime.now());
        draft.setPublishedBy(actor);
        versions.save(draft);
        def.setUpdatedAt(LocalDateTime.now());
        def.setUpdatedBy(actor);
        audit.record(WorkflowEventType.DEFINITION_PUBLISHED, actor, e -> {
            e.setWorkflowDefinitionKey(key);
            e.setSummary("Published %s v%d to %s (engine definition key %d, engine version %d)".formatted(key,
                    draft.getVersionNumber(), engine.info().engine(), deployment.processDefinitionKey(),
                    deployment.version()));
        });
        return get(key);
    }

    @Transactional
    public DefinitionDetail setActive(String key, boolean active, String actor) {
        WorkflowDefinition def = load(key);
        if (def.isActive() != active) {
            def.setActive(active);
            def.setUpdatedAt(LocalDateTime.now());
            def.setUpdatedBy(actor);
            audit.record(WorkflowEventType.DEFINITION_STATUS_CHANGED, actor, e -> {
                e.setWorkflowDefinitionKey(key);
                e.setSummary(key + (active ? " activated" : " deactivated"));
            });
        }
        return get(key);
    }

    // ------------------------------------------------------------------ helpers

    private Optional<ResolvedDefinition> publishedVersion(WorkflowDefinition def) {
        return versions.findFirstByWorkflowKeyAndStatus(def.getWorkflowKey(), Status.PUBLISHED)
                .filter(v -> v.getEngineProcessDefinitionKey() != null)
                .map(v -> new ResolvedDefinition(def, v, readRules(v.getRulesJson())));
    }

    private WorkflowDefinition load(String key) {
        return definitions.findByWorkflowKey(key).orElseThrow(() -> NotFoundException.of("Workflow definition", key));
    }

    private DefinitionSummary summary(WorkflowDefinition def) {
        List<WorkflowDefinitionVersion> all = versions.findByWorkflowKeyOrderByVersionNumberDesc(def.getWorkflowKey());
        WorkflowDefinitionVersion latest = all.isEmpty() ? null : all.get(0);
        Integer published = all.stream().filter(v -> v.getStatus() == Status.PUBLISHED)
                .map(WorkflowDefinitionVersion::getVersionNumber).findFirst().orElse(null);
        boolean hasDraft = all.stream().anyMatch(v -> v.getStatus() == Status.DRAFT);
        return new DefinitionSummary(def.getWorkflowKey(), def.getName(), def.getDescription(), def.getWorkflowType(),
                def.isActive(), latest == null ? null : latest.getVersionNumber(),
                latest == null ? null : latest.getStatus(), published, hasDraft, def.getUpdatedAt(), def.getUpdatedBy());
    }

    private VersionDetail detail(WorkflowDefinitionVersion v) {
        return new VersionDetail(VersionDto.of(v), v.getBpmnXml(), readRules(v.getRulesJson()));
    }

    private ApprovalRules readRules(String json) {
        try {
            return mapper.readValue(json, ApprovalRules.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt approval rules", e);
        }
    }

    private String writeRules(ApprovalRules rules) {
        try {
            return mapper.writeValueAsString(rules);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String template(String processId, String name) {
        try {
            String xml = new ClassPathResource("bpmn/template.bpmn").getContentAsString(StandardCharsets.UTF_8);
            return xml.replace("{{PROCESS_ID}}", processId).replace("{{PROCESS_NAME}}", escapeXml(name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
