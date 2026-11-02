package com.gsr.scm.workflow.definition;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Minimal, engine-neutral view of a single executable BPMN process. */
public record BpmnModel(String processId, String processName, boolean executable, Map<String, Node> nodes,
                        Map<String, Flow> flows) {

    public enum Kind { START_EVENT, END_EVENT, SERVICE_TASK, USER_TASK, EXCLUSIVE_GATEWAY, UNSUPPORTED }

    /**
     * @param tagName          BPMN element local name (useful for messages about unsupported elements)
     * @param jobType          zeebe:taskDefinition type (service tasks)
     * @param candidateGroups  zeebe:assignmentDefinition candidateGroups (user tasks)
     * @param camundaUserTask  true when the user task is a Camunda-managed user task (zeebe:userTask), which this
     *                         application cannot drive because it uses the job-worker implementation
     * @param hasEventDefinition true for start/end events that are not plain "none" events
     */
    public record Node(String id, String name, Kind kind, String tagName, String jobType, Integer retries,
                       List<String> candidateGroups, String dueDate, String defaultFlowId, boolean camundaUserTask,
                       boolean hasEventDefinition, List<String> incoming, List<String> outgoing) {

        public String displayName() {
            return name == null || name.isBlank() ? id : name;
        }
    }

    public record Flow(String id, String name, String sourceRef, String targetRef, String condition) {
    }

    public Optional<Node> node(String id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public List<Node> nodesOfKind(Kind kind) {
        return nodes.values().stream().filter(n -> n.kind() == kind).toList();
    }

    public String displayName(String elementId) {
        return node(elementId).map(Node::displayName).orElse(elementId);
    }
}
