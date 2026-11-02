package com.gsr.scm.workflow.definition;

import com.gsr.scm.config.Roles;
import com.gsr.scm.config.WorkflowProperties;
import com.gsr.scm.workflow.definition.BpmnModel.Flow;
import com.gsr.scm.workflow.definition.BpmnModel.Kind;
import com.gsr.scm.workflow.definition.BpmnModel.Node;
import com.gsr.scm.workflow.engine.dev.FeelLite;
import com.gsr.scm.workflow.worker.WorkflowWorker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Structural validation of a BPMN definition against what this application can execute:
 * known worker job types, approval candidate groups, conditions on gateways, reachability, and - when the
 * development adapter is active - the BPMN subset it supports.
 */
@Component
public class BpmnValidator {

    /** Resolved lazily: workers depend (indirectly) on the definition service, which depends on this validator. */
    private final ObjectProvider<WorkflowWorker> workers;
    private final boolean devEngine;

    public BpmnValidator(ObjectProvider<WorkflowWorker> workers, WorkflowProperties properties) {
        this.workers = workers;
        this.devEngine = properties.isDevEngine();
    }

    private Set<String> knownJobTypes() {
        return workers.orderedStream().map(WorkflowWorker::jobType).collect(Collectors.toCollection(TreeSet::new));
    }

    public record Result(boolean valid, List<String> errors, List<String> warnings, String processId,
                         List<ElementInfo> elements) {
    }

    public record ElementInfo(String id, String name, String kind, String jobType, List<String> candidateGroups) {
    }

    public Result validate(String xml, String expectedProcessId) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> knownJobTypes = knownJobTypes();
        BpmnModel model;
        try {
            model = BpmnParser.parse(xml);
        } catch (BpmnParser.BpmnParseException e) {
            return new Result(false, List.of(e.getMessage()), List.of(), null, List.of());
        }

        if (expectedProcessId != null && !expectedProcessId.equals(model.processId())) {
            errors.add("Process id must equal the workflow key '%s' (found '%s')"
                    .formatted(expectedProcessId, model.processId()));
        }
        if (!model.executable()) {
            errors.add("Process must be marked executable (isExecutable=\"true\")");
        }

        List<Node> starts = model.nodesOfKind(Kind.START_EVENT);
        if (starts.size() != 1) {
            errors.add("Exactly one start event is required (found " + starts.size() + ")");
        }
        if (model.nodesOfKind(Kind.END_EVENT).isEmpty()) {
            errors.add("At least one end event is required");
        }

        for (Flow f : model.flows().values()) {
            if (!model.nodes().containsKey(f.sourceRef()) || !model.nodes().containsKey(f.targetRef())) {
                errors.add("Sequence flow '" + f.id() + "' references an unknown element");
            }
        }

        for (Node n : model.nodes().values()) {
            String label = "'" + n.displayName() + "' (" + n.id() + ")";
            switch (n.kind()) {
                case UNSUPPORTED -> {
                    String msg = "Element " + label + " of type " + n.tagName() + " is not supported";
                    if (devEngine) {
                        errors.add(msg + " by the development workflow adapter");
                    } else {
                        warnings.add(msg + " by the application's validation; Camunda will execute it as modelled");
                    }
                }
                case START_EVENT, END_EVENT -> {
                    if (n.hasEventDefinition() && devEngine) {
                        errors.add("Event " + label + " must be a plain (none) event for the development adapter");
                    }
                }
                case SERVICE_TASK -> {
                    if (n.jobType() == null) {
                        errors.add("Service task " + label + " needs a job type (zeebe:taskDefinition type)");
                    } else if (!knownJobTypes.contains(n.jobType())) {
                        errors.add("Service task " + label + " uses job type '" + n.jobType()
                                + "' but no worker is registered for it. Known types: " + knownJobTypes);
                    }
                }
                case USER_TASK -> {
                    if (n.camundaUserTask()) {
                        errors.add("User task " + label + " uses the Camunda user task implementation "
                                + "(zeebe:userTask). Approval tasks must use the job-worker implementation so the "
                                + "application can manage them; remove <zeebe:userTask/>");
                    }
                    if (n.candidateGroups().isEmpty()) {
                        errors.add("User task " + label + " needs a candidate group (one of " + Roles.APPROVAL_GROUPS + ")");
                    } else {
                        n.candidateGroups().stream()
                                .filter(g -> !Roles.APPROVAL_GROUPS.contains(g))
                                .forEach(g -> errors.add("User task " + label + " has unknown candidate group '" + g
                                        + "'. Allowed: " + Roles.APPROVAL_GROUPS));
                    }
                    if (n.outgoing().size() == 1) {
                        Node next = model.nodes().get(model.flows().get(n.outgoing().get(0)).targetRef());
                        if (next != null && next.kind() != Kind.EXCLUSIVE_GATEWAY) {
                            warnings.add("User task " + label + " is not followed by a gateway; a rejection will "
                                    + "not change the path (check variable lastDecision)");
                        }
                    }
                }
                case EXCLUSIVE_GATEWAY -> validateGateway(model, n, label, errors, warnings);
            }
            if (n.kind() != Kind.END_EVENT && n.outgoing().isEmpty()) {
                errors.add("Element " + label + " has no outgoing sequence flow");
            }
            if (n.kind() != Kind.START_EVENT && n.incoming().isEmpty()) {
                errors.add("Element " + label + " has no incoming sequence flow");
            }
            if (n.kind() != Kind.EXCLUSIVE_GATEWAY && n.outgoing().size() > 1) {
                String msg = "Element " + label + " has several outgoing flows (implicit parallel split)";
                if (devEngine) {
                    errors.add(msg + ", which the development adapter does not support; use an exclusive gateway");
                } else {
                    warnings.add(msg);
                }
            }
        }

