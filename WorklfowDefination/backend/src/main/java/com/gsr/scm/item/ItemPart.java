package com.gsr.scm.item;

import com.gsr.scm.part.Part;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Explicit Item-Part relationship (bill of materials line) so quantity/position can be carried. */
@Entity
@Table(name = "item_parts",
        uniqueConstraints = @UniqueConstraint(name = "uk_item_part", columnNames = {"item_id", "part_id"}),
        indexes = @Index(name = "ix_item_parts_part", columnList = "part_id"))
@Getter
@Setter
@NoArgsConstructor
public class ItemPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id", nullable = false)
    private Part part;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(nullable = false)
    private int position;

    public ItemPart(Item item, Part part, int position) {
        this.item = item;
        this.part = part;
        this.position = position;
    }
}
