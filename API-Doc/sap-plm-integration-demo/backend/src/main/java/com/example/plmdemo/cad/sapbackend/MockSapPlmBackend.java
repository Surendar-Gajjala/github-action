package com.example.plmdemo.cad.sapbackend;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MOCK of the PLM functions inside SAP ECC / S/4HANA that SAP ECTR writes to:
 * Document Info Records (DIR), originals on a content repository, document structure (BoD),
 * material master with object links, and material BOMs.
 *
 * In-memory only. Keys and numbers look SAP-like for teaching purposes; nothing here is a real SAP record.
 */
@Component
public class MockSapPlmBackend {

    public static final String DOCUMENT_TYPE = "CAD";

    public enum DocumentStatus { CREATED, CHECKED_IN }

    public record DocumentInfoRecord(
            String dirKey, String documentType, String documentNumber, String documentPart, String version,
            String partNumber, String cadRevision, String description, String cadFileName, String sapFileName,
            DocumentStatus status, String originalId, String linkedMaterial) {

        DocumentInfoRecord checkedIn(String original) {
            return new DocumentInfoRecord(dirKey, documentType, documentNumber, documentPart, version, partNumber,
                    cadRevision, description, cadFileName, sapFileName, DocumentStatus.CHECKED_IN, original, linkedMaterial);
        }

        DocumentInfoRecord linkedTo(String material) {
            return new DocumentInfoRecord(dirKey, documentType, documentNumber, documentPart, version, partNumber,
                    cadRevision, description, cadFileName, sapFileName, status, originalId, material);
        }
    }

    public record StructureLink(String childDirKey, String partNumber, BigDecimal quantity) {
    }

    public record MaterialRecord(String materialNumber, String description) {
    }

    public record BomItemRecord(String itemNumber, String componentMaterial, BigDecimal quantity, String unit) {
    }

    public record BomRecord(String materialNumber, String plant, String changeNumber, List<BomItemRecord> items) {
        public BomRecord {
            items = List.copyOf(items);
        }
    }

    /** Result wrapper telling the caller whether an object was new or already existed. */
    public record Upsert<T>(T value, boolean created) {
    }

    private final AtomicLong documentNumbers = new AtomicLong(4_700_000_000L);
    private final AtomicLong originalNumbers = new AtomicLong();
    private final Map<String, DocumentInfoRecord> documents = new ConcurrentHashMap<>();
    private final Map<String, String> dirKeyByCadPart = new ConcurrentHashMap<>();
    private final Map<String, List<StructureLink>> structures = new ConcurrentHashMap<>();
    private final Map<String, MaterialRecord> materials = new ConcurrentHashMap<>();
    private final Map<String, BomRecord> boms = new ConcurrentHashMap<>();

    /** Create a DIR for a CAD part revision, or return the existing one. */
    public synchronized Upsert<DocumentInfoRecord> createDocument(String partNumber, String cadRevision,
                                                                 String description, String cadFileName) {
        String cadKey = partNumber + "|" + cadRevision;
        String existingKey = dirKeyByCadPart.get(cadKey);
        if (existingKey != null) {
            return new Upsert<>(documents.get(existingKey), false);
        }
        String number = String.format("%010d", documentNumbers.incrementAndGet());
        String dirKey = DOCUMENT_TYPE + "/" + number + "/000/00";
        String extension = cadFileName != null && cadFileName.contains(".")
                ? cadFileName.substring(cadFileName.lastIndexOf('.'))
                : ".cad";
        DocumentInfoRecord dir = new DocumentInfoRecord(dirKey, DOCUMENT_TYPE, number, "000", "00",
                partNumber, cadRevision, description, cadFileName, number + "_000_00" + extension,
                DocumentStatus.CREATED, null, null);
        documents.put(dirKey, dir);
        dirKeyByCadPart.put(cadKey, dirKey);
        return new Upsert<>(dir, true);
    }

    /** Store the original file on the (mock) content repository and mark the DIR checked in. */
    public synchronized DocumentInfoRecord checkIn(String dirKey) {
        DocumentInfoRecord dir = require(dirKey);
        String original = dir.originalId() != null
                ? dir.originalId()
                : String.format("MOCK-CS-%06d", originalNumbers.incrementAndGet());
        DocumentInfoRecord updated = dir.checkedIn(original);
        documents.put(dirKey, updated);
        return updated;
    }

    /** Replace the document structure (BoD) below a parent DIR. */
    public synchronized void setStructure(String parentDirKey, List<StructureLink> children) {
        require(parentDirKey);
        structures.put(parentDirKey, List.copyOf(children));
    }

    public synchronized Upsert<MaterialRecord> createMaterial(String materialNumber, String description) {
        MaterialRecord existing = materials.get(materialNumber);
        if (existing != null) {
            return new Upsert<>(existing, false);
        }
        MaterialRecord created = new MaterialRecord(materialNumber, description);
        materials.put(materialNumber, created);
        return new Upsert<>(created, true);
    }

    /** Object link Material <-> DIR. */
    public synchronized DocumentInfoRecord linkMaterial(String dirKey, String materialNumber) {
        if (!materials.containsKey(materialNumber)) {
            throw new IllegalStateException("Material " + materialNumber + " does not exist");
        }
        DocumentInfoRecord updated = require(dirKey).linkedTo(materialNumber);
        documents.put(dirKey, updated);
        return updated;
    }

    public synchronized Upsert<BomRecord> maintainBom(String materialNumber, String plant, String changeNumber,
                                                     List<BomItemRecord> items) {
        if (!materials.containsKey(materialNumber)) {
            throw new IllegalStateException("Header material " + materialNumber + " does not exist");
        }
        for (BomItemRecord item : items) {
            if (!materials.containsKey(item.componentMaterial())) {
                throw new IllegalStateException("Component material " + item.componentMaterial() + " does not exist");
            }
        }
        String key = materialNumber + "|" + plant;
        boolean created = !boms.containsKey(key);
        BomRecord bom = new BomRecord(materialNumber, plant, changeNumber, items);
        boms.put(key, bom);
        return new Upsert<>(bom, created);
    }

    public Optional<DocumentInfoRecord> findDocument(String dirKey) {
        return Optional.ofNullable(documents.get(dirKey));
    }

    public List<StructureLink> structureOf(String parentDirKey) {
        return structures.getOrDefault(parentDirKey, List.of());
    }

    public Optional<BomRecord> findBom(String materialNumber, String plant) {
        return Optional.ofNullable(boms.get(materialNumber + "|" + plant));
    }

    /** Counts for the UI: how many objects exist in the mock SAP backend overall. */
    public Map<String, Integer> counts() {
        return Map.of("documents", documents.size(),
                "originals", (int) documents.values().stream().filter(d -> d.originalId() != null).count(),
                "materials", materials.size(),
                "boms", boms.size());
    }

    private DocumentInfoRecord require(String dirKey) {
        DocumentInfoRecord dir = documents.get(dirKey);
        if (dir == null) {
            throw new IllegalStateException("DIR " + dirKey + " does not exist");
        }
        return dir;
    }

    /** For tests and the UI: all DIR keys created so far. */
    public List<String> allDirKeys() {
        return new ArrayList<>(documents.keySet());
    }
}
