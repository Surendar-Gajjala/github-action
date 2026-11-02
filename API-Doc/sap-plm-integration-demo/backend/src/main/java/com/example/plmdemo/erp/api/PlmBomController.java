package com.example.plmdemo.erp.api;

import com.example.plmdemo.erp.domain.EngineeringBom;
import com.example.plmdemo.erp.source.PlmBomService;
import com.example.plmdemo.erp.source.PlmBomSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mock PLM API used by the demo pages.
 *
 *   GET  /api/v1/plm/boms/{materialNumber}/{revision}?plant=1000           read the BOM
 *   POST /api/v1/plm/boms/{materialNumber}/{revision}/release?plant=1000   IN_WORK -> RELEASED
 */
@RestController
@RequestMapping("/api/v1/plm/boms")
public class PlmBomController {

    private final PlmBomSource source;
    private final PlmBomService plm;

    public PlmBomController(PlmBomSource source, PlmBomService plm) {
        this.source = source;
        this.plm = plm;
    }

    @GetMapping("/{materialNumber}/{revision}")
    public ResponseEntity<EngineeringBom> bom(@PathVariable String materialNumber,
                                              @PathVariable String revision,
                                              @RequestParam String plant) {
        return source.findBom(materialNumber, revision, plant)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{materialNumber}/{revision}/release")
    public EngineeringBom release(@PathVariable String materialNumber,
                                  @PathVariable String revision,
                                  @RequestParam String plant) {
        return plm.release(materialNumber, revision, plant);
    }
}
