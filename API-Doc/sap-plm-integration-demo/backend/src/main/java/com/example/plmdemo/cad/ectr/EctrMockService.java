package com.example.plmdemo.cad.ectr;

import com.example.plmdemo.cad.model.CadAssembly;
import com.example.plmdemo.cad.model.CadStructure;
import com.example.plmdemo.cad.model.CadStructureMapper;
import com.example.plmdemo.cad.sapbackend.MockSapPlmBackend;
import com.example.plmdemo.cad.sapbackend.MockSapPlmBackend.BomItemRecord;
import com.example.plmdemo.cad.sapbackend.MockSapPlmBackend.DocumentInfoRecord;
import com.example.plmdemo.cad.sapbackend.MockSapPlmBackend.StructureLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SIMULATION of the CAD -> SAP ECTR -> SAP backend path.
 *
 * Real SAP ECTR runs on the designer's PC and is driven through the client-side ECTR Connector
 * (not REST). Here each step is a server-side method so the flow can be shown in a browser.
 * Step names follow the documented ECTR Connector calls:
 *
 *   1. createDocuments(files, metadata)                 -> DIRs in SAP
 *   2. save(loadedParts, parts, SaveOption.SAVECHECKIN)  -> originals checked in, document structure (BoD)
 *   3. materialCreate(material, part)                    -> materials + object links to the DIRs
 *   4. cdeskBomWizard(part, materialHead, items)         -> material BOM
 *
 * Everything is written to MockSapPlmBackend (in memory). Release for production is NOT part of this
 * flow: in SAP it follows the change-number / status process.
 */
@Service
public class EctrMockService {

    private static final Logger log = LoggerFactory.getLogger(EctrMockService.class);

    private final CadStructureMapper mapper;
    private final MockSapPlmBackend sap;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public EctrMockService(CadStructureMapper mapper, MockSapPlmBackend sap) {
        this.mapper = mapper;
        this.sap = sap;
    }

    private static final class Session {
        final String id = UUID.randomUUID().toString();
        final CadStructure structure;
        EctrSessionStatus status;
        String assemblyDirKey;
        final Map<String, String> dirByPart = new LinkedHashMap<>();
        final Map<String, Boolean> reusedByDir = new LinkedHashMap<>();
        final Map<String, Boolean> materialCreated = new LinkedHashMap<>();
        Boolean bomCreated;
        final List<EctrSessionView.Step> steps = new ArrayList<>();

        Session(CadStructure structure) {
            this.structure = structure;
        }

        void step(String name, String call, List<String> messages) {
            steps.add(new EctrSessionView.Step(name, call, Instant.now(), List.copyOf(messages)));
        }
    }

    // ---- Step 1 ------------------------------------------------------------------------------

    public EctrSessionView createDocuments(CadAssembly cad) {
        CadStructure structure = mapper.analyze(cad);
        Session s = new Session(structure);
        List<String> messages = new ArrayList<>();

        synchronized (s) {
            s.assemblyDirKey = createDir(s, structure.partNumber(), cad.revision(), cad.description(), cad.fileName(), messages);
            for (CadStructure.Line line : structure.lines()) {
                createDir(s, line.partNumber(), line.revision() == null ? "-" : line.revision(),
                        line.description(), line.fileName(), messages);
            }
            s.status = EctrSessionStatus.DOCUMENTS_CREATED;
            s.step("1. Create documents", "createDocuments(files, metadata)", messages);
            sessions.put(s.id, s);
        }
        log.info("[MOCK ECTR] session {}: {} DIRs for {} rev {}", s.id, s.dirByPart.size(), structure.partNumber(), cad.revision());
        return view(s);
    }

    private String createDir(Session s, String part, String revision, String description, String cadFile,
                             List<String> messages) {
        MockSapPlmBackend.Upsert<DocumentInfoRecord> dir = sap.createDocument(part, revision, description, cadFile);
        String key = dir.value().dirKey();
        s.dirByPart.put(part, key);
        s.reusedByDir.put(key, !dir.created());
        messages.add(dir.created()
                ? "DIR " + key + " created for " + part + " (" + cadFile + " -> " + dir.value().sapFileName() + ")"
                : "DIR " + key + " already exists for " + part + " rev " + revision + " and is reused");
        return key;
    }

