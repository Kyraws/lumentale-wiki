package com.lumentale.wiki.mechanics.dto;

/**
 * An XP curve in the list view. {@code kind} ∈ polynomial|animation_curve;
 * {@code expAt50}/{@code expAt100} are pulled from the precomputed
 * {@code xp_level_exp} table when available (omitted otherwise).
 */
public record XpCurveSummary(int curveType, String name, String kind, Long expAt50, Long expAt100) {}
