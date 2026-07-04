package com.lumentale.wiki.creature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumentale.wiki.common.AssetResolver;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.common.ReferenceIndex;
import com.lumentale.wiki.creature.dto.CreatureDetail.LearnsetEntry;
import com.lumentale.wiki.creature.dto.CreatureSummary;
import com.lumentale.wiki.creature.dto.TypeChart;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Data access for creatures/forms. SQL lives here and nowhere else.
 *
 * Where v2 read denormalized {@code form.emo/ele/menu_art} columns directly, the
 * redesign stores integer type codes and no art columns — so this repo resolves
 * labels through {@link ReferenceIndex} and art through the hybrid
 * {@link AssetResolver}, and builds the two-axis {@link TypeChart} from
 * {@code form_weakness} (elemental, per-form) + {@code emotion_chart} (global 5×5).
 */
@Repository
public class CreatureRepository {

    private final JdbcTemplate jdbc;
    private final AssetResolver assets;
    private final ReferenceIndex ref;
    private final ObjectMapper mapper;
    private final LocalizationResolver loc;

    public CreatureRepository(JdbcTemplate jdbc, AssetResolver assets, ReferenceIndex ref,
                              ObjectMapper mapper, LocalizationResolver loc) {
        this.jdbc = jdbc;
        this.assets = assets;
        this.ref = ref;
        this.mapper = mapper;
        this.loc = loc;
    }

    /**
     * The English-resolved display block for one form (siblings of the raw {@code form}):
     * the species name (animon_name_&lt;speciesGuid&gt;, already English in source),
     * the variant label, dex, elemental-type label, and the English description
     * (animon_desc_&lt;formGuid&gt;, falling back to the Italian {@code form.description}).
     */
    public record Display(String species, String variant, Integer dex, String ele, String description) {}

