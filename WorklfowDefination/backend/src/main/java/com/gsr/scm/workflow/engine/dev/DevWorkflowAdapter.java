package com.gsr.scm.workflow.engine.dev;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsr.scm.common.Exceptions.ConflictException;
import com.gsr.scm.common.Exceptions.NotFoundException;
import com.gsr.scm.config.WorkflowProperties;
import com.gsr.scm.workflow.definition.BpmnModel;
import com.gsr.scm.workflow.definition.BpmnModel.Flow;
import com.gsr.scm.workflow.definition.BpmnModel.Kind;
import com.gsr.scm.workflow.definition.BpmnModel.Node;
import com.gsr.scm.workflow.definition.BpmnParser;
import com.gsr.scm.workflow.engine.JobContext;
import com.gsr.scm.workflow.engine.WorkflowEngineAdapter;
import com.gsr.scm.workflow.engine.WorkflowJobDispatcher;
import com.gsr.scm.workflow.engine.dev.DevEngineEntities.DevDeployment;
import com.gsr.scm.workflow.engine.dev.DevEngineEntities.DevDeploymentRepository;
import com.gsr.scm.workflow.engine.dev.DevEngineEntities.DevProcessInstance;
import com.gsr.scm.workflow.engine.dev.DevEngineEntities.DevProcessInstance.State;
import com.gsr.scm.workflow.engine.dev.DevEngineEntities.DevProcessInstanceRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DEVELOPMENT / TEST ONLY - this is not Camunda.
 * <p>
 * A single-threaded, in-process interpreter for the BPMN subset the application's definitions use: none start/end
 * events, service tasks (zeebe:taskDefinition), job-worker user tasks and exclusive gateways with simple FEEL
 * conditions. It reproduces the engine contract the application relies on - asynchronous job execution after the
 * caller's transaction commits, retries with backoff, incidents, and user-task jobs that wait for completion - so
 * that the business logic can be developed and tested without a Camunda cluster. It does not implement BPMN
 * semantics beyond that subset, timers, messages, parallelism, or Camunda's persistence and scalability.
 */
@Component
@ConditionalOnProperty(name = "scm.workflow.engine", havingValue = "dev", matchIfMissing = true)
public class DevWorkflowAdapter implements WorkflowEngineAdapter {

    private static final Logger log = LoggerFactory.getLogger(DevWorkflowAdapter.class);
    private static final int MAX_STEPS_PER_RUN = 500;
    private static final int DEFAULT_RETRIES = 3;

    private final DevDeploymentRepository deployments;
    private final DevProcessInstanceRepository instances;
    private final ObjectProvider<WorkflowJobDispatcher> dispatcher;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final long retryBackoffMs;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dev-workflow-engine");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger pending = new AtomicInteger();
    private final Map<Long, BpmnModel> models = new ConcurrentHashMap<>();

    public DevWorkflowAdapter(DevDeploymentRepository deployments, DevProcessInstanceRepository instances,
                              ObjectProvider<WorkflowJobDispatcher> dispatcher, PlatformTransactionManager txManager,
                              ObjectMapper mapper, WorkflowProperties properties) {
        this.deployments = deployments;
        this.instances = instances;
        this.dispatcher = dispatcher;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.mapper = mapper;
        this.retryBackoffMs = properties.dev().retryBackoffMs();
        log.warn("Workflow engine: DEVELOPMENT ADAPTER (in-process BPMN subset interpreter). This is NOT Camunda 8. "
                + "Set scm.workflow.engine=camunda to use a Camunda 8 cluster.");
    }

    @Override
    public EngineInfo info() {
        return new EngineInfo("DEV_ADAPTER", "Development workflow adapter (not Camunda)", false, true,
                "In-process interpreter for a BPMN subset (none events, service tasks, job-worker user tasks, "
                        + "exclusive gateways). For local development and automated tests only.");
    }

    // ------------------------------------------------------------------ engine API

    @Override
    public DeploymentResult deploy(String processId, String bpmnXml) {
        BpmnModel model = BpmnParser.parse(bpmnXml);
        if (!processId.equals(model.processId())) {
            throw new IllegalArgumentException("Process id " + model.processId() + " does not match " + processId);
        }
        int version = deployments.findFirstByProcessIdOrderByVersionDesc(processId).map(d -> d.getVersion() + 1).orElse(1);
        DevDeployment d = new DevDeployment();
        d.setProcessId(processId);
        d.setVersion(version);
        d.setBpmnXml(bpmnXml);
        deployments.save(d);
        models.put(d.getId(), model);
        return new DeploymentResult(d.getId(), version);
    }

