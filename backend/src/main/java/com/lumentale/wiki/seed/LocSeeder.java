package com.lumentale.wiki.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.lumentale.wiki.seed.SeedSupport.isEmpty;

/**
 * Modular seeder for Module 12 (localization): {@code localization} and
 * {@code loc_key}. Runs after the core {@link Seeder} (@Order(0)) via @Order(10),
 * the same slot as the other modular domain seeders.
 *
 * <h3>Source shape (data/seed/loc/)</h3>
 * Eight per-language files {@code {lang}.json} (it,en,de,es,fr,pt,ja,zh) and one
 * {@code _keys.json}, every one a {@code { CATEGORY → { string_id → value } }}
 * document with 30 identical top-level CATEGORY keys (ACHIEVEMENT_DESC, DIALOGUE,
 * ITEM_NAME, …). The CATEGORY is the DB {@code table_name}; the numeric inner key
 * is the {@code string_id}.
 *   <ul>
 *     <li>{@code {lang}.json}: inner value is the translated {@code text} →
 *         {@code localization(lang, table_name, string_id, text)}.</li>
 *     <li>{@code _keys.json}: inner value is the {@code m_key} (the loc reference
 *         entities store) → {@code loc_key(table_name, m_key, string_id)}. This is
 *         exactly the join {@code LocalizationResolver} walks
 *         ({@code loc_key.string_id = localization.string_id}).</li>
 *   </ul>
 *
 * <h3>FK ordering / conflicts</h3>
 * V12 has no cross-module FKs, so there is no parent-set guarding. Both tables
 * carry composite PKs; inserts are batched with {@code ON CONFLICT DO NOTHING}
 * (the {@code DIALOGUE} category, for one, repeats a handful of m_keys across
 * distinct string_ids — the first wins, the rest are dropped by the PK).
 *
 * Idempotent: guarded by {@link SeedSupport#isEmpty} on {@code localization}.
 */
@Component
@Order(10)
public class LocSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LocSeeder.class);

    /** Source language is first; all eight are seeded. Matches LocalizationResolver.LANGS. */
    private static final List<String> LANGS = List.of("it", "en", "de", "es", "fr", "pt", "ja", "zh");

    private static final int BATCH = 2000;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Value("${lumentale.seed.on-empty:true}") private boolean seedOnEmpty;
    @Value("${lumentale.seed.dir:data/seed}")  private String seedDir;

    public LocSeeder(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedOnEmpty) { log.info("LocSeeder: seeding disabled (lumentale.seed.on-empty=false)."); return; }

        if (!isEmpty(jdbc, "localization")) { log.info("  localization already seeded — skipping."); return; }

        Path locDir = Path.of(seedDir, "loc");
        if (!Files.isDirectory(locDir)) {
            log.warn("LocSeeder: no loc source directory at {} — localization NOT seeded. " +
                    "Expected {lang}.json (it,en,de,es,fr,pt,ja,zh) + _keys.json, each a " +
                    "{{CATEGORY -> {{string_id -> value}}}} document.", locDir);
            return;
        }

        seedLocalization(locDir);
        seedLocKeys(locDir);
    }

    /**
     * {@code localization}: one row per (lang, CATEGORY, string_id) across all eight
     * {@code {lang}.json} files. Missing language files are warned and skipped (the
     * others still seed). Empty-string text is preserved as-is.
     */
    private void seedLocalization(Path locDir) {
        long total = 0;
        for (String lang : LANGS) {
            Path file = locDir.resolve(lang + ".json");
            if (!Files.isRegularFile(file)) {
                log.warn("  localization: missing {} — language '{}' skipped.", file.getFileName(), lang);
                continue;
            }
            JsonNode root = readRoot(file);
            List<Object[]> rows = new ArrayList<>(BATCH);
            long langRows = 0;
            for (Iterator<Map.Entry<String, JsonNode>> cats = root.fields(); cats.hasNext(); ) {
                Map.Entry<String, JsonNode> cat = cats.next();
                String table = cat.getKey();
                for (Iterator<Map.Entry<String, JsonNode>> ids = cat.getValue().fields(); ids.hasNext(); ) {
                    Map.Entry<String, JsonNode> id = ids.next();
                    JsonNode v = id.getValue();
                    rows.add(new Object[]{ lang, table, id.getKey(), v.isNull() ? null : v.asText() });
                    if (rows.size() >= BATCH) { langRows += flush(LOC_SQL, rows); }
                }
            }
            langRows += flush(LOC_SQL, rows);
            total += langRows;
            log.info("  localization[{}]: {} rows", lang, langRows);
        }
        log.info("  localization: {} rows total ({} langs)", total, LANGS.size());
    }

    /**
     * {@code loc_key}: one row per (CATEGORY, string_id) in {@code _keys.json}, mapping
     * the entity {@code m_key} to its {@code string_id}. ON CONFLICT DO NOTHING absorbs
     * the few repeated m_keys.
     */
    private void seedLocKeys(Path locDir) {
        Path file = locDir.resolve("_keys.json");
        if (!Files.isRegularFile(file)) {
            log.warn("  loc_key: missing {} — loc_key NOT seeded. Without it, " +
                    "LocalizationResolver.all/table return empty (the join needs loc_key).",
                    file.getFileName());
            return;
        }
        JsonNode root = readRoot(file);
        List<Object[]> rows = new ArrayList<>(BATCH);
        long total = 0;
        for (Iterator<Map.Entry<String, JsonNode>> cats = root.fields(); cats.hasNext(); ) {
            Map.Entry<String, JsonNode> cat = cats.next();
            String table = cat.getKey();
            for (Iterator<Map.Entry<String, JsonNode>> ids = cat.getValue().fields(); ids.hasNext(); ) {
                Map.Entry<String, JsonNode> id = ids.next();
                JsonNode v = id.getValue();
                if (v.isNull()) continue;
                // _keys: string_id -> m_key ; loc_key columns are (table_name, m_key, string_id)
                rows.add(new Object[]{ table, v.asText(), id.getKey() });
                if (rows.size() >= BATCH) { total += flush(KEY_SQL, rows); }
            }
        }
        total += flush(KEY_SQL, rows);
        log.info("  loc_key: {} rows", total);
    }

    private static final String LOC_SQL =
            "INSERT INTO localization(lang,table_name,string_id,text) VALUES (?,?,?,?) " +
            "ON CONFLICT DO NOTHING";
    private static final String KEY_SQL =
            "INSERT INTO loc_key(table_name,m_key,string_id) VALUES (?,?,?) " +
            "ON CONFLICT DO NOTHING";

    /** Batch-insert the accumulated rows, clear the buffer, return the count attempted. */
    private int flush(String sql, List<Object[]> rows) {
        if (rows.isEmpty()) return 0;
        int n = rows.size();
        final List<Object[]> batch = new ArrayList<>(rows);
        rows.clear();
        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                Object[] r = batch.get(i);
                for (int c = 0; c < r.length; c++) ps.setString(c + 1, (String) r[c]);
            }
            @Override public int getBatchSize() { return batch.size(); }
        });
        return n;
    }

    private JsonNode readRoot(Path file) {
        try {
            return mapper.readTree(Files.readAllBytes(file));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read loc seed file " + file, e);
        }
    }
}
