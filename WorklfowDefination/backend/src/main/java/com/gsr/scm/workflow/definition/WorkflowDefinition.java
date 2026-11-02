package com.gsr.scm.workflow.definition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Header of a workflow definition; the BPMN content lives in versioned {@link WorkflowDefinitionVersion} rows. */
@Entity
@Table(name = "workflow_definitions")
@Getter
@Setter
public class WorkflowDefinition {

    public enum Type { ITEM_LIFECYCLE, ITEM_PART_CHANGE, REVISION_RELEASE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Also the BPMN process id. */
    @Column(nullable = false, unique = true, length = 100)
    private String workflowKey;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Type workflowType;

    /** Inactive definitions cannot start new instances; running instances continue. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(length = 64)
    private String updatedBy;

    @Version
    private Long version;
}
