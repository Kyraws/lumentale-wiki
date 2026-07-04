package com.lumentale.wiki.mechanics.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * One XP curve plus its precomputed level→exp table (so the wiki renders the
 * table without evaluating AnimationCurves client-side). Polynomial curves carry
 * a closed-form {@code expression}; data-driven ones carry {@code keyframes}.
 */
public record XpCurveDetail(
    int curveType,
    String name,
    String kind,
    String expression,
    String sourceFile,
    JsonNode keyframes,
    List<LevelExp> levels
) {
    public record LevelExp(int level, long exp) {}
}
