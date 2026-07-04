package com.lumentale.wiki.story;

import com.lumentale.wiki.story.dto.SceneDetail.BattleRef;
import com.lumentale.wiki.story.dto.SceneDetail.Flag;
import com.lumentale.wiki.story.dto.SceneDetail.MapRef;
import com.lumentale.wiki.story.dto.SceneDetail.Neighbour;
import com.lumentale.wiki.story.dto.SceneLite;
import com.lumentale.wiki.story.dto.SceneQuestLink;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for story scenes (Module 6). The scene flow is read as whole jsonb
 * documents in one row ({@code nodes/edges/entries::text}, parsed by the service —
 * the same whole-graph-per-page read {@code BossRepository} uses), with the small
 * cross-scene rollups ({@code story_scene_flag/_battle/_trigger}) joined to resolve
 * the names the page shows. {@code scene_id} is a {@code text} PK (no uuid edge
 * check — it arrives as a request param), so it binds straight as a string.
 */
@Repository
public class StoryRepository {

    private final JdbcTemplate jdbc;
    private final com.lumentale.wiki.common.LocalizationResolver loc;

    public StoryRepository(JdbcTemplate jdbc, com.lumentale.wiki.common.LocalizationResolver loc) {
        this.jdbc = jdbc;
        this.loc = loc;
    }

    /** Raw scene record; flow JSON kept as text for the service to parse. */
    public record SceneRow(String sceneId, String region, String name,
                           Integer chapter, Double mainNum,
                           String nodesText, String edgesText, String entriesText) {}

    /**
     * All scenes for the city index (no flow), ordered for stable grouping.
     * The V6 seed leaves {@code chapter}/{@code main_num} NULL, so both are
     * recovered here: the chapter from the city play order, the main number
     * from the scene name (see {@link StoryGeography#mainNumOf}).
     */
    public List<SceneLite> allScenes() {
        return jdbc.query(
            "SELECT scene_id, region, name, chapter, main_num, n_dialogue " +
            "FROM story_scene ORDER BY region, name",
            (rs, i) -> {
                String region = rs.getString("region");
                String name = rs.getString("name");
                Integer chapter = (Integer) rs.getObject("chapter");
                Double main = (Double) rs.getObject("main_num");
                return new SceneLite(rs.getString("scene_id"), name,
                    region, StoryGeography.trackOf(region),
                    chapter != null ? chapter : StoryGeography.chapterOf(region),
                    main != null ? main : StoryGeography.mainNumOf(name),
                    rs.getInt("n_dialogue"));
            });
    }