    // ---- Step 2 ------------------------------------------------------------------------------

    public EctrSessionView checkIn(String sessionId) {
        Session s = session(sessionId);
        synchronized (s) {
            require(s, EctrSessionStatus.DOCUMENTS_CREATED, "check in");
            List<String> messages = new ArrayList<>();
            for (String dirKey : s.dirByPart.values()) {
                DocumentInfoRecord dir = sap.checkIn(dirKey);
                messages.add("Original " + dir.sapFileName() + " stored on content repository as " + dir.originalId());
            }
            List<StructureLink> links = new ArrayList<>();
            for (CadStructure.Line line : s.structure.lines()) {
                links.add(new StructureLink(s.dirByPart.get(line.partNumber()), line.partNumber(), line.structureQuantity()));
            }
            sap.setStructure(s.assemblyDirKey, links);
            messages.add("Document structure (BoD) of " + s.structure.partNumber() + ": " + links.size()
                    + " child documents, mirroring the CAD assembly (including non-BOM parts)");
            s.status = EctrSessionStatus.CHECKED_IN;
            s.step("2. Check in", "save(loadedParts, parts, SaveOption.SAVECHECKIN)", messages);
            return view(s);
        }
    }

    // ---- Step 3 ------------------------------------------------------------------------------

    public EctrSessionView createMaterials(String sessionId) {
        Session s = session(sessionId);
        synchronized (s) {
            require(s, EctrSessionStatus.CHECKED_IN, "create materials");
            List<String> messages = new ArrayList<>();
            CadAssembly cad = s.structure.assembly();
            material(s, s.structure.partNumber(), cad.description(), messages);
            for (CadStructure.Line line : s.structure.lines()) {
                if (line.bomRelevant()) {
                    material(s, line.partNumber(), line.description(), messages);
                } else {
                    messages.add(line.partNumber() + ": no material (document only, not BOM-relevant)");
                }
            }
            s.status = EctrSessionStatus.MATERIALS_CREATED;
            s.step("3. Create materials", "materialCreate(material, part)", messages);
            return view(s);
        }
    }

    private void material(Session s, String part, String description, List<String> messages) {
        MockSapPlmBackend.Upsert<MockSapPlmBackend.MaterialRecord> m = sap.createMaterial(part, description);
        String dirKey = s.dirByPart.get(part);
        sap.linkMaterial(dirKey, part);
        s.materialCreated.put(part, m.created());
        messages.add((m.created() ? "Material " + part + " created" : "Material " + part + " already exists")
                + ", linked to DIR " + dirKey);
    }

    // ---- Step 4 ------------------------------------------------------------------------------

    public EctrSessionView maintainBom(String sessionId) {
        Session s = session(sessionId);
        synchronized (s) {
            require(s, EctrSessionStatus.MATERIALS_CREATED, "maintain BOM");
            List<BomItemRecord> items = new ArrayList<>();
            int pos = 10;
            for (CadStructure.Line line : s.structure.bomLines()) {
                items.add(new BomItemRecord(String.format("%04d", pos), line.partNumber(), line.bomQuantity(), line.unit()));
                pos += 10;
            }
            CadAssembly cad = s.structure.assembly();
            MockSapPlmBackend.Upsert<MockSapPlmBackend.BomRecord> bom =
                    sap.maintainBom(s.structure.partNumber(), cad.plant(), cad.changeNumber(), items);
            s.bomCreated = bom.created();

            List<String> messages = new ArrayList<>();
            messages.add((bom.created() ? "Material BOM created" : "Material BOM updated") + " for "
                    + s.structure.partNumber() + " plant " + cad.plant() + " with " + items.size() + " items"
                    + (cad.changeNumber() != null ? " (change number " + cad.changeNumber() + ")" : ""));
            items.forEach(i -> messages.add("  " + i.itemNumber() + "  " + i.componentMaterial() + "  "
                    + i.quantity().toPlainString() + " " + i.unit()));
            s.status = EctrSessionStatus.BOM_MAINTAINED;
            s.step("4. Maintain BOM", "cdeskBomWizard(part, materialHead, items)", messages);
            return view(s);
        }
    }

