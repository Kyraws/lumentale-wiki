package com.lumentale.wiki.boss.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * A boss's whole scripted-battle graph — one jsonb document read (the design's
 * one-row-per-page pattern) plus the {@code boss_graph_skill} rollup resolved to
 * named moves (the one cross-cut worth a join: "which skills does this boss
 * telegraph, at what target").
 */
public record BossGraph(
    String bossGuid,
    String graphName,
    String assetGuid,
    Integer nodeCount,
    JsonNode nodes,
    JsonNode edges,
    List<StrongSkill> strongSkills,
    String note
) {
    /** A telegraphed strong skill in the graph, resolved to a move. */
    public record StrongSkill(String moveGuid, String moveName, String targetForm, String targetFormula) {}
}
