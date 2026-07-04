package com.lumentale.wiki.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumentale.wiki.map.dto.MapGraph;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The world-map connectivity graph, built ONCE at startup (static post-seed data).
 * Reads the persisted {@code map_graph_edge} rows + a map-guid→name lookup from the
 * DB and delegates the grouping/naming to the pure {@link MapGraphAssembler}.
 *
 * <p>If {@code map_graph_edge} is empty (world not seeded), the graph is left
 * absent and {@code /api/map-graph} 404s — matching v2's behaviour when its source
 * file was missing.
 */
@Component
public class MapGraphIndex {

    private static final Logger log = LoggerFactory.getLogger(MapGraphIndex.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private MapGraph graph;   // null if there were no edges at startup
    private Map<String, String> names = Map.of();   // guid → resolved display name

    public MapGraphIndex(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<MapGraph> get() { return Optional.ofNullable(graph); }

    /** Resolved display name (curated → map_name → codename) for any map guid. */
    public String nameOf(String guid) { return names.get(guid); }

    @PostConstruct
    public void build() {
        Map<String, String> mapName = new HashMap<>();
        jdbc.query("SELECT guid, COALESCE(curated_display(guid), NULLIF(map_name,''), internal_name) AS nm FROM game_map",
            (RowCallbackHandler) rs -> mapName.put(((UUID) rs.getObject("guid")).toString(), rs.getString("nm")));
        this.names = mapName;

        List<MapGraphAssembler.EdgeRow> edges = new ArrayList<>();
        jdbc.query("SELECT from_map_guid, to_map_guid, conditions::text AS conditions " +
                   "FROM map_graph_edge ORDER BY id",
            (RowCallbackHandler) rs -> edges.add(new MapGraphAssembler.EdgeRow(
                ((UUID) rs.getObject("from_map_guid")).toString(),
                ((UUID) rs.getObject("to_map_guid")).toString(),
                parse(rs.getString("conditions")))));

        if (edges.isEmpty()) {
            log.warn("map_graph_edge is empty — /api/map-graph will 404 until the world tables seed.");
            return;
        }
        this.graph = MapGraphAssembler.assemble(edges, mapName);
        log.info("MapGraphIndex built: {} map nodes, {} edges at startup", graph.nodes().size(), edges.size());
    }

    /** conditions jsonb text → JsonNode; an empty array if absent or malformed. */
    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) return mapper.createArrayNode();
        try { return mapper.readTree(json); }
        catch (Exception e) { return mapper.createArrayNode(); }
    }
}
