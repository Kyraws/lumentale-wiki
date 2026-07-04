package com.lumentale.wiki.map;

import com.lumentale.wiki.common.Guids;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.error.NotFoundException;
import com.lumentale.wiki.map.MapRepository.Base;
import com.lumentale.wiki.map.dto.MapDetail;
import com.lumentale.wiki.map.dto.MapDetail.Bounds;
import com.lumentale.wiki.map.dto.MapDetail.Exit;
import com.lumentale.wiki.map.dto.MapDetail.Pickup;
import com.lumentale.wiki.map.dto.MapDetail.SpawnPoint;
import com.lumentale.wiki.map.dto.MapGraph;
import com.lumentale.wiki.map.dto.MapSummary;
import com.lumentale.wiki.map.dto.Pos;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assembles world-map responses from {@link MapRepository} (the per-section
 * cross-link queries) and {@link MapGraphIndex} (the startup-built graph). The
 * controller stays pure routing; guid validation → 400, missing map/graph → 404.
 */
@Service
public class MapService {

    /** All baked UI-map tiles are rendered at 3840×3840 (one stray 3839 — negligible). */
    private static final double TILE_PX = 3840.0;

    private final MapRepository repo;
    private final MapGraphIndex graphIndex;
    private final MapGeometryIndex geometryIndex;
    private final MapStateGroupIndex stateGroups;
    private final LocalizationResolver loc;
    private final TileCalibrationIndex calibration;

    public MapService(MapRepository repo, MapGraphIndex graphIndex, MapGeometryIndex geometryIndex,
                      MapStateGroupIndex stateGroups, LocalizationResolver loc,
                      TileCalibrationIndex calibration) {
        this.repo = repo;
        this.graphIndex = graphIndex;
        this.geometryIndex = geometryIndex;
        this.stateGroups = stateGroups;
        this.loc = loc;
        this.calibration = calibration;
    }

    public List<MapSummary> list(String lang) {
        return repo.summaries(loc.normalize(lang));
    }

    public MapDetail detail(String guidStr, String lang) {
        UUID guid = Guids.require(guidStr);
        String l = loc.normalize(lang);
        Base b = repo.base(guid).orElseThrow(() -> new NotFoundException("map", guidStr));
        String displayName = MapRepository.firstNonBlank(loc.display(l, "mapname_" + b.guid(), null), b.curated());

        // DB-sourced sections keep all names/links; the corrected geometry (loaded at
        // startup) overrides marker positions so they plot accurately on the tile.
        List<SpawnPoint> spawnPoints = repo.spawnPoints(guid);
        List<Exit> exits = repo.exits(guid);
        List<Pickup> pickups = repo.pickups(guid);

        MapGeometryIndex.Geometry geo = geometryIndex.get(b.guid()).orElse(null);
        Bounds bounds = geo == null ? null : geo.bounds();
        if (bounds != null) {
            // The baked UI-map texture is a square 3840px render covering
            // (3840 × MapScaleValue) world units, centred on the bounds offset — that
            // square, NOT the gameplay bounds rect, is the frame markers plot against.
            double tileWorldSize = b.tile() == null ? 0 : TILE_PX * b.mapScaleValue();
            // A few bakes are framed differently — attach the calibrated frame when one exists.
            TileCalibrationIndex.Frame cal = b.tile() == null ? null
                : calibration.get(b.guid()).orElse(null);
            bounds = cal == null
                ? new Bounds(bounds.offsetX(), bounds.offsetZ(),
                    bounds.sizeX(), bounds.sizeZ(), tileWorldSize)
                : new Bounds(bounds.offsetX(), bounds.offsetZ(),
                    bounds.sizeX(), bounds.sizeZ(), tileWorldSize,
                    cal.centerX(), cal.centerZ(), cal.spanX(), cal.spanZ());
        }
        if (geo != null) {
            exits = overrideExits(exits, geo.exits());
            pickups = overridePickups(pickups, geo.pickups());
            spawnPoints = overrideSpawnPoints(spawnPoints, geo.spawnPoints());
        }

        List<MapDetail.Connection> connections = enrichConnections(b.guid(), bounds, repo.connections(guid, l), exits);
        List<MapDetail.StateVariant> stateGroup = stateGroup(b.guid(), l);

        return new MapDetail(
            b.guid(), b.internalName(), displayName, b.mapName(), b.region(), b.interior(), b.tile(),
            repo.spawns(guid),
            spawnPoints,
            repo.shops(guid),
            repo.battles(guid),
            exits,
            pickups,
            connections,
            stateGroup,
            bounds);
    }

