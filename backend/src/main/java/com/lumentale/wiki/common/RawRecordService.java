package com.lumentale.wiki.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Fetches an entity's full extracted {@code raw} JSON for a detail page, pruned
 * of engine internals.
 *
 * The table is a closed {@link RawTable} enum, so a caller can never pass an
 * arbitrary string into the query, and the fetch+parse+prune lives in exactly one
 * place. v3 difference: entity PKs are {@code uuid}, so the guid is validated and
 * bound as a {@link UUID} (the driver maps it to the {@code uuid} column).
 */
@Service
public class RawRecordService {

    /** Entity tables with a {@code raw} jsonb column safe to surface as a record. */
    public enum RawTable {
        FORM("form"), SPECIES("species"), CARD("card"), MOVE("move"), ITEM("item"),
        QUEST("quest"), CAMP("camp"), FURNITURE("furniture"), BOSS("boss"),
        TRAINER("trainer"), ACHIEVEMENT("achievement"), QUIRK("quirk");

        private final String table;
        RawTable(String table) { this.table = table; }
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public RawRecordService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * The pruned record for {@code guid} in {@code table}, or empty if no such row.
     * The table is an enum constant, never caller-supplied text.
     */
    public Optional<JsonNode> find(RawTable table, UUID guid) {
        String json;
        try {
            json = jdbc.queryForObject(
                "SELECT raw::text FROM " + table.table + " WHERE guid = ?",
                String.class, guid);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
        if (json == null) return Optional.empty();
        try {
            return Optional.of(JsonPrune.prune(mapper.readTree(json)));
        } catch (Exception e) {
            // A row exists but its stored JSON is malformed — a real fault, not a
            // 404. Let it surface to the @RestControllerAdvice as a 500.
            throw new IllegalStateException(
                "Corrupt raw JSON for " + table.table + " guid=" + guid, e);
        }
    }
}
