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

import static com.lumentale.wiki.seed.SeedSupport.*;

/**
 * Modular seeder for Module 4 (trading cards): {@code card}, {@code card_pool},
 * {@code card_pool_entry}. Runs after the core {@link Seeder} (@Order(0)) so the
 * cross-module FKs it depends on are already in place.
 *
 * <h3>FK ordering / dependencies</h3>
 *   <ul>
 *     <li>{@code card.form_guid → form} (V13): the depicted form is pre-loaded as
 *         a guid set and nulled out when the form isn't seeded (a partial slice
 *         seed never aborts on the FK). {@code card.ele_type_code → ele_type}
 *         (already seeded by core) is taken from {@code type_raw}.</li>
 *     <li>{@code card_pool} PK is {@code name} (the source guid is empty for every
 *         pool), so {@code name} is the natural key.</li>
 *     <li>{@code card_pool_entry.card_guid → card} (V13): entries pointing at a
 *         card not present in {@code card} are skipped with a logged count;
 *         {@code pool_name → card_pool}.</li>
 *   </ul>
 *
 * Idempotent: each table is guarded by {@link SeedSupport#isEmpty}.
 */
@Component
@Order(10)
public class CardSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CardSeeder.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Value("${lumentale.seed.on-empty:true}") private boolean seedOnEmpty;
    @Value("${lumentale.seed.dir:data/seed}")  private String seedDir;

    public CardSeeder(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedOnEmpty) { log.info("CardSeeder: seeding disabled (lumentale.seed.on-empty=false)."); return; }

        if (isEmpty(jdbc, "card"))            seedCards();
        else log.info("  card already seeded — skipping.");

        if (isEmpty(jdbc, "card_pool"))       seedCardPools();
        else log.info("  card_pool already seeded — skipping.");

        if (isEmpty(jdbc, "card_pool_entry")) seedCardPoolEntries();
        else log.info("  card_pool_entry already seeded — skipping.");
    }

    /**
     * {@code card}: ele_type_code ← {@code type_raw}, emotion_attribute ←
     * {@code attribute_raw}, art/mask/holo ← {@code visuals.*_guid}. form_guid is
     * FK-guarded against the seeded form set.
     */
    private void seedCards() {
        Set<UUID> forms = guidSet(jdbc, "SELECT guid FROM form");
        JsonNode cards = readArray(mapper, seedDir, "cards.json");
        int n = 0, formMiss = 0;
        for (JsonNode c : cards) {
            UUID guid = uuidOrNull(c, "guid");
            if (guid == null) continue;
            UUID form = uuidOrNull(c, "form_guid");
            if (form != null && !forms.contains(form)) { form = null; formMiss++; }   // FK guard
            JsonNode vis = c.path("visuals");
            jdbc.update(
                "INSERT INTO card(guid,name_raw,form_guid,rarity,ele_type_code,emotion_attribute," +
                "artist_name,can_be_kickstarter,art_guid,mask_guid,holo_guid,raw) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?::jsonb) ON CONFLICT DO NOTHING",
                guid, txt(c, "name_raw"), form, txt(c, "rarity"),
                eleOrNull(c), intOrNull(c, "attribute_raw"),
                txt(c, "artist_name"), boolOrNull(c, "can_be_kickstarter"),
                blankToNull(txt(vis, "art_guid")), blankToNull(txt(vis, "mask_guid")),
                blankToNull(txt(vis, "holo_guid")), obj(c.get("raw")));
            n++;
        }
        log.info("  card: {} rows (form_guid nulled→form-absent={})", n, formMiss);
    }

    /** {@code card_pool}: keyed by {@code name}; card_count from the source entry count. */
    private void seedCardPools() {
        JsonNode pools = readArray(mapper, seedDir, "card_pools.json");
        int n = 0;
        for (JsonNode p : pools) {
            String name = txt(p, "name");
            if (name == null || name.isBlank()) continue;
            jdbc.update(
                "INSERT INTO card_pool(name,is_kickstarter_pool,card_count,raw) VALUES (?,?,?,?::jsonb) " +
                "ON CONFLICT DO NOTHING",
                name, boolOrNull(p, "is_kickstarter_pool"), p.path("card_drop_infos").size(), obj(p.get("raw")));
            n++;
        }
        log.info("  card_pool: {} rows", n);
    }

    /**
     * {@code card_pool_entry}: one row per {@code card_drop_infos} entry
     * ({@code Card.CardGUID}, {@code Weight}, {@code Level}), FK-guarded against the
     * seeded card set; {@code ord} is the per-pool index.
     */
    private void seedCardPoolEntries() {
        Set<UUID> cards = guidSet(jdbc, "SELECT guid FROM card");
        Set<String> poolNames = new HashSet<>();
        jdbc.query("SELECT name FROM card_pool", rs -> { poolNames.add(rs.getString(1)); });

        JsonNode pools = readArray(mapper, seedDir, "card_pools.json");
        int n = 0, cardMiss = 0, poolMiss = 0;
        for (JsonNode p : pools) {
            String pool = txt(p, "name");
            if (pool == null || !poolNames.contains(pool)) { poolMiss++; continue; }   // FK: pool must exist
            int ord = 0;
            for (JsonNode e : p.path("card_drop_infos")) {
                UUID card = uuidOrNull(e.path("Card"), "CardGUID");
                if (card == null || !cards.contains(card)) { cardMiss++; ord++; continue; }   // FK guard
                jdbc.update(
                    "INSERT INTO card_pool_entry(pool_name,card_guid,weight,card_level,ord) VALUES (?,?,?,?,?)",
                    pool, card, dblOrNull(e, "Weight"), intOrNull(e, "Level"), ord++);
                n++;
            }
        }
        log.info("  card_pool_entry: {} rows (skipped→card-absent={}, pool-absent={})", n, cardMiss, poolMiss);
    }

    // ---- local helpers ------------------------------------------------------

    /** ele code: 0 (NONE) → null so the FK to ele_type isn't forced to a NONE row. */
    private static Integer eleOrNull(JsonNode c) {
        Integer v = intOrNull(c, "type_raw");
        return (v == null || v == 0) ? null : v;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
