package com.gsr.scm.workflow.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, Long> {

    Optional<ApprovalTask> findByEngineJobKey(Long engineJobKey);

    List<ApprovalTask> findByWorkflowInstanceIdOrderByCreatedAtAsc(String workflowInstanceId);

    @Query("""
            select t from ApprovalTask t
            where t.status in :statuses
              and (:allGroups = true or t.candidateGroup in :groups)
              and (:q is null or lower(t.itemNumber) like :q or lower(t.itemName) like :q
                   or lower(t.name) like :q or lower(t.workflowInstanceId) like :q)
            """)
    Page<ApprovalTask> search(@Param("q") String q,
                              @Param("statuses") Collection<ApprovalTask.Status> statuses,
                              @Param("allGroups") boolean allGroups,
                              @Param("groups") Collection<String> groups,
                              Pageable pageable);
}
