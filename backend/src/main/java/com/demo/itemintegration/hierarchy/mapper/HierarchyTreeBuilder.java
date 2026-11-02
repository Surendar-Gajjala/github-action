package com.demo.itemintegration.hierarchy.mapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.demo.itemintegration.external.dto.ExternalGraphResponse;
import com.demo.itemintegration.external.dto.ExternalGraphResponse.ExternalGraphEdge;
import com.demo.itemintegration.external.dto.ExternalGraphResponse.ExternalGraphNode;
import com.demo.itemintegration.hierarchy.dto.HierarchyNodeDto;
import com.demo.itemintegration.hierarchy.model.NodeKind;

/**
 * Turns the flat graph-match response into the nested Item Hierarchy, in memory.
 * <ul>
 *   <li>Roots are the anchor items, in anchor order; anchors missing from the graph are skipped.</li>
 *   <li>item_bom is many-to-many, so a shared item (and everything below it) is repeated
 *       under every parent, each occurrence carrying the qty of its own edge.</li>
 *   <li>item_sources parts are leaves.</li>
 *   <li>Children: BOM items first (by item number), then parts (by part number).</li>
 *   <li>Cycles are cut and depth is limited to {@link #MAX_DEPTH}, the traversal depth requested.</li>
 *   <li>Edges whose nodes are not in the response are ignored.</li>
 * </ul>
 */
@Component
public class HierarchyTreeBuilder {

    static final int MAX_DEPTH = 6;

    private static final Comparator<HierarchyNodeDto> CHILD_ORDER = Comparator
            .comparing(HierarchyNodeDto::kind)
            .thenComparing(HierarchyTreeBuilder::sortLabel, Comparator.nullsLast(Comparator.naturalOrder()));

    private final HierarchyMapper mapper;

    public HierarchyTreeBuilder(HierarchyMapper mapper) {
        this.mapper = mapper;
    }

    public List<HierarchyNodeDto> build(List<String> anchors, ExternalGraphResponse graph) {
        Map<Long, ExternalGraphNode> nodesById = new HashMap<>();
        Map<String, ExternalGraphNode> itemsByNumber = new HashMap<>();
        for (ExternalGraphNode node : graph.nodes()) {
            if (node == null || node.id() == null || mapper.kindOf(node) == null) {
                continue;
            }
            nodesById.putIfAbsent(node.id(), node);
            if (mapper.kindOf(node) == NodeKind.ITEM) {
                String number = mapper.toItem(node).itemNumber();
                if (number != null) {
                    itemsByNumber.putIfAbsent(number, node);
                }
            }
        }

        Map<Long, List<ExternalGraphEdge>> childEdges = new HashMap<>();
        for (ExternalGraphEdge edge : graph.edges()) {
            if (edge != null && nodesById.containsKey(edge.fromNodeId()) && nodesById.containsKey(edge.toNodeId())) {
                childEdges.computeIfAbsent(edge.fromNodeId(), id -> new ArrayList<>()).add(edge);
            }
        }

        Tree tree = new Tree(nodesById, childEdges);
        List<HierarchyNodeDto> roots = new ArrayList<>();
        Set<String> seenAnchors = new HashSet<>();
        for (String anchor : anchors) {
            ExternalGraphNode root = itemsByNumber.get(anchor);
            if (root != null && seenAnchors.add(anchor)) {
                roots.add(tree.build(root, null, null, 0, new HashSet<>()));
            }
        }
        return roots;
    }

    private final class Tree {

        private final Map<Long, ExternalGraphNode> nodesById;
        private final Map<Long, List<ExternalGraphEdge>> childEdges;

        Tree(Map<Long, ExternalGraphNode> nodesById, Map<Long, List<ExternalGraphEdge>> childEdges) {
            this.nodesById = nodesById;
            this.childEdges = childEdges;
        }

        HierarchyNodeDto build(ExternalGraphNode node, BigDecimal qty, String parentKey, int depth, Set<Long> onPath) {
            String key = parentKey == null ? String.valueOf(node.id()) : parentKey + "/" + node.id();
            NodeKind kind = mapper.kindOf(node);
            if (kind == NodeKind.PART) {
                return new HierarchyNodeDto(key, kind, node.id(), qty, null, mapper.toPart(node), List.of());
            }

            List<HierarchyNodeDto> children = new ArrayList<>();
            if (depth < MAX_DEPTH) {
                onPath.add(node.id());
                for (ExternalGraphEdge edge : childEdges.getOrDefault(node.id(), List.of())) {
                    ExternalGraphNode child = nodesById.get(edge.toNodeId());
                    if (!onPath.contains(child.id())) {
                        children.add(build(child, mapper.qtyOf(edge), key, depth + 1, onPath));
                    }
                }
                onPath.remove(node.id());
                children.sort(CHILD_ORDER);
            }
            return new HierarchyNodeDto(key, kind, node.id(), qty, mapper.toItem(node), null, children);
        }
    }

    private static String sortLabel(HierarchyNodeDto node) {
        return node.item() != null ? node.item().itemNumber() : node.part() != null ? node.part().partNumber() : null;
    }
}
