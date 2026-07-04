package com.lumentale.wiki.map;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Story-state variant groups: a single location that exists as several maps swapped
 * by the story (e.g. Iris Hamlet → Borgo Iride). Detected data-drivenly from the
 * connectivity graph — when one exit leads to several maps gated on the SAME flag at
 * different check values, those maps are the same place in different states.
 *
 * <p>Currently the game has exactly one such group (BORGO_IRIDE_DESTROYED_VARIANT:
 * NORMAL=0 / DESTROYED_BOSS=1 / DESTROYED_POST=2), but nothing here is hard-coded to
 * it, so any future variant set is picked up automatically.
 */
@Component
public class MapStateGroupIndex {

    private static final Logger log = LoggerFactory.getLogger(MapStateGroupIndex.class);

    private final JdbcTemplate jdbc;
    private Map<String, Group> byMember = Map.of();

    public MapStateGroupIndex(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** A variant of a state group: one map = one story state. */
    public record Variant(String guid, String internalName, int check, String label) {}
    /** A group of state variants sharing a canonical place name, ordered by story progress. */
    public record Group(String canonicalName, List<Variant> variants) {}

    /** The state group this map belongs to, if any (length ≥ 2). */
    public Optional<Group> groupOf(String guid) { return Optional.ofNullable(byMember.get(guid)); }

    @PostConstruct
    public void build() {
        record Row(String from, String flag, String to, int chk, String internal, String mapName) {}
        List<Row> rows = new ArrayList<>();
        jdbc.query(
            "SELECT e.from_map_guid::text f, e.conditions->0->>'flag' flag, e.to_map_guid::text t, " +
            "  (e.conditions->0->>'check')::int chk, g.internal_name, g.map_name " +
            "FROM map_graph_edge e JOIN game_map g ON g.guid = e.to_map_guid " +
            "WHERE jsonb_array_length(e.conditions) > 0 AND e.conditions->0->>'flag' IS NOT NULL " +
            "  AND e.conditions->0->>'check' ~ '^[0-9]+$'",
            (rs, i) -> rows.add(new Row(rs.getString("f"), rs.getString("flag"), rs.getString("t"),
                rs.getInt("chk"), rs.getString("internal_name"), rs.getString("map_name"))));

        // group by (source, flag): a source whose single exit branches on one flag to
        // several distinct maps is presenting that place's states.
        Map<String, List<Row>> bySourceFlag = new LinkedHashMap<>();
        for (Row r : rows) bySourceFlag.computeIfAbsent(r.from() + "|" + r.flag(), k -> new ArrayList<>()).add(r);

        Map<String, Group> members = new LinkedHashMap<>();
        int groups = 0;
        for (List<Row> g : bySourceFlag.values()) {
            // distinct target maps, ordered by check value (story progress)
            Map<String, Row> distinct = new LinkedHashMap<>();
            for (Row r : g) distinct.putIfAbsent(r.to(), r);
            if (distinct.size() < 2) continue;
            List<Variant> variants = distinct.values().stream()
                .sorted((a, b) -> Integer.compare(a.chk(), b.chk()))
                .map(r -> new Variant(r.to(), r.internal(), r.chk(), labelFor(r.internal(), r.chk())))
                .toList();
            String canonical = distinct.values().stream()
                .map(Row::mapName).filter(n -> n != null && !n.isBlank())
                .findFirst().orElse(variants.get(0).internalName());
            Group group = new Group(canonical, variants);
            for (Variant v : variants) members.putIfAbsent(v.guid(), group); // first source wins
            groups++;
        }
        this.byMember = members;
        log.info("MapStateGroupIndex built: {} state-variant group(s), {} member maps", groups, members.size());
    }

    /** Human story-state label from the variant's internal-name suffix (falls back to order). */
    static String labelFor(String internal, int check) {
        String s = internal == null ? "" : internal.toUpperCase();
        if (s.endsWith("_NORMAL") || s.endsWith("_PRE")) return "Before";
        if (s.endsWith("_DESTROYED_BOSS") || s.endsWith("_BOSS")) return "Boss event";
        if (s.endsWith("_DESTROYED_POST") || s.endsWith("_POST") || s.endsWith("_POST2")) return "After";
        if (s.endsWith("_DESTROYED")) return "Destroyed";
        if (s.endsWith("_PT2")) return "Part 2";
        return "State " + (check + 1);
    }
}
