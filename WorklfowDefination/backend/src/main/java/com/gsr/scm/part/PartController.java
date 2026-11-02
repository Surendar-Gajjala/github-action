package com.gsr.scm.part;

import com.gsr.scm.common.PageResponse;
import com.gsr.scm.common.Pagination;
import com.gsr.scm.config.CurrentUser;
import com.gsr.scm.part.PartDtos.AssociationResult;
import com.gsr.scm.part.PartDtos.CreatePartRequest;
import com.gsr.scm.part.PartDtos.ItemRef;
import com.gsr.scm.part.PartDtos.PartDetail;
import com.gsr.scm.part.PartDtos.PartSummary;
import com.gsr.scm.part.PartDtos.ReplaceItemsRequest;
import com.gsr.scm.part.PartDtos.UpdatePartRequest;
import jakarta.validation.Valid;
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
@RequestMapping("/api/parts")
public class PartController {

    private final PartService parts;

    public PartController(PartService parts) {
        this.parts = parts;
    }

    @GetMapping
    public PageResponse<PartSummary> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) String partType,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String sort) {
        return parts.list(q, partType, Pagination.of(page, size, sort, PartService.SORTABLE, "partNumber,asc"));
    }

    @GetMapping("/{id}")
    public PartDetail get(@PathVariable Long id) {
        return parts.get(id);
    }

    @PostMapping
    public ResponseEntity<AssociationResult> create(@Valid @RequestBody CreatePartRequest req) {
        AssociationResult result = parts.create(req, CurrentUser.username());
        return ResponseEntity.created(URI.create("/api/parts/" + result.part().id())).body(result);
    }

    @PutMapping("/{id}")
    public PartDetail update(@PathVariable Long id, @Valid @RequestBody UpdatePartRequest req) {
        return parts.update(id, req);
    }

    @GetMapping("/{id}/items")
    public List<ItemRef> items(@PathVariable Long id) {
        parts.get(id);
        return parts.items(id);
    }

    @PutMapping("/{id}/items")
    public AssociationResult replaceItems(@PathVariable Long id, @Valid @RequestBody ReplaceItemsRequest req) {
        return parts.replaceItems(id, req.itemIds(), CurrentUser.username());
    }
}
