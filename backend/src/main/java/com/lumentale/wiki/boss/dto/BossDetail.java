package com.lumentale.wiki.boss.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Full boss page. The whole battle graph (nodes/edges jsonb) is NOT inlined here
 * — it's a separate one-row read at {@code /api/bosses/{guid}/graph}, matching the
 * design's whole-graph-per-page read pattern. This payload carries the
 * stats/kit/cross-links plus a compact {@link BossGraphInfo} pointer.
 */
public record BossDetail(
    String guid,
    String internalName,
    String display,
    Integer level,
    String ele,
    String emotion,
    String hiddenType,
    Integer expGiven,
    Integer targetBst,
    Integer extraHealthBars,
    JsonNode statsOverride,
    JsonNode ai,
    EntityRef originSpecies,
    EntityRef form,
    List<BossSkill> skills,
    BossGraphInfo graph
) {
    /** Compact pointer to the battle graph (or its absence note). */
    public record BossGraphInfo(String graphName, Integer nodeCount, boolean present, String note) {}
}
