package com.lumentale.wiki.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Shared, stateless helpers for the modular domain seeders ({@code *Seeder}
 * @Components). Lets each domain seeder stay self-contained — read its JSON,
 * coerce fields to typed values, and load a parent-guid set for FK-safe skipping —
 * without duplicating the boilerplate or touching the core {@link Seeder}.
 */
public final class SeedSupport {

    private SeedSupport() {}

    // ---- JSON field coercion (null-safe) ------------------------------------
    public static String txt(JsonNode n, String f) {
        JsonNode v = n.get(f); return (v == null || v.isNull()) ? null : v.asText();
    }
    public static Integer intOrNull(JsonNode n, String f) {
        JsonNode v = n.get(f); return (v == null || v.isNull() || !v.isNumber()) ? null : v.asInt();
    }
    public static Long longOrNull(JsonNode n, String f) {
        JsonNode v = n.get(f); return (v == null || v.isNull() || !v.isNumber()) ? null : v.asLong();
    }
    public static Double dblOrNull(JsonNode n, String f) {
        JsonNode v = n.get(f); return (v == null || v.isNull() || !v.isNumber()) ? null : v.asDouble();
    }
    public static Boolean boolOrNull(JsonNode n, String f) {
        JsonNode v = n.get(f); return (v == null || v.isNull()) ? null : v.asBoolean();
    }
    public static UUID uuidOrNull(JsonNode n, String f) {
        String s = txt(n, f); return (s == null || s.isBlank()) ? null : safeUuid(s);
    }
    public static UUID safeUuid(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    // ---- jsonb string forms -------------------------------------------------
    /** jsonb object text — "{}" when null. */
    public static String obj(JsonNode n) { return (n == null || n.isNull()) ? "{}" : n.toString(); }
    /** jsonb array text — "[]" when null. */
    public static String arr(JsonNode n) { return (n == null || n.isNull()) ? "[]" : n.toString(); }

    // ---- IO -----------------------------------------------------------------
    /** Read a JSON file under {@code seedDir} as an array (unwraps a {key:[...]} wrapper). */
    public static JsonNode readArray(ObjectMapper mapper, String seedDir, String file) {
        try {
            JsonNode root = mapper.readTree(Files.readAllBytes(Path.of(seedDir, file)));
            if (root.isArray()) return root;
            for (JsonNode v : root) if (v.isArray()) return v;
            return mapper.createArrayNode();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read seed file " + file + " under " + seedDir, e);
        }
    }

    /** Read a JSON file's root node (for {key:{...}} documents). */
    public static JsonNode readRoot(ObjectMapper mapper, String seedDir, String file) {
        try {
            return mapper.readTree(Files.readAllBytes(Path.of(seedDir, file)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read seed file " + file + " under " + seedDir, e);
        }
    }

    // ---- FK helpers ---------------------------------------------------------
    /** Load a set of uuids from a single-uuid-column query (for FK-safe child skipping). */
    public static Set<UUID> guidSet(JdbcTemplate jdbc, String sql) {
        Set<UUID> s = new HashSet<>();
        jdbc.query(sql, rs -> { s.add((UUID) rs.getObject(1)); });
        return s;
    }

    /** True if {@code table} has no rows (the idempotency guard for a seeder). */
    public static boolean isEmpty(JdbcTemplate jdbc, String table) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return n == null || n == 0;
    }
}
