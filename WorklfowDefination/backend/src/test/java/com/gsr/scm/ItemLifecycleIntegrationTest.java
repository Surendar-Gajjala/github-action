package com.gsr.scm;

import com.gsr.scm.common.Exceptions.BusinessRuleException;
import com.gsr.scm.common.Exceptions.ConflictException;
import com.gsr.scm.common.Exceptions.LifecycleTransitionException;
import com.gsr.scm.item.ChangeRequest;
import com.gsr.scm.item.ItemDtos.CreateItemRequest;
import com.gsr.scm.item.ItemDtos.ItemDetail;
import com.gsr.scm.item.ItemDtos.PartRef;
import com.gsr.scm.item.ItemDtos.PartSnapshot;
import com.gsr.scm.item.ItemDtos.UpdateItemRequest;
import com.gsr.scm.item.LifecycleStatus;
import com.gsr.scm.lifecycle.LifecycleService;
import com.gsr.scm.part.PartDtos.AssociationResult;
import com.gsr.scm.part.PartDtos.CreatePartRequest;
import com.gsr.scm.part.PartDtos.ItemRef;
import com.gsr.scm.part.PartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemLifecycleIntegrationTest extends IntegrationTestBase {

    @Autowired
    PartService partService;
    @Autowired
    LifecycleService lifecycle;

    @BeforeEach
    void publish() {
        ensurePublished("ITEM_LIFECYCLE_APPROVAL");
    }

    private ItemDetail approvedItem(String... partNumbers) {
        ItemDetail item = createItem(partNumbers);
        workflow.submitItem(item.id(), "ITEM_LIFECYCLE_APPROVAL", null, "author");
        approve(item.id(), "ENGINEERING", "engineer");
        openTask(item.id(), "MANAGER").ifPresent(t -> approve(item.id(), "MANAGER", "manager"));
        ItemDetail approved = items.get(item.id());
        assertThat(approved.lifecycleStatus()).isEqualTo(LifecycleStatus.APPROVED);
        return approved;
    }

    @Test
    void newItemStartsAsDraftWithOpenChangeRequestAndParts() {
        ItemDetail item = createItem("PART-101", "PART-102", "PART-103");
        assertThat(item.lifecycleStatus()).isEqualTo(LifecycleStatus.DRAFT);
        assertThat(item.revision()).isEqualTo("A");
        assertThat(item.approvedRevision()).isNull();
        assertThat(item.parts()).extracting(PartRef::partNumber).containsExactly("PART-101", "PART-102", "PART-103");
        assertThat(item.currentChangeRequest().status()).isEqualTo(ChangeRequest.Status.OPEN);
        assertThat(item.submittable()).isTrue();
    }

    @Test
    void duplicateItemNumberAndUnknownPartsAreRejected() {
        ItemDetail item = createItem("PART-101");
        assertThatThrownBy(() -> items.create(new CreateItemRequest(item.itemNumber().toLowerCase(), "x", null, null), "author"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> items.create(new CreateItemRequest(unique("ITEM"), "x", null, List.of(999_999L)), "author"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Unknown part");
    }

    @Test
    void partsCanBeAddedAndRemovedOnDraftWithoutStatusChange() {
        ItemDetail item = createItem("PART-101");
        ItemDetail updated = items.updateParts(item.id(), ids("PART-102", "PART-103"), item.version(), "author");
        assertThat(updated.parts()).extracting(PartRef::partNumber).containsExactly("PART-102", "PART-103");
        assertThat(updated.lifecycleStatus()).isEqualTo(LifecycleStatus.DRAFT);
        assertThat(updated.currentChangeRequest().id()).isEqualTo(item.currentChangeRequest().id());
        assertThat(updated.currentChangeRequest().changeLog()).contains("Parts added").contains("Parts removed");
    }

    @Test
    void savingWithoutChangesDoesNothing() {
        ItemDetail item = createItem("PART-101");
        ItemDetail same = items.update(item.id(), new UpdateItemRequest(item.name(), item.description(), null,
                item.version()), "author");
        assertThat(same.version()).isEqualTo(item.version());
    }

    @Test
    void changingAnApprovedItemCreatesChangeRequestAndPreservesApprovedRevision() {
        ItemDetail approved = approvedItem("PART-101", "PART-102", "PART-103");
        assertThat(approved.approvedRevision()).isEqualTo("A");
        Long approvedCr = approved.currentChangeRequest().id();

        ItemDetail changed = items.updateParts(approved.id(), ids("PART-101", "PART-102"), approved.version(), "author");

        assertThat(changed.lifecycleStatus()).isEqualTo(LifecycleStatus.CHANGE_PENDING);
        assertThat(changed.revision()).isEqualTo("B");
        assertThat(changed.approvedRevision()).isEqualTo("A");
        assertThat(changed.currentChangeRequest().id()).isNotEqualTo(approvedCr);
        assertThat(changed.currentChangeRequest().baseRevision()).isEqualTo("A");
        assertThat(changed.lastApprovedRevision().parts()).extracting(PartSnapshot::partNumber)
                .containsExactly("PART-101", "PART-102", "PART-103");
        assertThat(items.diff(approved.id()).partsRemoved()).extracting(PartSnapshot::partNumber)
                .containsExactly("PART-103");
    }

    @Test
    void headerEditOfApprovedItemAlsoRequiresReview() {
        ItemDetail approved = approvedItem("PART-104");
        ItemDetail changed = items.update(approved.id(), new UpdateItemRequest("Renamed", null, null,
                approved.version()), "author");
        assertThat(changed.lifecycleStatus()).isEqualTo(LifecycleStatus.CHANGE_PENDING);
        assertThat(changed.lastApprovedRevision().name()).isEqualTo("Hydraulic Pump Assembly");
    }

    @Test
    void sharedPartAssociationOnlyAffectsChangedItems() {
        ItemDetail a = approvedItem("PART-106");
        ItemDetail b = approvedItem("PART-106");
        ItemDetail c = approvedItem("PART-107");
        Long sensor = partId("PART-106");

        // Remove PART-106 from item A only, and add it to item C.
        List<Long> target = new ArrayList<>(partService.get(sensor).items().stream().map(ItemRef::id).toList());
        target.remove(a.id());
        target.add(c.id());
        AssociationResult result = partService.replaceItems(sensor, target, "author");

        assertThat(result.affectedItems()).extracting(ItemRef::id).containsExactlyInAnyOrder(a.id(), c.id());
        assertThat(items.get(a.id()).lifecycleStatus()).isEqualTo(LifecycleStatus.CHANGE_PENDING);
        assertThat(items.get(c.id()).lifecycleStatus()).isEqualTo(LifecycleStatus.CHANGE_PENDING);
        assertThat(items.get(b.id()).lifecycleStatus()).isEqualTo(LifecycleStatus.APPROVED);
    }

    @Test
    void creatingPartWithItemAssociationFlagsApprovedItem() {
        ItemDetail approved = approvedItem("PART-108");
        AssociationResult result = partService.create(new CreatePartRequest(unique("PART"), "Gasket", null,
                "CONSUMABLE", List.of(approved.id())), "author");
        assertThat(result.affectedItems()).extracting(ItemRef::id).containsExactly(approved.id());
        assertThat(items.get(approved.id()).lifecycleStatus()).isEqualTo(LifecycleStatus.CHANGE_PENDING);
    }

    @Test
    void itemsInReviewCannotBeEdited() {
        ItemDetail item = createItem("PART-101");
        workflow.submitItem(item.id(), null, null, "author");
        ItemDetail inReview = items.get(item.id());
        assertThat(inReview.lifecycleStatus()).isEqualTo(LifecycleStatus.IN_REVIEW);
        assertThatThrownBy(() -> items.update(item.id(), new UpdateItemRequest("x", null, null, inReview.version()), "author"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("in review");
        awaitEngine();
    }

    @Test
    void lifecycleTransitionsAreEnforcedFromConfiguration() {
        assertThat(lifecycle.isAllowed(LifecycleStatus.DRAFT, LifecycleStatus.IN_REVIEW)).isTrue();
        assertThat(lifecycle.isAllowed(LifecycleStatus.DRAFT, LifecycleStatus.APPROVED)).isFalse();
        assertThat(lifecycle.isAllowed(LifecycleStatus.CHANGE_PENDING, LifecycleStatus.APPROVED)).isFalse();

        // Disabling DRAFT -> IN_REVIEW blocks submissions of new items.
        lifecycle.upsert(LifecycleStatus.DRAFT, LifecycleStatus.IN_REVIEW, false, null, "admin");
        try {
            ItemDetail item = createItem("PART-101");
            assertThatThrownBy(() -> workflow.submitItem(item.id(), null, null, "author"))
                    .isInstanceOf(LifecycleTransitionException.class);
            assertThat(items.get(item.id()).lifecycleStatus()).isEqualTo(LifecycleStatus.DRAFT);
            assertThat(instanceRepository.existsByChangeRequestId(item.currentChangeRequest().id())).isFalse();
        } finally {
            lifecycle.upsert(LifecycleStatus.DRAFT, LifecycleStatus.IN_REVIEW, true, null, "admin");
        }
    }

    @Test
    void staleVersionIsRejected() {
        ItemDetail item = createItem("PART-101");
        items.update(item.id(), new UpdateItemRequest("first", null, null, item.version()), "author");
        assertThatThrownBy(() -> items.update(item.id(), new UpdateItemRequest("second", null, null, item.version()), "author"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void concurrentUpdatesWithSameVersionOnlyOneWins() throws Exception {
        ItemDetail item = createItem("PART-101");
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String name = "concurrent-" + i;
            results.add(pool.submit(() -> {
                start.await();
                try {
                    items.update(item.id(), new UpdateItemRequest(name, null, null, item.version()), "author");
                    return true;
                } catch (ConflictException | ObjectOptimisticLockingFailureException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        List<Boolean> outcomes = new ArrayList<>();
        for (Future<Boolean> f : results) {
            outcomes.add(f.get());
        }
        pool.shutdown();
        assertThat(Collections.frequency(outcomes, true)).isEqualTo(1);
        assertThat(items.get(item.id()).version()).isEqualTo(item.version() + 1);
    }
}
