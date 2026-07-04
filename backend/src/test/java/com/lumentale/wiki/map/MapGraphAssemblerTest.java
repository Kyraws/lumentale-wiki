package com.lumentale.wiki.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.lumentale.wiki.map.dto.MapGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure tests for the connectivity-graph assembly. No DB — edges + a name lookup
 * are passed directly, so grouping, name resolution, condition pass-through and
 * node ordering are pinned on a synthetic graph.
 */
class MapGraphAssemblerTest {

    private static final ObjectMapper M = new ObjectMapper();

    @Test
    void groupsEdgesBySource_resolvesNames_andSortsNodesByName() {
        ArrayNode empty = M.createArrayNode();
        ArrayNode gated = M.createArrayNode();
        gated.add(M.createObjectNode().put("type", "BoolVariableCondition").put("flag", "door_open"));

        List<MapGraphAssembler.EdgeRow> edges = List.of(
            new MapGraphAssembler.EdgeRow("zeta", "alpha", empty),     // source name "Zeta"
            new MapGraphAssembler.EdgeRow("alpha", "zeta", gated),     // source name "Alpha"
            new MapGraphAssembler.EdgeRow("alpha", "beta", empty));    // second edge from Alpha

        Map<String, String> names = Map.of("alpha", "Alpha", "beta", "Beta", "zeta", "Zeta");
        MapGraph g = MapGraphAssembler.assemble(edges, names);

        // two source nodes, sorted by name → Alpha before Zeta
        assertEquals(2, g.nodes().size());
        assertEquals("Alpha", g.nodes().get(0).name());
        assertEquals("Zeta", g.nodes().get(1).name());

        MapGraph.Node alpha = g.nodes().get(0);
        assertEquals("alpha", alpha.mapGuid());
        assertEquals(2, alpha.edges().size());
        // first edge resolved name + carried conditions
        MapGraph.Edge toZeta = alpha.edges().get(0);
        assertEquals("zeta", toZeta.toGuid());
        assertEquals("Zeta", toZeta.toName());
        assertEquals("door_open", toZeta.conditions().get(0).path("flag").asText());
    }

    @Test
    void unmappedGuidFallsBackToGuidAsName() {
        List<MapGraphAssembler.EdgeRow> edges = List.of(
            new MapGraphAssembler.EdgeRow("x", "y", M.createArrayNode()));
        MapGraph g = MapGraphAssembler.assemble(edges, Map.of());
        assertEquals("x", g.nodes().get(0).name());          // no name → guid
        assertEquals("y", g.nodes().get(0).edges().get(0).toName());
    }
}