    @Override
    public long startInstance(long processDefinitionKey, Map<String, Object> variables) {
        BpmnModel model = model(processDefinitionKey);
        Node start = model.nodesOfKind(Kind.START_EVENT).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Process has no start event"));
        DevProcessInstance i = new DevProcessInstance();
        i.setDeploymentKey(processDefinitionKey);
        i.setCurrentElementId(start.id());
        i.setVariablesJson(write(variables));
        instances.save(i);
        long key = i.getId();
        afterCommit(() -> schedule(key, 0));
        return key;
    }

    @Override
    public void completeUserTask(long jobKey, Map<String, Object> variables) {
        DevProcessInstance i = instances.findByWaitingJobKey(jobKey)
                .orElseThrow(() -> new ConflictException("No user task is waiting for job " + jobKey));
        if (i.getState() != State.WAITING) {
            throw new ConflictException("Process instance " + i.getId() + " is not waiting for a user task");
        }
        BpmnModel model = model(i.getDeploymentKey());
        Node task = model.nodes().get(i.getCurrentElementId());
        String next = onlyTarget(model, task);
        Map<String, Object> vars = read(i.getVariablesJson());
        vars.putAll(variables);
        i.setVariablesJson(write(vars));
        i.setCurrentElementId(next);
        i.setStepCounter(i.getStepCounter() + 1);
        i.setWaitingJobKey(null);
        i.setState(State.ACTIVE);
        i.setUpdatedAt(LocalDateTime.now());
        instances.save(i);
        afterCommit(() -> schedule(i.getId(), 0));
    }

    @Override
    public void retry(long engineInstanceKey, Long failedJobKey) {
        DevProcessInstance i = instances.findById(engineInstanceKey)
                .orElseThrow(() -> NotFoundException.of("Dev engine instance", engineInstanceKey));
        if (i.getState() != State.INCIDENT) {
            throw new ConflictException("Instance " + engineInstanceKey + " has no incident");
        }
        i.setState(State.ACTIVE);
        i.setRetriesLeft(-1);
        i.setIncidentMessage(null);
        instances.save(i);
        afterCommit(() -> schedule(engineInstanceKey, 0));
    }

