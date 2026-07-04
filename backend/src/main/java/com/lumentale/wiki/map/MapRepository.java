package com.lumentale.wiki.map;

import com.lumentale.wiki.common.AssetResolver;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.common.ReferenceIndex;
import com.lumentale.wiki.map.dto.MapDetail.*;
import com.lumentale.wiki.map.dto.MapSummary;
import com.lumentale.wiki.map.dto.Pos;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for the world map, ported from v2 onto the redesigned schema. Each
 * cross-link section of a map page is its own focused query/method; {@link MapService}
 * composes them into a {@code MapDetail}.
 *
 * <p>What changed vs v2:
 * <ul>
 *   <li>guids are native {@code uuid} — bind {@link UUID}, read via
 *       {@code ((UUID) rs.getObject(...)).toString()}.</li>
 *   <li>ele/emotion are int codes resolved through {@link ReferenceIndex}; art
 *       (tile, menu) resolves through the hybrid {@link AssetResolver}.</li>
 *   <li>shop entries read the typed {@code item/furniture/recipe/move_guid}
 *       columns (one per kind) instead of the polymorphic {@code ref_guid}/{@code ref_type}.</li>
 *   <li>battles read {@code map_battle.kind} with typed {@code trainer_guid}/{@code boss_guid}.</li>
 * </ul>
 * Spawn-form sub-records come from {@code map_spawn}/{@code map_battle_form} JOIN
 * {@code form}; the map-level spawn list comes from {@code form_spawn} JOIN {@code form}.
 */
@Repository
public class MapRepository {

    private final JdbcTemplate jdbc;
    private final AssetResolver assets;
    private final ReferenceIndex ref;
    private final LocalizationResolver loc;

    public MapRepository(JdbcTemplate jdbc, AssetResolver assets, ReferenceIndex ref,
                         LocalizationResolver loc) {
        this.jdbc = jdbc;
        this.assets = assets;
        this.ref = ref;
        this.loc = loc;
    }

    /** Base map fields (no cross-links). */
    public record Base(String guid, String internalName, String mapName, String region,
                       boolean interior, String tile, double mapScaleValue, String curated) {}

    public List<MapSummary> summaries(String lang) {
        return jdbc.query(
            "SELECT m.guid, m.internal_name, m.map_name, m.is_interior, m.tile_guid, " +
            // refined region: engine north/south, else the curated hub/center/prologue split
            "  COALESCE(m.region, (SELECT cn.extra->>'region' FROM curated_name cn WHERE cn.guid = m.guid)) AS region, " +
            "  curated_display(m.guid) AS curated, " +
            "  (SELECT count(DISTINCT ms.form_guid) FROM map_spawn ms WHERE ms.map_guid = m.guid) AS spawns " +
            "FROM game_map m ORDER BY m.internal_name",
            (rs, i) -> {
                UUID guid = (UUID) rs.getObject("guid");
                String g = guid.toString();
                return new MapSummary(
                    g, rs.getString("internal_name"),
                    // friendly name: localized LOCATION entry (mapname_<guid>) when the
                    // lang has one, else the curated English name (covers all 300 maps).
                    firstNonBlank(loc.display(lang, "mapname_" + g, null), rs.getString("curated")),
                    blankToNull(rs.getString("map_name")), rs.getString("region"),
                    rs.getBoolean("is_interior"), (int) rs.getLong("spawns"),
                    // tile.png is exported on disk per map → filesystem leg (the manifest
                    // two-hop isn't seeded). 66 maps have a rendered tile image.
                    assets.art("map", guid, "tile"));
            });
    }

