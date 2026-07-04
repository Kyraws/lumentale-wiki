package com.lumentale.wiki.story.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * A single story scene as a readable flow: the whole event graph
 * ({@code nodes}/{@code edges}/{@code entries}) read as jsonb documents in one row
 * (the redesign's whole-graph-per-page pattern), plus the cross-cut rollups —
 * the variables the scene touches, the trainer/boss battles it starts (resolved to
 * names), and the maps/NPCs that trigger it — and prev/next spine navigation.
 *
 * <p>{@code region}/{@code track}/{@code chapter}/{@code mainNum} are null-omitted
 * when absent (side scenes carry no main-spine numbering).
 */
public record SceneDetail(
    String sceneId,
    String region,
    String track,
    String name,
    Integer chapter,
    Double mainNum,
    JsonNode nodes,
    JsonNode edges,
    JsonNode entries,
    List<Flag> flags,
    List<BattleRef> battles,
    List<MapRef> maps,
    Neighbour prev,
    Neighbour next
) {
    /** A variable the scene sets or checks ({@code story_scene_flag}). */
    public record Flag(String flag, String mode) {}

    /** A battle the scene starts: {@code kind} ∈ trainer|boss, resolved to a name. */
    public record BattleRef(String kind, String guid, String name) {}

    /** A map/NPC that triggers the scene ({@code story_scene_trigger} → {@code game_map}). */
    public record MapRef(String guid, String name, String region, String npc) {}

    /** An adjacent main-spine scene (by chapter/mainNum), for prev/next. */
    public record Neighbour(String sceneId, String name) {}
}
