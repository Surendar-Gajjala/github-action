package com.gsr.scm.workflow.engine.dev;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Persistence for the development adapter's own execution state (tables prefixed dev_engine_). Kept separate from
 * the business tables to make clear that this is engine state, which in Camunda mode lives inside Camunda.
 */
public final class DevEngineEntities {

    private DevEngineEntities() {
    }

    @Entity
    @Table(name = "dev_engine_deployments")
    @Getter
    @Setter
    public static class DevDeployment {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false, length = 100)
        private String processId;

        @Column(nullable = false)
        private int version;

        @Lob
        @Column(nullable = false)
        private String bpmnXml;

        @Column(nullable = false)
        private LocalDateTime deployedAt = LocalDateTime.now();
    }

    @Entity
    @Table(name = "dev_engine_instances", indexes = @Index(name = "ix_dev_waiting_job", columnList = "waitingJobKey"))
    @Getter
    @Setter
    public static class DevProcessInstance {

        public enum State { ACTIVE, WAITING, COMPLETED, INCIDENT }

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false)
        private Long deploymentKey;

        @Column(nullable = false, length = 100)
        private String currentElementId;

        @Lob
        @Column(nullable = false)
        private String variablesJson;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 16)
        private State state = State.ACTIVE;

        private Long waitingJobKey;

        /** Remaining retries of the current service job; -1 = not yet attempted. */
        @Column(nullable = false)
        private int retriesLeft = -1;

        @Column(nullable = false)
        private int stepCounter;

        @Column(length = 2000)
        private String incidentMessage;

        private LocalDateTime updatedAt = LocalDateTime.now();

        @Version
        private Long version;
    }

    public interface DevDeploymentRepository extends JpaRepository<DevDeployment, Long> {
        Optional<DevDeployment> findFirstByProcessIdOrderByVersionDesc(String processId);
    }

    public interface DevProcessInstanceRepository extends JpaRepository<DevProcessInstance, Long> {
        Optional<DevProcessInstance> findByWaitingJobKey(Long waitingJobKey);
    }
}
