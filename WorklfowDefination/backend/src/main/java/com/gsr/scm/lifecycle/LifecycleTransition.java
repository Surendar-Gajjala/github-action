package com.gsr.scm.lifecycle;

import com.gsr.scm.item.LifecycleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A permitted lifecycle state transition; configurable in the Workflow tab. */
@Entity
@Table(name = "lifecycle_transitions",
        uniqueConstraints = @UniqueConstraint(name = "uk_lifecycle_transition", columnNames = {"from_status", "to_status"}))
@Getter
@Setter
@NoArgsConstructor
public class LifecycleTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 32)
    private LifecycleStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private LifecycleStatus toStatus;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 500)
    private String description;

    /** System transitions are required by the built-in workers; they can be disabled but not deleted. */
    @Column(nullable = false)
    private boolean system;

    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(length = 64)
    private String updatedBy;

    @Version
    private Long version;

    public LifecycleTransition(LifecycleStatus from, LifecycleStatus to, String description, boolean system) {
        this.fromStatus = from;
        this.toStatus = to;
        this.description = description;
        this.system = system;
    }
}