    public Optional<Base> base(UUID guid) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT guid, internal_name, map_name, is_interior, " +
                "  COALESCE(region, (SELECT cn.extra->>'region' FROM curated_name cn WHERE cn.guid = game_map.guid)) AS region, " +
                "  curated_display(guid) AS curated, " +
                "  (raw->>'MapScaleValue')::float8 AS map_scale_value " +
                "FROM game_map WHERE guid = ?",
                (rs, i) -> {
                    double scale = rs.getDouble("map_scale_value"); // 0 if NULL → no tile coverage
                    return new Base(
                        ((UUID) rs.getObject("guid")).toString(), rs.getString("internal_name"),
                        blankToNull(rs.getString("map_name")), rs.getString("region"),
                        rs.getBoolean("is_interior"), assets.art("map", guid, "tile"), scale,
                        rs.getString("curated"));
                },
                guid));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Wild creatures on this map, sourced from {@code map_spawn} — the actual extracted
     * SpawnArea encounters (where + which form + level range). This is the authoritative
     * set: {@code form_spawn} is a strict, incomplete SUBSET of it (it omitted ~3 species
     * on 40 maps, e.g. Toxigall/Owaxle/Flobesque on CENTER_AREA_05), so we aggregate
     * map_spawn directly to get every species with its correct min/max level.
     */
    public List<SpawnForm> spawns(UUID guid) {
        return jdbc.query(
            "SELECT f.guid, s.name AS species, f.variant_name, f.dex, f.emotion_code, f.ele_type_code, " +
            "  min(ms.level_min) AS lmin, max(ms.level_max) AS lmax " +
            "FROM map_spawn ms JOIN form f ON f.guid = ms.form_guid JOIN species s ON s.guid = f.species_guid " +
            "WHERE ms.map_guid = ? " +
            "GROUP BY f.guid, s.name, f.variant_name, f.dex, f.emotion_code, f.ele_type_code " +
            "ORDER BY f.dex, f.variant_name",
            (rs, i) -> spawnForm(rs), guid);
    }

    /** Wild-encounter zones (positions) with the forms that appear at each. */
    public List<SpawnPoint> spawnPoints(UUID guid) {
        Map<Long, String> names = new LinkedHashMap<>();
        Map<Long, Pos> positions = new java.util.HashMap<>();
        jdbc.query("SELECT id, name, pos_x, pos_y, pos_z FROM map_spawner " +
                   "WHERE map_guid = ? ORDER BY id",
            (RowCallbackHandler) rs -> {
                long id = rs.getLong("id");
                names.put(id, rs.getString("name"));
                positions.put(id, pos(rs, "pos_x", "pos_y", "pos_z"));
            }, guid);

        Map<Long, List<SpawnForm>> forms = new java.util.HashMap<>();
        jdbc.query(
            "SELECT msp.spawner_id, f.guid, s.name AS species, f.variant_name, f.dex, f.emotion_code, f.ele_type_code, " +
            "  min(msp.level_min) lmin, max(msp.level_max) lmax " +
            "FROM map_spawn msp JOIN form f ON f.guid = msp.form_guid JOIN species s ON s.guid = f.species_guid " +
            "WHERE msp.map_guid = ? " +
            "GROUP BY msp.spawner_id, f.guid, s.name, f.variant_name, f.dex, f.emotion_code, f.ele_type_code " +
            "ORDER BY max(msp.chance) DESC NULLS LAST",
            (RowCallbackHandler) rs -> forms.computeIfAbsent(rs.getLong("spawner_id"), k -> new ArrayList<>())
                .add(spawnForm(rs)), guid);

        List<SpawnPoint> out = new ArrayList<>();
        for (var e : names.entrySet())
            out.add(new SpawnPoint(e.getValue(), positions.get(e.getKey()),
                forms.getOrDefault(e.getKey(), List.of())));
        return out;
    }

    /** Vendor NPCs and their resolved inventory (typed FK columns, one per kind). */
    public List<Shop> shops(UUID guid) {
        Map<Long, String[]> meta = new LinkedHashMap<>();        // id → {npc, graph}
        Map<Long, Pos> positions = new java.util.HashMap<>();
        jdbc.query("SELECT id, npc_name, graph_name, pos_x, pos_y, pos_z FROM map_shop WHERE map_guid = ? ORDER BY id",
            (RowCallbackHandler) rs -> {
                long id = rs.getLong("id");
                meta.put(id, new String[]{ rs.getString("npc_name"), rs.getString("graph_name") });
                positions.put(id, pos(rs, "pos_x", "pos_y", "pos_z"));
            }, guid);

        Map<Long, List<ShopEntry>> entries = new java.util.HashMap<>();
        if (!meta.isEmpty()) jdbc.query(
            "SELECT e.shop_id, e.item_guid, e.furniture_guid, e.recipe_guid, e.move_guid, e.price_override, e.limit_amount, " +
            "  COALESCE(curated_display(i.guid), i.name_raw) AS i_name, i.price AS i_price, i.guid AS i_guid, " +
            "  COALESCE(curated_display(fu.guid), fu.name_raw) AS f_name, fu.price AS f_price, " +
            "  COALESCE(curated_display(mv.guid), mv.name_raw) AS m_name, " +
            "  COALESCE(curated_display(cri.guid), cri.name_raw) AS c_name " +
            "FROM map_shop_entry e JOIN map_shop sh ON sh.id = e.shop_id " +
            "LEFT JOIN item i             ON i.guid = e.item_guid " +
            "LEFT JOIN furniture fu       ON fu.guid = e.furniture_guid " +
            "LEFT JOIN move mv            ON mv.guid = e.move_guid " +
            "LEFT JOIN crafting_recipe cr ON cr.guid = e.recipe_guid " +
            "LEFT JOIN item cri           ON cri.guid = cr.result_item_guid " +
            "WHERE sh.map_guid = ? ORDER BY e.shop_id, e.id",
            (RowCallbackHandler) rs -> entries.computeIfAbsent(rs.getLong("shop_id"), k -> new ArrayList<>())
                .add(shopEntry(rs)), guid);

        List<Shop> out = new ArrayList<>();
        for (var e : meta.entrySet())
            out.add(new Shop(e.getValue()[0], e.getValue()[1], positions.get(e.getKey()),
                entries.getOrDefault(e.getKey(), List.of())));
        return out;
    }

    /** Trainer/boss/scripted fights, grouped per hosting NPC. */
    public List<Battle> battles(UUID guid) {
        Map<Long, List<SpawnForm>> scriptedForms = new java.util.HashMap<>();
        jdbc.query(
            "SELECT bf.battle_id, f.guid, s.name AS species, f.variant_name, f.dex, f.emotion_code, f.ele_type_code, " +
            "  bf.level_min AS lmin, bf.level_max AS lmax " +
            "FROM map_battle_form bf JOIN map_battle b ON b.id = bf.battle_id " +
            "JOIN form f ON f.guid = bf.form_guid JOIN species s ON s.guid = f.species_guid " +
            "WHERE b.map_guid = ? ORDER BY bf.id",
            (RowCallbackHandler) rs -> scriptedForms.computeIfAbsent(rs.getLong("battle_id"), k -> new ArrayList<>())
                .add(spawnForm(rs)), guid);

        Map<String, Battle> byNpc = new LinkedHashMap<>();
        jdbc.query(
            "SELECT b.id, b.npc_name, b.kind, b.trainer_guid, b.boss_guid, b.pos_x, b.pos_y, b.pos_z, " +
            "  curated_display(t.guid) AS t_cur, t.name_raw AS t_name, t.internal_name AS t_int, " +
            "  curated_display(bo.guid) AS bo_cur, bo.display AS bo_display, bo.internal_name AS bo_int " +
            "FROM map_battle b " +
            "LEFT JOIN trainer t ON b.kind = 'trainer' AND t.guid = b.trainer_guid " +
            "LEFT JOIN boss bo   ON b.kind = 'boss'    AND bo.guid = b.boss_guid " +
            "WHERE b.map_guid = ? ORDER BY b.npc_name, b.id",
            (RowCallbackHandler) rs -> {
                String npc = rs.getString("npc_name");
                if (npc == null) npc = "";
                Battle battle = byNpc.computeIfAbsent(npc,
                    k -> new Battle(k.isEmpty() ? null : k, pos(rs, "pos_x", "pos_y", "pos_z"), new ArrayList<>()));
                String kind = rs.getString("kind");
                String refGuid;
                String name;
                if ("trainer".equals(kind)) {
                    Object g = rs.getObject("trainer_guid");
                    refGuid = g == null ? null : g.toString();
                    name = firstNonBlank(rs.getString("t_cur"), rs.getString("t_name"), rs.getString("t_int"));
                } else if ("boss".equals(kind)) {
                    Object g = rs.getObject("boss_guid");
                    refGuid = g == null ? null : g.toString();
                    name = firstNonBlank(rs.getString("bo_cur"), rs.getString("bo_display"), rs.getString("bo_int"));
                } else {
                    refGuid = null;
                    name = null;
                }
                battle.fights().add(new Fight(kind, refGuid, name,
                    scriptedForms.getOrDefault(rs.getLong("id"), List.of())));
            }, guid);
        return new ArrayList<>(byNpc.values());
    }

    /** Physical exits with their resolved destination map. */
    public List<Exit> exits(UUID guid) {
        return jdbc.query(
            "SELECT e.name, e.target_map_guid, e.exit_direction, e.resolved_by, " +
            "  e.pos_x, e.pos_y, e.pos_z, e.target_pos_x, e.target_pos_y, e.target_pos_z, " +
            "  tm.internal_name AS target_name, tm.map_name AS target_map_name, tm.region AS target_region, " +
            "  curated_display(tm.guid) AS target_curated " +
            "FROM map_exit e LEFT JOIN game_map tm ON tm.guid = e.target_map_guid " +
            "WHERE e.source_map_guid = ? ORDER BY e.name",
            (rs, i) -> {
                Object tg = rs.getObject("target_map_guid");
                return new Exit(rs.getString("name"), tg == null ? null : tg.toString(),
                    firstNonBlank(rs.getString("target_curated"), blankToNull(rs.getString("target_map_name")), rs.getString("target_name")),
                    rs.getString("target_region"), (Integer) rs.getObject("exit_direction"),
                    rs.getString("resolved_by"), pos(rs, "pos_x", "pos_y", "pos_z"),
                    pos(rs, "target_pos_x", "target_pos_y", "target_pos_z"));
            }, guid);
    }

    /** World item pickups found on this map (JOIN item for name/icon). */
    public List<Pickup> pickups(UUID guid) {
        return jdbc.query(
            "SELECT p.name, p.amount, p.item_guid, p.pos_x, p.pos_y, p.pos_z, " +
            "  COALESCE(curated_display(i.guid), i.name_raw) AS item_name " +
            "FROM map_pickup p LEFT JOIN item i ON i.guid = p.item_guid " +
            "WHERE p.map_guid = ? ORDER BY item_name NULLS LAST, p.name",
            (rs, i) -> {
                Object ig = rs.getObject("item_guid");
                UUID itemGuid = ig == null ? null : (UUID) ig;
                return new Pickup(rs.getString("name"), (Integer) rs.getObject("amount"),
                    itemGuid == null ? null : itemGuid.toString(), rs.getString("item_name"),
                    itemGuid == null ? null : assets.art("item", itemGuid, "icon"),
                    pos(rs, "pos_x", "pos_y", "pos_z"));
            }, guid);
    }

    /**
     * Connected maps from the game connectivity graph (map_graph_edge), one row per
     * neighbour merged over both directions. Many area↔area links are seamless border
     * crossings with no door/teleport, so they never appear as an {@link Exit} — this is
     * how the reader sees that e.g. Area 01 leads to Area 02. {@code viaExit} marks
     * neighbours that ALSO have a door (listed under Exits); {@code conditions} surfaces
     * any flag gates ([] = always open).
     */
    public List<Connection> connections(UUID guid, String lang) {
        java.util.Set<String> exitTargets = new java.util.HashSet<>(jdbc.query(
            "SELECT DISTINCT target_map_guid FROM map_exit " +
            "WHERE source_map_guid = ? AND target_map_guid IS NOT NULL",
            (rs, i) -> ((UUID) rs.getObject(1)).toString(), guid));

        return jdbc.query(
            "SELECT n.guid, n.internal_name, n.map_name, n.region, n.is_interior, " +
            "  bool_or(n.out_dir) AS has_out, bool_or(n.in_dir) AS has_in, " +
            "  jsonb_agg(n.conditions) FILTER (WHERE n.conditions <> '[]'::jsonb) AS conds " +
            "FROM ( " +
            "  SELECT t.guid, t.internal_name, t.map_name, t.region, t.is_interior, " +
            "    true AS out_dir, false AS in_dir, e.conditions " +
            "  FROM map_graph_edge e JOIN game_map t ON t.guid = e.to_map_guid " +
            "  WHERE e.from_map_guid = ? " +
            "  UNION ALL " +
            "  SELECT s.guid, s.internal_name, s.map_name, s.region, s.is_interior, " +
            "    false, true, e.conditions " +
            "  FROM map_graph_edge e JOIN game_map s ON s.guid = e.from_map_guid " +
            "  WHERE e.to_map_guid = ? " +
            ") n GROUP BY n.guid, n.internal_name, n.map_name, n.region, n.is_interior " +
            "ORDER BY n.internal_name",
            (rs, i) -> {
                String g = ((UUID) rs.getObject("guid")).toString();
                boolean out = rs.getBoolean("has_out"), in = rs.getBoolean("has_in");
                String direction = out && in ? "both" : out ? "out" : "in";
                return new Connection(g, rs.getString("internal_name"),
                    loc.display(lang, "mapname_" + g, null),
                    blankToNull(rs.getString("map_name")), rs.getString("region"),
                    rs.getBoolean("is_interior"), direction, exitTargets.contains(g),
                    parseConditionFlags(rs.getString("conds")), List.of(), 1); // crossings/stateCount filled by MapService
            }, guid, guid);
    }

    /** Pull distinct human flag names out of the nested jsonb condition arrays ([] → none). */
    private List<String> parseConditionFlags(String condsJson) {
        if (condsJson == null) return List.of();
        java.util.LinkedHashSet<String> flags = new java.util.LinkedHashSet<>();
        try {
            com.fasterxml.jackson.databind.JsonNode arr = CONDITION_MAPPER.readTree(condsJson);
            for (com.fasterxml.jackson.databind.JsonNode group : arr)
                for (com.fasterxml.jackson.databind.JsonNode c : group) {
                    String flag = c.path("flag").asText(null);
                    if (flag != null && !flag.isBlank()) flags.add(flag);
                }
        } catch (Exception ignore) { /* malformed → no flags */ }
        return new ArrayList<>(flags);
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper CONDITION_MAPPER =
        new com.fasterxml.jackson.databind.ObjectMapper();

    // ---- row mappers / helpers ----

    /**
     * All three spawn-form queries (map spawns, spawn-point forms, scripted battle
     * forms) select the same column set, so this mapper reads them directly. ele/emo
     * are int codes resolved via {@link ReferenceIndex}; menu art via {@link AssetResolver}.
     */
    private SpawnForm spawnForm(ResultSet rs) throws SQLException {
        UUID g = (UUID) rs.getObject("guid");
        return new SpawnForm(g.toString(), rs.getString("species"), rs.getString("variant_name"),
            (Integer) rs.getObject("dex"),
            ref.emotion((Integer) rs.getObject("emotion_code")),
            ref.ele((Integer) rs.getObject("ele_type_code")),
            assets.art("form", g, "menu_art"),
            (Integer) rs.getObject("lmin"), (Integer) rs.getObject("lmax"));
    }

    /** Resolve a shop entry through whichever typed FK is set (CHECK guarantees one). */
    private ShopEntry shopEntry(ResultSet rs) throws SQLException {
        Integer ov = (Integer) rs.getObject("price_override");
        Integer limit = (Integer) rs.getObject("limit_amount");
        String kind, guid, name, icon = null;
        Integer base = null;

        Object item = rs.getObject("item_guid");
        Object furn = rs.getObject("furniture_guid");
        Object recipe = rs.getObject("recipe_guid");
        Object move = rs.getObject("move_guid");

        if (item != null) {
            kind = "item"; guid = item.toString(); name = rs.getString("i_name");
            base = (Integer) rs.getObject("i_price");
            icon = assets.art("item", (UUID) item, "icon");
        } else if (furn != null) {
            kind = "furniture"; guid = furn.toString(); name = rs.getString("f_name");
            base = (Integer) rs.getObject("f_price");
            icon = assets.art("furniture", (UUID) furn, "icon");
        } else if (recipe != null) {
            kind = "recipe"; guid = recipe.toString(); name = rs.getString("c_name");
        } else {
            kind = "move"; guid = move == null ? null : move.toString(); name = rs.getString("m_name");
        }
        Integer price = (ov != null && ov > 0) ? ov : base;
        return new ShopEntry(kind, guid, name, icon, price, limit);
    }

    private static Pos pos(ResultSet rs, String x, String y, String z) {
        try {
            Object ax = rs.getObject(x), ay = rs.getObject(y), az = rs.getObject(z);
            if (ax == null && ay == null && az == null) return null;
            return new Pos(toD(ax), toD(ay), toD(az));
        } catch (SQLException e) {
            return null;
        }
    }

    private static Double toD(Object o) { return o == null ? null : ((Number) o).doubleValue(); }

    static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isEmpty()) return v;
        return null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
