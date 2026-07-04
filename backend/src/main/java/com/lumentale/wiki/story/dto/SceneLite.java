package com.lumentale.wiki.story.dto;

/**
 * A story scene in a list (no flow graph): enough to render an entry and group it
 * by city/track on the Story page. {@code chapter}/{@code mainNum} are present only
 * for main-spine scenes (side scenes omit them, null-omitted in the body).
 */
public record SceneLite(
    String sceneId,
    String name,
    String region,
    String track,
    Integer chapter,
    Double mainNum,
    int dialogue
) {}
