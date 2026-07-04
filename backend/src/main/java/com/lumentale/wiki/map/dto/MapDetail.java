package com.lumentale.wiki.map.dto;

import java.util.List;

/**
 * Everything on a single map page — the "world connects everything" surface: the
 * forms that spawn here, the wild-encounter points, the vendor shops and their
 * resolved inventory, the trainer/boss/scripted battles, the exits to other maps,
 * and the world item pickups. Ported onto the redesigned schema: spawn/ele/emotion
 * labels resolve via {@code ReferenceIndex}; art via {@code AssetResolver}; shop
 * entries read the typed {@code item/furniture/recipe/move_guid} columns (not v2's
 * polymorphic {@code ref_guid}/{@code ref_type}); battles read {@code map_battle.kind}
 * with the typed {@code trainer_guid}/{@code boss_guid}.
 */
public record MapDetail(
        String guid,
        String internalName,
        String displayName,
        String mapName,
        String region,
        boolean interior,
        String tile,
        List<SpawnForm> spawns,
        List<SpawnPoint> spawnPoints,
        List<Shop> shops,
        List<Battle> battles,
        List<Exit> exits,
        List<Pickup> pickups,
        List<Connection> connections,
        List<StateVariant> stateGroup,
        Bounds bounds) {

    /**
     * Tile projection geometry. {@code offsetX/offsetZ} is the world-space centre the
     * tile texture is rendered around (= the gameplay-bounds centre). {@code sizeX/sizeZ}
     * is the gameplay bounds rect (kept for reference; NOT the tile's coverage).
     *
     * <p>The baked UI-map texture is SQUARE and covers a square world region of side
     * {@code tileWorldSize} (= texturePx × MapScaleValue) centred on offset — this is
     * generally NOT the same rect as sizeX/sizeZ (the gameplay bounds is often not
     * square). Markers therefore project onto the tile via the square coverage:
     * fracX = (x − (offsetX − tileWorldSize/2)) / tileWorldSize;
     * fracZ = (z − (offsetZ − tileWorldSize/2)) / tileWorldSize;
     * left% = fracX·100; top% = (1 − fracZ)·100.
     * {@code tileWorldSize} is 0 when the map has no tile / no scale (caller falls back).
     *
     * <p>A few bakes were rendered with a different framing (Speranova, CityFive,
     * the Paradine quarters, …); for those, the empirically calibrated frame
     * (tools/calibrate_map_tiles.py) is attached as {@code tileCenterX/Z} +
     * {@code tileSpanX/Z} — when present, the frontend projects against it instead.
     */
    public record Bounds(double offsetX, double offsetZ, double sizeX, double sizeZ,
                         double tileWorldSize,
                         Double tileCenterX, Double tileCenterZ,
                         Double tileSpanX, Double tileSpanZ) {

        public Bounds(double offsetX, double offsetZ, double sizeX, double sizeZ,
                      double tileWorldSize) {
            this(offsetX, offsetZ, sizeX, sizeZ, tileWorldSize, null, null, null, null);
        }
    }

    /** A form found on this map, with the aggregated wild level range. */
    public record SpawnForm(String guid, String species, String variant, Integer dex,
                            String emo, String ele, String menuArt,
                            Integer levelMin, Integer levelMax) {}

    /** A wild-encounter zone with a world position and the forms that appear there. */
    public record SpawnPoint(String name, Pos pos, List<SpawnForm> forms) {}

    /** A vendor NPC and its resolved inventory. */
    public record Shop(String npc, String graph, Pos pos, List<ShopEntry> entries) {}

    /**
     * One purchasable line, resolved through the typed FK. {@code kind} ∈
     * item|furniture|recipe|move.
     */
    public record ShopEntry(String kind, String guid, String name, String icon,
                            Integer price, Integer limit) {}

    /** An NPC that hosts one or more fights on this map. */
    public record Battle(String npc, Pos pos, List<Fight> fights) {}

    /**
     * A single fight: {@code kind} ∈ trainer|boss|scripted. trainer/boss carry the
     * resolved {@code guid}+{@code name}; scripted carries its {@code forms}.
     */
    public record Fight(String kind, String guid, String name, List<SpawnForm> forms) {}

    /** A physical exit (door/teleporter) and its resolved destination map. */
    public record Exit(String name, String targetGuid, String targetName, String targetRegion,
                       Integer direction, String resolvedBy, Pos pos, Pos targetPos) {}

    /** A world item pickup on this map. */
    public record Pickup(String name, Integer amount, String itemGuid, String itemName,
                         String icon, Pos pos) {}

    /**
     * A connected map from the game's connectivity graph (map_graph_edge). Many area↔area
     * links are seamless border crossings (no door/teleport), so they never appear as an
     * {@link Exit}; this is how you actually get from one area to its neighbours.
     * {@code direction}: "both" | "out" | "in". {@code viaExit} = a door/teleport to it
     * also exists (listed under Exits). {@code conditions} = flag gates on the link
     * (empty = always open).
     */
    public record Connection(String guid, String internalName, String displayName,
                             String mapName, String region, boolean interior,
                             String direction, boolean viaExit, List<String> conditions,
                             List<Pos> crossings, int stateCount) {}

    /**
     * One story-state of a location that exists as several maps (e.g. Iris Hamlet →
     * Borgo Iride). Ordered by story progress; {@code current} marks the map being viewed.
     * Lets the map page offer a state switcher instead of three near-identical pages.
     */
    public record StateVariant(String guid, String internalName, String displayName,
                               String label, boolean current) {}
}
