package com.lumentale.wiki.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.lumentale.wiki.seed.SeedSupport.boolOrNull;
import static com.lumentale.wiki.seed.SeedSupport.dblOrNull;
import static com.lumentale.wiki.seed.SeedSupport.guidSet;
import static com.lumentale.wiki.seed.SeedSupport.intOrNull;
import static com.lumentale.wiki.seed.SeedSupport.isEmpty;
import static com.lumentale.wiki.seed.SeedSupport.obj;
import static com.lumentale.wiki.seed.SeedSupport.readArray;
import static com.lumentale.wiki.seed.SeedSupport.readRoot;
import static com.lumentale.wiki.seed.SeedSupport.safeUuid;
import static com.lumentale.wiki.seed.SeedSupport.txt;
import static com.lumentale.wiki.seed.SeedSupport.uuidOrNull;

/**
 * Modular domain seeder for the world (Module 5): maps + their physical placement
 * layer + the reachability graph, plus the {@code form_spawn} join that links forms
 * to the maps they appear on. Runs after the core {@link Seeder} (@Order(0)) and
 * after the item/furniture/trainer/boss seeders via {@code @Order(50)}, so every
 * cross-module FK target (game_map, form, item, furniture, crafting_recipe, move,
 * trainer, boss) is already seeded — child rows whose parent is genuinely absent are
 * nulled (nullable cols) or skipped (NOT NULL cols), with a logged count.
 *
 * <h3>FK guards</h3>
 * Cross-table guids are checked against pre-loaded {@code guidSet}s. For nullable
 * columns a dangling reference is nulled; for NOT NULL columns the whole row is
 * skipped (form_spawn → game_map/form; map_spawn → form; map_battle_form → form).
 *
 * <h3>Typed shop entries</h3>
 * The source still carries the v2 polymorphic {@code (guid, ref_type)}; this maps
 * each {@code ref_type} to the matching typed column (ItemData→item_guid,
 * FurnitureData→furniture_guid, CraftingProjectData→recipe_guid, SkillData→move_guid)
 * and only inserts when exactly one resolves to a seeded parent — honouring the
 * {@code map_shop_entry_one_ref} CHECK.
 *
 * <h3>Synthesised ids</h3>
 * The bigint PKs for spawner/spawn/shop/shop_entry/battle/battle_form/graph_edge are
 * not present in the source (spawners/shops are positional), so they are assigned
 * from sequential counters within this run.
 */
