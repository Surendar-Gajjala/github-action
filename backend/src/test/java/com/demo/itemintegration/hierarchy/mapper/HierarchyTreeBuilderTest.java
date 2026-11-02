package com.demo.itemintegration.hierarchy.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.demo.itemintegration.external.dto.ExternalGraphResponse;
import com.demo.itemintegration.hierarchy.dto.HierarchyNodeDto;
import com.demo.itemintegration.hierarchy.model.NodeKind;
import com.demo.itemintegration.item.model.StructureRole;
import com.fasterxml.jackson.databind.ObjectMapper;

class HierarchyTreeBuilderTest {

    private final ObjectMapper json = new ObjectMapper();
    private final HierarchyTreeBuilder builder = new HierarchyTreeBuilder(new HierarchyMapper());

    private ExternalGraphResponse graph(String nodes, String edges) throws Exception {
        return json.readValue("{\"nodes\": [" + nodes + "], \"edges\": [" + edges + "]}", ExternalGraphResponse.class);
    }

    private static String item(long id, String number) {
        return "{\"id\": " + id + ", \"type\": \"item\", \"properties\": {\"item_number\": \"" + number + "\"}}";
    }

    private static String part(long id, String number, String manufacturer) {
        return "{\"id\": " + id + ", \"type\": \"part\", \"alias\": \"leaf\", \"properties\": {\"part_number\": \""
                + number + "\", \"manufacturer\": \"" + manufacturer + "\"}}";
    }

    private static String bom(long from, long to, String qty) {
        return "{\"id\": " + (from * 1000 + to) + ", \"type\": \"item_bom\", \"label\": \"Child Items\", \"fromNodeId\": "
                + from + ", \"toNodeId\": " + to + ", \"properties\": {\"qty_per_bom.qty\": \"" + qty + "\"}}";
    }

    private static String sources(long from, long to) {
        return "{\"id\": " + (from * 1000 + to) + ", \"type\": \"item_sources\", \"label\": \"Sources\", \"fromNodeId\": "
                + from + ", \"toNodeId\": " + to + ", \"properties\": {\"qty_per_bom.qty\": null}}";
    }

    @Test
    void buildsBomItemsAndSourcedPartsBelowEachAnchor() throws Exception {
        ExternalGraphResponse g = graph(
                String.join(",", item(1, "903239"), item(2, "E70293-013"), item(3, "K33608-001"),
                        part(9, "FTLX8574D3BCV-IT", "FINISAR CORPORATION")),
                String.join(",", bom(1, 2, "1"), bom(2, 3, "0.001"), sources(2, 9)));

        List<HierarchyNodeDto> roots = builder.build(List.of("903239"), g);

        assertThat(roots).hasSize(1);
        HierarchyNodeDto root = roots.get(0);
        assertThat(root.key()).isEqualTo("1");
        assertThat(root.kind()).isEqualTo(NodeKind.ITEM);
        assertThat(root.qty()).isNull();
        assertThat(root.item().itemNumber()).isEqualTo("903239");

        HierarchyNodeDto child = root.children().get(0);
        assertThat(child.key()).isEqualTo("1/2");
        assertThat(child.qty()).isEqualByComparingTo("1");
        assertThat(child.children()).extracting(HierarchyNodeDto::kind).containsExactly(NodeKind.ITEM, NodeKind.PART);

        HierarchyNodeDto grandChild = child.children().get(0);
        assertThat(grandChild.item().itemNumber()).isEqualTo("K33608-001");
        assertThat(grandChild.qty()).isEqualByComparingTo(new BigDecimal("0.001"));

        HierarchyNodeDto part = child.children().get(1);
        assertThat(part.key()).isEqualTo("1/2/9");
        assertThat(part.item()).isNull();
        assertThat(part.qty()).isNull();
        assertThat(part.part().partNumber()).isEqualTo("FTLX8574D3BCV-IT");
        assertThat(part.part().manufacturer()).isEqualTo("FINISAR CORPORATION");
        assertThat(part.children()).isEmpty();
    }

    @Test
    void sharedChildRepeatsUnderEveryParentWithItsOwnQty() throws Exception {
        // item_bom is many-to-many: item 3 is used by both products.
        ExternalGraphResponse g = graph(
                String.join(",", item(1, "P-1"), item(2, "P-2"), item(3, "SHARED"), part(9, "PRT", "ACME")),
                String.join(",", bom(1, 3, "2"), bom(2, 3, "6"), sources(3, 9)));

        List<HierarchyNodeDto> roots = builder.build(List.of("P-1", "P-2"), g);

        HierarchyNodeDto underFirst = roots.get(0).children().get(0);
        HierarchyNodeDto underSecond = roots.get(1).children().get(0);
        assertThat(underFirst.item().itemNumber()).isEqualTo("SHARED");
        assertThat(underSecond.item().itemNumber()).isEqualTo("SHARED");
        assertThat(underFirst.qty()).isEqualByComparingTo("2");
        assertThat(underSecond.qty()).isEqualByComparingTo("6");
        assertThat(underFirst.key()).isEqualTo("1/3");
        assertThat(underSecond.key()).isEqualTo("2/3");
        assertThat(underFirst.children().get(0).key()).isEqualTo("1/3/9");
        assertThat(underSecond.children().get(0).key()).isEqualTo("2/3/9");
    }

