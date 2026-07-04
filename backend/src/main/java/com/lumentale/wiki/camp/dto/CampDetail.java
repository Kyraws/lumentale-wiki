package com.lumentale.wiki.camp.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Full camp page: curated base fields + the camp's two cross-link sections —
 * the target forms it can attract ({@code camp_target} → {@code form}) and the
 * tasks/quests it unlocks ({@code camp_task} → {@code quest}). The {@code raw}
 * record carries the unmodelled engine detail (effect data block, etc.).
 *
 * <p>{@code name} is the raw internal codename; {@code displayName}/{@code region}/
 * {@code area} are derived from it (see {@code CampNaming}) so the UI shows the
 * camp's real location rather than the codename. {@code effectLabel} is a short
 * human label for the effect class and {@code effectText} is the fully resolved,
 * localized effect description with the increment substituted in (e.g.
 * "Increases XP gained by 10%."). {@code tasks} reads the typed {@code quest_guid}
 * column.
 */
public record CampDetail(
    String guid,
    String name,
    String displayName,
    String region,
    String area,
    String effectClass,
    String effectLabel,
    String effectDescription,
    String effectText,
    Integer effectDuration,
    Double effectIncrement,
    Integer influence,
    Integer lumenAmount,
    JsonNode raw,
    List<Target> targets,
    List<Task> tasks
) {
    public record Target(String formGuid, String species, String variant, String menuArt) {}
    public record Task(String questGuid, String questName) {}
}
