package com.lumentale.wiki.creature.dto;

/** A map this form spawns on (wild encounter), with the level band if known. */
public record SpawnRef(
    String guid,
    String name,
    String region,
    boolean interior,
    Integer levelMin,
    Integer levelMax
) {}
