package com.gsr.onboarding.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gsr.onboarding.domain.entity.WorkflowStep;
import com.gsr.onboarding.domain.enums.StepStatus;

public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, Long> {

    List<WorkflowStep> findByWorkflowIdOrderByStepOrder(Long workflowId);

    List<WorkflowStep> findByWorkflowIdInOrderByStepOrder(Collection<Long> workflowIds);

    List<WorkflowStep> findByStatus(StepStatus status);

    /**
     * Atomically claims a pending step for execution. Returns 1 when this caller won the claim,
     * 0 when another worker already claimed it or it is no longer pending.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WorkflowStep s
               set s.status = com.gsr.onboarding.domain.enums.StepStatus.IN_PROGRESS,
                   s.attempts = s.attempts + 1,
                   s.startedAt = :now,
                   s.completedAt = null,
                   s.errorMessage = null,
                   s.updatedAt = :now,
                   s.version = s.version + 1
             where s.id = :stepId
               and s.status = com.gsr.onboarding.domain.enums.StepStatus.PENDING
            """)
    int claim(@Param("stepId") Long stepId, @Param("now") Instant now);
}
