package com.example.plmdemo.cad.ectr;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** JSON view of a simulated ECTR session and the SAP backend objects it created (all MOCK). */
public record EctrSessionView(
        String sessionId,
        String mode,
        String status,
        String nextStep,
        Assembly assembly,
        int cadInstances,
        int excludedInstances,
        List<String> notes,
        List<Document> documents,
        List<StructureItem> documentStructure,
        List<Material> materials,
        Bom bom,
        List<Step> steps,
        Map<String, Integer> sapBackendTotals) {

    public record Assembly(String partNumber, String revision, String plant, String changeNumber, String description,
                           String cadSystem, String fileName) {
    }

    public record Document(String dirKey, String partNumber, String cadRevision, String description,
                           String cadFileName, String sapFileName, String status, String originalId,
                           String linkedMaterial, boolean assembly, boolean bomRelevant, boolean reused) {
    }

    public record StructureItem(String parentDirKey, String childDirKey, String partNumber, BigDecimal quantity,
                                boolean bomRelevant) {
    }

    public record Material(String materialNumber, String description, String linkedDirKey, boolean created) {
    }

    public record Bom(String materialNumber, String plant, String changeNumber, boolean created,
                      List<Item> items, String note) {
        public record Item(String itemNumber, String componentMaterial, BigDecimal quantity, String unit) {
        }
    }

    /** One simulated ECTR step: which ECTR Connector call it stands for and what happened in SAP. */
    public record Step(String step, String connectorCall, Instant at, List<String> messages) {
    }
}
