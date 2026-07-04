package com.lumentale.wiki.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static com.lumentale.wiki.seed.SeedSupport.arr;
import static com.lumentale.wiki.seed.SeedSupport.dblOrNull;
import static com.lumentale.wiki.seed.SeedSupport.intOrNull;
import static com.lumentale.wiki.seed.SeedSupport.longOrNull;
import static com.lumentale.wiki.seed.SeedSupport.safeUuid;
import static com.lumentale.wiki.seed.SeedSupport.txt;

/**
 * Modular domain seeder for the story scene graphs (Module 6). Runs after the core
 * {@link Seeder} (@Order(0)) and after the seeders its rollups reference —
 * trainer (@Order(20)), boss (core), game_map (world @Order(50)) — via @Order(60),
 * so the FK-resolving joins below have their parents in place.
 *
 * <p>Follows the established pattern: idempotent (guards on an empty
 * {@code story_scene}), self-contained (reads its own JSON through
 * {@link SeedSupport}), logs counts + skipped. Storage is HYBRID: the whole scene
 * graph is written as jsonb ({@code nodes/edges/entries}, {@code ?::jsonb}), with
 * the small cross-scene rollups derived alongside —
 * <ul>
 *   <li>{@code story_scene_flag} from the scene's {@code flags_set}/{@code flags_checked};</li>
 *   <li>{@code story_scene_battle} from {@code bosses}/{@code trainers} — typed FK per
 *       kind, only inserted when the guid resolves against a pre-loaded parent set
 *       (the {@code num_nonnulls(trainer_guid,boss_guid)=1} CHECK means exactly one
 *       column is set, so an unresolved guid is skipped, not nulled-into-a-violation);</li>
 *   <li>{@code story_scene_trigger} from {@code story_links.json} (keyed by scene_id) —
 *       the map_guid is nulled out when it isn't a known {@code game_map} (the column
 *       is nullable), so a trigger always seeds its NPC even if the map is absent.</li>
 * </ul>
 * Inserts are {@code ON CONFLICT DO NOTHING}. {@code chapter}/{@code main_num} are
 * absent from the source (no main-spine numbering in the export) → seeded null.
 */
@Component
@Order(60)
public class StorySeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StorySeeder.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Value("${lumentale.seed.on-empty:true}") private boolean seedOnEmpty;
    @Value("${lumentale.seed.dir:data/seed}")  private String seedDir;

    public StorySeeder(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedOnEmpty) return;
        if (!SeedSupport.isEmpty(jdbc, "story_scene")) {
            log.info("  story_scene already seeded — skipping.");
            return;
        }

        // Parent-guid sets for FK-safe rollup insertion (loaded once).
        Set<UUID> trainers = SeedSupport.guidSet(jdbc, "SELECT guid FROM trainer");
        Set<UUID> bosses   = SeedSupport.guidSet(jdbc, "SELECT guid FROM boss");
        Set<UUID> maps     = SeedSupport.guidSet(jdbc, "SELECT guid FROM game_map");

        JsonNode scenes = SeedSupport.readArray(mapper, seedDir, "story_scenes.json");
        JsonNode links  = SeedSupport.readRoot(mapper, seedDir, "story_links.json");

        int nScenes = 0, nFlags = 0, nBattles = 0, nTriggers = 0;
        int skippedBattles = 0, nulledMaps = 0;

        for (JsonNode s : scenes) {
            String sceneId = txt(s, "scene_id");
            if (sceneId == null) continue;

            JsonNode nodes = s.get("nodes");
            JsonNode edges = s.get("edges");
            JsonNode entries = s.get("entries");
            int nNodes = (nodes != null && nodes.isArray()) ? nodes.size() : 0;

            int ins = jdbc.update(
                "INSERT INTO story_scene(scene_id, graph_pathid, region, name, main_num, chapter, " +
                "n_dialogue, n_nodes, nodes, edges, entries) " +
                "VALUES (?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb) ON CONFLICT DO NOTHING",
                sceneId, longOrNull(s, "graph_pathid"), txt(s, "region"), txt(s, "name"),
                dblOrNull(s, "main_num"), intOrNull(s, "chapter"),
                intOrNull(s, "n_dialogue"), nNodes,
                arr(nodes), arr(edges), arr(entries));
            if (ins == 0) continue;   // already present (idempotent re-entry)
            nScenes++;

            // ---- story_scene_flag (set + check) ----
            nFlags += seedFlags(sceneId, s.get("flags_set"), "set");
            nFlags += seedFlags(sceneId, s.get("flags_checked"), "check");

            // ---- story_scene_battle (typed FK, exactly one column) ----
            for (JsonNode b : array(s.get("bosses"))) {
                UUID g = safeUuid(b.asText());
                if (g != null && bosses.contains(g)) {
                    jdbc.update("INSERT INTO story_scene_battle(scene_id, boss_guid) VALUES (?,?) " +
                        "ON CONFLICT DO NOTHING", sceneId, g);
                    nBattles++;
                } else skippedBattles++;
            }
            for (JsonNode t : array(s.get("trainers"))) {
                UUID g = safeUuid(t.asText());
                if (g != null && trainers.contains(g)) {
                    jdbc.update("INSERT INTO story_scene_battle(scene_id, trainer_guid) VALUES (?,?) " +
                        "ON CONFLICT DO NOTHING", sceneId, g);
                    nBattles++;
                } else skippedBattles++;
            }

            // ---- story_scene_trigger (story_links.json, map_guid nullable) ----
            JsonNode triggers = links.get(sceneId);
            for (JsonNode tr : array(triggers)) {
                UUID mapGuid = safeUuid(txt(tr, "map_guid"));
                if (mapGuid != null && !maps.contains(mapGuid)) { mapGuid = null; nulledMaps++; }
                JsonNode pos = tr.get("pos");
                jdbc.update(
                    "INSERT INTO story_scene_trigger(scene_id, map_guid, npc, pos_x, pos_y, pos_z) " +
                    "VALUES (?,?,?,?,?,?)",
                    sceneId, mapGuid, txt(tr, "npc"),
                    pos == null ? null : dblOrNull(pos, "x"),
                    pos == null ? null : dblOrNull(pos, "y"),
                    pos == null ? null : dblOrNull(pos, "z"));
                nTriggers++;
            }
        }

        log.info("  story_scene: {} scenes, {} flags, {} battles ({} skipped, no parent), " +
                 "{} triggers ({} map_guid nulled, no game_map)",
            nScenes, nFlags, nBattles, skippedBattles, nTriggers, nulledMaps);
    }

    private int seedFlags(String sceneId, JsonNode flags, String mode) {
        int n = 0;
        for (JsonNode f : array(flags)) {
            String flag = f.asText(null);
            if (flag == null || flag.isBlank()) continue;
            n += jdbc.update("INSERT INTO story_scene_flag(scene_id, flag, mode) VALUES (?,?,?) " +
                "ON CONFLICT DO NOTHING", sceneId, flag, mode);
        }
        return n;
    }

    /** Null-/non-array-safe iteration helper. */
    private static Iterable<JsonNode> array(JsonNode n) {
        return (n != null && n.isArray()) ? n : java.util.Collections.emptyList();
    }
}