    @Test
    void rootsFollowAnchorOrderAndSkipMissingAnchors() throws Exception {
        ExternalGraphResponse g = graph(String.join(",", item(1, "A"), item(2, "B")), "");

        List<HierarchyNodeDto> roots = builder.build(List.of("B", "MISSING", "A"), g);

        assertThat(roots).extracting(r -> r.item().itemNumber()).containsExactly("B", "A");
    }

    @Test
    void ordersChildrenItemsFirstThenParts() throws Exception {
        ExternalGraphResponse g = graph(
                String.join(",", item(1, "ROOT"), part(5, "Z-PART", "M"), item(3, "C"), part(4, "A-PART", "M"),
                        item(2, "B")),
                String.join(",", sources(1, 5), bom(1, 3, "1"), sources(1, 4), bom(1, 2, "1")));

        List<HierarchyNodeDto> children = builder.build(List.of("ROOT"), g).get(0).children();

        assertThat(children).extracting(c -> c.item() != null ? c.item().itemNumber() : c.part().partNumber())
                .containsExactly("B", "C", "A-PART", "Z-PART");
    }

    @Test
    void cutsCyclesAndIgnoresDanglingEdges() throws Exception {
        ExternalGraphResponse g = graph(
                String.join(",", item(1, "A"), item(2, "B")),
                String.join(",", bom(1, 2, "1"), bom(2, 1, "1"), bom(2, 777, "1")));

        HierarchyNodeDto root = builder.build(List.of("A"), g).get(0);

        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).children()).isEmpty();
    }

    @Test
    void stopsAtMaxDepth() throws Exception {
        StringBuilder nodes = new StringBuilder(item(0, "L0"));
        StringBuilder edges = new StringBuilder();
        for (int i = 1; i <= 9; i++) {
            nodes.append(',').append(item(i, "L" + i));
            edges.append(i > 1 ? "," : "").append(bom(i - 1, i, "1"));
        }

        HierarchyNodeDto node = builder.build(List.of("L0"), graph(nodes.toString(), edges.toString())).get(0);
        int depth = 0;
        while (!node.children().isEmpty()) {
            node = node.children().get(0);
            depth++;
        }

        assertThat(depth).isEqualTo(HierarchyTreeBuilder.MAX_DEPTH);
    }

    @Test
    void mapsItemColumnsWithReadableLabels() throws Exception {
        ExternalGraphResponse g = graph("""
                {"id": 1, "type": "item", "properties": {
                  "item_number": "9999MJ", "description": "NIC X710T2LOCPV3", "revision": "09", "item_type": "BD",
                  "item_status": "PRODN_APPROVED", "make_buy": "MAKE", "design_group": "LAD", "restricted": "false",
                  "exemption_status": "N_A", "project": "CAMPBELL_FLAT",
                  "environmental_compliance.collection": "", "environmental_compliance.collection_lock": "NOT_LOCKED",
                  "environmental_compliance.eu_rohs": "YES", "environmental_compliance.plastic_lead_halogen": "NO_DATA",
                  "environmental_compliance.pnr": "PNR", "business_unit": "NPO", "is_product": "true",
                  "structure_role": "BOM"}}
                """, "");

        var item = builder.build(List.of("9999MJ"), g).get(0).item();

        assertThat(item.itemType()).isEqualTo("BD");
        assertThat(item.itemStatus()).isEqualTo("Production Approved");
        assertThat(item.makeBuy()).isEqualTo("MAKE");
        assertThat(item.restricted()).isFalse();
        assertThat(item.exemptionStatus()).isEqualTo("N/A");
        assertThat(item.sourceNotes()).isNull();
        assertThat(item.collection()).isNull();
        assertThat(item.collectionLock()).isEqualTo("Not Locked");
        assertThat(item.euRohs()).isEqualTo("Yes");
        assertThat(item.plasticLeadHalogen()).isEqualTo("No Data");
        assertThat(item.pnr()).isEqualTo("PNR");
        assertThat(item.ecExemptions()).isNull();
        assertThat(item.isProduct()).isTrue();
        assertThat(item.structureRole()).isEqualTo(StructureRole.BOM);
    }
}
