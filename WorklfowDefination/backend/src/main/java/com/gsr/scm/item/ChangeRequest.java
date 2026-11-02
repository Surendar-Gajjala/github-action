package com.gsr.scm.item;

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

import java.time.LocalDateTime;

/**
 * Groups the edits made to an Item between two approvals. One OPEN change request per item at most; submitting it
 * starts exactly one workflow instance (enforced by a unique constraint on workflow_instances.change_request_id).
 */
@Entity
@Table(name = "change_requests", indexes = @Index(name = "ix_cr_item_status", columnList = "item_id,status"))
@Getter
@Setter
public class ChangeRequest {

    public enum Status { OPEN, SUBMITTED, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.OPEN;

    /** Approved revision this change is based on (null for an item that was never approved). */
    @Column(length = 16)
    private String baseRevision;

    @Column(nullable = false, length = 16)
    private String targetRevision;

    /** Human-readable, append-only log of the edits included in this change request. */
    @Lob
    private String changeLog;

    @Column(length = 2000)
    private String rejectionReason;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false, length = 64)
    private String createdBy;

    private LocalDateTime submittedAt;

    @Column(length = 64)
    private String submittedBy;

    private LocalDateTime decidedAt;

    @Column(length = 64)
    private String decidedBy;

    @Version
    private Long version;

    public void appendLog(String line) {
        changeLog = (changeLog == null || changeLog.isEmpty()) ? line : changeLog + "\n" + line;
    }

    public String getNumber() {
        return id == null ? null : "CR-%05d".formatted(id);
    }
}