    /**
     * Normalises each connection's direction and attaches a plottable position WHERE ONE
     * TRULY EXISTS:
     * <ul>
     *   <li><b>Doors/teleports</b> are exact, so we keep EVERY entrance: a cave with three
     *       mouths returns three crossing points (the front-end pins each, clustering only
     *       when they truly coincide). Their direction is the real door direction (a genuine
     *       one-way door stays one-way).</li>
     *   <li><b>Seamless borders</b> have no in-scene gate object — only a world-map bearing,
     *       which is approximate — so we return a SINGLE crossing on the bounds edge facing
     *       the neighbour (validated to ~1° median against the real doors), even when the
     *       world map shows several border segments. They are two-way by nature.</li>
     * </ul>
     * A connection with no door position and no world-map bearing has an empty crossing list
     * (it shows in the Connections list, not as a pin).
     */
    private List<MapDetail.Connection> enrichConnections(
            String fromGuid, Bounds bounds, List<MapDetail.Connection> raw, List<Exit> exits) {
        // ALL door positions per target map (every distinct entrance)
        Map<String, List<Pos>> doorPos = new LinkedHashMap<>();
        for (Exit e : exits)
            if (e.targetGuid() != null && e.pos() != null)
                doorPos.computeIfAbsent(e.targetGuid(), k -> new ArrayList<>()).add(e.pos());

        List<MapDetail.Connection> out = new ArrayList<>(raw.size());
        // collapse connections that lead to several states of ONE place into a single entry
        Map<String, MapDetail.Connection> emittedGroup = new LinkedHashMap<>(); // canonicalName → kept connection
        Map<String, List<Pos>> groupCrossings = new LinkedHashMap<>();
        Map<String, Integer> groupCount = new LinkedHashMap<>();

        for (MapDetail.Connection c : raw) {
            String direction = c.viaExit() ? c.direction() : "both"; // seamless ⇒ two-way
            List<Pos> crossings;
            if (c.viaExit()) {
                crossings = doorPos.getOrDefault(c.guid(), List.of());          // every entrance
            } else {
                // every world-map border crossing (some borders have 2+ passages)
                crossings = geometryIndex.borderPoints(fromGuid, bounds, c.guid());
            }

            var group = stateGroups.groupOf(c.guid()).orElse(null);
            if (group != null) {
                // one entry per place; the primary (earliest) state is the link target
                String key = group.canonicalName();
                groupCrossings.computeIfAbsent(key, k -> new ArrayList<>()).addAll(crossings);
                groupCount.merge(key, 1, Integer::sum);
                boolean isPrimary = group.variants().get(0).guid().equals(c.guid());
                if (isPrimary || !emittedGroup.containsKey(key)) {
                    emittedGroup.put(key, new MapDetail.Connection(c.guid(), c.internalName(),
                        group.canonicalName(), c.mapName(), c.region(), c.interior(),
                        direction, c.viaExit(), c.conditions(), List.of(), 0));
                }
                continue;
            }
            out.add(new MapDetail.Connection(c.guid(), c.internalName(), c.displayName(),
                c.mapName(), c.region(), c.interior(), direction, c.viaExit(), c.conditions(),
                crossings, 1));
        }
        // finalise collapsed state-group entries with combined crossings + state count
        for (var e : emittedGroup.entrySet()) {
            MapDetail.Connection c = e.getValue();
            out.add(new MapDetail.Connection(c.guid(), c.internalName(), c.displayName(),
                c.mapName(), c.region(), c.interior(), c.direction(), c.viaExit(), c.conditions(),
                dedupeCrossings(groupCrossings.get(e.getKey())), groupCount.get(e.getKey())));
        }
        return out;
    }

