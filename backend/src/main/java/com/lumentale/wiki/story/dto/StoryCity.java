package com.lumentale.wiki.story.dto;

import java.util.List;

/**
 * One city on the Story page: its track (north/south/hub/prologue/other) and the
 * dialogue scenes set there. This is the "browse by city" backbone.
 *
 * <p>The v2 contract also carried a {@code quests} list per city; the redesigned
 * {@code quest} table has no region column and the new {@code story_scene} drops
 * the name prefix that v2 used to derive it, so quest-by-city grouping is omitted
 * here (scenes only) — see {@code StoryService} note. The field is left out of the
 * record entirely rather than emitted empty.
 */
public record StoryCity(
    String region,
    String track,
    List<SceneLite> scenes
) {}
