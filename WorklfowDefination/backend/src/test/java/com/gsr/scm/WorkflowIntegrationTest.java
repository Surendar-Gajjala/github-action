package com.gsr.scm;

import com.gsr.scm.audit.WorkflowEvent;
import com.gsr.scm.audit.WorkflowEventType;
import com.gsr.scm.common.Exceptions.BusinessRuleException;
import com.gsr.scm.common.Exceptions.ConflictException;
import com.gsr.scm.common.Exceptions.ForbiddenException;
import com.gsr.scm.item.ChangeRequest;
import com.gsr.scm.item.ItemDtos.ItemDetail;
import com.gsr.scm.item.ItemDtos.PartRef;
import com.gsr.scm.item.ItemDtos.PartSnapshot;
import com.gsr.scm.item.LifecycleStatus;
import com.gsr.scm.workflow.definition.DefinitionDtos.SaveDraftRequest;
import com.gsr.scm.workflow.definition.DefinitionDtos.DefinitionDetail;
import com.gsr.scm.workflow.instance.InstanceDtos.InstanceSummary;
import com.gsr.scm.workflow.instance.WorkflowInstance;
import com.gsr.scm.workflow.task.ApprovalTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowIntegrationTest extends IntegrationTestBase {

    @BeforeEach
    void publish() {
        ensurePublished("ITEM_LIFECYCLE_APPROVAL");
        ensurePublished("ITEM_PART_CHANGE_APPROVAL");
    }

    @Test
    void endToEndApprovalChangeAndReapproval() {
        // 1-3: create a draft with three parts
        ItemDetail item = createItem("PART-101", "PART-102", "PART-103");

        // 5-6: submit -> workflow instance started, item in review
        InstanceSummary instance = workflow.submitItem(item.id(), null, null, "author");
        assertThat(instance.definitionKey()).isEqualTo("ITEM_LIFECYCLE_APPROVAL");
        assertThat(instance.businessKey()).isEqualTo("ITEM-" + item.id() + "-CR-" + item.currentChangeRequest().id());
        assertThat(instance.managerApprovalRequired()).isTrue(); // 3 part changes > threshold 2
        assertThat(items.get(item.id()).lifecycleStatus()).isEqualTo(LifecycleStatus.IN_REVIEW);

        // 7-9: validation, engineering approval, manager approval
        approve(item.id(), "ENGINEERING", "engineer");
        assertThat(items.get(item.id()).lifecycleStatus()).isEqualTo(LifecycleStatus.IN_REVIEW);
        approve(item.id(), "MANAGER", "manager");

        // 10-12: released
        ItemDetail approved = items.get(item.id());
        assertThat(approved.lifecycleStatus()).isEqualTo(LifecycleStatus.APPROVED);
        assertThat(approved.approvedRevision()).isEqualTo("A");
        assertThat(approved.parts()).hasSize(3);
        assertThat(instanceRepository.findById(instance.id()).orElseThrow().getStatus())
                .isEqualTo(WorkflowInstance.Status.COMPLETED);

        // 13: history has every step
        List<WorkflowEventType> actions = items.history(item.id()).stream().map(WorkflowEvent::getAction).toList();
        assertThat(actions).contains(WorkflowEventType.ITEM_CREATED, WorkflowEventType.ITEM_SUBMITTED,
                WorkflowEventType.WORKFLOW_STARTED, WorkflowEventType.VALIDATION_COMPLETED,
                WorkflowEventType.APPROVAL_REQUESTED, WorkflowEventType.APPROVAL_COMPLETED,
                WorkflowEventType.REVISION_RELEASED);

        // 14-15: remove PART-103 -> change pending, approved revision kept
        ItemDetail changed = items.updateParts(item.id(), ids("PART-101", "PART-102"), approved.version(), "author");
        assertThat(changed.lifecycleStatus()).isEqualTo(LifecycleStatus.CHANGE_PENDING);
        assertThat(changed.lastApprovedRevision().revision()).isEqualTo("A");

        // 16-18: change workflow (part removed -> manager required), approved only after all approvals
        InstanceSummary second = workflow.submitItem(item.id(), null, null, "author");
        assertThat(second.definitionKey()).isEqualTo("ITEM_PART_CHANGE_APPROVAL");
        assertThat(second.managerApprovalRequired()).isTrue();
        approve(item.id(), "ENGINEERING", "engineer");
        assertThat(items.get(item.id()).lifecycleStatus()).isEqualTo(LifecycleStatus.IN_REVIEW);
        approve(item.id(), "MANAGER", "manager");

        ItemDetail released = items.get(item.id());
        assertThat(released.lifecycleStatus()).isEqualTo(LifecycleStatus.APPROVED);
        assertThat(released.approvedRevision()).isEqualTo("B");
        assertThat(released.parts()).extracting(PartRef::partNumber).containsExactly("PART-101", "PART-102");
        assertThat(items.approvedRevisions(item.id())).hasSize(2)
                .anySatisfy(r -> {
                    assertThat(r.revision()).isEqualTo("A");
                    assertThat(r.parts()).extracting(PartSnapshot::partNumber).contains("PART-103");
                });
    }

    @Test
    void engineeringOnlyWhenManagerNotRequired() {
        ItemDetail item = createItem("PART-101");
        InstanceSummary instance = workflow.submitItem(item.id(), null, null, "author");
        assertThat(instance.managerApprovalRequired()).isFalse();
        approve(item.id(), "ENGINEERING", "engineer");
        assertThat(items.get(item.id()).lifecycleStatus()).isEqualTo(LifecycleStatus.APPROVED);
        assertThat(openTask(item.id(), "MANAGER")).isEmpty();
    }

    @Test
    void rejectionRecordsReasonAndReturnsItemForRework() {
        ItemDetail item = createItem("PART-101");
        workflow.submitItem(item.id(), null, null, "author");
        reject(item.id(), "ENGINEERING", "engineer", "Wrong bearing size");

        ItemDetail after = items.get(item.id());
        assertThat(after.lifecycleStatus()).isEqualTo(LifecycleStatus.DRAFT);
        assertThat(after.approvedRevision()).isNull();
        assertThat(after.currentChangeRequest().status()).isEqualTo(ChangeRequest.Status.OPEN);
        assertThat(after.currentChangeRequest().changeLog()).contains("Wrong bearing size");
        assertThat(items.history(item.id())).anySatisfy(e -> {
            assertThat(e.getAction()).isEqualTo(WorkflowEventType.REQUEST_REJECTED);
            assertThat(e.getSummary()).contains("Wrong bearing size");
            assertThat(e.getActor()).isEqualTo("engineer");
        });
        assertThat(instanceRepository.findByItemIdOrderByStartedAtDesc(item.id()).get(0).getStatus())
                .isEqualTo(WorkflowInstance.Status.REJECTED);

        // Rework and resubmit
        workflow.submitItem(item.id(), null, null, "author");
        approve(item.id(), "ENGINEERING", "engineer");
        assertThat(items.get(item.id()).lifecycleStatus()).isEqualTo(LifecycleStatus.APPROVED);
    }

    @Test
    void rejectionOfChangeKeepsPreviouslyApprovedRevision() {
        ItemDetail item = createItem("PART-102");
        workflow.submitItem(item.id(), null, null, "author");
        approve(item.id(), "ENGINEERING", "engineer");
        ItemDetail approved = items.get(item.id());
        items.updateParts(item.id(), ids("PART-102", "PART-103"), approved.version(), "author");
        workflow.submitItem(item.id(), null, null, "author");
        reject(item.id(), "ENGINEERING", "engineer", "Not needed");

        ItemDetail after = items.get(item.id());
        assertThat(after.lifecycleStatus()).isEqualTo(LifecycleStatus.CHANGE_PENDING);
        assertThat(after.approvedRevision()).isEqualTo("A");
        assertThat(after.lastApprovedRevision().parts()).extracting(PartSnapshot::partNumber).containsExactly("PART-102");
    }

    @Test
    void rejectionReasonIsMandatory() {
        ItemDetail item = createItem("PART-101");
        workflow.submitItem(item.id(), null, null, "author");
        ApprovalTask task = openTask(item.id(), "ENGINEERING").orElseThrow();
        assertThatThrownBy(() -> taskService.reject(task.getId(), " ", "engineer", Set.of("ENGINEERING")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(ApprovalTask.Status.OPEN);
    }

    @Test
    void onlyCandidateRoleCanCompleteTaskAndSubmitterCannotSelfApprove() {
        ItemDetail item = createItem("PART-101");
        workflow.submitItem(item.id(), null, null, "engineer"); // engineer is also the submitter
        ApprovalTask task = openTask(item.id(), "ENGINEERING").orElseThrow();

        assertThatThrownBy(() -> taskService.approve(task.getId(), null, "manager", Set.of("MANAGER")))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("requires role ENGINEERING");
        assertThatThrownBy(() -> taskService.approve(task.getId(), null, "engineer", Set.of("ENGINEERING")))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("Segregation of duties");
        taskService.approve(task.getId(), null, "engineer2", Set.of("ENGINEERING"));
        assertThatThrownBy(() -> taskService.approve(task.getId(), null, "engineer3", Set.of("ENGINEERING")))
                .isInstanceOf(ConflictException.class);
        awaitEngine();
    }

    @Test
    void duplicateSubmissionIsPrevented() {
        ItemDetail item = createItem("PART-101");
        workflow.submitItem(item.id(), null, null, "author");
        assertThatThrownBy(() -> workflow.submitItem(item.id(), null, null, "author"))
                .isInstanceOf(ConflictException.class);
        assertThat(instanceRepository.findByItemIdOrderByStartedAtDesc(item.id())).hasSize(1);
        awaitEngine();
    }

    @Test
    void concurrentSubmissionsStartExactlyOneWorkflow() throws Exception {
        ItemDetail item = createItem("PART-101");
        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    workflow.submitItem(item.id(), null, null, "author");
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        List<Boolean> outcomes = new ArrayList<>();
        for (Future<Boolean> f : futures) {
            outcomes.add(f.get());
        }
        pool.shutdown();
        assertThat(Collections.frequency(outcomes, true)).isEqualTo(1);
        assertThat(instanceRepository.findByItemIdOrderByStartedAtDesc(item.id())).hasSize(1);
        awaitEngine();
    }

    @Test
    void submittingApprovedItemWithoutChangesIsRejected() {
        ItemDetail item = createItem("PART-101");
        workflow.submitItem(item.id(), null, null, "author");
        approve(item.id(), "ENGINEERING", "engineer");
        assertThatThrownBy(() -> workflow.submitItem(item.id(), null, null, "author"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void publishingCreatesNewVersionAndRunningInstancesKeepTheirVersion() {
        String key = "ITEM_REVISION_RELEASE";
        ensurePublished(key);
        ItemDetail item = createItem("PART-105");
        InstanceSummary v1 = workflow.submitItem(item.id(), key, null, "author");
        awaitEngine();

        DefinitionDetail detail = definitions.get(key);
        definitions.saveDraft(key, new SaveDraftRequest(null, "updated", null, detail.current().bpmnXml(),
                detail.current().rules()), "admin");
        DefinitionDetail published = definitions.publish(key, "admin");

        assertThat(published.definition().publishedVersion()).isEqualTo(v1.definitionVersion() + 1);
        assertThat(published.versions()).anySatisfy(v -> assertThat(v.status().name()).isEqualTo("SUPERSEDED"));
        approve(item.id(), "MANAGER", "manager");
        assertThat(items.get(item.id()).lifecycleStatus()).isEqualTo(LifecycleStatus.APPROVED);
        assertThat(instanceRepository.findById(v1.id()).orElseThrow().getDefinitionVersion())
                .isEqualTo(v1.definitionVersion());
    }

    @Test
    void invalidDraftCannotBePublished() {
        String key = "BROKEN_FLOW_" + System.nanoTime() % 100000;
        definitions.create(new com.gsr.scm.workflow.definition.DefinitionDtos.CreateDefinitionRequest(key, "Broken",
                null, com.gsr.scm.workflow.definition.WorkflowDefinition.Type.ITEM_LIFECYCLE,
                "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"/>", null), "admin");
        assertThatThrownBy(() -> definitions.publish(key, "admin"))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getDetails()).isNotEmpty());
    }
}
