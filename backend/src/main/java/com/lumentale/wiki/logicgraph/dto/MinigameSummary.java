package com.lumentale.wiki.logicgraph.dto;

/** One minigame instance in the list view ({@code minigame_instance}). */
public record MinigameSummary(
    long pathId,
    String className,
    String bundle,
    String gameobjectName
) {}
