package com.gsr.scm.workflow.engine;

import com.gsr.scm.workflow.instance.WorkflowInstanceService;
import com.gsr.scm.workflow.task.WorkflowTaskService;
import com.gsr.scm.workflow.worker.WorkflowWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Engine-neutral routing of activated jobs to the application's workers and task inbox. Both engine adapters call
 * into this class, so the business behaviour is identical whichever engine is running.
 */
@Component
public class WorkflowJobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WorkflowJobDispatcher.class);

    private final Map<String, WorkflowWorker> workers;
    private final WorkflowTaskService taskService;
    private final WorkflowInstanceService instanceService;

    public WorkflowJobDispatcher(List<WorkflowWorker> workers, WorkflowTaskService taskService,
                                 WorkflowInstanceService instanceService) {
        this.workers = workers.stream().collect(Collectors.toMap(WorkflowWorker::jobType, Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException("Duplicate worker for job type " + a.jobType());
                }, TreeMap::new));
        this.taskService = taskService;
        this.instanceService = instanceService;
    }

    public Collection<WorkflowWorker> workers() {
        return workers.values();
    }

    public Map<String, Object> executeServiceJob(JobContext job) {
        WorkflowWorker worker = workers.get(job.jobType());
        if (worker == null) {
            throw new IllegalStateException("No worker registered for job type '" + job.jobType() + "'");
        }
        log.debug("Executing job {} ({}) for engine instance {}", job.jobKey(), job.jobType(), job.engineInstanceKey());
        return worker.execute(job);
    }

    public void onUserTaskActivated(JobContext job) {
        taskService.registerTask(job);
    }

    public void onJobFailed(JobContext job, Throwable error, int retriesLeft) {
        String message = rootMessage(error);
        if (retriesLeft > 0) {
            log.warn("Job {} ({}) failed, {} retries left: {}", job.jobKey(), job.jobType(), retriesLeft, message);
        } else {
            log.error("Job {} ({}) failed with no retries left - incident: {}", job.jobKey(), job.jobType(), message);
            instanceService.markIncident(job, message);
        }
    }

    public void onProcessEnded(long engineInstanceKey) {
        instanceService.markEnded(engineInstanceKey);
    }

    public static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        return root == t ? msg : t.getMessage() + " (cause: " + msg + ")";
    }
}
