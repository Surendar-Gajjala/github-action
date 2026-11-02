package com.gsr.scm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsr.scm.item.ItemDtos.CreateItemRequest;
import com.gsr.scm.item.ItemDtos.ItemDetail;
import com.gsr.scm.item.ItemService;
import com.gsr.scm.part.Part;
import com.gsr.scm.part.PartRepository;
import com.gsr.scm.workflow.WorkflowService;
import com.gsr.scm.workflow.definition.WorkflowDefinitionService;
import com.gsr.scm.workflow.engine.dev.DevWorkflowAdapter;
import com.gsr.scm.workflow.instance.WorkflowInstanceRepository;
import com.gsr.scm.workflow.task.ApprovalTask;
import com.gsr.scm.workflow.task.ApprovalTaskRepository;
import com.gsr.scm.workflow.task.WorkflowTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full application context on the H2 database with the development workflow adapter (not Camunda).
 * Seed data provides lifecycle transitions, parts PART-101..108 and draft workflow definitions.
 */
@SpringBootTest(properties = {"scm.workflow.engine=dev", "scm.workflow.dev.retry-backoff-ms=0"})
@AutoConfigureMockMvc
abstract class IntegrationTestBase {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected ItemService items;
    @Autowired
    protected PartRepository parts;
    @Autowired
    protected WorkflowService workflow;
    @Autowired
    protected WorkflowDefinitionService definitions;
    @Autowired
    protected WorkflowTaskService taskService;
    @Autowired
    protected ApprovalTaskRepository taskRepository;
    @Autowired
    protected WorkflowInstanceRepository instanceRepository;
    @Autowired
    protected DevWorkflowAdapter engine;

    protected static String unique(String prefix) {
        return prefix + "-" + System.nanoTime() % 1_000_000 + "-" + SEQ.incrementAndGet();
    }

    protected Long partId(String number) {
        return parts.findAll().stream().filter(p -> p.getPartNumber().equals(number)).map(Part::getId)
                .findFirst().orElseThrow();
    }

    protected ItemDetail createItem(String... partNumbers) {
        return items.create(new CreateItemRequest(unique("ITEM"), "Hydraulic Pump Assembly", "test",
                Arrays.stream(partNumbers).map(this::partId).toList()), "author");
    }

    protected void ensurePublished(String key) {
        if (definitions.get(key).definition().publishedVersion() == null) {
            definitions.publish(key, "admin");
        }
    }

    protected void awaitEngine() {
        assertThat(engine.awaitIdle(Duration.ofSeconds(15))).as("dev engine idle").isTrue();
    }

    protected Optional<ApprovalTask> openTask(Long itemId, String group) {
        awaitEngine();
        return taskRepository.findAll().stream()
                .filter(t -> t.getItemId().equals(itemId) && t.getStatus() == ApprovalTask.Status.OPEN
                        && t.getCandidateGroup().equals(group))
                .findFirst();
    }

    protected void approve(Long itemId, String group, String user) {
        ApprovalTask task = openTask(itemId, group).orElseThrow(() -> new AssertionError("no open " + group + " task"));
        taskService.approve(task.getId(), "ok", user, Set.of(group));
        awaitEngine();
    }

    protected void reject(Long itemId, String group, String user, String reason) {
        ApprovalTask task = openTask(itemId, group).orElseThrow(() -> new AssertionError("no open " + group + " task"));
        taskService.reject(task.getId(), reason, user, Set.of(group));
        awaitEngine();
    }

    protected List<Long> ids(String... partNumbers) {
        return Arrays.stream(partNumbers).map(this::partId).toList();
    }
}
