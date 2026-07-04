package com.lumentale.wiki.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumentale.wiki.map.dto.MapDetail.Bounds;
import com.lumentale.wiki.map.dto.Pos;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The corrected world-map placement geometry, loaded ONCE at startup from
 * {@code data/seed/map_placements_v2.json} (CWD is the repo root via the bootRun
 * {@code workingDir}). Mirrors {@link MapGraphIndex}'s @PostConstruct load.
 *
 * <p>Each map carries its tile {@code bounds} (offset/size, the frame the tile is
 * rendered in) and per-marker LOCAL-frame positions. {@link MapService} uses these
 * to OVERRIDE the marker positions coming out of the DB so they plot accurately on
 * the tile via the verified bounds projection (the DB {@code MapScaleValue}-based
 * projection was a broken approximation).
 */
@Component
public class MapGeometryIndex {

    private static final Logger log = LoggerFactory.getLogger(MapGeometryIndex.class);
    private static final String SEED_PATH = "data/seed/map_placements_v2.json";
    private static final String DIRS_PATH = "data/seed/map_connection_dirs.json";

    private final ObjectMapper mapper;
    private final Map<String, Geometry> byMap = new HashMap<>();
    /** source guid → target guid → world-map bearing(s) in degrees (+x east, +y north).
     *  Authoritative direction to each connected map, used to place seamless border pins. */
    private final Map<String, Map<String, List<Double>>> connDir = new HashMap<>();

    public MapGeometryIndex(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Per-map geometry: tile bounds + the corrected marker positions, keyed for override matching. */
    public record Geometry(Bounds bounds,
                           List<Exit> exits,         // ordered, match by targetMapGuid
                           List<Pos> spawnPoints,     // ordered, match positionally
                           List<Pickup> pickups) {}   // ordered, match by itemGuid

    public record Exit(String targetMapGuid, Pos pos) {}
    public record Pickup(String itemGuid, Pos pos) {}

    public Optional<Geometry> get(String mapGuid) {
        return Optional.ofNullable(byMap.get(mapGuid));
    }

    @PostConstruct
    public void build() {
        File f = new File(SEED_PATH);
        if (!f.isFile()) {
            log.warn("{} not found (cwd={}) — map markers will have no corrected geometry.",
                SEED_PATH, new File(".").getAbsolutePath());
            return;
        }
        try {
            JsonNode root = mapper.readTree(f);
            for (JsonNode m : root) {
                String guid = m.path("mapGuid").asText(null);
                if (guid == null) continue;
                byMap.put(guid, new Geometry(
                    bounds(m.path("bounds")),
                    exits(m.path("exits")),
                    spawnPoints(m.path("spawnPoints")),
                    pickups(m.path("pickups"))));
            }
            log.info("MapGeometryIndex built: corrected geometry for {} maps at startup", byMap.size());
        } catch (Exception e) {
            log.error("Failed to load {} — map markers will have no corrected geometry.", SEED_PATH, e);
        }
        loadConnectionDirs();
    }

    /** Load the world-map connection bearings (extract_map_connection_dirs.py output). */
    private void loadConnectionDirs() {
        File f = new File(DIRS_PATH);
        if (!f.isFile()) {
            log.warn("{} not found — seamless border connections won't be plotted on tiles.", DIRS_PATH);
            return;
        }
        try {
            int n = 0;
            for (JsonNode r : mapper.readTree(f)) {
                String s = r.path("sourceMapGuid").asText(null), t = r.path("targetMapGuid").asText(null);
                if (s == null || t == null) continue;
                connDir.computeIfAbsent(s, k -> new HashMap<>())
                       .computeIfAbsent(t, k -> new ArrayList<>())
                       .add(r.path("angleDeg").asDouble());
                n++;
            }
            log.info("MapGeometryIndex: {} world-map connection bearings loaded", n);
        } catch (Exception e) {
            log.error("Failed to load {} — seamless borders won't be plotted.", DIRS_PATH, e);
        }
    }

    /**
     * LOCAL-frame points on {@code from}'s bounds edge bearing toward {@code to}, using
     * the authoritative world-map directions (NOT the unreliable scene roots). The
     * in-game world map shows ONE link node per physical border crossing, so a pair
     * with two passages (e.g. Area 03 ↔ Area 16) yields TWO bearings — one pin each.
     * Empty when no direction is known. World-map +x = east = tile +x; +y = north =
     * tile +z. Each ray is cast from the bounds centre and clipped just inside the edge.
     */
    public List<Pos> borderPoints(String from, Bounds b, String to) {
        if (b == null) return List.of();
        List<Double> angles = connDir.getOrDefault(from, Map.of()).get(to);
        if (angles == null || angles.isEmpty()) return List.of();
        List<Pos> out = new ArrayList<>(angles.size());
        for (double deg : angles) {
            double rad = Math.toRadians(deg);
            double dx = Math.cos(rad), dz = Math.sin(rad);
            double hx = b.sizeX() / 2, hz = b.sizeZ() / 2;
            double t = Double.MAX_VALUE;
            if (dx != 0) t = Math.min(t, hx / Math.abs(dx));
            if (dz != 0) t = Math.min(t, hz / Math.abs(dz));
            t *= 0.92;   // pull inside the edge so the pin sits on the tile
            out.add(new Pos(b.offsetX() + t * dx, null, b.offsetZ() + t * dz));
        }
        return out;
    }

    private static Bounds bounds(JsonNode n) {
        if (n.isMissingNode() || n.isNull()) return null;
        // tileWorldSize is filled in by MapService from the DB MapScaleValue (the
        // placements file has no scale); 0 here means "compute it downstream".
        return new Bounds(n.path("offsetX").asDouble(), n.path("offsetZ").asDouble(),
            n.path("sizeX").asDouble(), n.path("sizeZ").asDouble(), 0);
    }

    private static List<Exit> exits(JsonNode arr) {
        List<Exit> out = new ArrayList<>();
        for (JsonNode e : arr)
            out.add(new Exit(e.path("targetMapGuid").asText(null), pos(e)));
        return out;
    }

    private static List<Pos> spawnPoints(JsonNode arr) {
        List<Pos> out = new ArrayList<>();
        for (JsonNode s : arr) out.add(pos(s));
        return out;
    }

    private static List<Pickup> pickups(JsonNode arr) {
        List<Pickup> out = new ArrayList<>();
        for (JsonNode p : arr)
            out.add(new Pickup(p.path("itemGuid").asText(null), pos(p)));
        return out;
    }

    /** Local-frame (x,z) into a Pos (y unused by the projection). */
    private static Pos pos(JsonNode n) {
        JsonNode x = n.path("x"), z = n.path("z");
        if (x.isMissingNode() || z.isMissingNode()) return null;
        return new Pos(x.asDouble(), null, z.asDouble());
    }
}
