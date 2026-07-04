package com.lumentale.wiki.creature.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Full creature page. The raw pruned form record sits under {@code form} (still
 * carrying the Italian {@code name_raw}); every computed/cross-linked field is a
 * sibling key (v2's contract). {@code typeChart} is the two-axis profile the
 * redesign enables.
 *
 * The display siblings ({@code species}/{@code variant}/{@code dex}/{@code ele}/
 * {@code description}/{@code learnset}) are English-resolved (animon_name_/
 * animon_desc_/skill_name_ via {@code LocalizationResolver}), restoring the data
 * the v2 wiki rendered.
 */
public record CreatureDetail(
    JsonNode form,
    String species,
    String variant,
    Integer dex,
    String ele,
    String description,
    List<LearnsetEntry> learnset,
    List<String> regions,
    List<StatGrade> statGrades,
    TypeChart typeChart,
    List<SpawnRef> spawns,
    List<UsedByRef> usedBy,
    List<EvoNode> evolvesFrom,
    List<List<EvoNode>> evoChain
) {
    /** One learnable move for the form: English move name, element label, level, learn method. */
    public record LearnsetEntry(String moveGuid, String name, String type, Integer level, String method) {}
}
