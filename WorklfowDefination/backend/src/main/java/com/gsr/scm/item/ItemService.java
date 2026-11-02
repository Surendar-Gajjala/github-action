package com.gsr.scm.item;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsr.scm.audit.AuditService;
import com.gsr.scm.audit.WorkflowEvent;
import com.gsr.scm.audit.WorkflowEventRepository;
import com.gsr.scm.audit.WorkflowEventType;
import com.gsr.scm.common.Exceptions.BusinessRuleException;
import com.gsr.scm.common.Exceptions.ConflictException;
import com.gsr.scm.common.Exceptions.NotFoundException;
import com.gsr.scm.common.PageResponse;
import com.gsr.scm.common.Pagination;
import com.gsr.scm.item.ItemDtos.ChangeDiff;
import com.gsr.scm.item.ItemDtos.ChangeRequestDto;
import com.gsr.scm.item.ItemDtos.CreateItemRequest;
import com.gsr.scm.item.ItemDtos.FieldChange;
import com.gsr.scm.item.ItemDtos.ItemDetail;
import com.gsr.scm.item.ItemDtos.ItemRevisionDto;
import com.gsr.scm.item.ItemDtos.ItemSummary;
import com.gsr.scm.item.ItemDtos.PartRef;
import com.gsr.scm.item.ItemDtos.PartSnapshot;
import com.gsr.scm.item.ItemDtos.UpdateItemRequest;
import com.gsr.scm.item.ItemDtos.WorkflowRef;
import com.gsr.scm.item.ItemRepositories.ChangeRequestRepository;
import com.gsr.scm.item.ItemRepositories.ItemPartRepository;
import com.gsr.scm.item.ItemRepositories.ItemRepository;
import com.gsr.scm.item.ItemRepositories.ItemRevisionRepository;
import com.gsr.scm.lifecycle.LifecycleService;
import com.gsr.scm.lifecycle.LifecycleService.TransitionContext;
import com.gsr.scm.part.Part;
import com.gsr.scm.part.PartRepository;
import com.gsr.scm.workflow.instance.WorkflowInstanceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Item CRUD, Item-Part relationship management and change detection.
 * <p>
 * Every relevant change goes through {@link #applyChanges}: it opens/extends a change request and moves the item to
 * an editable lifecycle state (APPROVED → CHANGE_PENDING, REJECTED → DRAFT/CHANGE_PENDING). Nothing here can make an
 * item APPROVED; only the release-revision workflow worker can.
 */
@Service
public class ItemService {

    public static final Set<String> SORTABLE = Set.of("itemNumber", "name", "revision", "lifecycleStatus",
            "updatedAt", "createdAt");

    private final ItemRepository items;
    private final ItemPartRepository itemParts;
    private final ItemRevisionRepository revisions;
    private final ChangeRequestRepository changeRequests;
    private final PartRepository parts;
    private final WorkflowInstanceRepository workflowInstances;
    private final WorkflowEventRepository events;
    private final LifecycleService lifecycle;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public ItemService(ItemRepository items, ItemPartRepository itemParts, ItemRevisionRepository revisions,
                       ChangeRequestRepository changeRequests, PartRepository parts,
                       WorkflowInstanceRepository workflowInstances, WorkflowEventRepository events,
                       LifecycleService lifecycle, AuditService audit, ObjectMapper mapper) {
        this.items = items;
        this.itemParts = itemParts;
        this.revisions = revisions;
        this.changeRequests = changeRequests;
        this.parts = parts;
        this.workflowInstances = workflowInstances;
        this.events = events;
        this.lifecycle = lifecycle;
        this.audit = audit;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<ItemSummary> list(String q, LifecycleStatus status, Pageable pageable) {
        Page<Item> page = items.search(Pagination.likePattern(q), status, pageable);
        Map<Long, Long> counts = countParts(page.getContent().stream().map(Item::getId).toList());
        return PageResponse.of(page, i -> new ItemSummary(i.getId(), i.getItemNumber(), i.getName(),
                i.getDescription(), i.getRevision(), i.getApprovedRevision(), counts.getOrDefault(i.getId(), 0L),
                i.getLifecycleStatus(), i.getUpdatedAt(), i.getVersion()));
    }

    @Transactional(readOnly = true)
    public ItemDetail get(Long id) {
        Item item = load(id);
        ChangeRequestDto cr = changeRequests.findFirstByItemIdOrderByIdDesc(id).map(ChangeRequestDto::of).orElse(null);
        ItemRevisionDto lastApproved = item.getApprovedRevision() == null ? null
                : revisions.findByItemIdAndRevision(id, item.getApprovedRevision()).map(this::toDto).orElse(null);
        WorkflowRef workflow = workflowInstances.findFirstByItemIdOrderByStartedAtDesc(id)
                .map(w -> new WorkflowRef(w.getId(), w.getDefinitionKey(), w.getDefinitionVersion(),
                        w.getStatus().name(), w.getCurrentStage(), w.getStartedAt()))
                .orElse(null);
        LifecycleStatus s = item.getLifecycleStatus();
        return new ItemDetail(item.getId(), item.getItemNumber(), item.getName(), item.getDescription(), s,
                item.getRevision(), item.getApprovedRevision(), item.getCreatedAt(), item.getUpdatedAt(),
                item.getVersion(), partRefs(id), cr, lastApproved, workflow,
                s != LifecycleStatus.IN_REVIEW,
                s == LifecycleStatus.DRAFT || s == LifecycleStatus.CHANGE_PENDING);
    }

    @Transactional(readOnly = true)
    public List<PartRef> partRefs(Long itemId) {
        return itemParts.findByItemIdWithPart(itemId).stream()
                .map(ip -> new PartRef(ip.getPart().getId(), ip.getPart().getPartNumber(), ip.getPart().getName(),
                        ip.getPart().getPartType(), ip.getQuantity()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowEvent> history(Long itemId) {
        load(itemId);
        return events.findByItemIdOrderByTimestampAscIdAsc(itemId);
    }

    @Transactional(readOnly = true)
    public List<ItemRevisionDto> approvedRevisions(Long itemId) {
        load(itemId);
        return revisions.findByItemIdOrderByApprovedAtDesc(itemId).stream().map(this::toDto).toList();
    }

    /** Net difference between the working state and the last approved revision (or empty baseline). */
    @Transactional(readOnly = true)
    public ChangeDiff diff(Long itemId) {
        Item item = load(itemId);
        ItemRevision base = item.getApprovedRevision() == null ? null
                : revisions.findByItemIdAndRevision(itemId, item.getApprovedRevision()).orElse(null);
        List<FieldChange> fields = new ArrayList<>();
        String baseName = base == null ? null : base.getName();
        String baseDescription = base == null ? null : base.getDescription();
        if (!Objects.equals(baseName, item.getName())) {
            fields.add(new FieldChange("name", baseName, item.getName()));
        }
        if (!Objects.equals(blankToNull(baseDescription), blankToNull(item.getDescription()))) {
            fields.add(new FieldChange("description", baseDescription, item.getDescription()));
        }
        List<PartSnapshot> before = base == null ? List.of() : readSnapshot(base.getPartsJson());
        List<PartSnapshot> after = currentSnapshot(itemId);
        Set<String> beforeNumbers = before.stream().map(PartSnapshot::partNumber).collect(Collectors.toSet());
        Set<String> afterNumbers = after.stream().map(PartSnapshot::partNumber).collect(Collectors.toSet());
        List<PartSnapshot> added = after.stream().filter(p -> !beforeNumbers.contains(p.partNumber())).toList();
        List<PartSnapshot> removed = before.stream().filter(p -> !afterNumbers.contains(p.partNumber())).toList();
        List<PartSnapshot> unchanged = after.stream().filter(p -> beforeNumbers.contains(p.partNumber())).toList();
        ChangeRequestDto cr = changeRequests.findFirstByItemIdOrderByIdDesc(itemId).map(ChangeRequestDto::of)
                .orElse(null);
        return new ChangeDiff(item.getId(), item.getItemNumber(), item.getName(), item.getApprovedRevision(),
                item.getRevision(), fields, added, removed, unchanged, cr);
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public ItemDetail create(CreateItemRequest req, String actor) {
        String number = req.itemNumber().trim();
        if (items.existsByItemNumberIgnoreCase(number)) {
            throw new ConflictException("Item number " + number + " already exists");
        }
        Item item = new Item();
        item.setItemNumber(number);
        item.setName(req.name().trim());
        item.setDescription(blankToNull(req.description()));
        item.setRevision(Revisions.INITIAL);
        items.saveAndFlush(item);

        ChangeRequest cr = openChangeRequest(item, null, actor);
        cr.appendLog("Item created by " + actor);
        audit.record(WorkflowEventType.ITEM_CREATED, actor, e -> {
            itemEvent(e, item, cr);
            e.setToStatus(LifecycleStatus.DRAFT.name());
            e.setSummary("Item " + item.getItemNumber() + " '" + item.getName() + "' created as DRAFT (rev "
                    + item.getRevision() + ")");
        });
        List<Long> partIds = req.partIds() == null ? List.of() : req.partIds();
        replaceParts(item, partIds, cr, actor);
        items.flush();
        return get(item.getId());
    }

    @Transactional
    public ItemDetail update(Long id, UpdateItemRequest req, String actor) {
        Item item = load(id);
        checkVersion(item, req.version());
        applyChanges(item, req.name().trim(), blankToNull(req.description()), req.partIds(), actor);
        items.flush();
        return get(id);
    }

    @Transactional
    public ItemDetail updateParts(Long id, List<Long> partIds, Long version, String actor) {
        Item item = load(id);
        checkVersion(item, version);
        applyChanges(item, item.getName(), item.getDescription(), partIds, actor);
        items.flush();
        return get(id);
    }

    /** Adds or removes one part (used when the association is edited from the Parts tab). */
    @Transactional
    public void changePartMembership(Long itemId, Long partId, boolean add, String actor) {
        Item item = load(itemId);
        List<Long> current = new ArrayList<>(item.getParts().stream().map(ip -> ip.getPart().getId()).toList());
        boolean changed = add ? !current.contains(partId) && current.add(partId) : current.remove(partId);
        if (changed) {
            applyChanges(item, item.getName(), item.getDescription(), current, actor);
        }
    }

    /**
     * Applies edits and handles lifecycle consequences. Returns false when nothing relevant changed
     * (no change request is opened and the status is untouched).
     */
    private boolean applyChanges(Item item, String name, String description, List<Long> partIds, String actor) {
        List<FieldChange> fieldChanges = new ArrayList<>();
        if (!Objects.equals(item.getName(), name)) {
            fieldChanges.add(new FieldChange("name", item.getName(), name));
        }
        if (!Objects.equals(blankToNull(item.getDescription()), description)) {
            fieldChanges.add(new FieldChange("description", item.getDescription(), description));
        }
        List<Long> currentIds = item.getParts().stream().map(ip -> ip.getPart().getId()).toList();
        List<Long> targetIds = partIds == null ? currentIds : List.copyOf(new LinkedHashSet<>(partIds));
        boolean partsChanged = !new LinkedHashSet<>(currentIds).equals(new LinkedHashSet<>(targetIds));

        if (fieldChanges.isEmpty() && !partsChanged) {
            return false;
        }
        ChangeRequest cr = prepareForChange(item, actor);
        if (!fieldChanges.isEmpty()) {
            item.setName(name);
            item.setDescription(description);
            String summary = fieldChanges.stream()
                    .map(c -> c.field() + ": '" + nullToEmpty(c.oldValue()) + "' → '" + nullToEmpty(c.newValue()) + "'")
                    .collect(Collectors.joining("; "));
            cr.appendLog("Updated by " + actor + ": " + summary);
            audit.record(WorkflowEventType.ITEM_UPDATED, actor, e -> {
                itemEvent(e, item, cr);
                e.setSummary(summary);
            });
        }
        if (partsChanged) {
            replaceParts(item, targetIds, cr, actor);
        }
        item.touch();
        return true;
    }

    /** Ensures the item is in an editable state and returns the OPEN change request that collects the edits. */
    private ChangeRequest prepareForChange(Item item, String actor) {
        LifecycleStatus status = item.getLifecycleStatus();
        switch (status) {
            case IN_REVIEW -> throw new ConflictException("Item " + item.getItemNumber()
                    + " is in review and cannot be changed until the approval workflow completes");
            case APPROVED -> {
                String approved = item.getApprovedRevision();
                item.setRevision(Revisions.next(approved));
                lifecycle.transition(item, LifecycleStatus.CHANGE_PENDING, TransitionContext.of(actor,
                        "Change started on approved revision " + approved + "; working revision " + item.getRevision()));
                return openChangeRequest(item, approved, actor);
            }
            case REJECTED -> {
                LifecycleStatus target = item.getApprovedRevision() != null
                        ? LifecycleStatus.CHANGE_PENDING : LifecycleStatus.DRAFT;
                lifecycle.transition(item, target, TransitionContext.of(actor, "Rework after rejection"));
                return openChangeRequest(item, item.getApprovedRevision(), actor);
            }
            default -> {
                return changeRequests.findFirstByItemIdAndStatusOrderByIdDesc(item.getId(), ChangeRequest.Status.OPEN)
                        .orElseGet(() -> openChangeRequest(item, item.getApprovedRevision(), actor));
            }
        }
    }

    private ChangeRequest openChangeRequest(Item item, String baseRevision, String actor) {
        ChangeRequest cr = new ChangeRequest();
        cr.setItemId(item.getId());
        cr.setBaseRevision(baseRevision);
        cr.setTargetRevision(item.getRevision());
        cr.setCreatedBy(actor);
        changeRequests.saveAndFlush(cr);
        audit.record(WorkflowEventType.CHANGE_REQUEST_CREATED, actor, e -> {
            itemEvent(e, item, cr);
            e.setSummary(cr.getNumber() + " opened for revision " + cr.getTargetRevision()
                    + (baseRevision == null ? "" : " (based on approved revision " + baseRevision + ")"));
        });
        return cr;
    }

    private void replaceParts(Item item, List<Long> targetIds, ChangeRequest cr, String actor) {
        List<Long> distinct = List.copyOf(new LinkedHashSet<>(targetIds));
        Map<Long, Part> found = parts.findAllById(distinct).stream()
                .collect(Collectors.toMap(Part::getId, Function.identity()));
        List<Long> missing = distinct.stream().filter(pid -> !found.containsKey(pid)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("Unknown part id(s): " + missing);
        }
        Set<Long> target = new LinkedHashSet<>(distinct);
        List<Part> removed = new ArrayList<>();
        item.getParts().removeIf(ip -> {
            if (!target.contains(ip.getPart().getId())) {
                removed.add(ip.getPart());
                return true;
            }
            return false;
        });
        Set<Long> existing = item.getParts().stream().map(ip -> ip.getPart().getId()).collect(Collectors.toSet());
        List<Part> added = new ArrayList<>();
        for (Long pid : distinct) {
            if (!existing.contains(pid)) {
                item.getParts().add(new ItemPart(item, found.get(pid), 0));
                added.add(found.get(pid));
            }
        }
        Map<Long, Integer> order = new HashMap<>();
        for (int i = 0; i < distinct.size(); i++) {
            order.put(distinct.get(i), i);
        }
        item.getParts().forEach(ip -> ip.setPosition(order.getOrDefault(ip.getPart().getId(), 0)));
        item.getParts().sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));

        if (!added.isEmpty()) {
            String list = partList(added);
            cr.appendLog("Parts added by " + actor + ": " + list);
            audit.record(WorkflowEventType.PARTS_ADDED, actor, e -> {
                itemEvent(e, item, cr);
                e.setSummary("Added " + list);
            });
        }
        if (!removed.isEmpty()) {
            String list = partList(removed);
            cr.appendLog("Parts removed by " + actor + ": " + list);
            audit.record(WorkflowEventType.PARTS_REMOVED, actor, e -> {
                itemEvent(e, item, cr);
                e.setSummary("Removed " + list);
            });
        }
    }

    // ------------------------------------------------------------------ workflow support (workers only)

    /** Opens a fresh change request after a rejection so the item can be reworked and resubmitted. */
    @Transactional
    public ChangeRequest reopenAfterRejection(Item item, ChangeRequest rejected, String actor) {
        return changeRequests.findFirstByItemIdAndStatusOrderByIdDesc(item.getId(), ChangeRequest.Status.OPEN)
                .orElseGet(() -> {
                    ChangeRequest cr = openChangeRequest(item, item.getApprovedRevision(), actor);
                    cr.appendLog("Reopened after rejection of " + rejected.getNumber() + ": "
                            + rejected.getRejectionReason());
                    return cr;
                });
    }

    /** Creates the immutable snapshot of the working revision. Idempotent per (item, revision). */
    @Transactional
    public ItemRevision snapshotApprovedRevision(Item item, String approvedBy, Long changeRequestId,
                                                 String workflowInstanceId) {
        return revisions.findByItemIdAndRevision(item.getId(), item.getRevision()).orElseGet(() -> {
            ItemRevision rev = new ItemRevision();
            rev.setItemId(item.getId());
            rev.setRevision(item.getRevision());
            rev.setName(item.getName());
            rev.setDescription(item.getDescription());
            rev.setPartsJson(writeSnapshot(currentSnapshot(item.getId())));
            rev.setApprovedAt(LocalDateTime.now());
            rev.setApprovedBy(approvedBy);
            rev.setChangeRequestId(changeRequestId);
            rev.setWorkflowInstanceId(workflowInstanceId);
            return revisions.save(rev);
        });
    }

    // ------------------------------------------------------------------ helpers

    public Item load(Long id) {
        return items.findById(id).orElseThrow(() -> NotFoundException.of("Item", id));
    }

    private void checkVersion(Item item, Long expected) {
        if (expected != null && !expected.equals(item.getVersion())) {
            throw new ConflictException("Item " + item.getItemNumber()
                    + " was modified by someone else (version " + item.getVersion() + "). Reload and try again.");
        }
    }

    private Map<Long, Long> countParts(Collection<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return itemParts.countByItemIds(itemIds).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }

    private List<PartSnapshot> currentSnapshot(Long itemId) {
        return itemParts.findByItemIdWithPart(itemId).stream()
                .map(ip -> new PartSnapshot(ip.getPart().getId(), ip.getPart().getPartNumber(),
                        ip.getPart().getName(), ip.getPart().getPartType(), ip.getQuantity()))
                .toList();
    }

    private ItemRevisionDto toDto(ItemRevision r) {
        return new ItemRevisionDto(r.getId(), r.getRevision(), r.getName(), r.getDescription(),
                readSnapshot(r.getPartsJson()), r.getApprovedAt(), r.getApprovedBy(), r.getChangeRequestId(),
                r.getWorkflowInstanceId());
    }

    private List<PartSnapshot> readSnapshot(String json) {
        try {
            return mapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt revision snapshot", e);
        }
    }

    private String writeSnapshot(List<PartSnapshot> snapshot) {
        try {
            return mapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void itemEvent(WorkflowEvent e, Item item, ChangeRequest cr) {
        e.setItemId(item.getId());
        e.setItemNumber(item.getItemNumber());
        e.setChangeRequestId(cr == null ? null : cr.getId());
    }

    private static String partList(List<Part> list) {
        return list.stream().map(p -> p.getPartNumber() + " (" + p.getName() + ")").collect(Collectors.joining(", "));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
