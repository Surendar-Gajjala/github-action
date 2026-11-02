package com.gsr.onboarding.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.gsr.onboarding.domain.entity.OnboardingWorkflow;

public interface OnboardingWorkflowRepository extends JpaRepository<OnboardingWorkflow, Long> {

    Optional<OnboardingWorkflow> findByEmployeeId(Long employeeId);

    List<OnboardingWorkflow> findByEmployeeIdIn(Collection<Long> employeeIds);

    /** Workflows that still have pending steps and are not finished; candidates for the job runner. */
    @Query("""
            select distinct w.id from OnboardingWorkflow w, WorkflowStep s
            where s.workflowId = w.id
              and s.status = com.gsr.onboarding.domain.enums.StepStatus.PENDING
              and w.overallStatus <> com.gsr.onboarding.domain.enums.WorkflowStatus.COMPLETED
            order by w.id
            """)
    List<Long> findWorkflowIdsWithPendingSteps(Pageable pageable);
}