    /** Test support: waits until all scheduled work has been executed. */
    public boolean awaitIdle(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (pending.get() == 0) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return pending.get() == 0;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------ interpreter

    private enum Outcome { CONTINUE, STOP, RETRY_LATER }

    private record Snapshot(long key, long deploymentKey, String elementId, Map<String, Object> vars, int retriesLeft,
                            int step) {
    }

    private void schedule(long key, long delayMs) {
        pending.incrementAndGet();
        executor.schedule(() -> {
            try {
                run(key);
            } catch (RuntimeException e) {
                log.error("Dev engine failure for instance {}", key, e);
            } finally {
                pending.decrementAndGet();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void run(long key) {
        for (int i = 0; i < MAX_STEPS_PER_RUN; i++) {
            Outcome outcome = step(key);
            if (outcome == Outcome.STOP) {
                return;
            }
            if (outcome == Outcome.RETRY_LATER) {
                schedule(key, retryBackoffMs);
                return;
            }
        }
        incident(key, null, "Process exceeded " + MAX_STEPS_PER_RUN + " steps without waiting (loop?)");
    }

    private Outcome step(long key) {
        Snapshot s = tx.execute(status -> instances.findById(key)
                .filter(i -> i.getState() == State.ACTIVE)
                .map(i -> new Snapshot(i.getId(), i.getDeploymentKey(), i.getCurrentElementId(),
                        read(i.getVariablesJson()), i.getRetriesLeft(), i.getStepCounter()))
                .orElse(null));
        if (s == null) {
            return Outcome.STOP;
        }
        BpmnModel model = model(s.deploymentKey());
        Node node = model.nodes().get(s.elementId());
        if (node == null) {
            return incident(key, s, "Unknown element " + s.elementId());
        }
        long jobKey = key * 10_000L + s.step();
        return switch (node.kind()) {
            case START_EVENT -> advance(key, onlyTarget(model, node), Map.of());
            case END_EVENT -> {
                tx.executeWithoutResult(status -> instances.findById(key).ifPresent(i -> {
                    i.setState(State.COMPLETED);
                    i.setUpdatedAt(LocalDateTime.now());
                }));
                dispatcher.getObject().onProcessEnded(key);
                yield Outcome.STOP;
            }
            case EXCLUSIVE_GATEWAY -> {
                String target;
                try {
                    target = chooseFlow(model, node, s.vars());
                } catch (RuntimeException e) {
                    yield incident(key, s, "Gateway '" + node.displayName() + "': " + e.getMessage());
                }
                yield advance(key, target, Map.of());
            }
            case SERVICE_TASK -> {
                int retries = s.retriesLeft() >= 0 ? s.retriesLeft()
                        : node.retries() != null ? node.retries() : DEFAULT_RETRIES;
                JobContext job = new JobContext(jobKey, node.jobType(), key, node.id(), retries, s.vars(), Map.of());
                Map<String, Object> result;
                try {
                    result = dispatcher.getObject().executeServiceJob(job);
                } catch (RuntimeException e) {
                    yield failed(key, job, e, retries - 1);
                }
                yield advance(key, onlyTarget(model, node), result == null ? Map.of() : result);
            }
            case USER_TASK -> {
                Map<String, String> headers = new HashMap<>();
                if (!node.candidateGroups().isEmpty()) {
                    headers.put(JobContext.HEADER_CANDIDATE_GROUPS, write(node.candidateGroups()));
                }
                if (node.dueDate() != null) {
                    headers.put(JobContext.HEADER_DUE_DATE, node.dueDate());
                }
                int retries = s.retriesLeft() >= 0 ? s.retriesLeft() : DEFAULT_RETRIES;
                JobContext job = new JobContext(jobKey, JobContext.USER_TASK_JOB_TYPE, key, node.id(), retries,
                        s.vars(), headers);
                try {
                    dispatcher.getObject().onUserTaskActivated(job);
                } catch (RuntimeException e) {
                    yield failed(key, job, e, retries - 1);
                }
                tx.executeWithoutResult(status -> instances.findById(key).ifPresent(i -> {
                    i.setState(State.WAITING);
                    i.setWaitingJobKey(jobKey);
                    i.setRetriesLeft(-1);
                    i.setUpdatedAt(LocalDateTime.now());
                }));
                yield Outcome.STOP;
            }
            case UNSUPPORTED -> incident(key, s, "Element type " + node.tagName()
                    + " is not supported by the development adapter");
        };
    }

    private Outcome advance(long key, String target, Map<String, Object> outputs) {
        tx.executeWithoutResult(status -> instances.findById(key).ifPresent(i -> {
            if (!outputs.isEmpty()) {
                Map<String, Object> vars = read(i.getVariablesJson());
                vars.putAll(outputs);
                i.setVariablesJson(write(vars));
            }
            i.setCurrentElementId(target);
            i.setStepCounter(i.getStepCounter() + 1);
            i.setRetriesLeft(-1);
            i.setUpdatedAt(LocalDateTime.now());
        }));
        return Outcome.CONTINUE;
    }

    private Outcome failed(long key, JobContext job, RuntimeException error, int retriesLeft) {
        dispatcher.getObject().onJobFailed(job, error, Math.max(retriesLeft, 0));
        tx.executeWithoutResult(status -> instances.findById(key).ifPresent(i -> {
            i.setRetriesLeft(Math.max(retriesLeft, 0));
            if (retriesLeft <= 0) {
                i.setState(State.INCIDENT);
                i.setIncidentMessage(truncate(WorkflowJobDispatcher.rootMessage(error)));
            }
            i.setUpdatedAt(LocalDateTime.now());
        }));
        return retriesLeft > 0 ? Outcome.RETRY_LATER : Outcome.STOP;
    }

    private Outcome incident(long key, Snapshot s, String message) {
        String elementId = s == null ? "?" : s.elementId();
        JobContext job = new JobContext(key * 10_000L + (s == null ? 0 : s.step()), "engine", key, elementId, 0,
                s == null ? Map.of() : s.vars(), Map.of());
        dispatcher.getObject().onJobFailed(job, new IllegalStateException(message), 0);
        tx.executeWithoutResult(status -> instances.findById(key).ifPresent(i -> {
            i.setState(State.INCIDENT);
            i.setIncidentMessage(truncate(message));
        }));
        return Outcome.STOP;
    }

    private static String chooseFlow(BpmnModel model, Node gateway, Map<String, Object> vars) {
        for (String flowId : gateway.outgoing()) {
            if (flowId.equals(gateway.defaultFlowId())) {
                continue;
            }
            Flow f = model.flows().get(flowId);
            if (f.condition() == null || gateway.outgoing().size() == 1) {
                return f.targetRef();
            }
            if (FeelLite.test(f.condition(), vars)) {
                return f.targetRef();
            }
        }
        if (gateway.defaultFlowId() != null) {
            return model.flows().get(gateway.defaultFlowId()).targetRef();
        }
        throw new IllegalStateException("no outgoing condition matched and there is no default flow");
    }

    private static String onlyTarget(BpmnModel model, Node node) {
        List<String> out = node.outgoing();
        if (out.size() != 1) {
            throw new IllegalStateException("Element " + node.id() + " must have exactly one outgoing flow");
        }
        return model.flows().get(out.get(0)).targetRef();
    }

    private BpmnModel model(long deploymentKey) {
        return models.computeIfAbsent(deploymentKey, k -> BpmnParser.parse(deployments.findById(k)
                .orElseThrow(() -> NotFoundException.of("Dev engine deployment", k)).getBpmnXml()));
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private Map<String, Object> read(String json) {
        try {
            return new HashMap<>(mapper.readValue(json, new TypeReference<Map<String, Object>>() {
            }));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 2000 ? s : s.substring(0, 1997) + "...";
    }
}
