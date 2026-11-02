package com.gsr.scm.workflow.definition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public final class WorkflowDefinitionRepositories {

    private WorkflowDefinitionRepositories() {
    }

    public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, Long> {

        Optional<WorkflowDefinition> findByWorkflowKey(String workflowKey);

        boolean existsByWorkflowKey(String workflowKey);

        List<WorkflowDefinition> findAllByOrderByWorkflowKeyAsc();

        List<WorkflowDefinition> findByWorkflowTypeAndActiveTrue(WorkflowDefinition.Type type);
    }

    public interface WorkflowDefinitionVersionRepository extends JpaRepository<WorkflowDefinitionVersion, Long> {

        List<WorkflowDefinitionVersion> findByWorkflowKeyOrderByVersionNumberDesc(String workflowKey);

        Optional<WorkflowDefinitionVersion> findByWorkflowKeyAndVersionNumber(String workflowKey, int versionNumber);

        Optional<WorkflowDefinitionVersion> findFirstByWorkflowKeyAndStatus(String workflowKey,
                                                                            WorkflowDefinitionVersion.Status status);

        Optional<WorkflowDefinitionVersion> findFirstByWorkflowKeyOrderByVersionNumberDesc(String workflowKey);
    }
}