    public Optional<Display> display(UUID formGuid, String lang) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT s.guid AS species_guid, s.name AS species, f.variant_name, f.dex, " +
                "       f.ele_type_code, f.description " +
                "FROM form f JOIN species s ON s.guid = f.species_guid WHERE f.guid = ?",
                (rs, i) -> {
                    UUID speciesGuid = (UUID) rs.getObject("species_guid");
                    String species = loc.display(lang, "animon_name_" + speciesGuid, rs.getString("species"));
                    String description = loc.display(lang, "animon_desc_" + formGuid, rs.getString("description"));
                    return new Display(species, rs.getString("variant_name"), (Integer) rs.getObject("dex"),
                        ref.ele((Integer) rs.getObject("ele_type_code")), description);
                },
                formGuid));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * The form's learnset: each {@code form_skill} row joined to {@code move}, with
     * the move name English-resolved (skill_name_&lt;moveGuid&gt; via the move's
     * {@code name_key}, fallback Italian {@code name_raw}) and the type label.
     * Ordered by level then resolved name. {@code method}: 0 = Level Up, else Other.
     */
    public List<LearnsetEntry> learnset(UUID formGuid, String lang) {
        return jdbc.query(
            "SELECT m.guid, m.name_raw, m.name_key, m.ele_type_code, fs.level, fs.method " +
            "FROM form_skill fs JOIN move m ON m.guid = fs.move_guid WHERE fs.form_guid = ?",
            (rs, i) -> {
                UUID moveGuid = (UUID) rs.getObject("guid");
                String name = loc.display(lang, rs.getString("name_key"), rs.getString("name_raw"));
                String method = rs.getInt("method") == 0 ? "Level Up" : "Other";
                return new LearnsetEntry(moveGuid.toString(), name,
                    ref.ele((Integer) rs.getObject("ele_type_code")),
                    (Integer) rs.getObject("level"), method);
            },
            formGuid).stream()
            .sorted(java.util.Comparator
                .comparing((LearnsetEntry e) -> e.level() == null ? Integer.MAX_VALUE : e.level())
                .thenComparing(LearnsetEntry::name, java.util.Comparator.nullsLast(String::compareTo)))
            .toList();
    }

    /**
     * Dex-grid summaries: one row per non-boss form. Regions are supplied by the
     * caller's lookup (the startup-built {@link RegionIndex}), keeping this method
     * pure data-access while the region source stays injected.
     */
    public List<CreatureSummary> dexGrid(Function<String, List<String>> regionLookup) {
        return jdbc.query(
            "SELECT f.guid, s.name AS species, f.variant_name, f.dex, " +
            "       f.ele_type_code, f.emotion_code, " +
            "       (SELECT count(*) FROM form f2 WHERE f2.species_guid = f.species_guid) AS variants " +
            "FROM form f JOIN species s ON s.guid = f.species_guid " +
            // hide boss-only forms from the dex (still reachable by guid)
            "WHERE f.variant_name NOT ILIKE '%boss%' " +
            "ORDER BY f.dex, f.variant_name",
            (rs, i) -> {
                UUID guid = (UUID) rs.getObject("guid");
                return new CreatureSummary(
                    guid.toString(),
                    rs.getString("species"),
                    rs.getString("variant_name"),
                    (Integer) rs.getObject("dex"),
                    ref.emotion((Integer) rs.getObject("emotion_code")),
                    ref.ele((Integer) rs.getObject("ele_type_code")),
                    rs.getInt("variants"),
                    assets.art("form", guid, "lost_menu_art") != null,
                    assets.art("form", guid, "menu_art"),
                    assets.art("form", guid, "front_sprite"),
                    regionLookup.apply(guid.toString()));
            });
    }

    /** The full extracted record for one form, or empty if no such form. */
    public Optional<JsonNode> rawForm(UUID guid) {
        String json;
        try {
            json = jdbc.queryForObject("SELECT raw::text FROM form WHERE guid = ?", String.class, guid);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
        if (json == null) return Optional.empty();
        try {
            // A form's `raw` is surfaced as-is (matching the v2 endpoint); the generic
            // prune lives in RawRecordService for the table-keyed pages.
            return Optional.of(mapper.readTree(json));
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt raw JSON for form guid=" + guid, e);
        }
    }

    /** Sibling forms of a species (full records), ordered by variant name. */
    public List<JsonNode> variantsOfSpecies(UUID speciesGuid) {
        return jdbc.query(
            "SELECT raw::text FROM form WHERE species_guid = ? ORDER BY variant_name",
            (rs, i) -> {
                try { return mapper.readTree(rs.getString(1)); }
                catch (Exception e) { throw new IllegalStateException("Corrupt raw JSON in species " + speciesGuid, e); }
            }, speciesGuid);
    }

    /**
     * The form's two-axis {@link TypeChart}: elemental reactions from
     * {@code form_weakness}, emotion offense/defense sliced from the global
     * {@code emotion_chart}. Assembly is delegated to the pure
     * {@link TypeChartService}.
     */
    public TypeChart typeChart(UUID formGuid) {
        Integer emoCode = jdbc.query("SELECT emotion_code FROM form WHERE guid = ?",
            rs -> rs.next() ? (Integer) rs.getObject(1) : null, formGuid);
        String emotion = ref.emotion(emoCode);

        Map<Integer,String> weak = new HashMap<>();
        jdbc.query("SELECT attacker_code, effectiveness FROM form_weakness WHERE form_guid = ?",
            rs -> { weak.put(rs.getInt("attacker_code"), rs.getString("effectiveness")); }, formGuid);
        List<TypeChart.EleReaction> elemental = TypeChartService.elementalFrom(weak, ref.eleTypes());

        List<TypeChartService.EmotionCell> chart = emotionChart();
        return TypeChartService.build(emotion, elemental, chart);
    }

    /** The global emotion chart with code axes translated to labels (loaded per call; tiny). */
    private List<TypeChartService.EmotionCell> emotionChart() {
        return jdbc.query("SELECT attacker_code, defender_code, multiplier FROM emotion_chart",
            (rs, i) -> new TypeChartService.EmotionCell(
                ref.emotion(rs.getInt("attacker_code")),
                ref.emotion(rs.getInt("defender_code")),
                rs.getDouble("multiplier")));
    }
}
