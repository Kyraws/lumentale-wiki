package com.lumentale.wiki.creature.dto;

/** One node in an evolution chain; {@code current} marks the form being viewed. */
public record EvoNode(
    String formGuid,
    String species,
    String variant,
    String menuArt,
    boolean current,
    String methodClass,
    String level
) {}
