package com.lumentale.wiki.quest;

import com.lumentale.wiki.quest.QuestFlowAnalyzer.StateNode;
import com.lumentale.wiki.quest.dto.QuestStartEnd.MapLink;
import com.lumentale.wiki.quest.dto.QuestStartEnd.SceneLink;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cross-reference reads that link a quest's state-machine to the world and the story:
 * <ul>
 *   <li>the map each opening/closing step targets ({@code quest_node.raw.Objectives[].TargetArea}
 *       → {@code game_map}), and</li>
 *   <li>the story scenes that set/check the quest's flags
 *       ({@code story_scene_flag} matched by the naming-convention prefix from
 *       {@link QuestFlowAnalyzer#flagPrefix}).</li>
 * </ul>
 * Pure data access; the linking <em>policy</em> (which prefix, which nodes) lives in
 * {@link QuestService}/{@link QuestFlowAnalyzer}.
 */
@Repository
public class QuestLinkRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public QuestLinkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    /** A {@code kind='state'} node with its raw objective target-area, for the analyzer. */
    public record StateRow(StateNode node, String targetAreaGuid) {}

    /**
     * Every state node of a quest, plus the per-node incoming/outgoing transition
     * counts (for topology) and the first objective's TargetArea (for the map link).
     */
    public List<StateRow> stateNodes(UUID questGuid) {
        return jdbc.query(
            "SELECT n.pathid, n.state_id, " +
            "       COALESCE(NULLIF(n.mission_label_raw,''), n.state_name, n.state_id) AS label, " +
            "       (n.raw->'Objectives'->0->>'TargetArea') AS target_area " +
            "FROM quest_node n WHERE n.quest_guid=? AND n.kind='state' " +
            "ORDER BY n.state_id, n.pathid",
            (rs, i) -> new StateRow(
                new StateNode(rs.getLong("pathid"), rs.getString("state_id"), rs.getString("label")),
                rs.getString("target_area")),
            questGuid);
    }

    /** pathid → number of transitions pointing AT it (incoming). */
    public Map<Long, Integer> incoming(UUID questGuid) {
        return degree(questGuid, "to_pathid");
    }

    /** pathid → number of transitions leaving it (outgoing). */
    public Map<Long, Integer> outgoing(UUID questGuid) {
        return degree(questGuid, "from_pathid");
    }

    private Map<Long, Integer> degree(UUID questGuid, String col) {
        Map<Long, Integer> out = new HashMap<>();
        jdbc.query(
            "SELECT t." + col + " AS pid, count(*) AS c FROM quest_transition t " +
            "JOIN quest_node n ON n.pathid=t." + col + " WHERE n.quest_guid=? GROUP BY t." + col,
            rs -> { out.put(rs.getLong("pid"), rs.getInt("c")); },
            questGuid);
        return out;
    }

    /** Resolve a set of {@code game_map} guids (as strings) to {name,region}. */
    public Map<String, MapLink> maps(Collection<String> guids) {
        Map<String, MapLink> out = new HashMap<>();
        if (guids == null || guids.isEmpty()) return out;
        var p = new MapSqlParameterSource("guids",
            guids.stream().filter(g -> g != null && !g.isBlank()).map(UUID::fromString).toList());
        if (((List<?>) p.getValue("guids")).isEmpty()) return out;
        named.query(
            "SELECT guid, COALESCE(curated_display(guid), NULLIF(map_name,''), internal_name) AS name, region " +
            "FROM game_map WHERE guid IN (:guids)",
            p,
            rs -> {
                String g = ((UUID) rs.getObject("guid")).toString();
                out.put(g, new MapLink(g, rs.getString("name"), rs.getString("region")));
            });
        return out;
    }

    /**
     * Story scenes that touch a flag with the given prefix. {@code mode} (set/check)
     * is returned so the caller can show "set in scene X" vs "checked in scene Y".
     * {@code exactFlag} (nullable) narrows to one flag (the start/end flag); when null
     * every flag with the prefix is returned (the whole linked-scene rollup).
     */
    public List<SceneLink> scenesForFlag(String prefix, String exactFlag, String mode) {
        StringBuilder sql = new StringBuilder(
            "SELECT DISTINCT f.scene_id, s.name, s.region, f.flag, f.mode " +
            "FROM story_scene_flag f JOIN story_scene s ON s.scene_id=f.scene_id WHERE ");
        var p = new MapSqlParameterSource();
        if (exactFlag != null) {
            sql.append("f.flag = :flag");
            p.addValue("flag", exactFlag);
        } else {
            sql.append("f.flag LIKE :pref");
            p.addValue("pref", prefix + "%");
        }
        if (mode != null) {
            sql.append(" AND f.mode = :mode");
            p.addValue("mode", mode);
        }
        sql.append(" ORDER BY f.mode, s.region, s.name");
        return named.query(sql.toString(), p,
            (rs, i) -> new SceneLink(rs.getString("scene_id"), rs.getString("name"),
                rs.getString("region"), rs.getString("flag"), rs.getString("mode")));
    }
}
