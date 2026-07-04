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

import java.util.UUID;

import static com.lumentale.wiki.seed.SeedSupport.boolOrNull;
import static com.lumentale.wiki.seed.SeedSupport.intOrNull;
import static com.lumentale.wiki.seed.SeedSupport.obj;
import static com.lumentale.wiki.seed.SeedSupport.txt;

/**
 * Modular domain seeder for the furniture catalogue (Module 8). Runs after the
 * core {@link Seeder} (@Order(0)) via @Order(10), following the identical pattern:
 * idempotent (only seeds when the table is empty), self-contained (reads its own
 * JSON through {@link SeedSupport}), and logs its row count.
 *
 * <p>Furniture has <b>no cross-module FK</b> — {@code model_guid}/{@code sprite_guid}
 * are 32-hex Addressables art GUIDs (text), not entity references — so there is no
 * FK-ordering or parent-set skipping. Guids bind as {@link UUID}; {@code raw} binds
 * as {@code ?::jsonb}; the insert is {@code ON CONFLICT DO NOTHING}.
 *
 * <p>{@code rarity_label} is left null: the source ({@code furniture.json}) carries
 * only the raw {@code rarity_raw} code with no resolved label, and the DDL keeps the
 * label inline precisely because there is no clean game enum to derive it from.
 */
@Component
@Order(10)
public class FurnitureSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FurnitureSeeder.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Value("${lumentale.seed.on-empty:true}") private boolean seedOnEmpty;
    @Value("${lumentale.seed.dir:data/seed}")  private String seedDir;

    public FurnitureSeeder(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedOnEmpty) return;
        if (!SeedSupport.isEmpty(jdbc, "furniture")) {
            log.info("  furniture already seeded — skipping.");
            return;
        }

        JsonNode rows = SeedSupport.readArray(mapper, seedDir, "furniture.json");
        int n = 0;
        for (JsonNode f : rows) {
            UUID guid = SeedSupport.uuidOrNull(f, "guid");
            if (guid == null) continue;
            jdbc.update(
                "INSERT INTO furniture(guid,name_raw,name_key,price,rarity,rarity_label," +
                "size,size_x,size_y,is_carpet,model_guid,sprite_guid,raw) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb) ON CONFLICT DO NOTHING",
                guid, txt(f, "name_raw"), txt(f, "name_key"), intOrNull(f, "price"),
                intOrNull(f, "rarity_raw"), null,
                intOrNull(f, "size"), intOrNull(f, "size_x"), intOrNull(f, "size_y"),
                boolOrNull(f, "is_carpet"), txt(f, "model_guid"), txt(f, "sprite_guid"),
                obj(f.get("raw")));
            n++;
        }
        log.info("  furniture: {} rows", n);
    }
}
