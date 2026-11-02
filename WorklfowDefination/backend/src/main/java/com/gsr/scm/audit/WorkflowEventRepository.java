package com.gsr.scm.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkflowEventRepository extends JpaRepository<WorkflowEvent, Long> {

    List<WorkflowEvent> findByItemIdOrderByTimestampAscIdAsc(Long itemId);

    List<WorkflowEvent> findByWorkflowInstanceIdOrderByTimestampAscIdAsc(String workflowInstanceId);

    @Query("""
            select e from WorkflowEvent e
            where (:action is null or e.action = :action)
              and (:q is null or lower(e.businessKey) like :q or lower(e.itemNumber) like :q
                   or lower(e.actor) like :q or lower(e.summary) like :q or lower(e.workflowInstanceId) like :q)
            """)
    Page<WorkflowEvent> search(@Param("q") String q, @Param("action") WorkflowEventType action, Pageable pageable);
}
