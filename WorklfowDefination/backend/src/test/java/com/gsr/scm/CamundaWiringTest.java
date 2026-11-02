package com.gsr.scm;

import com.gsr.scm.common.Exceptions.EngineUnavailableException;
import com.gsr.scm.workflow.definition.WorkflowDefinitionService;
import com.gsr.scm.workflow.engine.WorkflowEngineAdapter;
import com.gsr.scm.workflow.engine.camunda.CamundaWorkflowAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the Camunda 8 integration is wired when selected. No cluster runs here (nothing listens on the configured
 * addresses), so this checks selection and failure handling only; real execution requires Camunda 8 Run - see README.
 */
@SpringBootTest(properties = {
        "scm.workflow.engine=camunda",
        "scm.workflow.camunda.grpc-address=http://localhost:1",
        "scm.workflow.camunda.rest-address=http://localhost:1"})
class CamundaWiringTest {

    @Autowired
    WorkflowEngineAdapter engine;
    @Autowired
    WorkflowDefinitionService definitions;

    @Test
    void camundaAdapterIsSelectedAndReportsUnreachableCluster() {
        assertThat(engine).isInstanceOf(CamundaWorkflowAdapter.class);
        WorkflowEngineAdapter.EngineInfo info = engine.info();
        assertThat(info.engine()).isEqualTo("CAMUNDA_8");
        assertThat(info.realCamunda()).isTrue();
        assertThat(info.connected()).isFalse();
    }

    @Test
    void publishFailsCleanlyWhenClusterIsUnavailable() {
        assertThatThrownBy(() -> definitions.publish("ITEM_LIFECYCLE_APPROVAL", "admin"))
                .isInstanceOf(EngineUnavailableException.class);
        assertThat(definitions.get("ITEM_LIFECYCLE_APPROVAL").definition().publishedVersion()).isNull();
    }
}
