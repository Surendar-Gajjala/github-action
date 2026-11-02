package com.gsr.onboarding.domain.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.gsr.onboarding.domain.enums.ArtifactStatus;
import com.gsr.onboarding.domain.enums.ArtifactType;
import com.gsr.onboarding.domain.enums.ContentSource;

import lombok.Getter;
import lombok.Setter;

/**
 * Output produced by the workflow: the welcome email or the GitHub onboarding issue.
 * {@code metadata} holds a small JSON document (checklist, target repository).
 */
@Getter
@Setter
@Entity
@Table(name = "onboarding_artifacts")
public class OnboardingArtifact extends BaseEntity {

    @Column(name = "workflow_id", nullable = false, updatable = false)
    private Long workflowId;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false, updatable = false, length = 30)
    private ArtifactType artifactType;

    @Column(name = "recipient", length = 254)
    private String recipient;

    @Column(name = "subject", length = 300)
    private String subject;

    @Column(name = "content", length = 20000)
    private String content;

    @Column(name = "metadata", length = 10000)
    private String metadata;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_source", length = 20)
    private ContentSource contentSource;

    @Column(name = "external_url", length = 500)
    private String externalUrl;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ArtifactStatus status;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;
}
