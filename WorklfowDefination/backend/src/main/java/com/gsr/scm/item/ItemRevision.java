package com.gsr.scm.item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Immutable snapshot of a released (approved) revision; created only by the release-revision worker. */
@Entity
@Table(name = "item_revisions",
        uniqueConstraints = @UniqueConstraint(name = "uk_item_revision", columnNames = {"item_id", "revision"}))
@Getter
@Setter
public class ItemRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false, length = 16)
    private String revision;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    /** JSON array of {@link ItemDtos.PartSnapshot}. */
    @Lob
    @Column(nullable = false)
    private String partsJson;

    @Column(nullable = false)
    private LocalDateTime approvedAt;

    @Column(nullable = false, length = 64)
    private String approvedBy;

    private Long changeRequestId;

    @Column(length = 64)
    private String workflowInstanceId;
}
