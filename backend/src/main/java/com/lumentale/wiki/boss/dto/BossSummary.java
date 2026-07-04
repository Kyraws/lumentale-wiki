package com.lumentale.wiki.boss.dto;

/**
 * One boss list row. {@code ele}/{@code emotion} are resolved labels;
 * {@code hasGraph} flags whether a scripted battle graph exists (14 bosses use
 * default AI and have none — see {@code boss_battle_graph.note}).
 */
public record BossSummary(
    String guid,
    String name,
    String display,
    Integer level,
    String ele,
    String emotion,
    Integer expGiven,
    Integer extraHealthBars,
    String originSpecies,
    boolean hasGraph,
    String menuArt
) {}
