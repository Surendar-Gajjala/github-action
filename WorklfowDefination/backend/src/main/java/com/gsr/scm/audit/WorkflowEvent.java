package com.gsr.scm.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Append-only business audit record. */
@Entity
@Table(name = "workflow_events", indexes = {
        @Index(name = "ix_event_item", columnList = "itemId"),
        @Index(name = "ix_event_instance", columnList = "workflowInstanceId"),
        @Index(name = "ix_event_business_key", columnList = "businessKey")})
@Getter
@Setter
public class WorkflowEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    @Column(nullable = false, length = 64)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WorkflowEventType action;

    private Long itemId;

    @Column(length = 64)
    private String itemNumber;

    private Long changeRequestId;

    @Column(length = 100)
    private String businessKey;

    @Column(length = 64)
    private String workflowInstanceId;

    @Column(length = 100)
    private String workflowDefinitionKey;

    @Column(length = 32)
    private String fromStatus;

    @Column(length = 32)
    private String toStatus;

    @Column(length = 4000)
    private String summary;
}
