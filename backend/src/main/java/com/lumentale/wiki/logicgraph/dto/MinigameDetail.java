package com.lumentale.wiki.logicgraph.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * A minigame instance with its full extracted {@code fields} blob. {@code prizes}
 * is the {@code minigame_prize} rollup (→ item) when populated; the prize tables
 * currently live inside {@code fields} (opaque config, no resolved item GUIDs), so
 * the rollup is typically empty — see the Seeder note.
 */
public record MinigameDetail(
    long pathId,
    String className,
    String bundle,
    String gameobjectName,
    JsonNode fields,
    List<Prize> prizes
) {
    public record Prize(String tier, String itemGuid, Integer amount) {}
}
