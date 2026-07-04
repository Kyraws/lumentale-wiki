package com.lumentale.wiki.quest;

import com.lumentale.wiki.quest.dto.QuestGraph.Transition;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for quests: the list, the quest base record, the node/transition
 * graph, and the typed item/furniture reward rollup.
 *
 * Built on the redesigned schema — rewards live in {@code quest_item_reward}
 * (typed {@code item_guid}/{@code furniture_guid} FKs, not v2's polymorphic blob),
 * and a node's conditions/objectives are stored as {@code jsonb} columns that the
 * service surfaces directly. Localization is applied in {@link QuestService}.
 */
@Repository
public class QuestRepository {

    private final JdbcTemplate jdbc;

    public QuestRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record SummaryRow(String guid, String internalName, String nameRaw, String giver, Integer type, int nodes) {}
    public record QuestRow(String guid, String internalName, String nameRaw, String descriptionRaw,
                           String giver, Integer type, Integer money, Integer exp) {}
    public record NodeRow(long pathid, String kind, String stateId, String stateName,
                          String missionLabelRaw, String objectivesKey, String conditionsJson) {}
    /** A reward target with its resolved kind/name + loc key (kind = item | furniture). */
    public record RewardRow(String kind, String guid, Integer amount, String name, String nameKey) {}

    public List<SummaryRow> summaries() {
        return jdbc.query(
            "SELECT q.guid, q.internal_name, q.name_raw, q.quest_giver, q.quest_type, " +
            "       (SELECT count(*) FROM quest_node n WHERE n.quest_guid=q.guid) AS nodes " +
            "FROM quest q ORDER BY q.internal_name",
            (rs, i) -> new SummaryRow(((UUID) rs.getObject("guid")).toString(), rs.getString("internal_name"),
                rs.getString("name_raw"), rs.getString("quest_giver"),
                (Integer) rs.getObject("quest_type"), rs.getInt("nodes")));
    }

    public Optional<QuestRow> base(UUID guid) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT guid, internal_name, name_raw, description_raw, quest_giver, quest_type, " +
                "money_reward, exp_reward FROM quest WHERE guid=?",
                (rs, i) -> new QuestRow(((UUID) rs.getObject("guid")).toString(), rs.getString("internal_name"),
                    rs.getString("name_raw"), rs.getString("description_raw"), rs.getString("quest_giver"),
                    (Integer) rs.getObject("quest_type"), (Integer) rs.getObject("money_reward"),
                    (Integer) rs.getObject("exp_reward")),
                guid));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<NodeRow> nodeRows(UUID guid) {
        return jdbc.query(
            "SELECT pathid, kind, state_id, state_name, mission_label_raw, objectives_key, " +
            "       conditions::text AS conditions FROM quest_node WHERE quest_guid=? " +
            "ORDER BY kind, state_id, pathid",
            (rs, i) -> new NodeRow(rs.getLong("pathid"), rs.getString("kind"), rs.getString("state_id"),
                rs.getString("state_name"), rs.getString("mission_label_raw"),
                rs.getString("objectives_key"), rs.getString("conditions")),
            guid);
    }

    /** from_pathid → outgoing transitions, for the quest's nodes. */
    public Map<Long, List<Transition>> transitions(UUID guid) {
        Map<Long, List<Transition>> out = new HashMap<>();
        jdbc.query(
            "SELECT qt.from_pathid, qt.to_pathid, qt.port FROM quest_transition qt " +
            "JOIN quest_node n ON n.pathid=qt.from_pathid WHERE n.quest_guid=?",
            (RowCallbackHandler) rs -> out.computeIfAbsent(rs.getLong("from_pathid"), k -> new ArrayList<>())
                .add(new Transition(rs.getLong("to_pathid"), rs.getString("port"))),
            guid);
        return out;
    }

    /**
     * Typed item/furniture rewards for a quest, with display names joined from the
     * target table. The CHECK guarantees exactly one of item_guid/furniture_guid is
     * set per row, so the COALESCE picks the live kind/guid/name unambiguously.
     */
    public List<RewardRow> rewards(UUID questGuid) {
        return jdbc.query(
            "SELECT CASE WHEN r.item_guid IS NOT NULL THEN 'item' ELSE 'furniture' END AS kind, " +
            "       COALESCE(r.item_guid, r.furniture_guid) AS ref_guid, r.amount, " +
            "       COALESCE(i.name_raw, fu.name_raw) AS name, " +
            "       COALESCE(i.name_key, fu.name_key) AS name_key " +
            "FROM quest_item_reward r " +
            "LEFT JOIN item i      ON i.guid = r.item_guid " +
            "LEFT JOIN furniture fu ON fu.guid = r.furniture_guid " +
            "WHERE r.quest_guid=? ORDER BY r.id",
            (rs, i) -> new RewardRow(rs.getString("kind"), ((UUID) rs.getObject("ref_guid")).toString(),
                (Integer) rs.getObject("amount"), rs.getString("name"), rs.getString("name_key")),
            questGuid);
    }
}
