package com.lumentale.wiki.logicgraph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumentale.wiki.logicgraph.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for the M10 logic-graph layer — behavior trees, cutscene timelines,
 * minigames. Each detail is a single jsonb-document row read (the design's
 * whole-graph-per-page pattern); list views select only the scalar columns. Keys
 * are Unity {@code path_id}s (signed bigint), bound as {@code long}.
 */
@Repository
public class LogicGraphRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public LogicGraphRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    // -------------------------------------------------------- behavior trees ---

    public List<BehaviorTreeSummary> behaviorTrees() {
        return jdbc.query(
            "SELECT path_id, behavior_name, object_name, bundle, kind, task_count " +
            "FROM behavior_tree ORDER BY behavior_name NULLS LAST, object_name NULLS LAST, path_id",
            (rs, i) -> new BehaviorTreeSummary(rs.getLong("path_id"), rs.getString("behavior_name"),
                rs.getString("object_name"), rs.getString("bundle"), rs.getString("kind"),
                (Integer) rs.getObject("task_count")));
    }

    public Optional<BehaviorTreeDetail> behaviorTree(long pathId) {
        return jdbc.query(
            "SELECT path_id, bundle, cab, object_name, behavior_name, behavior_desc, bd_version, kind, " +
            "task_count, flags::text AS flags, external_behavior::text AS external_behavior, " +
            "nodes::text AS nodes, edges::text AS edges FROM behavior_tree WHERE path_id = ?",
            (rs, i) -> new BehaviorTreeDetail(rs.getLong("path_id"), rs.getString("bundle"), rs.getString("cab"),
                rs.getString("object_name"), rs.getString("behavior_name"), rs.getString("behavior_desc"),
                rs.getString("bd_version"), rs.getString("kind"), (Integer) rs.getObject("task_count"),
                parse(rs.getString("flags")), parse(rs.getString("external_behavior")),
                parse(rs.getString("nodes")), parse(rs.getString("edges"))),
            pathId).stream().findFirst();
    }

    // -------------------------------------------------------------- timelines ---

    public List<TimelineSummary> timelines() {
        return jdbc.query(
            "SELECT director_path_id, timeline_name, gameobject, bundle, n_tracks, n_clips, crossbundle " +
            "FROM timeline_director ORDER BY timeline_name NULLS LAST, director_path_id",
            (rs, i) -> new TimelineSummary(rs.getLong("director_path_id"), rs.getString("timeline_name"),
                rs.getString("gameobject"), rs.getString("bundle"), (Integer) rs.getObject("n_tracks"),
                (Integer) rs.getObject("n_clips"), rs.getBoolean("crossbundle")));
    }

    public Optional<TimelineDetail> timeline(long pathId) {
        return jdbc.query(
            "SELECT director_path_id, bundle, gameobject, playable_asset_id, timeline_name, wrap_mode, " +
            "initial_state, update_mode, n_scene_bindings, n_tracks, n_clips, crossbundle, tracks::text AS tracks " +
            "FROM timeline_director WHERE director_path_id = ?",
            (rs, i) -> new TimelineDetail(rs.getLong("director_path_id"), rs.getString("bundle"),
                rs.getString("gameobject"), (Long) rs.getObject("playable_asset_id"), rs.getString("timeline_name"),
                (Integer) rs.getObject("wrap_mode"), (Integer) rs.getObject("initial_state"),
                (Integer) rs.getObject("update_mode"), (Integer) rs.getObject("n_scene_bindings"),
                (Integer) rs.getObject("n_tracks"), (Integer) rs.getObject("n_clips"),
                rs.getBoolean("crossbundle"), parse(rs.getString("tracks"))),
            pathId).stream().findFirst();
    }

    // -------------------------------------------------------------- minigames ---

    public List<MinigameSummary> minigames() {
        return jdbc.query(
            "SELECT path_id, class_name, bundle, gameobject_name FROM minigame_instance " +
            "ORDER BY class_name, path_id",
            (rs, i) -> new MinigameSummary(rs.getLong("path_id"), rs.getString("class_name"),
                rs.getString("bundle"), rs.getString("gameobject_name")));
    }

    public Optional<MinigameDetail> minigame(long pathId) {
        return jdbc.query(
            "SELECT path_id, class_name, bundle, gameobject_name, fields::text AS fields " +
            "FROM minigame_instance WHERE path_id = ?",
            (rs, i) -> new MinigameDetail(rs.getLong("path_id"), rs.getString("class_name"),
                rs.getString("bundle"), rs.getString("gameobject_name"),
                parse(rs.getString("fields")), prizes(pathId)),
            pathId).stream().findFirst();
    }

    private List<MinigameDetail.Prize> prizes(long pathId) {
        return jdbc.query(
            "SELECT tier, item_guid, amount FROM minigame_prize WHERE instance_path_id = ? ORDER BY tier",
            (rs, i) -> {
                Object item = rs.getObject("item_guid");
                return new MinigameDetail.Prize(rs.getString("tier"),
                    item == null ? null : item.toString(), (Integer) rs.getObject("amount"));
            }, pathId);
    }

    private JsonNode parse(String json) {
        if (json == null) return null;
        try { return mapper.readTree(json); }
        catch (Exception e) { throw new IllegalStateException("Corrupt logic-graph jsonb", e); }
    }
}