    public EctrSessionView get(String sessionId) {
        Session s = session(sessionId);
        synchronized (s) {
            return view(s);
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    private Session session(String id) {
        Session s = sessions.get(id);
        if (s == null) {
            throw new EctrSessionNotFoundException(id);
        }
        return s;
    }

    private static void require(Session s, EctrSessionStatus expected, String step) {
        if (s.status != expected) {
            throw new EctrStateException("Cannot " + step + ": session is " + s.status + ", expected " + expected
                    + ". ECTR steps run in order: create documents -> check in -> create materials -> maintain BOM.");
        }
    }

    private static String nextStep(EctrSessionStatus status) {
        return switch (status) {
            case DOCUMENTS_CREATED -> "checkin";
            case CHECKED_IN -> "materials";
            case MATERIALS_CREATED -> "bom";
            case BOM_MAINTAINED -> null;
        };
    }

    private EctrSessionView view(Session s) {
        CadStructure st = s.structure;
        CadAssembly cad = st.assembly();

        List<EctrSessionView.Document> documents = new ArrayList<>();
        Map<String, Boolean> bomRelevantByPart = new LinkedHashMap<>();
        bomRelevantByPart.put(st.partNumber(), true);
        st.lines().forEach(l -> bomRelevantByPart.put(l.partNumber(), l.bomRelevant()));
        s.dirByPart.forEach((part, dirKey) -> sap.findDocument(dirKey).ifPresent(d -> documents.add(
                new EctrSessionView.Document(d.dirKey(), d.partNumber(), d.cadRevision(), d.description(),
                        d.cadFileName(), d.sapFileName(), d.status().name(), d.originalId(), d.linkedMaterial(),
                        dirKey.equals(s.assemblyDirKey), bomRelevantByPart.getOrDefault(part, false),
                        s.reusedByDir.getOrDefault(dirKey, false)))));

        List<EctrSessionView.StructureItem> structure = sap.structureOf(s.assemblyDirKey).stream()
                .map(l -> new EctrSessionView.StructureItem(s.assemblyDirKey, l.childDirKey(), l.partNumber(),
                        l.quantity(), bomRelevantByPart.getOrDefault(l.partNumber(), false)))
                .toList();
        if (s.status == EctrSessionStatus.DOCUMENTS_CREATED) {
            structure = List.of(); // BoD is only written on check-in
        }

        List<EctrSessionView.Material> materials = new ArrayList<>();
        s.materialCreated.forEach((m, created) ->
                materials.add(new EctrSessionView.Material(m, descriptionOf(st, m), s.dirByPart.get(m), created)));

        EctrSessionView.Bom bom = null;
        if (s.bomCreated != null) {
            bom = sap.findBom(st.partNumber(), cad.plant())
                    .map(b -> new EctrSessionView.Bom(b.materialNumber(), b.plant(), b.changeNumber(), s.bomCreated,
                            b.items().stream().map(i -> new EctrSessionView.Bom.Item(i.itemNumber(),
                                    i.componentMaterial(), i.quantity(), i.unit())).toList(),
                            "The BOM exists in SAP but is not released for production; "
                                    + "release follows the change-number / status process."))
                    .orElse(null);
        }

        return new EctrSessionView(s.id, "MOCK", s.status.name(), nextStep(s.status),
                new EctrSessionView.Assembly(st.partNumber(), cad.revision(), cad.plant(), cad.changeNumber(),
                        cad.description(), cad.cadSystem(), cad.fileName()),
                st.cadInstances(), st.excludedInstances(), st.notes(), documents, structure, materials, bom,
                List.copyOf(s.steps), sap.counts());
    }

    private static String descriptionOf(CadStructure st, String part) {
        if (part.equals(st.partNumber())) {
            return st.assembly().description();
        }
        return st.lines().stream().filter(l -> l.partNumber().equals(part))
                .findFirst().map(CadStructure.Line::description).orElse(null);
    }
}