@Component
@Order(50)
public class MapSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MapSeeder.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Value("${lumentale.seed.on-empty:true}") private boolean seedOnEmpty;
    @Value("${lumentale.seed.dir:data/seed}")  private String seedDir;

    // FK guard sets (loaded once, after the upstream seeders have run).
    private Set<UUID> maps;
    private Set<UUID> forms;
    private Set<UUID> items;
    private Set<UUID> furniture;
    private Set<UUID> recipes;
    private Set<UUID> moves;
    private Set<UUID> trainers;
    private Set<UUID> bosses;

    // Sequential bigint id counters for tables whose source has no PK.
    private long spawnerId = 1, spawnId = 1, shopId = 1, shopEntryId = 1, battleId = 1, battleFormId = 1;

    public MapSeeder(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedOnEmpty) return;

        // FK guard sets — every parent table has been seeded by now (@Order(50)).
        maps      = guidSet(jdbc, "SELECT guid FROM game_map");          // refreshed after game_map seed below
        forms     = guidSet(jdbc, "SELECT guid FROM form");
        items     = guidSet(jdbc, "SELECT guid FROM item");
        furniture = guidSet(jdbc, "SELECT guid FROM furniture");
        recipes   = guidSet(jdbc, "SELECT guid FROM crafting_recipe");
        moves     = guidSet(jdbc, "SELECT guid FROM move");
        trainers  = guidSet(jdbc, "SELECT guid FROM trainer");
        bosses    = guidSet(jdbc, "SELECT guid FROM boss");

        // game_map is the FK parent of everything else here, so seed it first, then
        // re-load the guard set so the rest can reference the maps just inserted.
        if (isEmpty(jdbc, "game_map")) { seedMaps(); maps = guidSet(jdbc, "SELECT guid FROM game_map"); }
        else log.info("  game_map already seeded — skipping.");

        if (isEmpty(jdbc, "mini_map"))       seedMiniMaps();        else skip("mini_map");
        if (isEmpty(jdbc, "map_sibling"))    seedSiblings();        else skip("map_sibling");
        if (isEmpty(jdbc, "map_graph_edge")) seedGraphEdges();      else skip("map_graph_edge");
        if (isEmpty(jdbc, "map_exit"))       seedExits();           else skip("map_exit");
        if (isEmpty(jdbc, "map_pickup"))     seedPickups();         else skip("map_pickup");
        if (isEmpty(jdbc, "map_spawner"))    seedSpawners();        else skip("map_spawner");
        if (isEmpty(jdbc, "map_shop"))       seedShops();           else skip("map_shop");
        if (isEmpty(jdbc, "map_battle"))     seedBattles();         else skip("map_battle");
        if (isEmpty(jdbc, "form_spawn"))     seedFormSpawns();      else skip("form_spawn");

        log.info("MapSeeder finished.");
    }

    private void skip(String table) { log.info("  {} already seeded — skipping.", table); }

    // ============================================================== game_map

    /**
     * All maps. {@code parent_guid} is a self-FK, so we insert every row with
     * {@code parent_guid = NULL} first, then a second pass UPDATEs it (only when the
     * parent is itself a seeded map). {@code tile_guid} = the UI-map prefab guid;
     * {@code skybox_guid} is left null (the source SkyboxMaterial is always a pathid
     * pair, no Addressables guid).
     */
    private void seedMaps() {
        JsonNode rows = readArray(mapper, seedDir, "maps.json");
        int n = 0;
        Map<UUID, UUID> parents = new HashMap<>();
        for (JsonNode m : rows) {
            UUID guid = uuidOrNull(m, "guid");
            if (guid == null) continue;
            UUID parent = uuidOrNull(m, "parent_guid");
            if (parent != null) parents.put(guid, parent);
            String tile = txt(m, "ui_map_prefab_guid");
            jdbc.update(
                "INSERT INTO game_map(guid,internal_name,map_name,parent_guid,region,region_side," +
                "is_interior,tile_guid,skybox_guid,raw) " +
                "VALUES (?,?,?,NULL,?,?,?,?,NULL,?::jsonb) ON CONFLICT DO NOTHING",
                guid, txt(m, "internal_name"), txt(m, "map_name"),
                txt(m, "region"), intOrNull(m, "region_side"), boolOrNull(m, "is_interior"),
                (tile == null || tile.isBlank()) ? null : tile, obj(m.get("raw")));
            n++;
        }
        // second pass: set parent_guid where the parent is a seeded map
        Set<UUID> seeded = guidSet(jdbc, "SELECT guid FROM game_map");
        int linked = 0, parentMiss = 0;
        for (var e : parents.entrySet()) {
            if (!seeded.contains(e.getValue())) { parentMiss++; continue; }
            jdbc.update("UPDATE game_map SET parent_guid = ? WHERE guid = ?", e.getValue(), e.getKey());
            linked++;
        }
        log.info("  game_map: {} rows ({} parent links, skipped parent→absent={})", n, linked, parentMiss);
    }

    private void seedMiniMaps() {
        JsonNode rows = readArray(mapper, seedDir, "mini_maps.json");
        int n = 0;
        for (JsonNode m : rows) {
            UUID guid = uuidOrNull(m, "guid");
            if (guid == null) continue;
            jdbc.update(
                "INSERT INTO mini_map(guid,name,region_side,is_interior,raw) VALUES (?,?,?,?,?::jsonb) " +
                "ON CONFLICT DO NOTHING",
                guid, txt(m, "name"), intOrNull(m, "region_side"), boolOrNull(m, "is_interior"),
                obj(m.get("raw")));
            n++;
        }
        log.info("  mini_map: {} rows", n);
    }

    // ============================================================== map_sibling

    /** Sibling links from each map's {@code sibling_guids}; both ends must be seeded maps. */
    private void seedSiblings() {
        JsonNode rows = readArray(mapper, seedDir, "maps.json");
        int n = 0, miss = 0;
        for (JsonNode m : rows) {
            UUID guid = uuidOrNull(m, "guid");
            if (guid == null || !maps.contains(guid)) continue;
            for (JsonNode sib : m.path("sibling_guids")) {
                UUID s = safeUuid(sib.asText());
                if (s == null || !maps.contains(s) || s.equals(guid)) { miss++; continue; }
                jdbc.update("INSERT INTO map_sibling(map_guid,sibling_guid) VALUES (?,?) ON CONFLICT DO NOTHING",
                    guid, s);
                n++;
            }
        }
        log.info("  map_sibling: {} rows (skipped→absent/self={})", n, miss);
    }

    // ============================================================ map_graph_edge

    /**
     * The connectivity graph (map_graph.json): one edge per (node → to_map_guid),
     * with each edge's {@code rid} conditions resolved (via the file's
     * {@code references.RefIds} + the {@code variable} name table) into a readable
     * {@code [{type,flag,check,checkType}]} jsonb array. Both endpoints must be
     * seeded maps (the from/to cols are NOT NULL with V13 FKs).
     */
    private void seedGraphEdges() {
        JsonNode root = readRoot(mapper, seedDir, "map_graph.json");

        // rid → resolved condition object
        Map<Long, JsonNode> conds = new HashMap<>();
        Map<Long, String> varName = new HashMap<>();
        jdbc.query("SELECT pathid, name FROM variable", rs -> { varName.put(rs.getLong(1), rs.getString(2)); });
        for (JsonNode r : root.path("references").path("RefIds")) {
            long rid = r.path("rid").asLong();
            JsonNode data = r.path("data");
            long pid = data.path("Variable").path("m_PathID").asLong();
            ObjectNode c = mapper.createObjectNode();
            c.put("type", r.path("type").path("class").asText());
            c.put("flag", varName.getOrDefault(pid, "#" + pid));
            c.put("check", data.path("Check").asInt());
            if (data.has("CheckType")) c.put("checkType", data.path("CheckType").asInt());
            conds.put(rid, c);
        }

        int n = 0, miss = 0;
        for (JsonNode node : root.path("nodes")) {
            UUID from = safeUuid(node.path("map_guid").asText());
            if (from == null || !maps.contains(from)) { miss += node.path("edges").size(); continue; }
            for (JsonNode e : node.path("edges")) {
                UUID to = safeUuid(e.path("to_map_guid").asText());
                if (to == null || !maps.contains(to)) { miss++; continue; }
                ArrayNode resolved = mapper.createArrayNode();
                for (JsonNode c : e.path("conditions")) {
                    JsonNode rc = conds.get(c.path("rid").asLong());
                    if (rc != null) resolved.add(rc);
                }
                jdbc.update("INSERT INTO map_graph_edge(from_map_guid,to_map_guid,conditions) VALUES (?,?,?::jsonb)",
                    from, to, resolved.toString());
                n++;
            }
        }
        log.info("  map_graph_edge: {} rows (skipped→absent map={})", n, miss);
    }

    // ================================================== map_exit / map_pickup

    /** Physical exits from map_placements.json. target_map_guid nulled if not seeded. */
    private void seedExits() {
        JsonNode rows = readArray(mapper, seedDir, "map_placements.json");
        int n = 0, srcMiss = 0;
        for (JsonNode rec : rows) {
            UUID src = uuidOrNull(rec, "source_map_guid");
            if (src == null || !maps.contains(src)) { srcMiss += rec.path("exits").size(); continue; }
            for (JsonNode e : rec.path("exits")) {
                UUID tgt = uuidOrNull(e, "target_map_guid");
                if (tgt != null && !maps.contains(tgt)) tgt = null;   // nullable FK
                JsonNode pos = e.path("pos"), tpos = e.path("target_pos");
                jdbc.update(
                    "INSERT INTO map_exit(source_map_guid,target_map_guid,target_asset_guid,name,exit_direction," +
                    "pos_x,pos_y,pos_z,target_pos_x,target_pos_y,target_pos_z,resolved_by) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    src, tgt, txt(e, "target_asset_guid"), txt(e, "name"), intOrNull(e, "exit_direction"),
                    dblOrNull(pos, "x"), dblOrNull(pos, "y"), dblOrNull(pos, "z"),
                    dblOrNull(tpos, "x"), dblOrNull(tpos, "y"), dblOrNull(tpos, "z"),
                    txt(e, "target_resolved_by"));
                n++;
            }
        }
        log.info("  map_exit: {} rows (skipped→source absent={})", n, srcMiss);
    }

    /** World item pickups from map_placements.json. item_guid nulled if not seeded. */
    private void seedPickups() {
        JsonNode rows = readArray(mapper, seedDir, "map_placements.json");
        int n = 0, srcMiss = 0;
        for (JsonNode rec : rows) {
            UUID src = uuidOrNull(rec, "source_map_guid");
            if (src == null || !maps.contains(src)) { srcMiss += rec.path("pickups").size(); continue; }
            for (JsonNode p : rec.path("pickups")) {
                UUID item = uuidOrNull(p, "item_guid");
                if (item != null && !items.contains(item)) item = null;   // nullable FK
                JsonNode pos = p.path("pos");
                jdbc.update(
                    "INSERT INTO map_pickup(map_guid,item_guid,name,amount,pos_x,pos_y,pos_z) " +
                    "VALUES (?,?,?,?,?,?,?)",
                    src, item, txt(p, "name"), intOrNull(p, "amount"),
                    dblOrNull(pos, "x"), dblOrNull(pos, "y"), dblOrNull(pos, "z"));
                n++;
            }
        }
        log.info("  map_pickup: {} rows (skipped→source absent={})", n, srcMiss);
    }

    // ============================================== map_spawner / map_spawn

    /**
     * Spawners (map_spawns.json) and their encounters. A spawner whose map is absent
     * is skipped; an encounter whose form is absent is skipped (map_spawn.form_guid
     * is NOT NULL). bigint ids are synthesised sequentially.
     */
    private void seedSpawners() {
        JsonNode rows = readArray(mapper, seedDir, "map_spawns.json");
        int sp = 0, spMiss = 0, en = 0, enMiss = 0;
        for (JsonNode rec : rows) {
            UUID map = uuidOrNull(rec, "source_map_guid");
            if (map == null || !maps.contains(map)) { spMiss += rec.path("spawners").size(); continue; }
            for (JsonNode s : rec.path("spawners")) {
                long sid = spawnerId++;
                JsonNode pos = s.path("pos");
                jdbc.update(
                    "INSERT INTO map_spawner(id,map_guid,name,respawn_time,spawn_limit,level_scale_offset," +
                    "side_scale_var,pos_x,pos_y,pos_z) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    sid, map, txt(s, "name"), dblOrNull(s, "respawn_time"), intOrNull(s, "spawn_limit"),
                    intOrNull(s, "level_scale_offset"), txt(s, "side_scale_var"),
                    dblOrNull(pos, "x"), dblOrNull(pos, "y"), dblOrNull(pos, "z"));
                sp++;
                for (JsonNode e : s.path("encounters")) {
                    UUID form = uuidOrNull(e, "form_guid");
                    if (form == null || !forms.contains(form)) { enMiss++; continue; }
                    jdbc.update(
                        "INSERT INTO map_spawn(id,spawner_id,map_guid,form_guid,level_min,level_max,chance," +
                        "time_band,kind,max_enemies) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        spawnId++, sid, map, form, intOrNull(e, "level_min"), intOrNull(e, "level_max"),
                        dblOrNull(e, "chance"), intOrNull(e, "time"), txt(e, "kind"), intOrNull(e, "max_enemies"));
                    en++;
                }
            }
        }
        log.info("  map_spawner: {} rows (skipped→map absent={}); map_spawn: {} rows (skipped→form absent={})",
            sp, spMiss, en, enMiss);
    }

    // =============================================== map_shop / map_shop_entry

    private static final Map<String, String> REF_TYPE_COL = Map.of(
        "ItemData", "item", "FurnitureData", "furniture",
        "CraftingProjectData", "recipe", "SkillData", "move");

    /**
     * Shops (map_shops.json) and their entries. Each entry's {@code (guid, ref_type)}
     * is mapped to the matching typed column; an entry is inserted only when EXACTLY
     * one typed guid resolves to a seeded parent (honouring the one-ref CHECK).
     */
    private void seedShops() {
        JsonNode rows = readArray(mapper, seedDir, "map_shops.json");
        int sh = 0, shMiss = 0, en = 0, enMiss = 0;
        for (JsonNode rec : rows) {
            UUID map = uuidOrNull(rec, "source_map_guid");
            if (map == null || !maps.contains(map)) { shMiss += rec.path("shops").size(); continue; }
            for (JsonNode s : rec.path("shops")) {
                long sid = shopId++;
                JsonNode pos = s.path("pos");
                jdbc.update(
                    "INSERT INTO map_shop(id,map_guid,npc_name,graph_name,identifier,pos_x,pos_y,pos_z) " +
                    "VALUES (?,?,?,?,?,?,?,?)",
                    sid, map, txt(s, "npc"), txt(s, "graph_name"), txt(s, "identifier"),
                    dblOrNull(pos, "x"), dblOrNull(pos, "y"), dblOrNull(pos, "z"));
                sh++;
                for (JsonNode e : s.path("entries")) {
                    UUID g = uuidOrNull(e, "guid");
                    String col = REF_TYPE_COL.get(txt(e, "ref_type"));
                    if (g == null || col == null || !resolves(col, g)) { enMiss++; continue; }
                    UUID item = "item".equals(col) ? g : null;
                    UUID furn = "furniture".equals(col) ? g : null;
                    UUID rec2 = "recipe".equals(col) ? g : null;
                    UUID move = "move".equals(col) ? g : null;
                    jdbc.update(
                        "INSERT INTO map_shop_entry(id,shop_id,item_guid,furniture_guid,recipe_guid,move_guid," +
                        "price_override,limit_amount) VALUES (?,?,?,?,?,?,?,?)",
                        shopEntryId++, sid, item, furn, rec2, move,
                        intOrNull(e, "price_override"), intOrNull(e, "limit"));
                    en++;
                }
            }
        }
        log.info("  map_shop: {} rows (skipped→map absent={}); map_shop_entry: {} rows (skipped→unresolved ref={})",
            sh, shMiss, en, enMiss);
    }

    /** True if {@code guid} resolves to a seeded parent for the given typed column. */
    private boolean resolves(String col, UUID guid) {
        return switch (col) {
            case "item" -> items.contains(guid);
            case "furniture" -> furniture.contains(guid);
            case "recipe" -> recipes.contains(guid);
            case "move" -> moves.contains(guid);
            default -> false;
        };
    }

    // ============================================ map_battle / map_battle_form

    /**
     * Battles from map_npcs.json: per NPC, each battle is one map_battle row.
     * {@code kind} ∈ trainer|boss|scripted. trainer/boss carry the typed guid (the
     * row is skipped if the trainer/boss isn't seeded, since the CHECK requires the
     * matching guid be NOT NULL); scripted carries neither + its forms in
     * map_battle_form (a scripted form whose form is absent is skipped).
     */
    private void seedBattles() {
        JsonNode rows = readArray(mapper, seedDir, "map_npcs.json");
        int b = 0, bMiss = 0, bf = 0, bfMiss = 0;
        for (JsonNode rec : rows) {
            UUID map = uuidOrNull(rec, "source_map_guid");
            if (map == null || !maps.contains(map)) continue;
            for (JsonNode npc : rec.path("npcs")) {
                String npcName = txt(npc, "npc");
                JsonNode pos = npc.path("pos");
                for (JsonNode battle : npc.path("battles")) {
                    String kind = txt(battle, "kind");
                    UUID trainer = null, boss = null;
                    if ("trainer".equals(kind)) {
                        trainer = uuidOrNull(battle, "guid");
                        if (trainer == null || !trainers.contains(trainer)) { bMiss++; continue; }
                    } else if ("boss".equals(kind)) {
                        boss = uuidOrNull(battle, "guid");
                        if (boss == null || !bosses.contains(boss)) { bMiss++; continue; }
                    } else if (!"scripted".equals(kind)) {
                        bMiss++; continue;   // unknown kind → CHECK would reject
                    }
                    long bid = battleId++;
                    jdbc.update(
                        "INSERT INTO map_battle(id,map_guid,npc_name,kind,trainer_guid,boss_guid,pos_x,pos_y,pos_z) " +
                        "VALUES (?,?,?,?,?,?,?,?,?)",
                        bid, map, npcName, kind, trainer, boss,
                        dblOrNull(pos, "x"), dblOrNull(pos, "y"), dblOrNull(pos, "z"));
                    b++;
                    for (JsonNode f : battle.path("forms")) {
                        UUID form = uuidOrNull(f, "form_guid");
                        if (form == null || !forms.contains(form)) { bfMiss++; continue; }
                        jdbc.update(
                            "INSERT INTO map_battle_form(id,battle_id,form_guid,level_min,level_max) " +
                            "VALUES (?,?,?,?,?)",
                            battleFormId++, bid, form, intOrNull(f, "level_min"), intOrNull(f, "level_max"));
                        bf++;
                    }
                }
            }
        }
        log.info("  map_battle: {} rows (skipped→trainer/boss absent/unknown kind={}); map_battle_form: {} rows (skipped→form absent={})",
            b, bMiss, bf, bfMiss);
    }

    // ================================================================ form_spawn

    /**
     * form_spawn: which maps each form actually appears on. Derived from {@code map_spawn}
     * — the extracted SpawnArea encounters (the ground truth for where a creature spawns).
     * The forms.json {@code spawn_map_guids} list was a strict, incomplete subset (it
     * under-listed ~3 species on 40 maps, e.g. Toxigall/Owaxle/Flobesque on CENTER_AREA_05),
     * so we build the join from the real encounters instead. map_spawn is seeded first
     * (seedSpawners) and its FKs already guarantee valid form/map guids.
     */
    private void seedFormSpawns() {
        int n = jdbc.update(
            "INSERT INTO form_spawn(form_guid, map_guid) " +
            "SELECT DISTINCT form_guid, map_guid FROM map_spawn");
        log.info("  form_spawn: {} rows (distinct form×map from map_spawn encounters)", n);
    }
}
