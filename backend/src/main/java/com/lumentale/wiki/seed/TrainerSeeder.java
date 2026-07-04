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
 * Modular domain seeder for the TRAINER slice ({@code trainer}, {@code trainer_party},
 * {@code trainer_inventory}), backfilling from {@code data/seed/trainers.json} after
 * Flyway + the core {@link Seeder} (@Order 0) have run. Runs at {@code @Order(20)}
 * so the FK targets it guards against — {@code form}, {@code item}, {@code quirk} —
 * are already seeded by the core slice.
 *
 * <h3>FK guards (V13 cross-module constraints)</h3>
 * <ul>
 *   <li>{@code trainer_party.form_guid → form} — NOT NULL, so a party row whose
 *       form is absent is SKIPPED entirely (logged).</li>
 *   <li>{@code trainer_party.item_guid → item} — nulled out if the held item is
 *       absent (the party row still seeds, just without the item).</li>
 *   <li>{@code trainer_party.quirk_class → quirk} — nulled out if absent.</li>
 *   <li>{@code trainer_inventory.item_guid → item} — NOT NULL, so an inventory row
 *       whose item is absent is SKIPPED (logged).</li>
 * </ul>
 *
 * <h3>Idempotency</h3>
 * Guarded by {@link SeedSupport#isEmpty}: seeds only when {@code trainer} is empty,
 * so a reboot is a no-op and a partial backfill reseeds cleanly.
 */
@Component
@Order(20)
public class TrainerSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TrainerSeeder.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Value("${lumentale.seed.on-empty:true}") private boolean seedOnEmpty;
    @Value("${lumentale.seed.dir:data/seed}")  private String seedDir;

    public TrainerSeeder(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedOnEmpty) { log.info("TrainerSeeder: seeding disabled (lumentale.seed.on-empty=false)."); return; }
        if (!isEmpty(jdbc, "trainer")) {
            Integer n = jdbc.queryForObject("SELECT count(*) FROM trainer", Integer.class);
            log.info("TrainerSeeder: trainer already has {} rows — skipping.", n);
            return;
        }

        JsonNode trainers = readArray(mapper, seedDir, "trainers.json");

        // FK parent sets, loaded once for FK-safe child skipping / null-out.
        Set<UUID> forms = guidSet(jdbc, "SELECT guid FROM form");
        Set<UUID> items = guidSet(jdbc, "SELECT guid FROM item");
        Set<String> quirks = new HashSet<>();
        jdbc.query("SELECT quirk_class FROM quirk", rs -> { quirks.add(rs.getString(1)); });

        int tn = 0, party = 0, partySkip = 0, itemNull = 0, quirkNull = 0, inv = 0, invSkip = 0;

        for (JsonNode t : trainers) {
            UUID guid = safeUuid(txt(t, "guid"));
            if (guid == null) continue;

            jdbc.update(
                "INSERT INTO trainer(guid,internal_name,name_raw,prefix,region,lumen_class,level_cap," +
                "money_drop,evolve_with_level,scale_with_powers,scale_levels,ai,sprite_anim_guid," +
                "idle_sprite_guid,raw) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?::jsonb) ON CONFLICT DO NOTHING",
                guid, txt(t, "internal_name"), txt(t, "name_raw"), txt(t, "prefix"), txt(t, "region"),
                intOrNull(t, "lumen_class"), intOrNull(t, "level_cap"), intOrNull(t, "money_drop"),
                boolOrNull(t, "evolve_with_level"), boolOrNull(t, "scale_with_powers"),
                boolOrNull(t, "scale_levels"), obj(t.get("ai")), txt(t, "sprite_anim_guid"),
                txt(t, "idle_sprite_guid"), obj(t.get("raw")));
            tn++;

            int ord = 0;
            for (JsonNode p : t.path("party")) {
                UUID formGuid = uuidOrNull(p, "form_guid");
                if (formGuid == null || !forms.contains(formGuid)) { partySkip++; ord++; continue; }

                UUID itemGuid = uuidOrNull(p, "item_guid");
                if (itemGuid != null && !items.contains(itemGuid)) { itemGuid = null; itemNull++; }

                String quirk = txt(p, "quirk_class");
                if (quirk != null && quirk.isBlank()) quirk = null;
                if (quirk != null && !quirks.contains(quirk)) { quirk = null; quirkNull++; }

                jdbc.update(
                    "INSERT INTO trainer_party(trainer_guid,ord,form_guid,level,level_offset," +
                    "nickname,item_guid,quirk_class,is_hidden_quirk,raw) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?::jsonb)",
                    guid, ord++, formGuid, intOrNull(p, "level"), intOrNull(p, "level_scale_offset"),
                    txt(p, "nickname"), itemGuid, quirk,
                    Boolean.TRUE.equals(boolOrNull(p, "randomize_quirk")), obj(p));
                party++;
            }

            for (JsonNode iv : t.path("inventory")) {
                UUID itemGuid = uuidOrNull(iv, "item_guid");
                if (itemGuid == null || !items.contains(itemGuid)) { invSkip++; continue; }
                jdbc.update("INSERT INTO trainer_inventory(trainer_guid,item_guid,amount) VALUES (?,?,?)",
                    guid, itemGuid, intOrNull(iv, "amount"));
                inv++;
            }
        }

        log.info("TrainerSeeder: trainer={} rows, trainer_party={} (skipped→form={}, item nulled={}, quirk nulled={}), " +
                "trainer_inventory={} (skipped→item={}).",
            tn, party, partySkip, itemNull, quirkNull, inv, invSkip);
    }
}
