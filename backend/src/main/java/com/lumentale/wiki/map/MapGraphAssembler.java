package com.lumentale.wiki.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.lumentale.wiki.map.dto.MapGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure assembler for the world connectivity graph. Given the flat
 * {@code map_graph_edge} rows (from/to map guid + a resolved {@code conditions}
 * jsonb) plus a map-guid→name lookup, it groups edges into per-source nodes and
 * produces the {@link MapGraph} DTO — no DB, no Spring, so the grouping/naming
 * logic is unit-testable on a synthetic edge list.
 *
 * <p>Unlike v2 (which parsed the {@code UIMapConnectivityGraph} JSON and resolved
 * SerializeReference {@code rid}s at read time), v3 reads the edges already
 * persisted in {@code map_graph_edge} with conditions resolved at seed time, so
 * this layer only does the grouping + name join.
 */
public final class MapGraphAssembler {

    private MapGraphAssembler() {}

    /** One flat edge row as read from {@code map_graph_edge}. */
    public record EdgeRow(String fromGuid, String toGuid, JsonNode conditions) {}

    /**
     * Group edges by source map, attach destination names, and emit nodes sorted by
     * name. A node is created for every distinct source map that has an outgoing edge.
     */
    public static MapGraph assemble(List<EdgeRow> edges, Map<String, String> mapName) {
        Map<String, MapGraph.Node> byMap = new LinkedHashMap<>();
        for (EdgeRow e : edges) {
            MapGraph.Node node = byMap.computeIfAbsent(e.fromGuid(),
                g -> new MapGraph.Node(g, mapName.getOrDefault(g, g), new ArrayList<>()));
            node.edges().add(new MapGraph.Edge(
                e.toGuid(), mapName.getOrDefault(e.toGuid(), e.toGuid()), e.conditions()));
        }
        List<MapGraph.Node> nodes = new ArrayList<>(byMap.values());
        nodes.sort(Comparator.comparing(MapGraph.Node::name, Comparator.nullsLast(Comparator.naturalOrder())));
        return new MapGraph(nodes);
    }
}
