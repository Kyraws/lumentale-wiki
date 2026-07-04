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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.lumentale.wiki.seed.SeedSupport.arr;
import static com.lumentale.wiki.seed.SeedSupport.guidSet;
import static com.lumentale.wiki.seed.SeedSupport.intOrNull;
import static com.lumentale.wiki.seed.SeedSupport.isEmpty;
import static com.lumentale.wiki.seed.SeedSupport.longOrNull;
import static com.lumentale.wiki.seed.SeedSupport.obj;
import static com.lumentale.wiki.seed.SeedSupport.readArray;
import static com.lumentale.wiki.seed.SeedSupport.safeUuid;
import static com.lumentale.wiki.seed.SeedSupport.txt;

/**
 * Modular domain seeder for the quest page (Module 6), run after the core
 * {@link Seeder} via {@code @Order(20)}. Backfills {@code quest},
 * {@code quest_node}, {@code quest_transition}, {@code quest_item_reward}, and
 * {@code variable} from {@code data/seed/{quests,variables}.json}.
 *
 * <h3>FK-safe partial seeding</h3>
 * Following the core seeder's pattern, child rows pointing at parents that aren't
 * present in THIS seed are skipped with a logged count rather than aborting:
 * <ul>
 *   <li>{@code quest_transition.to_pathid} → {@code quest_node.pathid} (FK in V13):
 *       valid pathids are pre-loaded from the just-seeded nodes; a transition whose
 *       target is dangling is skipped.</li>
 *   <li>{@code quest_item_reward}: the table has
 *       {@code CHECK num_nonnulls(item_guid,furniture_guid)=1}, so exactly one typed
 *       FK must be set. The source carries a {@code kind} (ItemData / FurnitureData /
 *       GameCardData / CraftingProjectData); only ItemData→{@code item} and
 *       FurnitureData→{@code furniture} have a typed column, and the referent must
 *       exist in the pre-loaded guid set — otherwise the whole reward row is skipped
 *       (card / crafting rewards have no column yet).</li>
 * </ul>
 *
 * Idempotent: guarded by {@link SeedSupport#isEmpty} on {@code quest}; all inserts
 * use {@code ON CONFLICT DO NOTHING}.
 */
@Component
@Order(20)
public class QuestSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(QuestSeeder.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Value("${lumentale.seed.on-empty:true}") private boolean seedOnEmpty;
    @Value("${lumentale.seed.dir:data/seed}")  private String seedDir;

    public QuestSeeder(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedOnEmpty) { log.info("QuestSeeder: seeding disabled."); return; }
        if (!isEmpty(jdbc, "quest")) {
            log.info("QuestSeeder: quest already populated — skipping.");
            return;
        }
        log.info("QuestSeeder: source={}", seedDir);

        seedVariables();   // pathid PK, independent of quests
        seedQuests();
    }

    // ----------------------------------------------------------------- variable

    private void seedVariables() {
        JsonNode vars = readArray(mapper, seedDir, "variables.json");
        int n = 0;
        for (JsonNode v : vars) {
            Long pathid = longOrNull(v, "pathid");
            if (pathid == null) continue;   // pathid is the PK
            jdbc.update(
                "INSERT INTO variable(pathid,name,kind,default_value) VALUES (?,?,?,?) " +
                "ON CONFLICT (pathid) DO NOTHING",
                pathid, txt(v, "name"), txt(v, "kind"), intOrNull(v, "default_value"));
            n++;
        }
        log.info("  variable: {} rows", n);
    }

    // -------------------------------------------------------------------- quests

    private void seedQuests() {
        JsonNode quests = readArray(mapper, seedDir, "quests.json");

        // FK-safe parent sets for the typed reward columns.
        Set<UUID> items = guidSet(jdbc, "SELECT guid FROM item");
        Set<UUID> furniture = guidSet(jdbc, "SELECT guid FROM furniture");

        // Pass 1: quest + quest_node (must precede transitions — FK on from_pathid).
        Set<Long> nodePathids = new HashSet<>();
        int qn = 0, nodes = 0;
        for (JsonNode q : quests) {
            UUID guid = safeUuid(txt(q, "guid"));
            if (guid == null) continue;
            jdbc.update(
                "INSERT INTO quest(guid,internal_name,name_raw,description_raw,quest_giver,quest_type," +
                "money_reward,exp_reward,card_level,raw) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?::jsonb) ON CONFLICT (guid) DO NOTHING",
                guid, txt(q, "internal_name"), txt(q, "quest_name_raw"), txt(q, "quest_description_raw"),
                txt(q, "quest_giver"), intOrNull(q, "quest_type"), intOrNull(q, "money_reward"),
                intOrNull(q, "exp_reward"), intOrNull(q, "card_level"), obj(q));
            qn++;

            for (JsonNode node : q.path("nodes")) {
                Long pathid = longOrNull(node, "pathid");
                if (pathid == null) continue;   // pathid is the PK
                jdbc.update(
                    "INSERT INTO quest_node(pathid,quest_guid,kind,state_id,state_name,mission_label_raw," +
                    "objectives_key,conditions,objectives,raw) " +
                    "VALUES (?,?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb) ON CONFLICT (pathid) DO NOTHING",
                    pathid, guid, txt(node, "kind"), txt(node, "state_id"), txt(node, "state_name"),
                    txt(node, "mission_label_raw"), txt(node, "objectives_key_raw"),
                    arr(node.get("conditions")), arr(node.get("objectives")), obj(node.get("raw")));
                nodePathids.add(pathid);
                nodes++;
            }
        }

        // Pass 2: quest_transition (skip dangling to_pathid) + quest_item_reward.
        int tr = 0, trMiss = 0, rew = 0, rewMiss = 0;
        for (JsonNode q : quests) {
            UUID guid = safeUuid(txt(q, "guid"));
            if (guid == null) continue;

            for (JsonNode node : q.path("nodes")) {
                Long from = longOrNull(node, "pathid");
                if (from == null || !nodePathids.contains(from)) continue;
                for (JsonNode t : node.path("transitions")) {
                    Long to = longOrNull(t, "to_pathid");
                    if (to == null || !nodePathids.contains(to)) { trMiss++; continue; }   // FK guard
                    jdbc.update(
                        "INSERT INTO quest_transition(from_pathid,to_pathid,port) VALUES (?,?,?)",
                        from, to, txt(t, "port"));
                    tr++;
                }
            }

            for (JsonNode r : q.path("item_rewards")) {
                String kind = txt(r, "kind");
                UUID ref = safeUuid(txt(r, "guid"));
                UUID itemGuid = null, furnitureGuid = null;
                if ("ItemData".equals(kind) && ref != null && items.contains(ref)) {
                    itemGuid = ref;
                } else if ("FurnitureData".equals(kind) && ref != null && furniture.contains(ref)) {
                    furnitureGuid = ref;
                }
                if (itemGuid == null && furnitureGuid == null) { rewMiss++; continue; }   // CHECK: exactly one
                jdbc.update(
                    "INSERT INTO quest_item_reward(quest_guid,item_guid,furniture_guid,amount) " +
                    "VALUES (?,?,?,?)",
                    guid, itemGuid, furnitureGuid, intOrNull(r, "amount"));
                rew++;
            }
        }

        log.info("  quest: {} rows, quest_node: {} rows", qn, nodes);
        log.info("  quest_transition: {} rows (skipped dangling to_pathid={})", tr, trMiss);
        log.info("  quest_item_reward: {} rows (skipped unresolved/typeless={})", rew, rewMiss);
    }
}
