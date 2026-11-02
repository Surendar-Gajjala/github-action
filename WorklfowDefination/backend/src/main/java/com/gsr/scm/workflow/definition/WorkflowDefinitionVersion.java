package com.gsr.scm.workflow.definition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One version of a workflow definition. Lifecycle: DRAFT (editable) → PUBLISHED (deployed, immutable) →
 * SUPERSEDED (a newer version was published; running instances keep using it).
 */
@Entity
@Table(name = "workflow_definition_versions",
        uniqueConstraints = @UniqueConstraint(name = "uk_definition_version", columnNames = {"workflowKey", "versionNumber"}))
@Getter
@Setter
public class WorkflowDefinitionVersion {

    public enum Status { DRAFT, PUBLISHED, SUPERSEDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String workflowKey;

    @Column(nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.DRAFT;

    @Lob
    @Column(nullable = false)
    private String bpmnXml;

    /** JSON of {@link ApprovalRules}. */
    @Lob
    @Column(nullable = false)
    private String rulesJson;

    /** Engine that the version was deployed to (DEV_ADAPTER or CAMUNDA_8). */
    @Column(length = 32)
    private String engine;

    private Long engineProcessDefinitionKey;

    private Integer engineVersion;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(length = 64)
    private String createdBy;

    private LocalDateTime updatedAt = LocalDateTime.now();

    private LocalDateTime publishedAt;

    @Column(length = 64)
    private String publishedBy;

    @Version
    private Long version;
}
