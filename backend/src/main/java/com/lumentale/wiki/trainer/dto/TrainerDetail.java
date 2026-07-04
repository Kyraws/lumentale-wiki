package com.lumentale.wiki.trainer.dto;

import java.util.List;

/**
 * Full trainer page: identity + reward (money on defeat), the team, and the
 * cross-links for "where you find them" — the maps they're placed on
 * ({@code map_battle.trainer_guid}), the story scenes they appear in
 * ({@code story_scene_battle.trainer_guid}), and any squadron they belong to
 * ({@code squadron_member.trainer_guid}).
 *
 * Those three cross-link sections reference tables seeded by OTHER slices (world,
 * story, squadron); the queries are written against the redesigned typed
 * {@code trainer_guid} columns and simply return {@code []} until those tables
 * seed. {@code rank} and {@code lumenClass} are nullable (rank lives in the Unity
 * {@code raw}; lumen_class is an int code with no clean lookup).
 */
public record TrainerDetail(
        String guid,
        String name,
        String display,
        Integer rank,
        Integer levelCap,
        Integer money,
        Integer lumenClass,
        String idle,
        List<PartyMember> party,
        List<MapRef> foundOnMaps,
        List<SceneRef> foundInScenes,
        List<SquadronRef> squadrons) {

    public record MapRef(String guid, String name, String region, String tile) {}

    public record SceneRef(String sceneId, String name, String region, Integer chapter) {}

    public record SquadronRef(String guid, String name, String role) {}
}
