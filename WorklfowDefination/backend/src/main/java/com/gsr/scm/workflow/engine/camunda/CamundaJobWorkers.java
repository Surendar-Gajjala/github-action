package com.gsr.scm.workflow.engine.camunda;

import com.gsr.scm.config.WorkflowProperties;
import com.gsr.scm.workflow.engine.JobContext;
import com.gsr.scm.workflow.engine.WorkflowJobDispatcher;
import com.gsr.scm.workflow.worker.WorkflowWorker;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobWorker;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Opens Camunda job workers once the application is ready:
 * <ul>
 *   <li>one per {@link WorkflowWorker} job type (service tasks) - complete on success, fail with decremented
 *       retries and backoff on error; zero retries makes Camunda raise an incident;</li>
 *   <li>one for {@code io.camunda.zeebe:userTask} (job-worker based user tasks) - registers an inbox task and leaves
 *       the job activated (long timeout) until a user decides. Re-activation after a timeout is idempotent because the
 *       job key is stable.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "scm.workflow.engine", havingValue = "camunda")
public class CamundaJobWorkers {

    private static final Logger log = LoggerFactory.getLogger(CamundaJobWorkers.class);

    private final CamundaClient client;
    private final WorkflowJobDispatcher dispatcher;
    private final WorkflowProperties.Camunda cfg;
    private final List<JobWorker> opened = new ArrayList<>();

    public CamundaJobWorkers(CamundaClient client, WorkflowJobDispatcher dispatcher, WorkflowProperties properties) {
        this.client = client;
        this.dispatcher = dispatcher;
        this.cfg = properties.camunda();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void open() {
        for (WorkflowWorker worker : dispatcher.workers()) {
            opened.add(client.newWorker()
                    .jobType(worker.jobType())
                    .handler(this::handleServiceJob)
                    .name("scm-" + worker.jobType())
                    .timeout(cfg.serviceJobTimeout())
                    .maxJobsActive(8)
                    .open());
            log.info("Camunda job worker opened for type '{}'", worker.jobType());
        }
        opened.add(client.newWorker()
                .jobType(JobContext.USER_TASK_JOB_TYPE)
                .handler(this::handleUserTaskJob)
                .name("scm-approval-inbox")
                .timeout(cfg.userTaskJobTimeout())
                .maxJobsActive(64)
                .open());
        log.info("Camunda job worker opened for job-worker user tasks ({})", JobContext.USER_TASK_JOB_TYPE);
    }

    @PreDestroy
    void close() {
        opened.forEach(JobWorker::close);
    }

    private void handleServiceJob(JobClient jobClient, ActivatedJob job) {
        JobContext ctx = toContext(job);
        try {
            Map<String, Object> result = dispatcher.executeServiceJob(ctx);
            jobClient.newCompleteCommand(job.getKey()).variables(result == null ? Map.of() : result).send().join();
        } catch (Exception e) {
            fail(jobClient, job, ctx, e);
        }
    }

    private void handleUserTaskJob(JobClient jobClient, ActivatedJob job) {
        JobContext ctx = toContext(job);
        try {
            dispatcher.onUserTaskActivated(ctx);
            // Intentionally not completed: the job is completed when a user approves/rejects the task.
        } catch (Exception e) {
            fail(jobClient, job, ctx, e);
        }
    }

    private void fail(JobClient jobClient, ActivatedJob job, JobContext ctx, Exception e) {
        int retriesLeft = Math.max(job.getRetries() - 1, 0);
        String message = WorkflowJobDispatcher.rootMessage(e);
        try {
            jobClient.newFailCommand(job.getKey())
                    .retries(retriesLeft)
                    .retryBackoff(retriesLeft > 0 ? cfg.retryBackoff() : Duration.ZERO)
                    .errorMessage(message)
                    .send().join();
        } catch (RuntimeException failError) {
            log.error("Could not report failure of job {} to Camunda", job.getKey(), failError);
        }
        dispatcher.onJobFailed(ctx, e, retriesLeft);
    }

    private static JobContext toContext(ActivatedJob job) {
        return new JobContext(job.getKey(), job.getType(), job.getProcessInstanceKey(), job.getElementId(),
                job.getRetries(), job.getVariablesAsMap(), job.getCustomHeaders());
    }
}
