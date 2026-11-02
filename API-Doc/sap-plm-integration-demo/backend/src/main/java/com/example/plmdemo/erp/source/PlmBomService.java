package com.example.plmdemo.erp.source;

import com.example.plmdemo.cad.model.CadAssembly;
import com.example.plmdemo.cad.model.CadStructure;
import com.example.plmdemo.cad.model.CadStructureMapper;
import com.example.plmdemo.erp.domain.BomItem;
import com.example.plmdemo.erp.domain.EngineeringBom;
import com.example.plmdemo.erp.domain.ReleaseState;
import com.example.plmdemo.erp.transfer.BomNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * External-PLM operations of the demo: receive CAD data as an IN_WORK engineering BOM, and release it.
 * (In a real landscape the PLM's own CAD integration does the import; this stands in for it.)
 * Release is a separate, explicit step: importing CAD data never releases anything.
 */
@Service
public class PlmBomService {

    private static final Logger log = LoggerFactory.getLogger(PlmBomService.class);

    private final PlmBomStore store;
    private final CadStructureMapper cadMapper;

    public PlmBomService(PlmBomStore store, CadStructureMapper cadMapper) {
        this.store = store;
        this.cadMapper = cadMapper;
    }

    public record ImportResult(EngineeringBom bom, List<String> notes, int cadInstances,
                               int excludedInstances, boolean replacedExisting) {
    }

    public synchronized ImportResult importFromCad(CadAssembly cad) {
        CadStructure structure = cadMapper.analyze(cad);
        List<BomItem> items = structure.bomLines().stream()
                .map(l -> new BomItem(l.partNumber(), l.bomQuantity(), l.unit()))
                .toList();
        String source = "CAD:" + (cad.cadSystem() == null || cad.cadSystem().isBlank() ? "UNKNOWN" : cad.cadSystem().trim());
        String change = cad.changeNumber() == null || cad.changeNumber().isBlank() ? null : cad.changeNumber().trim();
        EngineeringBom bom = new EngineeringBom(source, structure.partNumber(), cad.revision().trim(),
                cad.plant().trim(), ReleaseState.IN_WORK, change, items);

        Optional<EngineeringBom> existing = store.findBom(bom.materialNumber(), bom.revision(), bom.plant());
        if (existing.isPresent() && existing.get().releaseState() != ReleaseState.IN_WORK) {
            throw new PlmConflictException("BOM " + bom.materialNumber() + " rev " + bom.revision()
                    + " plant " + bom.plant() + " is already " + existing.get().releaseState()
                    + " in PLM and cannot be overwritten; export a new revision from CAD");
        }

        store.save(bom);
        log.info("Imported CAD assembly {} rev {} into PLM: {} items, replaced={}",
                bom.materialNumber(), bom.revision(), bom.items().size(), existing.isPresent());
        return new ImportResult(bom, structure.notes(), structure.cadInstances(), structure.excludedInstances(),
                existing.isPresent());
    }

    public synchronized EngineeringBom release(String materialNumber, String revision, String plant) {
        EngineeringBom bom = store.findBom(materialNumber, revision, plant)
                .orElseThrow(() -> new BomNotFoundException(materialNumber, revision, plant));
        if (bom.releaseState() != ReleaseState.IN_WORK) {
            throw new PlmConflictException("BOM " + materialNumber + " rev " + revision
                    + " is " + bom.releaseState() + "; only IN_WORK BOMs can be released");
        }
        log.info("Released BOM {} rev {} plant {} in PLM", materialNumber, revision, plant);
        return store.save(bom.withReleaseState(ReleaseState.RELEASED));
    }
}
