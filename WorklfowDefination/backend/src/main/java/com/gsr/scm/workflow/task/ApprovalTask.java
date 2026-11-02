package com.gsr.scm.workflow.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Approval task inbox entry, created when the engine activates a BPMN user task (job-worker implementation).
 * The engine job stays activated until a user decides; the decision completes the job.
 */
@Entity
@Table(name = "approval_tasks", indexes = {
        @Index(name = "ix_task_instance", columnList = "workflowInstanceId"),
        @Index(name = "ix_task_status_group", columnList = "status,candidateGroup")})
@Getter
@Setter
public class ApprovalTask {

    public enum Status { OPEN, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String workflowInstanceId;

    /** Engine job key; unique so a re-delivered activation never creates a second task. */
    @Column(nullable = false, unique = true)
    private Long engineJobKey;

    private Long engineInstanceKey;

    @Column(nullable = false, length = 100)
    private String elementId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 64)
    private String candidateGroup;

    @Column(nullable = false)
    private Long itemId;

    @Column(length = 64)
    private String itemNumber;

    @Column(length = 200)
    private String itemName;

    private Long changeRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.OPEN;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime dueDate;

    private LocalDateTime completedAt;

    @Column(length = 64)
    private String decisionBy;

    @Column(length = 2000)
    private String decisionComment;

    @Version
    private Long version;
}
