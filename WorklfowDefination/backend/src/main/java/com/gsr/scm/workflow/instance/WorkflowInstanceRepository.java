package com.gsr.scm.workflow.instance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, String> {

    boolean existsByChangeRequestId(Long changeRequestId);

    Optional<WorkflowInstance> findByEngineInstanceKey(Long engineInstanceKey);

    Optional<WorkflowInstance> findFirstByItemIdOrderByStartedAtDesc(Long itemId);

    List<WorkflowInstance> findByItemIdOrderByStartedAtDesc(Long itemId);

    @Query("""
            select w from WorkflowInstance w
            where w.status in :statuses
              and (:definitionKey is null or w.definitionKey = :definitionKey)
              and (:q is null or lower(w.businessKey) like :q or lower(w.itemNumber) like :q
                   or lower(w.id) like :q or lower(w.currentStage) like :q)
            """)
    Page<WorkflowInstance> search(@Param("q") String q,
                                  @Param("statuses") Collection<WorkflowInstance.Status> statuses,
                                  @Param("definitionKey") String definitionKey,
                                  Pageable pageable);
}
