package com.example.plmdemo.erp.api;

import com.example.plmdemo.cad.api.dto.CadExportDto;
import com.example.plmdemo.erp.source.PlmBomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ERP path, step 1: CAD data enters the EXTERNAL PLM as an engineering BOM (IN_WORK).
 * Stands in for the PLM product's own CAD integration (MOCK).
 *
 *   POST /api/v1/plm/imports
 *
 * 201 = new BOM revision created in PLM, 200 = existing IN_WORK revision replaced.
 */
@RestController
@RequestMapping("/api/v1/plm/imports")
public class PlmImportController {

    private final PlmBomService plm;

    public PlmImportController(PlmBomService plm) {
        this.plm = plm;
    }

    @PostMapping
    public ResponseEntity<PlmBomService.ImportResult> importCad(@Valid @RequestBody CadExportDto body) {
        PlmBomService.ImportResult result = plm.importFromCad(body.toDomain());
        return ResponseEntity.status(result.replacedExisting() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }
}
