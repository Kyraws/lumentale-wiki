package com.lumentale.wiki.quest.dto;

import java.util.List;

/**
 * Where a quest <em>starts</em> and where it <em>completes</em>, recovered from the
 * state-machine topology ({@code QuestFlowAnalyzer}) cross-referenced with the story
 * scenes that set/check the quest's flags ({@code QuestLinkRepository}).
 *
 * <p>Both ends carry the same shape: the opening/closing step(s) (objective text +
 * the map the step targets), the flag that gates it, and the scene(s) that set that
 * flag. Any leg that couldn't be recovered is an empty list / null — never fabricated.
 */
public record QuestStartEnd(Endpoint start, Endpoint end, List<SceneLink> linkedScenes) {

    /** One end of the quest (start or completion). */
    public record Endpoint(List<Step> steps, String flag, List<SceneLink> scenes) {}

    /** A narrative step: its objective text + the map it points the player to. */
    public record Step(long pathid, String stateId, String objective, MapLink map) {}

    /** A map a step targets ({@code TargetArea}/{@code _mapData} → {@code game_map}). */
    public record MapLink(String guid, String name, String region) {}

    /** A story scene linked to the quest by a shared flag. */
    public record SceneLink(String sceneId, String name, String region, String flag, String mode) {}
}
