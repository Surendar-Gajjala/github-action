package com.gsr.scm.config;

import com.gsr.scm.item.ItemDtos.CreateItemRequest;
import com.gsr.scm.item.ItemService;
import com.gsr.scm.item.LifecycleStatus;
import com.gsr.scm.lifecycle.LifecycleTransition;
import com.gsr.scm.lifecycle.LifecycleTransitionRepository;
import com.gsr.scm.part.Part;
import com.gsr.scm.part.PartRepository;
import com.gsr.scm.workflow.definition.ApprovalRules;
import com.gsr.scm.workflow.definition.ApprovalRules.ManagerApproval;
import com.gsr.scm.workflow.definition.DefinitionDtos.CreateDefinitionRequest;
import com.gsr.scm.workflow.definition.WorkflowDefinition;
import com.gsr.scm.workflow.definition.WorkflowDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.gsr.scm.item.LifecycleStatus.APPROVED;
import static com.gsr.scm.item.LifecycleStatus.CHANGE_PENDING;
import static com.gsr.scm.item.LifecycleStatus.DRAFT;
import static com.gsr.scm.item.LifecycleStatus.IN_REVIEW;
import static com.gsr.scm.item.LifecycleStatus.REJECTED;

/**
 * Seeds reference data on startup (in-memory database). Workflow definitions are seeded as unpublished DRAFTs:
 * publishing them in the Workflow tab is what deploys them to the engine. No item is seeded as APPROVED, because
 * approval is only reachable through the workflow.
 */
@Component
@ConditionalOnProperty(name = "scm.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String SEED_USER = "seed";

    private final LifecycleTransitionRepository transitions;
    private final PartRepository parts;
    private final ItemService items;
    private final WorkflowDefinitionService definitions;

    public DataSeeder(LifecycleTransitionRepository transitions, PartRepository parts, ItemService items,
                      WorkflowDefinitionService definitions) {
        this.transitions = transitions;
        this.parts = parts;
        this.items = items;
        this.definitions = definitions;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (transitions.count() > 0) {
            return;
        }
        seedTransitions();
        seedParts();
        seedDefinitions();
        items.create(new CreateItemRequest("ITEM-0900", "Gearbox Housing Assembly",
                "Cast aluminium housing with bearing set", parts.findAll().stream()
                .filter(p -> List.of("PART-104", "PART-105").contains(p.getPartNumber())).map(Part::getId).toList()),
                SEED_USER);
        log.info("Seed data created: lifecycle transitions, {} parts, 3 workflow definitions (drafts), 1 draft item",
                parts.count());
    }

    private void seedTransitions() {
        transitions.saveAll(List.of(
                t(DRAFT, IN_REVIEW, "Submit a new item for approval"),
                t(IN_REVIEW, APPROVED, "Release after all required approvals (release-revision worker only)"),
                t(IN_REVIEW, REJECTED, "Approval rejected or validation failed"),
                t(REJECTED, DRAFT, "Rework of an item that was never approved"),
                t(REJECTED, CHANGE_PENDING, "Rework of a change to a previously approved item"),
                t(APPROVED, CHANGE_PENDING, "Change started on an approved item"),
                t(CHANGE_PENDING, IN_REVIEW, "Submit a change for approval")));
    }

    private static LifecycleTransition t(LifecycleStatus from, LifecycleStatus to, String description) {
        return new LifecycleTransition(from, to, description, true);
    }

    private void seedParts() {
        part("PART-101", "Shaft", "Hardened steel drive shaft, 25 mm", "MECHANICAL");
        part("PART-102", "Bearing", "Deep groove ball bearing 6205", "MECHANICAL");
        part("PART-103", "Housing", "Pump housing, cast iron", "STRUCTURAL");
        part("PART-104", "Gear Set", "Helical gear set, ratio 3:1", "MECHANICAL");
        part("PART-105", "Seal Kit", "Nitrile O-ring and lip seal kit", "CONSUMABLE");
        part("PART-106", "Pressure Sensor", "0-250 bar pressure transducer", "ELECTRICAL");
        part("PART-107", "Impeller", "Stainless steel impeller, 6 vanes", "MECHANICAL");
        part("PART-108", "Motor Mount", "Steel motor mounting bracket", "STRUCTURAL");
    }

    private void part(String number, String name, String description, String type) {
        Part p = new Part();
        p.setPartNumber(number);
        p.setName(name);
        p.setDescription(description);
        p.setPartType(type);
        parts.save(p);
    }

    private void seedDefinitions() throws IOException {
        definitions.create(new CreateDefinitionRequest("ITEM_LIFECYCLE_APPROVAL", "Item Lifecycle Approval",
                "Validation, engineering approval and - for larger bill-of-material changes - manager approval "
                        + "before a revision is released.",
                WorkflowDefinition.Type.ITEM_LIFECYCLE, bpmn("item-lifecycle-approval.bpmn"),
                new ApprovalRules(ManagerApproval.WHEN_PART_CHANGES_EXCEED, 2, List.of(Roles.ENGINEERING), 5, true)),
                SEED_USER);
        definitions.create(new CreateDefinitionRequest("ITEM_PART_CHANGE_APPROVAL", "Item Part Change Approval",
                "Change to an approved item. Manager approval is required whenever parts are removed.",
                WorkflowDefinition.Type.ITEM_PART_CHANGE, bpmn("item-part-change-approval.bpmn"),
                new ApprovalRules(ManagerApproval.WHEN_PARTS_REMOVED, 0, List.of(Roles.ENGINEERING), 3, true)),
                SEED_USER);
        definitions.create(new CreateDefinitionRequest("ITEM_REVISION_RELEASE", "Item Revision Release",
                "Manager-only release of a revision (e.g. documentation-only changes).",
                WorkflowDefinition.Type.REVISION_RELEASE, bpmn("item-revision-release.bpmn"),
                new ApprovalRules(ManagerApproval.ALWAYS, 0, List.of(Roles.MANAGER), 2, true)),
                SEED_USER);
    }

    private static String bpmn(String file) throws IOException {
        return new ClassPathResource("bpmn/" + file).getContentAsString(StandardCharsets.UTF_8);
    }
}
