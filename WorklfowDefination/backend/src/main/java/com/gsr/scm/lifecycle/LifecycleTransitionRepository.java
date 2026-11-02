package com.gsr.scm.lifecycle;

import com.gsr.scm.item.LifecycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LifecycleTransitionRepository extends JpaRepository<LifecycleTransition, Long> {

    boolean existsByFromStatusAndToStatusAndEnabledTrue(LifecycleStatus from, LifecycleStatus to);

    Optional<LifecycleTransition> findByFromStatusAndToStatus(LifecycleStatus from, LifecycleStatus to);

    List<LifecycleTransition> findAllByOrderByFromStatusAscToStatusAsc();
}
