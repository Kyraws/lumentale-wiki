package com.lumentale.wiki.quest.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * A quest's full state-machine graph: localized name/description, rewards, and the
 * node graph (state + branch nodes with their transitions). Built on the redesigned
 * schema — rewards come from the typed {@code quest_item_reward} table (item /
 * furniture), and a node's conditions / objectives are surfaced straight from their
 * stored {@code jsonb} columns rather than re-parsed from the raw record.
 */
public record QuestGraph(
        String guid,
        String internalName,
        String name,
        String description,
        String giver,
        Integer type,
        Rewards rewards,
        QuestStartEnd flow,
        List<Node> nodes) {

    public record Rewards(Integer money, Integer exp, List<RewardItem> items) {}

    /** A reward target resolved to its typed kind ({@code item}/{@code furniture}). */
    public record RewardItem(String kind, String guid, Integer amount, String name) {}

    /**
     * A graph node. State nodes carry {@code stateId}/{@code stateName}/
     * {@code objective}; branch nodes carry {@code conditions} (the stored jsonb).
     * Both carry outgoing {@code transitions}. Unused fields are null/empty and
     * dropped from JSON by the non_null policy.
     */
    public record Node(
            long pathid,
            String kind,
            String stateId,
            String stateName,
            String objective,
            JsonNode conditions,
            List<Transition> transitions) {}

    /** An outgoing edge to another node, via a named port. */
    public record Transition(long to, String port) {}
}
