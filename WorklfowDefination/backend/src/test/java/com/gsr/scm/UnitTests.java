package com.gsr.scm;

import com.gsr.scm.config.WorkflowProperties;
import com.gsr.scm.item.ItemDtos.ChangeDiff;
import com.gsr.scm.item.ItemDtos.PartSnapshot;
import com.gsr.scm.item.Revisions;
import com.gsr.scm.workflow.definition.ApprovalRules;
import com.gsr.scm.workflow.definition.ApprovalRules.ManagerApproval;
import com.gsr.scm.workflow.definition.BpmnValidator;
import com.gsr.scm.workflow.definition.WorkflowDefinitionService;
import com.gsr.scm.workflow.engine.dev.FeelLite;
import com.gsr.scm.workflow.worker.WorkflowWorker;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnitTests {

    @Nested
    class RevisionSequence {
        @Test
        void incrementsAlphabetically() {
            assertThat(Revisions.next(null)).isEqualTo("A");
            assertThat(Revisions.next("A")).isEqualTo("B");
            assertThat(Revisions.next("Z")).isEqualTo("AA");
            assertThat(Revisions.next("AZ")).isEqualTo("BA");
            assertThat(Revisions.next("ZZ")).isEqualTo("AAA");
        }
    }

    @Nested
    class FeelSubset {
        @Test
        void evaluatesComparisonsAndBooleans() {
            Map<String, Object> vars = Map.of("lastDecision", "APPROVED", "managerApprovalRequired", true,
                    "count", 3, "item", Map.of("revision", "B"));
            assertThat(FeelLite.test("= lastDecision = \"APPROVED\"", vars)).isTrue();
            assertThat(FeelLite.test("= lastDecision != \"APPROVED\"", vars)).isFalse();
            assertThat(FeelLite.test("= managerApprovalRequired = true", vars)).isTrue();
            assertThat(FeelLite.test("= managerApprovalRequired", vars)).isTrue();
            assertThat(FeelLite.test("= count > 2 and count <= 3", vars)).isTrue();
            assertThat(FeelLite.test("= count < 2 or item.revision = \"B\"", vars)).isTrue();
            assertThat(FeelLite.test("= not(count = 3)", vars)).isFalse();
            assertThat(FeelLite.test("= missing = null", vars)).isTrue();
        }

        @Test
        void rejectsUnsupportedSyntax() {
            assertThatThrownBy(() -> FeelLite.compile("= count + 1 > 2")).isInstanceOf(FeelLite.FeelException.class);
            assertThatThrownBy(() -> FeelLite.compile("= \"open")).isInstanceOf(FeelLite.FeelException.class);
        }
    }

    @Nested
    class ApprovalRuleEvaluation {
        private final PartSnapshot p = new PartSnapshot(1L, "P-1", "Part", "X", 1);

        private ChangeDiff diff(int added, int removed) {
            return new ChangeDiff(1L, "I", "Item", "A", "B", List.of(), java.util.Collections.nCopies(added, p),
                    java.util.Collections.nCopies(removed, p), List.of(), null);
        }

        @Test
        void managerApprovalPolicies() {
            assertThat(rules(ManagerApproval.ALWAYS, 0).managerApprovalRequired(diff(0, 0), false)).isTrue();
            assertThat(rules(ManagerApproval.NEVER, 0).managerApprovalRequired(diff(5, 5), true)).isFalse();
            assertThat(rules(ManagerApproval.WHEN_PARTS_REMOVED, 0).managerApprovalRequired(diff(3, 0), true)).isFalse();
            assertThat(rules(ManagerApproval.WHEN_PARTS_REMOVED, 0).managerApprovalRequired(diff(0, 1), true)).isTrue();
            assertThat(rules(ManagerApproval.WHEN_PART_CHANGES_EXCEED, 2).managerApprovalRequired(diff(2, 0), false)).isFalse();
            assertThat(rules(ManagerApproval.WHEN_PART_CHANGES_EXCEED, 2).managerApprovalRequired(diff(2, 1), false)).isTrue();
            assertThat(rules(ManagerApproval.WHEN_PREVIOUSLY_APPROVED, 0).managerApprovalRequired(diff(0, 0), true)).isTrue();
        }

        private ApprovalRules rules(ManagerApproval m, int threshold) {
            return new ApprovalRules(m, threshold, List.of("ENGINEERING"), null, true);
        }
    }

    @Nested
    class BpmnDefinitionValidation {

        private final BpmnValidator validator = validator("dev");

        @SuppressWarnings("unchecked")
        private static BpmnValidator validator(String engine) {
            ObjectProvider<WorkflowWorker> provider = mock(ObjectProvider.class);
            List<WorkflowWorker> workers = Stream.of("validate-item", "validate-part-relationships",
                    "release-revision", "record-rejection", "return-to-editable").map(type -> {
                WorkflowWorker w = mock(WorkflowWorker.class);
                when(w.jobType()).thenReturn(type);
                return w;
            }).toList();
            when(provider.orderedStream()).thenAnswer(inv -> workers.stream());
            return new BpmnValidator(provider, new WorkflowProperties(engine, null, null));
        }

        private static String resource(String name) throws IOException {
            return new ClassPathResource("bpmn/" + name).getContentAsString(StandardCharsets.UTF_8);
        }

        @Test
        void seededDefinitionsAreValid() throws IOException {
            assertThat(validator.validate(resource("item-lifecycle-approval.bpmn"), "ITEM_LIFECYCLE_APPROVAL").errors()).isEmpty();
            assertThat(validator.validate(resource("item-part-change-approval.bpmn"), "ITEM_PART_CHANGE_APPROVAL").errors()).isEmpty();
            assertThat(validator.validate(resource("item-revision-release.bpmn"), "ITEM_REVISION_RELEASE").errors()).isEmpty();
            assertThat(validator.validate(WorkflowDefinitionService.template("NEW_FLOW", "New"), "NEW_FLOW").valid()).isTrue();
        }

        @Test
        void rejectsWrongProcessIdUnknownJobTypeAndMissingConditions() throws IOException {
            String xml = resource("item-revision-release.bpmn")
                    .replace("type=\"release-revision\"", "type=\"release-everything\"")
                    .replace("candidateGroups=\"MANAGER\"", "candidateGroups=\"JANITOR\"")
                    .replaceFirst("<bpmn:conditionExpression[^>]*>[^<]*</bpmn:conditionExpression>", "");
            BpmnValidator.Result result = validator.validate(xml, "SOMETHING_ELSE");
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("Process id must equal"))
                    .anyMatch(e -> e.contains("release-everything"))
                    .anyMatch(e -> e.contains("JANITOR"))
                    .anyMatch(e -> e.contains("without a condition"));
        }

        @Test
        void rejectsCamundaUserTasksAndMalformedXml() throws IOException {
            String xml = resource("item-revision-release.bpmn")
                    .replace("<zeebe:assignmentDefinition candidateGroups=\"MANAGER\" />",
                            "<zeebe:userTask /><zeebe:assignmentDefinition candidateGroups=\"MANAGER\" />");
            assertThat(validator.validate(xml, "ITEM_REVISION_RELEASE").errors())
                    .anyMatch(e -> e.contains("zeebe:userTask"));
            assertThat(validator.validate("<not-bpmn", "X").errors()).anyMatch(e -> e.contains("not well-formed"));
        }

        @Test
        void rejectsDoctypeToPreventXxe() {
            String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                    + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">&e;</bpmn:definitions>";
            assertThat(validator.validate(xxe, "X").valid()).isFalse();
        }

        @Test
        void parallelGatewayIsAnErrorForDevAdapterButOnlyAWarningForCamunda() throws IOException {
            String xml = resource("item-revision-release.bpmn")
                    .replace("<bpmn:exclusiveGateway id=\"gwValid\"", "<bpmn:parallelGateway id=\"gwValid\"")
                    .replace("</bpmn:exclusiveGateway>\n    <bpmn:userTask", "</bpmn:parallelGateway>\n    <bpmn:userTask");
            assertThat(validator.validate(xml, "ITEM_REVISION_RELEASE").errors())
                    .anyMatch(e -> e.contains("parallelGateway"));
            BpmnValidator.Result camunda = validator("camunda").validate(xml, "ITEM_REVISION_RELEASE");
            assertThat(camunda.warnings()).anyMatch(w -> w.contains("parallelGateway"));
        }
    }
}
