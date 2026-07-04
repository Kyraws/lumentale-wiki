package com.lumentale.wiki.move;

import com.lumentale.wiki.common.AssetResolver;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.common.ReferenceIndex;
import com.lumentale.wiki.move.dto.MoveLearner;
import com.lumentale.wiki.move.dto.MoveSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data access for moves: the summary list and the per-move learner list.
 *
 * Ported to the redesigned schema — the move's {@code type/category/target/aoe}
 * are int codes resolved through {@link ReferenceIndex}, {@code cost} is
 * {@code sp_cost}, and learner thumbnails resolve through the hybrid
 * {@link AssetResolver} (no {@code menu_art} column). The detail (raw record) is
 * served by {@code RawRecordService}, not here.
 */
@Repository
public class MoveRepository {

    private final JdbcTemplate jdbc;
    private final ReferenceIndex ref;
    private final AssetResolver assets;
    private final LocalizationResolver loc;

    public MoveRepository(JdbcTemplate jdbc, ReferenceIndex ref, AssetResolver assets, LocalizationResolver loc) {
        this.jdbc = jdbc;
        this.ref = ref;
        this.assets = assets;
        this.loc = loc;
    }

    /** How many learner thumbnails a list row carries (the rest is the +N badge). */
    private static final int LEARNER_PREVIEW_CAP = 6;

    /** Move list with English-resolved names (skill_name_&lt;guid&gt; via name_key, fallback name_raw). */
    public List<MoveSummary> summaries(String lang) {
        // One grouped pass: the first few dex-ordered learner forms per move, for
        // the thumbnail strip in the list (avoids a per-row /learners fetch).
        Map<UUID, List<String>> preview = new HashMap<>();
        jdbc.query(
            "SELECT DISTINCT fs.move_guid, f.guid AS form_guid, f.dex, f.variant_name " +
            "FROM form_skill fs JOIN form f ON f.guid = fs.form_guid " +
            "ORDER BY f.dex, f.variant_name",
            rs -> {
                List<String> l = preview.computeIfAbsent((UUID) rs.getObject("move_guid"), k -> new ArrayList<>());
                if (l.size() < LEARNER_PREVIEW_CAP) l.add(((UUID) rs.getObject("form_guid")).toString());
            });

        return jdbc.query(
            "SELECT m.guid, m.name_raw, m.name_key, m.desc_key, m.description, " +
            "       m.power, m.accuracy, m.sp_cost, " +
            "       m.ele_type_code, m.category_code, m.target_code, m.aoe_code, " +
            "       (SELECT count(*) FROM form_skill fs WHERE fs.move_guid = m.guid) AS learners, " +
            // referenced anywhere a player can meet it? bosses, battle graphs, tutor shops
            "       (EXISTS(SELECT 1 FROM boss_skill bs WHERE bs.move_guid = m.guid) " +
            "        OR EXISTS(SELECT 1 FROM boss_graph_skill bg WHERE bg.move_guid = m.guid) " +
            "        OR EXISTS(SELECT 1 FROM map_shop_entry se WHERE se.move_guid = m.guid)) AS used_elsewhere " +
            "FROM move m",
            (rs, i) -> new MoveSummary(
                ((UUID) rs.getObject("guid")).toString(),
                loc.display(lang, rs.getString("name_key"), rs.getString("name_raw")),
                loc.display(lang, rs.getString("desc_key"), rs.getString("description")),
                (Integer) rs.getObject("power"), (Integer) rs.getObject("accuracy"), (Integer) rs.getObject("sp_cost"),
                ref.skillCategory((Integer) rs.getObject("category_code")),
                ref.ele((Integer) rs.getObject("ele_type_code")),
                ref.skillTarget((Integer) rs.getObject("target_code")),
                ref.skillAoe((Integer) rs.getObject("aoe_code")),
                rs.getInt("learners"),
                preview.getOrDefault((UUID) rs.getObject("guid"), List.of()),
                rs.getInt("learners") == 0 && !rs.getBoolean("used_elsewhere")))
            .stream()
            .sorted(Comparator.comparing(MoveSummary::name, Comparator.nullsLast(String::compareToIgnoreCase)))
            .toList();
    }

    /** Forms that learn a move, with thumbnails (distinct, dex-ordered). */
    public List<MoveLearner> learners(UUID moveGuid) {
        return jdbc.query(
            "SELECT DISTINCT f.guid, s.name AS species, f.variant_name, f.dex, fs.level " +
            "FROM form_skill fs JOIN form f ON f.guid=fs.form_guid JOIN species s ON s.guid=f.species_guid " +
            "WHERE fs.move_guid=? ORDER BY f.dex, f.variant_name",
            (rs, i) -> {
                UUID guid = (UUID) rs.getObject("guid");
                return new MoveLearner(guid.toString(), rs.getString("species"), rs.getString("variant_name"),
                    (Integer) rs.getObject("dex"), (Integer) rs.getObject("level"),
                    assets.art("form", guid, "menu_art"));
            },
            moveGuid);
    }
}