    /** Merge crossing points that coincide — the state variants share one physical exit. */
    private static List<Pos> dedupeCrossings(List<Pos> pts) {
        if (pts == null || pts.isEmpty()) return List.of();
        List<Pos> out = new ArrayList<>();
        for (Pos p : pts) {
            if (p == null || p.x() == null || p.z() == null) continue;
            boolean dup = out.stream().anyMatch(q ->
                Math.abs(q.x() - p.x()) < 1.0 && Math.abs(q.z() - p.z()) < 1.0);
            if (!dup) out.add(p);
        }
        return out;
    }

    /** Story-state variants for the viewed map (empty unless it belongs to a state group). */
    private List<MapDetail.StateVariant> stateGroup(String guid, String lang) {
        var group = stateGroups.groupOf(guid).orElse(null);
        if (group == null) return List.of();
        List<MapDetail.StateVariant> out = new ArrayList<>(group.variants().size());
        for (var v : group.variants())
            out.add(new MapDetail.StateVariant(v.guid(), v.internalName(),
                MapRepository.firstNonBlank(loc.display(lang, "mapname_" + v.guid(), null),
                    graphIndex.nameOf(v.guid())), v.label(), v.guid().equals(guid)));
        return out;
    }

    /** Override exit positions, matching DB exit.targetGuid ↔ v2 targetMapGuid (in order for duplicates). */
    private static List<Exit> overrideExits(List<Exit> db, List<MapGeometryIndex.Exit> geo) {
        Map<String, java.util.Deque<Pos>> byTarget = new LinkedHashMap<>();
        for (MapGeometryIndex.Exit e : geo)
            if (e.targetMapGuid() != null)
                byTarget.computeIfAbsent(e.targetMapGuid(), k -> new java.util.ArrayDeque<>()).add(e.pos());
        List<Exit> out = new ArrayList<>(db.size());
        for (Exit e : db) {
            Pos pos = null;
            var q = e.targetGuid() == null ? null : byTarget.get(e.targetGuid());
            if (q != null && !q.isEmpty()) pos = q.poll();
            out.add(new Exit(e.name(), e.targetGuid(), e.targetName(), e.targetRegion(),
                e.direction(), e.resolvedBy(), pos, e.targetPos()));
        }
        return out;
    }

    /** Override pickup positions, matching DB itemGuid ↔ v2 itemGuid (in order for duplicates). */
    private static List<Pickup> overridePickups(List<Pickup> db, List<MapGeometryIndex.Pickup> geo) {
        Map<String, java.util.Deque<Pos>> byItem = new LinkedHashMap<>();
        for (MapGeometryIndex.Pickup p : geo)
            if (p.itemGuid() != null)
                byItem.computeIfAbsent(p.itemGuid(), k -> new java.util.ArrayDeque<>()).add(p.pos());
        List<Pickup> out = new ArrayList<>(db.size());
        for (Pickup p : db) {
            Pos pos = null;
            var q = p.itemGuid() == null ? null : byItem.get(p.itemGuid());
            if (q != null && !q.isEmpty()) pos = q.poll();
            out.add(new Pickup(p.name(), p.amount(), p.itemGuid(), p.itemName(), p.icon(), pos));
        }
        return out;
    }

    /** Override spawn-point positions positionally (the v2 spawnPoints are the same SpawnArea set, in order). */
    private static List<SpawnPoint> overrideSpawnPoints(List<SpawnPoint> db, List<Pos> geo) {
        List<SpawnPoint> out = new ArrayList<>(db.size());
        for (int i = 0; i < db.size(); i++) {
            SpawnPoint sp = db.get(i);
            Pos pos = i < geo.size() ? geo.get(i) : null;
            out.add(new SpawnPoint(sp.name(), pos, sp.forms()));
        }
        return out;
    }

    public MapGraph graph() {
        return graphIndex.get().orElseThrow(() -> new NotFoundException("map-graph", "map_graph_edge"));
    }
}
