package com.example.plmdemo.cad.api;

import com.example.plmdemo.cad.api.dto.CadExportDto;
import com.example.plmdemo.cad.ectr.EctrMockService;
import com.example.plmdemo.cad.ectr.EctrSessionView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CAD path: CAD -> SAP ECTR -> SAP backend, as a SIMULATION over HTTP.
 *
 * Real SAP ECTR is a desktop client driven through the client-side ECTR Connector, NOT a REST API.
 * These endpoints only exist so the demo can show the ECTR steps in a browser.
 *
 *   POST /api/v1/ectr/sessions                       1. createDocuments   (body: CAD JSON)
 *   POST /api/v1/ectr/sessions/{id}/checkin          2. save(SAVECHECKIN)
 *   POST /api/v1/ectr/sessions/{id}/materials        3. materialCreate
 *   POST /api/v1/ectr/sessions/{id}/bom              4. cdeskBomWizard
 *   GET  /api/v1/ectr/sessions/{id}                  session state
 */
@RestController
@RequestMapping("/api/v1/ectr/sessions")
public class EctrController {

    private final EctrMockService ectr;

    public EctrController(EctrMockService ectr) {
        this.ectr = ectr;
    }

    @PostMapping
    public ResponseEntity<EctrSessionView> createDocuments(@Valid @RequestBody CadExportDto body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ectr.createDocuments(body.toDomain()));
    }

    @PostMapping("/{sessionId}/checkin")
    public EctrSessionView checkIn(@PathVariable String sessionId) {
        return ectr.checkIn(sessionId);
    }

    @PostMapping("/{sessionId}/materials")
    public EctrSessionView createMaterials(@PathVariable String sessionId) {
        return ectr.createMaterials(sessionId);
    }

    @PostMapping("/{sessionId}/bom")
    public EctrSessionView maintainBom(@PathVariable String sessionId) {
        return ectr.maintainBom(sessionId);
    }

    @GetMapping("/{sessionId}")
    public EctrSessionView get(@PathVariable String sessionId) {
        return ectr.get(sessionId);
    }
}
