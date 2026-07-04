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

import static com.lumentale.wiki.seed.SeedSupport.*;

/**
 * Modular domain seeder for camps + squadrons (Module 7's group tables), backfilling
 * {@code camp, camp_target, camp_task, squadron, squadron_member} from
 * {@code data/seed/{camps,squadrons}.json} after Flyway and the core {@link Seeder}.
 *
 * <h3>FK ordering ({@code @Order(40)})</h3>
 * The cross-module references — {@code camp_target.form_guid → form},
 * {@code camp_task.quest_guid → quest}, {@code squadron.camp_boss_guid →
 * trainer}, {@code squadron_member.trainer_guid → trainer} — are V13 constraints.
 * This runs at {@code @Order(40)}, after {@code form} (core {@code @Order(0)}) and
 * after {@code quest}/{@code trainer} (their own slice seeders at {@code @Order(20)}),
 * so the parent rows exist. Child rows whose required parent is still absent from a
 * partial seed are skipped with a logged count (NOT NULL FKs: {@code camp_target},
 * {@code camp_task}, {@code squadron_member}) or nulled out (the nullable
 * {@code squadron.camp_boss_guid}) — so a slice seed never aborts on an FK.
 *
 * <h3>Idempotency</h3>
 * Each table is guarded by {@link SeedSupport#isEmpty}; a reboot is a no-op, and a
 * later trainer/quest seed backfills the empty cross-link tables on next boot.
 */
@Component
@Order(40)
public class CampSquadronSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CampSquadronSeeder.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Value("${lumentale.seed.on-empty:true}") private boolean seedOnEmpty;
    @Value("${lumentale.seed.dir:data/seed}")  private String seedDir;

    public CampSquadronSeeder(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedOnEmpty) { log.info("CampSquadronSeeder: seeding disabled."); return; }

        if (isEmpty(jdbc, "camp"))     seedCamps();
        if (isEmpty(jdbc, "squadron")) seedSquadrons();
    }

    // ================================================================= camps

    private void seedCamps() {
        JsonNode camps = readArray(mapper, seedDir, "camps.json");
        // FK guard sets for the NOT NULL child references.
        Set<UUID> formGuids  = guidSet(jdbc, "SELECT guid FROM form");
        Set<UUID> questGuids = guidSet(jdbc, "SELECT guid FROM quest");

        int n = 0, targets = 0, tMiss = 0, tasks = 0, taskMiss = 0;
        for (JsonNode c : camps) {
            UUID guid = safeUuid(txt(c, "guid"));
            if (guid == null) continue;
            JsonNode effect = c.path("effect");
            JsonNode data = effect.path("data");
            jdbc.update(
                "INSERT INTO camp(guid,name,effect_class,effect_description,effect_duration," +
                "effect_increment,influence,lumen_amount,raw) " +
                "VALUES (?,?,?,?,?,?,?,?,?::jsonb) ON CONFLICT DO NOTHING",
                guid, txt(c, "name"), txt(effect, "class"), txt(data, "Description"),
                intOrNull(data, "Duration"), dblOrNull(data, "Increment"),
                intOrNull(c, "influence"), intOrNull(c, "lumen_amount"), obj(c.get("raw")));
            n++;

            for (JsonNode tg : c.path("target_animon_guids")) {
                UUID form = safeUuid(tg.asText());
                if (form == null || !formGuids.contains(form)) { tMiss++; continue; }   // NOT NULL FK
                jdbc.update("INSERT INTO camp_target(camp_guid,form_guid) VALUES (?,?)", guid, form);
                targets++;
            }
            for (JsonNode qg : c.path("task_guids")) {
                UUID quest = safeUuid(qg.asText());
                if (quest == null || !questGuids.contains(quest)) { taskMiss++; continue; }   // NOT NULL FK
                jdbc.update("INSERT INTO camp_task(camp_guid,quest_guid) VALUES (?,?)", guid, quest);
                tasks++;
            }
        }
        log.info("  camp: {} rows, camp_target: {} (skipped→form={}), camp_task: {} (skipped→quest={})",
            n, targets, tMiss, tasks, taskMiss);
    }

    // ============================================================= squadrons

    private void seedSquadrons() {
        JsonNode squadrons = readArray(mapper, seedDir, "squadrons.json");
        Set<UUID> trainerGuids = guidSet(jdbc, "SELECT guid FROM trainer");

        int n = 0, members = 0, mMiss = 0, bossNulled = 0;
        for (JsonNode s : squadrons) {
            UUID guid = safeUuid(txt(s, "guid"));
            if (guid == null) continue;

            UUID campBoss = uuidOrNull(s, "camp_boss_guid");
            if (campBoss != null && !trainerGuids.contains(campBoss)) { campBoss = null; bossNulled++; }   // nullable FK

            JsonNode memberGuids = s.path("member_guids");
            JsonNode rankGuids = s.path("rank_guids");
            // Source carries no member_count; derive from the roster the source lists.
            Integer memberCount = memberGuids.size() + rankGuids.size();

            jdbc.update(
                "INSERT INTO squadron(guid,internal_name,name_raw,display_name,rank,member_count," +
                "color,camp_boss_guid,logo_guid,texture_guid,raw) " +
                "VALUES (?,?,?,?,?,?,?::jsonb,?,?,?,?::jsonb) ON CONFLICT DO NOTHING",
                guid, txt(s, "internal_name"), txt(s, "name_raw"), txt(s, "display_name"),
                intOrNull(s, "rank"), memberCount, obj(s.get("color")), campBoss,
                txt(s, "logo_guid"), txt(s, "texture_guid"), obj(s.get("raw")));
            n++;

            int ord = 0;
            for (JsonNode mg : memberGuids) {
                int r = insertMember(guid, mg.asText(), "member", ord, trainerGuids);
                if (r == 1) { members++; ord++; } else mMiss++;
            }
            ord = 0;
            for (JsonNode rg : rankGuids) {
                int r = insertMember(guid, rg.asText(), "rank", ord, trainerGuids);
                if (r == 1) { members++; ord++; } else mMiss++;
            }
        }
        log.info("  squadron: {} rows, squadron_member: {} (skipped→trainer={}, camp_boss nulled→trainer={})",
            n, members, mMiss, bossNulled);
    }

    /** Insert one squadron_member; skip (return 0) if the trainer FK is absent (NOT NULL). */
    private int insertMember(UUID squadron, String trainerStr, String role, int ord, Set<UUID> trainerGuids) {
        UUID trainer = safeUuid(trainerStr);
        if (trainer == null || !trainerGuids.contains(trainer)) return 0;
        jdbc.update("INSERT INTO squadron_member(squadron_guid,trainer_guid,role,ord) VALUES (?,?,?,?)",
            squadron, trainer, role, ord);
        return 1;
    }
}
