package com.gsr.onboarding.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gsr.onboarding.domain.entity.OnboardingArtifact;
import com.gsr.onboarding.domain.enums.ArtifactStatus;
import com.gsr.onboarding.domain.enums.ArtifactType;

public interface OnboardingArtifactRepository extends JpaRepository<OnboardingArtifact, Long> {

    Optional<OnboardingArtifact> findByWorkflowIdAndArtifactType(Long workflowId, ArtifactType artifactType);

    List<OnboardingArtifact> findByWorkflowIdIn(Collection<Long> workflowIds);

    /** Compare-and-set status transition, used to guard side effects such as sending email. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OnboardingArtifact a
               set a.status = :to, a.updatedAt = :now, a.version = a.version + 1
             where a.id = :id and a.status in :from
            """)
    int transition(@Param("id") Long id,
                   @Param("from") Collection<ArtifactStatus> from,
                   @Param("to") ArtifactStatus to,
                   @Param("now") Instant now);
}
