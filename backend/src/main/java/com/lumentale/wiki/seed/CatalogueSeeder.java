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

import java.util.TreeMap;
import java.util.UUID;

import static com.lumentale.wiki.seed.SeedSupport.intOrNull;
import static com.lumentale.wiki.seed.SeedSupport.obj;
import static com.lumentale.wiki.seed.SeedSupport.txt;
import static com.lumentale.wiki.seed.SeedSupport.uuidOrNull;

/**
 * Modular domain seeder for two Module-6/8 catalogue pages: achievements
 * (Module 8) and tutorials + their pages (Module 6). Runs after the core
 * {@link Seeder} (@Order(0)) via @Order(10), following the identical pattern as
 * {@link FurnitureSeeder}: idempotent (each table guarded by
 * {@link SeedSupport#isEmpty}), self-contained (reads its own JSON through
 * {@link SeedSupport}), and logs every row count.
 *
 * <h3>achievement enum FKs</h3>
 * {@code achievement.rarity} and {@code achievement.visibility_type} are int FK
 * columns to the (initially empty) {@code achievement_rarity} /
 * {@code achievement_visibility} lookup tables (Module 1). To avoid FK violations:
 * <ul>
 *   <li><b>rarity</b> — {@code achievements.json} carries BOTH the int
 *       {@code rarity_raw} and a resolved label {@code rarity} (0 Common, 1 Rare,
 *       2 Elite, 3 Champion). So this seeder FIRST backfills
 *       {@code achievement_rarity} from the distinct {@code (rarity_raw → rarity)}
 *       pairs in the data, then sets {@code achievement.rarity = rarity_raw}.
 *   <li><b>visibility_type</b> — the source has only the int code, NO label, and
 *       there is no clean game enum to derive one from. Rather than invent a label
 *       (and rather than risk an FK violation against the empty lookup),
 *       {@code visibility_type} is set to {@code null}. The raw code is still
 *       available in the achievement's pruned {@code raw} record on the detail
 *       endpoint ({@code VisibilityType}).
 * </ul>
 *
 * <h3>tutorial / tutorial_page</h3>
 * {@code tutorial_page.tutorial_guid} is an intra-module FK → {@code tutorial}, so
 * pages are inserted in the same loop after their parent tutorial row. Each page is
 * a {@code (text_key, asset_guid)} pair drawn positionally from the source's
 * parallel {@code page_keys[]} / {@code page_references[].m_AssetGUID} arrays, with
 * {@code ord} = the array index. Guids bind as {@link UUID}; {@code raw} binds as
 * {@code ?::jsonb}; inserts are {@code ON CONFLICT DO NOTHING}.
 */
@Component
@Order(10)
public class CatalogueSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogueSeeder.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Value("${lumentale.seed.on-empty:true}") private boolean seedOnEmpty;
    @Value("${lumentale.seed.dir:data/seed}")  private String seedDir;

    public CatalogueSeeder(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedOnEmpty) return;
        seedAchievements();
        seedTutorials();
    }

    // ============================================================= achievements

    private void seedAchievements() {
        if (!SeedSupport.isEmpty(jdbc, "achievement")) {
            log.info("  achievement already seeded — skipping.");
            return;
        }
        JsonNode rows = SeedSupport.readArray(mapper, seedDir, "achievements.json");

        // Backfill achievement_rarity from the distinct (code → label) pairs the
        // source carries, so the rarity FK is satisfiable. visibility has no label
        // in the data → left unseeded and the FK column set null below.
        boolean seedRarity = SeedSupport.isEmpty(jdbc, "achievement_rarity");
        TreeMap<Integer,String> rarities = new TreeMap<>();
        if (seedRarity) {
            for (JsonNode a : rows) {
                Integer code = intOrNull(a, "rarity_raw");
                String label = txt(a, "rarity");
                if (code != null && label != null) rarities.putIfAbsent(code, label);
            }
            rarities.forEach((code, name) ->
                jdbc.update("INSERT INTO achievement_rarity(code,name) VALUES (?,?) ON CONFLICT DO NOTHING",
                    code, name));
            log.info("  achievement_rarity: {} rows (from achievements.json labels)", rarities.size());
        }

        int n = 0;
        for (JsonNode a : rows) {
            UUID guid = uuidOrNull(a, "guid");
            if (guid == null) continue;
            jdbc.update(
                "INSERT INTO achievement(guid,internal_id,name_raw,name_en,name_key,desc_key," +
                "description_it,description_en,rarity,visibility_type,steps,store_id,icon_guid,raw) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb) ON CONFLICT DO NOTHING",
                guid, txt(a, "internal_id"), txt(a, "name_raw"), txt(a, "name_en"),
                txt(a, "name_key"), txt(a, "desc_key"), txt(a, "description_it"), txt(a, "description_en"),
                intOrNull(a, "rarity_raw"),      // rarity FK → achievement_rarity (seeded above)
                null,                            // visibility_type FK left null (no label in source)
                intOrNull(a, "steps"), txt(a, "store_id"), txt(a, "icon_guid"),
                obj(a.get("raw")));
            n++;
        }
        log.info("  achievement: {} rows", n);
    }

    // ================================================================ tutorials

    private void seedTutorials() {
        if (!SeedSupport.isEmpty(jdbc, "tutorial")) {
            log.info("  tutorial already seeded — skipping.");
            return;
        }
        JsonNode rows = SeedSupport.readArray(mapper, seedDir, "tutorials.json");
        int tut = 0, pages = 0;
        for (JsonNode t : rows) {
            UUID guid = uuidOrNull(t, "guid");
            if (guid == null) continue;
            jdbc.update(
                "INSERT INTO tutorial(guid,internal_name,title_key,page_count,raw) " +
                "VALUES (?,?,?,?,?::jsonb) ON CONFLICT DO NOTHING",
                guid, txt(t, "internal_name"), txt(t, "title_key"),
                intOrNull(t, "page_count"), obj(t.get("raw")));
            tut++;

            // intra-module child: pages pair page_keys[ord] with page_references[ord].m_AssetGUID
            JsonNode keys = t.path("page_keys");
            JsonNode refs = t.path("page_references");
            int count = Math.max(keys.size(), refs.size());
            for (int ord = 0; ord < count; ord++) {
                JsonNode k = ord < keys.size() ? keys.get(ord) : null;
                String textKey = (k == null || k.isNull()) ? null : k.asText();
                String assetGuid = ord < refs.size() ? txt(refs.get(ord), "m_AssetGUID") : null;
                jdbc.update(
                    "INSERT INTO tutorial_page(tutorial_guid,ord,text_key,asset_guid) VALUES (?,?,?,?)",
                    guid, ord, textKey, assetGuid);
                pages++;
            }
        }
        log.info("  tutorial: {} rows, tutorial_page: {} rows", tut, pages);
    }
}
