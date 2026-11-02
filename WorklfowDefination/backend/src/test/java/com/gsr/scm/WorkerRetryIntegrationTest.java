package com.gsr.scm;

import com.gsr.scm.audit.WorkflowEventType;
import com.gsr.scm.item.ItemDtos.ItemDetail;
import com.gsr.scm.item.LifecycleStatus;
import com.gsr.scm.workflow.engine.JobContext;
import com.gsr.scm.workflow.instance.InstanceDtos.InstanceSummary;
import com.gsr.scm.workflow.instance.WorkflowInstance;
import com.gsr.scm.workflow.instance.WorkflowInstanceService;
import com.gsr.scm.workflow.worker.ValidateItemWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

/** Failed worker behaviour: retries, incident after exhausting retries, and manual retry. */
class WorkerRetryIntegrationTest extends IntegrationTestBase {

    @MockitoSpyBean
    ValidateItemWorker validateItemWorker;

    @Autowired
    WorkflowInstanceService instances;

    @BeforeEach
    void setUp() {
        reset(validateItemWorker);
        ensurePublished("ITEM_LIFECYCLE_APPROVAL");
    }

    @Test
    void transientFailuresAreRetriedAndProcessContinues() {
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            if (calls.incrementAndGet() <= 2) {
                throw new IllegalStateException("database briefly unavailable");
            }
            return inv.callRealMethod();
        }).when(validateItemWorker).execute(any(JobContext.class));

        ItemDetail item = createItem("PART-101");
        InstanceSummary instance = workflow.submitItem(item.id(), null, null, "author");
        awaitEngine();

        assertThat(calls.get()).isEqualTo(3); // BPMN retries="3": two failures, third attempt succeeds
        assertThat(openTask(item.id(), "ENGINEERING")).isPresent();
        assertThat(instanceRepository.findById(instance.id()).orElseThrow().getStatus())
                .isEqualTo(WorkflowInstance.Status.RUNNING);
    }

    @Test
    void exhaustedRetriesRaiseIncidentWhichCanBeRetried() {
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            if (calls.incrementAndGet() <= 3) {
                throw new IllegalStateException("validation service down");
            }
            return inv.callRealMethod();
        }).when(validateItemWorker).execute(any(JobContext.class));

        ItemDetail item = createItem("PART-101");
        InstanceSummary instance = workflow.submitItem(item.id(), null, null, "author");
        awaitEngine();

        WorkflowInstance failed = instanceRepository.findById(instance.id()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(WorkflowInstance.Status.INCIDENT);
        assertThat(failed.getIncidentMessage()).contains("validation service down");
        assertThat(items.get(item.id()).lifecycleStatus()).isEqualTo(LifecycleStatus.IN_REVIEW);
        assertThat(items.history(item.id())).anyMatch(e -> e.getAction() == WorkflowEventType.WORKFLOW_FAILED);

        instances.retry(instance.id(), "admin");
        awaitEngine();

        assertThat(calls.get()).isEqualTo(4);
        assertThat(instanceRepository.findById(instance.id()).orElseThrow().getStatus())
                .isEqualTo(WorkflowInstance.Status.RUNNING);
        approve(item.id(), "ENGINEERING", "engineer");
        assertThat(items.get(item.id()).lifecycleStatus()).isEqualTo(LifecycleStatus.APPROVED);
    }
}
