package com.lumentale.wiki.boss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumentale.wiki.boss.dto.*;
import com.lumentale.wiki.common.AssetResolver;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.common.ReferenceIndex;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for bosses + their logic-graph layer. Demonstrates the redesign's
 * whole-graph storage: the battle graph is read as a single jsonb-document row
 * ({@link #graph}), while the cross-cut {@code boss_graph_skill} rollup is the one
 * join worth materializing (telegraphed skill → move). Enum codes are labelled
 * via {@link ReferenceIndex}; the battle-graph art GUID resolves through the
 * hybrid {@link AssetResolver}.
 */
@Repository
public class BossRepository {

    private final JdbcTemplate jdbc;
    private final ReferenceIndex ref;
    private final AssetResolver assets;
    private final ObjectMapper mapper;
    private final LocalizationResolver loc;

    public BossRepository(JdbcTemplate jdbc, ReferenceIndex ref, AssetResolver assets,
                          ObjectMapper mapper, LocalizationResolver loc) {
        this.jdbc = jdbc;
        this.ref = ref;
        this.assets = assets;
        this.mapper = mapper;
        this.loc = loc;
    }

    public List<BossSummary> list() {
        return jdbc.query(
            "SELECT b.guid, COALESCE(curated_display(b.guid), b.display, b.internal_name) AS name, b.display, b.level, " +
            "       b.ele_type_code, b.emotion_code, b.exp_given, b.extra_health_bars, " +
            "       os.name AS origin_species, (g.boss_guid IS NOT NULL) AS has_graph, " +
            "       b.form_guid " +
            "FROM boss b " +
            "LEFT JOIN species os ON os.guid = b.origin_species_guid " +
            "LEFT JOIN boss_battle_graph g ON g.boss_guid = b.guid " +
            "ORDER BY b.level NULLS LAST, name",
            (rs, i) -> {
                UUID formGuid = (UUID) rs.getObject("form_guid");
                // Bosses derive from a creature form; use that form's menu art as the
                // boss icon (resolves via the on-disk leg /data/forms/<guid>/menu.png).
                String menuArt = formGuid == null ? null : assets.art("form", formGuid, "menu_art");
                return new BossSummary(
                    ((UUID) rs.getObject("guid")).toString(),
                    rs.getString("name"),
                    rs.getString("display"),
                    (Integer) rs.getObject("level"),
                    ref.ele((Integer) rs.getObject("ele_type_code")),
                    ref.emotion((Integer) rs.getObject("emotion_code")),
                    (Integer) rs.getObject("exp_given"),
                    (Integer) rs.getObject("extra_health_bars"),
                    rs.getString("origin_species"),
                    rs.getBoolean("has_graph"),
                    menuArt);
            });
    }

    public Optional<BossDetail> detail(UUID guid, String lang) {
        List<BossDetail> found = jdbc.query(
            "SELECT b.guid, b.internal_name, b.display, b.level, b.ele_type_code, b.emotion_code, " +
            "       b.hidden_type_code, b.exp_given, b.target_bst, b.extra_health_bars, " +
            "       b.stats_override::text AS stats_override, b.ai::text AS ai, " +
            "       b.origin_species_guid, os.name AS origin_species, " +
            // Resolve the origin species to a representative FORM guid so the
            // frontend's /dex/{guid} (which keys on form, not species) links work.
            // Prefer this boss's own form if it belongs to the origin species,
            // otherwise the species' "Base Form", otherwise its lowest dex/first form.
            "       COALESCE(" +
            "         (SELECT bf.guid FROM form bf WHERE bf.species_guid = b.origin_species_guid AND bf.guid = b.form_guid)," +
            "         (SELECT of2.guid FROM form of2 WHERE of2.species_guid = b.origin_species_guid AND of2.variant_name = 'Base Form' LIMIT 1)," +
            "         (SELECT of3.guid FROM form of3 WHERE of3.species_guid = b.origin_species_guid ORDER BY of3.dex NULLS LAST, of3.guid LIMIT 1)" +
            "       ) AS origin_form_guid, " +
            "       b.form_guid, fs.name AS form_species, f.variant_name, " +
            "       g.graph_name, g.node_count, (g.boss_guid IS NOT NULL) AS has_graph, g.note " +
            "FROM boss b " +
            "LEFT JOIN species os ON os.guid = b.origin_species_guid " +
            "LEFT JOIN form f     ON f.guid = b.form_guid " +
            "LEFT JOIN species fs ON fs.guid = f.species_guid " +
            "LEFT JOIN boss_battle_graph g ON g.boss_guid = b.guid " +
            "WHERE b.guid = ?",
            (rs, i) -> {
                UUID originGuid = (UUID) rs.getObject("origin_species_guid");
                UUID originFormGuid = (UUID) rs.getObject("origin_form_guid");
                UUID formGuid   = (UUID) rs.getObject("form_guid");
                // The frontend /dex/{guid} route keys on FORM guid, not species guid.
                // Link via the resolved origin-form guid (all 51 bosses resolve one);
                // fall back to the species guid only if no form exists.
                UUID originLink = originFormGuid != null ? originFormGuid : originGuid;
                EntityRef origin = originGuid == null ? null
                    : new EntityRef(originLink.toString(), rs.getString("origin_species"), null,
                        originFormGuid == null ? null : assets.art("form", originFormGuid, "menu_art"));
                EntityRef form = formGuid == null ? null
                    : new EntityRef(formGuid.toString(), rs.getString("form_species"),
                        rs.getString("variant_name"), assets.art("form", formGuid, "menu_art"));
                boolean hasGraph = rs.getBoolean("has_graph");
                BossDetail.BossGraphInfo gi = (hasGraph || rs.getString("note") != null)
                    ? new BossDetail.BossGraphInfo(rs.getString("graph_name"),
                        (Integer) rs.getObject("node_count"), hasGraph, rs.getString("note"))
                    : null;
                return new BossDetail(
                    ((UUID) rs.getObject("guid")).toString(),
                    rs.getString("internal_name"),
                    rs.getString("display"),
                    (Integer) rs.getObject("level"),
                    ref.ele((Integer) rs.getObject("ele_type_code")),
                    ref.emotion((Integer) rs.getObject("emotion_code")),
                    ref.ele((Integer) rs.getObject("hidden_type_code")),
                    (Integer) rs.getObject("exp_given"),
                    (Integer) rs.getObject("target_bst"),
                    (Integer) rs.getObject("extra_health_bars"),
                    parse(rs.getString("stats_override")),
                    parse(rs.getString("ai")),
                    origin, form,
                    skills(guid, lang),
                    gi);
            }, guid);
        return found.stream().findFirst();
    }

    /** The boss's move kit ({@code boss_skill} → {@code move}), names English-resolved, ordered. */
    public List<BossSkill> skills(UUID guid, String lang) {
        return jdbc.query(
            "SELECT bs.move_guid, m.name_raw, m.name_key, m.ele_type_code, bs.skill_level, bs.ord " +
            "FROM boss_skill bs JOIN move m ON m.guid = bs.move_guid " +
            "WHERE bs.boss_guid = ? ORDER BY bs.ord NULLS LAST, m.name_raw",
            (rs, i) -> new BossSkill(
                ((UUID) rs.getObject("move_guid")).toString(),
                loc.display(lang, rs.getString("name_key"), rs.getString("name_raw")),
                ref.ele((Integer) rs.getObject("ele_type_code")),
                (Integer) rs.getObject("skill_level"),
                (Integer) rs.getObject("ord")),
            guid);
    }

    /** Whole battle graph as one jsonb-document row + the strong-skill rollup. */
    public Optional<BossGraph> graph(UUID guid, String lang) {
        List<BossGraph> found = jdbc.query(
            "SELECT boss_guid, graph_name, asset_guid, node_count, " +
            "       nodes::text AS nodes, edges::text AS edges, note " +
            "FROM boss_battle_graph WHERE boss_guid = ?",
            (rs, i) -> new BossGraph(
                ((UUID) rs.getObject("boss_guid")).toString(),
                rs.getString("graph_name"),
                rs.getString("asset_guid"),
                (Integer) rs.getObject("node_count"),
                parse(rs.getString("nodes")),
                parse(rs.getString("edges")),
                strongSkills(guid, lang),
                rs.getString("note")),
            guid);
        return found.stream().findFirst();
    }

    private List<BossGraph.StrongSkill> strongSkills(UUID guid, String lang) {
        return jdbc.query(
            "SELECT bgs.move_guid, m.name_raw, m.name_key, bgs.target_form, bgs.target_formula " +
            "FROM boss_graph_skill bgs JOIN move m ON m.guid = bgs.move_guid " +
            "WHERE bgs.boss_guid = ? ORDER BY m.name_raw",
            (rs, i) -> new BossGraph.StrongSkill(
                ((UUID) rs.getObject("move_guid")).toString(),
                loc.display(lang, rs.getString("name_key"), rs.getString("name_raw")),
                rs.getString("target_form"),
                rs.getString("target_formula")),
            guid);
    }

    private JsonNode parse(String json) {
        if (json == null) return null;
        try { return mapper.readTree(json); }
        catch (Exception e) { throw new IllegalStateException("Corrupt boss jsonb: " + json.substring(0, Math.min(80, json.length())), e); }
    }
}
