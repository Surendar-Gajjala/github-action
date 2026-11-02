package com.gsr.scm.workflow.worker;

import com.gsr.scm.workflow.engine.JobContext;

import java.util.Map;

/**
 * A service-task worker. Implementations hold the business logic and are shared by both engine adapters.
 * Workers must be idempotent: engines deliver jobs at-least-once (timeouts, retries, restarts).
 */
public interface WorkflowWorker {

    /** The zeebe:taskDefinition type this worker handles. */
    String jobType();

    /** Short description shown in the designer's job-type picker. */
    String description();

    /**
     * Executes the job and returns variables to merge into the process. Throw
     * {@link com.gsr.scm.workflow.engine.RetryableWorkflowException} (or any exception) to fail the job; the
     * engine retries until the BPMN retries are exhausted, then raises an incident.
     */
    Map<String, Object> execute(JobContext job);
}
