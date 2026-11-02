package com.gsr.scm.item;

import com.gsr.scm.lifecycle.TransitionPermit;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "items")
@Getter
@Setter
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String itemNumber;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    /**
     * Status of the working (current) revision. There is deliberately no setter: the only way to change it is
     * {@link #applyTransition}, which requires a permit that only LifecycleService can issue.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Setter(lombok.AccessLevel.NONE)
    private LifecycleStatus lifecycleStatus = LifecycleStatus.DRAFT;

    /** Working revision; may be ahead of {@link #approvedRevision} while a change is pending. */
    @Column(nullable = false, length = 16)
    private String revision;

    /** Last released revision; its content is preserved in {@link ItemRevision}. Null until first approval. */
    @Column(length = 16)
    private String approvedRevision;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ItemPart> parts = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Forces a version increment / updatedAt change when only the parts collection changed. */
    public void touch() {
        updatedAt = LocalDateTime.now();
    }

    public void applyTransition(LifecycleStatus target, TransitionPermit permit) {
        Objects.requireNonNull(permit, "Lifecycle transitions require a permit from LifecycleService");
        if (permit.target() != target) {
            throw new IllegalStateException("Permit was issued for " + permit.target() + ", not " + target);
        }
        this.lifecycleStatus = target;
    }
}
