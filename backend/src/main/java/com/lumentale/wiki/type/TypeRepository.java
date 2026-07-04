package com.lumentale.wiki.type;

import com.lumentale.wiki.common.AssetResolver;
import com.lumentale.wiki.common.ReferenceIndex;
import com.lumentale.wiki.type.dto.Quirk.Owner;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Data access for the type analytics: forms with their per-form elemental
 * relations ({@code form_weakness}), quirk metadata + owners, and the meta-panel
 * catalogue counts. SQL lives here; the redesign's int type codes are translated
 * to labels via {@link ReferenceIndex}, never read denormalized from {@code form}.
 */
@Repository
public class TypeRepository {

    /**
     * Tables surfaced in the /api/meta counts (keyed by table name). Every name is
     * a real table in the redesign schema; an empty table simply counts 0.
     */
    private static final String[] META_TABLES = {
        "species", "form", "move", "item", "crafting_recipe", "card", "card_pool",
        "game_map", "mini_map", "quest", "quest_node", "variable", "trainer", "boss",
        "camp", "squadron", "achievement", "furniture", "tutorial",
        "formula", "game_constant", "xp_curve", "behavior_tree", "timeline_director",
        "minigame_instance", "asset" };

    private final JdbcTemplate jdbc;
    private final ReferenceIndex ref;
    private final AssetResolver assets;

    public TypeRepository(JdbcTemplate jdbc, ReferenceIndex ref, AssetResolver assets) {
        this.jdbc = jdbc;
        this.ref = ref;
        this.assets = assets;
    }

    /** Attacking ele types in code order (NONE=0 is never an attacker). */
    public List<String> eleTypes() {
        return ref.eleTypes().entrySet().stream()
            .filter(e -> e.getKey() != 0)
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .toList();
    }

    /**
     * All forms with their elemental relations folded in from {@code form_weakness}
     * (attacker_code → effectiveness), with both the form's own ele/emotion and the
     * attacker codes resolved to labels. Cached — the form tables are static
     * post-seed. Boss-only forms are hidden (matching the dex grid).
     */
    @Cacheable("type.forms")
    public List<TypeAnalytics.Form> loadForms() {
        // 1) base rows
        Map<UUID, FormRow> byGuid = new LinkedHashMap<>();
        jdbc.query(
            "SELECT f.guid, s.name AS species, f.variant_name, f.ele_type_code, f.emotion_code, " +
            "       COALESCE(f.stat_def,0) AS def, COALESCE(f.stat_spd,0) AS spd " +
            "FROM form f JOIN species s ON s.guid = f.species_guid " +
            "WHERE f.variant_name NOT ILIKE '%boss%' " +
            "ORDER BY f.dex, f.guid",
            (RowCallbackHandler) rs -> {
                UUID guid = (UUID) rs.getObject("guid");
                byGuid.put(guid, new FormRow(
                    guid.toString(),
                    rs.getString("species"),
                    rs.getString("variant_name"),
                    ref.emotion((Integer) rs.getObject("emotion_code")),
                    ref.ele((Integer) rs.getObject("ele_type_code")),
                    rs.getInt("def"),
                    rs.getInt("spd"),
                    new HashMap<>()));
            });

        // 2) elemental relations, attacker code → label
        jdbc.query(
            "SELECT form_guid, attacker_code, effectiveness FROM form_weakness",
            (RowCallbackHandler) rs -> {
                FormRow row = byGuid.get((UUID) rs.getObject("form_guid"));
                if (row == null) return;                       // boss-only / filtered form
                String atk = ref.ele(rs.getInt("attacker_code"));
                if (atk != null) row.w().put(atk, rs.getString("effectiveness"));
            });

        List<TypeAnalytics.Form> out = new ArrayList<>(byGuid.size());
        for (FormRow r : byGuid.values())
            out.add(new TypeAnalytics.Form(r.guid(), r.species(), r.variant(),
                r.emo(), r.ele(), r.def(), r.spd(), r.w()));
        return out;
    }

    private record FormRow(String guid, String species, String variant, String emo, String ele,
                           int def, int spd, Map<String, String> w) {}

    /** Owners per quirk class (sorted by class), one entry per form that has it. */
    public Map<String, List<Owner>> quirkOwners() {
        Map<String, List<Owner>> owners = new TreeMap<>();
        jdbc.query(
            "SELECT fq.quirk_class, fq.is_hidden, f.guid, f.dex, s.name AS species, f.variant_name " +
            "FROM form_quirk fq JOIN form f ON f.guid = fq.form_guid " +
            "JOIN species s ON s.guid = f.species_guid " +
            "ORDER BY fq.quirk_class, f.dex",
            (RowCallbackHandler) rs -> {
                UUID guid = (UUID) rs.getObject("guid");
                int dexCol = rs.getInt("dex");
                Integer dex = rs.wasNull() ? null : dexCol;
                String menuArt = assets.art("form", guid, "menu_art");
                owners.computeIfAbsent(rs.getString("quirk_class"), k -> new ArrayList<>())
                    .add(new Owner(guid.toString(), rs.getString("species"),
                        rs.getString("variant_name"), rs.getBoolean("is_hidden"),
                        dex, menuArt));
            });
        return owners;
    }

    /**
     * Per-table catalogue counts (keyed by table). Localization totals are added
     * only when the localization tables are populated — guarded so an unseeded
     * loc layer simply omits the {@code languages}/{@code localized_strings} keys.
     */
    public Map<String, Integer> metaCounts() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String t : META_TABLES)
            m.put(t, count(t));
        int locStrings = count("localization");
        if (locStrings > 0) {
            Integer langs = jdbc.queryForObject("SELECT count(DISTINCT lang) FROM localization", Integer.class);
            m.put("languages", langs == null ? 0 : langs);
            m.put("localized_strings", locStrings);
        }
        return m;
    }

    private int count(String table) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }
}
