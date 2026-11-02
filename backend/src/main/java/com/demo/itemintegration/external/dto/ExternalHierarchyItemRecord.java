package com.demo.itemintegration.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Properties of an item node in the graph-match response, exactly as the hosted server
 * names them (the {@code select.item} list in {@code item-hierarchy-graph.json}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalHierarchyItemRecord(
        @JsonProperty("item_number") JsonNode itemNumber,
        @JsonProperty("description") JsonNode description,
        @JsonProperty("revision") JsonNode revision,
        @JsonProperty("item_type") JsonNode itemType,
        @JsonProperty("item_status") JsonNode itemStatus,
        @JsonProperty("make_buy") JsonNode makeBuy,
        @JsonProperty("design_group") JsonNode designGroup,
        @JsonProperty("restricted") JsonNode restricted,
        @JsonProperty("exemption_status") JsonNode exemptionStatus,
        @JsonProperty("project") JsonNode project,
        @JsonProperty("source_notes") JsonNode sourceNotes,
        @JsonProperty("environmental_compliance.collection") JsonNode collection,
        @JsonProperty("environmental_compliance.collection_lock") JsonNode collectionLock,
        @JsonProperty("environmental_compliance.eu_rohs") JsonNode euRohs,
        @JsonProperty("environmental_compliance.eu_rohs_lock") JsonNode euRohsLock,
        @JsonProperty("environmental_compliance.ec_exemptions") JsonNode ecExemptions,
        @JsonProperty("environmental_compliance.pwb_lead_halogen") JsonNode pwbLeadHalogen,
        @JsonProperty("environmental_compliance.pwb_lead_halogen_lock") JsonNode pwbLeadHalogenLock,
        @JsonProperty("environmental_compliance.plastic_lead_halogen") JsonNode plasticLeadHalogen,
        @JsonProperty("environmental_compliance.plastic_lead_halogen_lock") JsonNode plasticLeadHalogenLock,
        @JsonProperty("environmental_compliance.pnr") JsonNode pnr,
        @JsonProperty("environmental_compliance.pnr_lock") JsonNode pnrLock,
        @JsonProperty("business_unit") JsonNode businessUnit,
        @JsonProperty("is_product") JsonNode isProduct,
        @JsonProperty("structure_role") JsonNode structureRole) {
}
