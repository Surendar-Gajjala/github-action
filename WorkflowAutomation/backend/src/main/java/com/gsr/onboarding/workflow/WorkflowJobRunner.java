package com.gsr.onboarding.workflow;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.gsr.onboarding.config.WorkflowExecutorConfig;
import com.gsr.onboarding.domain.entity.WorkflowStep;
import com.gsr.onboarding.domain.enums.StepName;

/**
 * Durable, database-backed job runner. Workflow state lives in {@code workflow_steps}; this runner
 * claims pending steps (compare-and-set), executes them outside a transaction and records the outcome.
 * <p>
 * New work is started immediately after the creating transaction commits; a scheduled poller also
 * sweeps for pending steps, so work survives restarts and rejected executor tasks.
 */
@Component
public class WorkflowJobRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkflowJobRunner.class);
    private static final int MAX_STEPS_PER_DRAIN = 20;
    private static final int POLL_BATCH = 50;
    private static final int COMPLETE_RETRIES = 3;

    private final WorkflowStepTxService tx;
    private final TaskExecutor executor;
    private final Map<StepName, StepHandler> handlers = new EnumMap<>(StepName.class);
    private final AtomicBoolean recovered = new AtomicBoolean(false);

    public WorkflowJobRunner(WorkflowStepTxService tx,
                             @Qualifier(WorkflowExecutorConfig.WORKFLOW_EXECUTOR) TaskExecutor executor,
                             List<StepHandler> stepHandlers) {
        this.tx = tx;
        this.executor = executor;
        stepHandlers.forEach(handler -> handlers.put(handler.step(), handler));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkflowScheduled(WorkflowScheduledEvent event) {
        executor.execute(() -> drain(event.workflowId()));
    }

    @Scheduled(fixedDelayString = "${app.workflow.poll-interval-ms:2000}",
               initialDelayString = "${app.workflow.poll-initial-delay-ms:1000}")
    public void poll() {
        ensureRecovered();
        for (Long workflowId : tx.findWorkflowIdsWithPendingSteps(POLL_BATCH)) {
            drain(workflowId);
        }
    }

    /** Runs runnable steps of one workflow in order until it finishes, fails or waits for approval. */
    public void drain(Long workflowId) {
        ensureRecovered();
        MDC.put("workflowId", String.valueOf(workflowId));
        try {
            for (int i = 0; i < MAX_STEPS_PER_DRAIN; i++) {
                Optional<WorkflowStep> next = tx.findNextRunnableStep(workflowId);
                if (next.isEmpty()) {
                    return;
                }
                WorkflowStep step = next.get();
                if (!claim(step.getId())) {
                    return; // another worker owns this step
                }
                StepOutcome outcome = execute(workflowId, step.getStepName());
                complete(step.getId(), outcome);
            }
        } finally {
            MDC.remove("workflowId");
        }
    }

    private boolean claim(Long stepId) {
        try {
            return tx.claim(stepId);
        } catch (ObjectOptimisticLockingFailureException e) {
            return false; // concurrent workflow update; the step stays PENDING and will be picked up again
        }
    }

    private StepOutcome execute(Long workflowId, StepName stepName) {
        StepHandler handler = handlers.get(stepName);
        if (handler == null) {
            return StepOutcome.failed("No handler is registered for step " + stepName + ".");
        }
        try {
            return handler.execute(workflowId);
        } catch (RuntimeException e) {
            log.error("Step {} failed unexpectedly", stepName, e);
            return StepOutcome.failed("Unexpected error while running this step (" + e.getClass().getSimpleName() + ").");
        }
    }

    private void complete(Long stepId, StepOutcome outcome) {
        for (int attempt = 1; ; attempt++) {
            try {
                tx.complete(stepId, outcome);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt >= COMPLETE_RETRIES) {
                    // Left IN_PROGRESS; recovered on the next start-up.
                    log.error("Could not record outcome of step {} after {} attempts", stepId, attempt, e);
                    return;
                }
            }
        }
    }

    private void ensureRecovered() {
        if (recovered.get()) {
            return;
        }
        synchronized (recovered) {
            if (!recovered.get()) {
                int count = tx.recoverInterruptedSteps();
                if (count > 0) {
                    log.warn("Recovered {} workflow step(s) interrupted by a previous shutdown", count);
                }
                recovered.set(true);
            }
        }
    }
}
