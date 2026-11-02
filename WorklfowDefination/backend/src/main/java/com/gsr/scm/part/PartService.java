package com.gsr.scm.part;

import com.gsr.scm.common.Exceptions.BusinessRuleException;
import com.gsr.scm.common.Exceptions.ConflictException;
import com.gsr.scm.common.Exceptions.NotFoundException;
import com.gsr.scm.common.PageResponse;
import com.gsr.scm.common.Pagination;
import com.gsr.scm.item.Item;
import com.gsr.scm.item.ItemPart;
import com.gsr.scm.item.ItemRepositories.ItemPartRepository;
import com.gsr.scm.item.ItemRepositories.ItemRepository;
import com.gsr.scm.item.ItemService;
import com.gsr.scm.part.PartDtos.AssociationResult;
import com.gsr.scm.part.PartDtos.CreatePartRequest;
import com.gsr.scm.part.PartDtos.ItemRef;
import com.gsr.scm.part.PartDtos.PartDetail;
import com.gsr.scm.part.PartDtos.PartSummary;
import com.gsr.scm.part.PartDtos.UpdatePartRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Part CRUD and Part → Item association. Association changes are delegated to {@link ItemService} per affected item,
 * so only those items get change requests; editing part attributes does not touch item lifecycles.
 */
@Service
public class PartService {

    public static final Set<String> SORTABLE = Set.of("partNumber", "name", "partType", "createdAt", "updatedAt");

    private final PartRepository parts;
    private final ItemRepository items;
    private final ItemPartRepository itemParts;
    private final ItemService itemService;

    public PartService(PartRepository parts, ItemRepository items, ItemPartRepository itemParts,
                       ItemService itemService) {
        this.parts = parts;
        this.items = items;
        this.itemParts = itemParts;
        this.itemService = itemService;
    }

    @Transactional(readOnly = true)
    public PageResponse<PartSummary> list(String q, String partType, Pageable pageable) {
        Page<Part> page = parts.search(Pagination.likePattern(q),
                partType == null || partType.isBlank() ? null : partType, pageable);
        List<Long> ids = page.getContent().stream().map(Part::getId).toList();
        Map<Long, Long> counts = ids.isEmpty() ? Map.of() : itemParts.countByPartIds(ids).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        return PageResponse.of(page, p -> new PartSummary(p.getId(), p.getPartNumber(), p.getName(),
                p.getDescription(), p.getPartType(), counts.getOrDefault(p.getId(), 0L), p.getCreatedAt(),
                p.getUpdatedAt(), p.getVersion()));
    }

    @Transactional(readOnly = true)
    public PartDetail get(Long id) {
        Part p = load(id);
        return new PartDetail(p.getId(), p.getPartNumber(), p.getName(), p.getDescription(), p.getPartType(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getVersion(), items(id));
    }

    @Transactional(readOnly = true)
    public List<ItemRef> items(Long partId) {
        return itemParts.findByPartIdWithItem(partId).stream().map(ItemPart::getItem).map(PartService::ref).toList();
    }

    @Transactional
    public AssociationResult create(CreatePartRequest req, String actor) {
        String number = req.partNumber().trim();
        if (parts.existsByPartNumberIgnoreCase(number)) {
            throw new ConflictException("Part number " + number + " already exists");
        }
        Part p = new Part();
        p.setPartNumber(number);
        p.setName(req.name().trim());
        p.setDescription(blankToNull(req.description()));
        p.setPartType(blankToNull(req.partType()));
        parts.saveAndFlush(p);
        List<ItemRef> affected = req.itemIds() == null || req.itemIds().isEmpty() ? List.of()
                : applyAssociations(p, req.itemIds(), actor);
        return new AssociationResult(get(p.getId()), affected);
    }

    @Transactional
    public PartDetail update(Long id, UpdatePartRequest req) {
        Part p = load(id);
        if (!req.version().equals(p.getVersion())) {
            throw new ConflictException("Part " + p.getPartNumber() + " was modified by someone else (version "
                    + p.getVersion() + "). Reload and try again.");
        }
        p.setName(req.name().trim());
        p.setDescription(blankToNull(req.description()));
        p.setPartType(blankToNull(req.partType()));
        parts.flush();
        return get(id);
    }

    /** Sets the items that use this part. Only items whose membership changes get a change request. */
    @Transactional
    public AssociationResult replaceItems(Long partId, List<Long> itemIds, String actor) {
        Part p = load(partId);
        List<ItemRef> affected = applyAssociations(p, itemIds, actor);
        return new AssociationResult(get(partId), affected);
    }

    private List<ItemRef> applyAssociations(Part part, List<Long> itemIds, String actor) {
        Set<Long> target = new LinkedHashSet<>(itemIds);
        List<Item> found = items.findAllById(target);
        if (found.size() != target.size()) {
            Set<Long> foundIds = found.stream().map(Item::getId).collect(Collectors.toSet());
            throw new BusinessRuleException("Unknown item id(s): "
                    + target.stream().filter(i -> !foundIds.contains(i)).toList());
        }
        Set<Long> current = itemParts.findByPartIdWithItem(part.getId()).stream()
                .map(ip -> ip.getItem().getId()).collect(Collectors.toCollection(LinkedHashSet::new));
        List<Long> changed = new ArrayList<>();
        for (Long itemId : current) {
            if (!target.contains(itemId)) {
                itemService.changePartMembership(itemId, part.getId(), false, actor);
                changed.add(itemId);
            }
        }
        for (Long itemId : target) {
            if (!current.contains(itemId)) {
                itemService.changePartMembership(itemId, part.getId(), true, actor);
                changed.add(itemId);
            }
        }
        items.flush();
        return items.findAllById(changed).stream().map(PartService::ref).toList();
    }

    private Part load(Long id) {
        return parts.findById(id).orElseThrow(() -> NotFoundException.of("Part", id));
    }

    private static ItemRef ref(Item i) {
        return new ItemRef(i.getId(), i.getItemNumber(), i.getName(), i.getLifecycleStatus(), i.getRevision(),
                i.getApprovedRevision());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