        if (starts.size() == 1) {
            Set<String> reachable = reachable(model, starts.get(0).id());
            model.nodes().keySet().stream().filter(id -> !reachable.contains(id))
                    .forEach(id -> errors.add("Element '" + model.displayName(id) + "' is not reachable from the start event"));
        }

        boolean hasRelease = model.nodesOfKind(Kind.SERVICE_TASK).stream()
                .anyMatch(n -> "release-revision".equals(n.jobType()));
        if (!hasRelease) {
            warnings.add("No 'release-revision' service task: items processed by this workflow can never become APPROVED");
        }
        if (model.nodesOfKind(Kind.USER_TASK).isEmpty()) {
            warnings.add("No approval (user) task: the release worker will refuse to release without an approval");
        }

        List<ElementInfo> elements = model.nodes().values().stream()
                .map(n -> new ElementInfo(n.id(), n.displayName(), n.kind().name(), n.jobType(), n.candidateGroups()))
                .toList();
        return new Result(errors.isEmpty(), errors, warnings, model.processId(), elements);
    }

    private void validateGateway(BpmnModel model, Node n, String label, List<String> errors, List<String> warnings) {
        if (n.outgoing().size() <= 1) {
            if (n.incoming().size() <= 1) {
                warnings.add("Gateway " + label + " neither splits nor merges");
            }
            return;
        }
        int unconditioned = 0;
        for (String flowId : n.outgoing()) {
            Flow f = model.flows().get(flowId);
            if (flowId.equals(n.defaultFlowId())) {
                continue;
            }
            if (f.condition() == null || f.condition().isBlank()) {
                unconditioned++;
                continue;
            }
            if (!f.condition().startsWith("=")) {
                errors.add("Condition on flow '" + (f.name() == null ? f.id() : f.name())
                        + "' must be a FEEL expression starting with '=' (e.g. = lastDecision = \"APPROVED\")");
            } else if (devEngine) {
                try {
                    FeelLite.compile(f.condition());
                } catch (FeelLite.FeelException e) {
                    errors.add("Condition on flow '" + (f.name() == null ? f.id() : f.name())
                            + "' is not supported by the development adapter: " + e.getMessage());
                }
            }
        }
        if (unconditioned > 0) {
            errors.add("Gateway " + label + " has " + unconditioned
                    + " outgoing flow(s) without a condition; add conditions or mark one flow as default");
        }
        if (n.defaultFlowId() == null) {
            warnings.add("Gateway " + label + " has no default flow; an incident is raised if no condition matches");
        }
    }

    private static Set<String> reachable(BpmnModel model, String startId) {
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(List.of(startId));
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!seen.add(id)) {
                continue;
            }
            Node n = model.nodes().get(id);
            if (n == null) {
                continue;
            }
            for (String flowId : n.outgoing()) {
                Flow f = model.flows().get(flowId);
                if (f != null) {
                    queue.add(f.targetRef());
                }
            }
        }
        return seen;
    }
}
