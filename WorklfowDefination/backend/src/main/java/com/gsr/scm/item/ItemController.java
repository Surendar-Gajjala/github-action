package com.gsr.scm.item;

import com.gsr.scm.audit.WorkflowEventDto;
import com.gsr.scm.common.PageResponse;
import com.gsr.scm.common.Pagination;
import com.gsr.scm.config.CurrentUser;
import com.gsr.scm.item.ItemDtos.ChangeDiff;
import com.gsr.scm.item.ItemDtos.CreateItemRequest;
import com.gsr.scm.item.ItemDtos.ItemDetail;
import com.gsr.scm.item.ItemDtos.ItemRevisionDto;
import com.gsr.scm.item.ItemDtos.ItemSummary;
import com.gsr.scm.item.ItemDtos.PartRef;
import com.gsr.scm.item.ItemDtos.ReplacePartsRequest;
import com.gsr.scm.item.ItemDtos.SubmitRequest;
import com.gsr.scm.item.ItemDtos.UpdateItemRequest;
import com.gsr.scm.workflow.WorkflowService;
import com.gsr.scm.workflow.instance.InstanceDtos.InstanceSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService items;
    private final WorkflowService workflow;

    public ItemController(ItemService items, WorkflowService workflow) {
        this.items = items;
        this.workflow = workflow;
    }

    @GetMapping
    public PageResponse<ItemSummary> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) LifecycleStatus status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String sort) {
        return items.list(q, status, Pagination.of(page, size, sort, ItemService.SORTABLE, "updatedAt,desc"));
    }

    @GetMapping("/{id}")
    public ItemDetail get(@PathVariable Long id) {
        return items.get(id);
    }

    @PostMapping
    public ResponseEntity<ItemDetail> create(@Valid @RequestBody CreateItemRequest req) {
        ItemDetail created = items.create(req, CurrentUser.username());
        return ResponseEntity.created(URI.create("/api/items/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ItemDetail update(@PathVariable Long id, @Valid @RequestBody UpdateItemRequest req) {
        return items.update(id, req, CurrentUser.username());
    }

    @GetMapping("/{id}/parts")
    public List<PartRef> parts(@PathVariable Long id) {
        items.load(id);
        return items.partRefs(id);
    }

    @PutMapping("/{id}/parts")
    public ItemDetail replaceParts(@PathVariable Long id, @Valid @RequestBody ReplacePartsRequest req) {
        return items.updateParts(id, req.partIds(), req.version(), CurrentUser.username());
    }

    @PostMapping("/{id}/submit-for-approval")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    public InstanceSummary submit(@PathVariable Long id, @RequestBody(required = false) SubmitRequest req) {
        return workflow.submitItem(id, req == null ? null : req.workflowKey(), req == null ? null : req.version(),
                CurrentUser.username());
    }

    @GetMapping("/{id}/workflow-history")
    public List<WorkflowEventDto> history(@PathVariable Long id) {
        return items.history(id).stream().map(WorkflowEventDto::of).toList();
    }

    @GetMapping("/{id}/revisions")
    public List<ItemRevisionDto> revisions(@PathVariable Long id) {
        return items.approvedRevisions(id);
    }

    @GetMapping("/{id}/changes")
    public ChangeDiff changes(@PathVariable Long id) {
        return items.diff(id);
    }
}
