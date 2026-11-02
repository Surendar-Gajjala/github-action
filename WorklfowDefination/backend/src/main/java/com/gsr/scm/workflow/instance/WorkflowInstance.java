package com.gsr.scm.workflow.instance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Application-side record of a process instance running in the workflow engine. The engine owns execution state;
 * this row owns business correlation (item, change request, business key) and the UI-facing status.
 */
@Entity
@Table(name = "workflow_instances", indexes = {
        @Index(name = "ix_wi_item", columnList = "itemId"),
        @Index(name = "ix_wi_engine_key", columnList = "engineInstanceKey")})
@Getter
@Setter
public class WorkflowInstance {

    public enum Status { STARTING, RUNNING, COMPLETED, REJECTED, INCIDENT }

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 32)
    private String engine;

    private Long engineInstanceKey;

    @Column(nullable = false, length = 100)
    private String definitionKey;

    @Column(length = 200)
    private String definitionName;

    @Column(nullable = false)
    private int definitionVersion;

    @Column(nullable = false)
    private Long definitionVersionId;

    /** Unique: ITEM-{itemId}-CR-{changeRequestId}. */
    @Column(nullable = false, unique = true, length = 100)
    private String businessKey;

    @Column(nullable = false)
    private Long itemId;

    @Column(length = 64)
    private String itemNumber;

    /** Unique: one workflow per submitted change request (duplicate-start guard). */
    @Column(nullable = false, unique = true)
    private Long changeRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.STARTING;

    @Column(length = 200)
    private String currentStage;

    @Column(length = 100)
    private String currentElementId;

    private boolean managerApprovalRequired;

    @Column(length = 2000)
    private String incidentMessage;

    private Long lastFailedJobKey;

    @Column(nullable = false, length = 64)
    private String startedBy;

    @Column(nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    private LocalDateTime endedAt;

    @Version
    private Long version;

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isTerminal() {
        return status == Status.COMPLETED || status == Status.REJECTED;
    }
}