    /** One scene row (flow as text), or empty if no such scene_id. */
    public Optional<SceneRow> sceneRow(String id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT scene_id, region, name, chapter, main_num, " +
                "       nodes::text AS nodes, edges::text AS edges, entries::text AS entries " +
                "FROM story_scene WHERE scene_id = ?",
                (rs, i) -> new SceneRow(rs.getString("scene_id"), rs.getString("region"),
                    rs.getString("name"), (Integer) rs.getObject("chapter"),
                    (Double) rs.getObject("main_num"),
                    rs.getString("nodes"), rs.getString("edges"), rs.getString("entries")),
                id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Flag> flags(String id) {
        return jdbc.query(
            "SELECT flag, mode FROM story_scene_flag WHERE scene_id = ? ORDER BY mode, flag",
            (rs, i) -> new Flag(rs.getString("flag"), rs.getString("mode")), id);
    }

    /**
     * The trainer/boss battles a scene starts ({@code story_scene_battle} carries
     * typed {@code trainer_guid}/{@code boss_guid} — exactly one set per row), with
     * the name injected via the appropriate join.
     */
    public List<BattleRef> battles(String id) {
        return jdbc.query(
            "SELECT ssb.trainer_guid, ssb.boss_guid, " +
            "       COALESCE(curated_display(t.guid), NULLIF(t.name_raw,''), t.internal_name) AS t_name, " +
            "       COALESCE(curated_display(b.guid), NULLIF(b.display,''), b.internal_name)  AS b_name " +
            "FROM story_scene_battle ssb " +
            "LEFT JOIN trainer t ON t.guid = ssb.trainer_guid " +
            "LEFT JOIN boss    b ON b.guid = ssb.boss_guid " +
            "WHERE ssb.scene_id = ? " +
            "ORDER BY ssb.id",
            (rs, i) -> {
                Object trainer = rs.getObject("trainer_guid");
                if (trainer != null)
                    return new BattleRef("trainer", trainer.toString(), rs.getString("t_name"));
                Object boss = rs.getObject("boss_guid");
                return new BattleRef("boss", boss == null ? null : boss.toString(), rs.getString("b_name"));
            }, id);
    }

    /**
     * Where a scene fires ({@code story_scene_trigger} → {@code game_map}); the
     * map_guid is nullable, so unresolved triggers still surface their NPC.
     */
    public List<MapRef> maps(String id) {
        return jdbc.query(
            "SELECT sst.map_guid, sst.npc, " +
            "       COALESCE(curated_display(gm.guid), NULLIF(gm.map_name,''), gm.internal_name) AS name, gm.region " +
            "FROM story_scene_trigger sst " +
            "LEFT JOIN game_map gm ON gm.guid = sst.map_guid " +
            "WHERE sst.scene_id = ? " +
            "ORDER BY name NULLS LAST, sst.npc",
            (rs, i) -> {
                Object mapGuid = rs.getObject("map_guid");
                return new MapRef(mapGuid == null ? null : mapGuid.toString(),
                    rs.getString("name"), rs.getString("region"), rs.getString("npc"));
            }, id);
    }

    /**
     * Quests this scene is linked to via a shared flag. A scene's flags name a quest
     * by convention: a main quest's {@code <CITY>_QuestStart} flag → the
     * {@code <CITY>_MAIN} quest; a side quest's {@code <CITY>_Q<n>_*} flag → the
     * {@code <CITY>_Q<n>_*} quest. The {@code relation} classifies the flag — a
     * {@code _QuestStart} flag set here means the scene STARTS the quest; an
     * {@code _END}/{@code _Completed}/{@code _Complete} flag set here means it
     * COMPLETES it; anything else is {@code related}. {@code name_raw} is returned raw
     * (the service localizes it).
     */
    public List<SceneQuestLink> questLinks(String sceneId) {
        return jdbc.query(
            "SELECT DISTINCT q.guid, q.internal_name, q.name_raw, f.flag, f.mode, " +
            "  CASE " +
            "    WHEN f.mode='set' AND upper(f.flag) LIKE '%QUESTSTART' THEN 'starts' " +
            "    WHEN f.mode='set' AND (upper(f.flag) LIKE '%\\_END' OR upper(f.flag) LIKE '%COMPLETED' " +
            "         OR upper(f.flag) LIKE '%\\_COMPLETE') THEN 'completes' " +
            "    ELSE 'related' END AS relation " +
            "FROM story_scene_flag f " +
            "JOIN quest q ON ( " +
            "   /* main quest: <CITY>_QuestStart matches <CITY>_MAIN / <CITY>_Main */ " +
            "   ( upper(f.flag) = upper(split_part(f.flag,'_',1)) || '_QUESTSTART' " +
            "     AND upper(q.internal_name) ~ ('^' || upper(split_part(f.flag,'_',1)) || '_MAIN') ) " +
            "   OR " +
            "   /* side quest: <CITY>_Q<n>_* matches the quest with that prefix */ " +
            "   ( substring(f.flag from '^([A-Za-z]{2,4}_Q[0-9]+)') IS NOT NULL " +
            "     AND upper(q.internal_name) LIKE upper(substring(f.flag from '^([A-Za-z]{2,4}_Q[0-9]+)')) || '%' ) " +
            ") " +
            "WHERE f.scene_id = ? " +
            "ORDER BY relation, q.internal_name",
            (rs, i) -> new SceneQuestLink(((UUID) rs.getObject("guid")).toString(),
                rs.getString("internal_name"), rs.getString("name_raw"),
                rs.getString("relation"), rs.getString("flag"), rs.getString("mode")),
            sceneId);
    }

    /** English-resolved item name for a "Give Item" node, or null if unknown. */
    public String itemName(String guid, String lang) {
        try {
            var row = jdbc.queryForMap("SELECT name_raw, name_key FROM item WHERE guid = ?::uuid", guid);
            return loc.display(lang, (String) row.get("name_key"), (String) row.get("name_raw"));
        } catch (Exception e) {
            return null;
        }
    }

    /** Prev/next main-spine scene by (chapter, main_num), or null at an end. */
    public Neighbour neighbour(String id, Integer chapter, double main, boolean next) {
        String cmp = next ? ">" : "<", ord = next ? "ASC" : "DESC";
        List<Neighbour> r = jdbc.query(
            "SELECT scene_id, name FROM story_scene " +
            "WHERE main_num IS NOT NULL AND scene_id <> ? AND " +
            "(chapter, main_num) " + cmp + " (?, ?) " +
            "ORDER BY chapter " + ord + ", main_num " + ord + " LIMIT 1",
            (rs, i) -> new Neighbour(rs.getString("scene_id"), rs.getString("name")),
            id, chapter == null ? 0 : chapter, main);
        return r.isEmpty() ? null : r.get(0);
    }
}
