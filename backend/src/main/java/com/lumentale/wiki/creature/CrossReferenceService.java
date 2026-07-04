package com.lumentale.wiki.creature;

import com.lumentale.wiki.creature.dto.SpawnRef;
import com.lumentale.wiki.creature.dto.UsedByRef;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The "everything connects to everything" seam: resolve what links to a given
 * entity. Centralized so each new page asks one place "what references this?" and
 * gets typed results, instead of re-deriving spawns/usedBy/drops/shops per page.
 *
 * Scoped to the creature cross-links for this reference slice; item drops/shops,
 * quest rewards and map placements move here as those pages land.
 */
@Service
public class CrossReferenceService {

    private final JdbcTemplate jdbc;

    public CrossReferenceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Maps where this form spawns (distinct), with name/region + level range. */
    public List<SpawnRef> spawnsForForm(UUID formGuid) {
        return jdbc.query(
            "SELECT DISTINCT gm.guid, COALESCE(curated_display(gm.guid), NULLIF(gm.map_name,''), gm.internal_name) AS name, " +
            "       gm.region, gm.is_interior, ms.lmin, ms.lmax " +
            "FROM form_spawn fs JOIN game_map gm ON gm.guid = fs.map_guid " +
            "LEFT JOIN (SELECT map_guid, min(level_min) lmin, max(level_max) lmax FROM map_spawn " +
            "           WHERE form_guid = ? GROUP BY map_guid) ms ON ms.map_guid = gm.guid " +
            "WHERE fs.form_guid = ? ORDER BY gm.region NULLS LAST, name",
            (rs, i) -> new SpawnRef(
                rs.getString("guid"),
                rs.getString("name"),
                rs.getString("region"),
                rs.getBoolean("is_interior"),
                (Integer) rs.getObject("lmin"),
                (Integer) rs.getObject("lmax")),
            formGuid, formGuid);
    }

    /** Trainers (named, non-junk) + bosses that field this form. */
    public List<UsedByRef> usedByForm(UUID formGuid) {
        List<UsedByRef> out = new ArrayList<>();
        out.addAll(jdbc.query(
            "SELECT t.guid, COALESCE(curated_display(t.guid), NULLIF(t.name_raw,''), t.internal_name) AS nm, min(tp.level) AS lvl " +
            "FROM trainer_party tp JOIN trainer t ON t.guid = tp.trainer_guid " +
            "WHERE tp.form_guid = ? AND t.internal_name NOT LIKE '%UNUSED%' " +
            "GROUP BY t.guid, nm ORDER BY nm",
            (rs, i) -> new UsedByRef("trainer", rs.getString("guid"), rs.getString("nm"),
                (Integer) rs.getObject("lvl")),
            formGuid));
        out.addAll(jdbc.query(
            "SELECT guid, COALESCE(display, internal_name) AS nm, level FROM boss WHERE form_guid = ? ORDER BY level",
            (rs, i) -> new UsedByRef("boss", rs.getString("guid"), rs.getString("nm"),
                (Integer) rs.getObject("level")),
            formGuid));
        return out;
    }
}
