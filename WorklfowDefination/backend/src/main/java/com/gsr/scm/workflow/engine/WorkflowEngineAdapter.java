package com.gsr.scm.workflow.engine;

import java.util.Map;

/**
 * Integration boundary to the workflow engine. Two implementations exist:
 * <ul>
 *   <li>{@code CamundaWorkflowAdapter} - real Camunda 8 (Zeebe) via the Camunda Java client.</li>
 *   <li>{@code DevWorkflowAdapter} - an in-process interpreter for a BPMN subset, for local development and tests
 *       only. It is NOT Camunda and is reported as such by {@link #info()}.</li>
 * </ul>
 * Business code never talks to the engine directly; it goes through this interface.
 */
public interface WorkflowEngineAdapter {

    EngineInfo info();

    /** Deploys a BPMN resource. The BPMN process id must equal {@code processId}. */
    DeploymentResult deploy(String processId, String bpmnXml);

    /** Starts an instance of a specific deployed version; returns the engine's process instance key. */
    long startInstance(long processDefinitionKey, Map<String, Object> variables);

    /** Completes an activated user-task job, merging the given variables into the process scope. */
    void completeUserTask(long jobKey, Map<String, Object> variables);

    /** Re-enables a failed job / resolves its incident so the engine retries it. */
    void retry(long engineInstanceKey, Long failedJobKey);

    record DeploymentResult(long processDefinitionKey, int version) {
    }

    record EngineInfo(String engine, String label, boolean realCamunda, boolean connected, String details) {
    }
}
