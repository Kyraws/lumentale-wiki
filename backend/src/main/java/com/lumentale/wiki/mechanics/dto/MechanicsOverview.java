package com.lumentale.wiki.mechanics.dto;

import java.util.List;

/**
 * The Mechanics landing payload — the real, data-backed replacement for v2's
 * hardcoded {@code Guides}. Ties the four M9 reference layers together for one
 * page read.
 */
public record MechanicsOverview(
    List<FormulaSummary> formulas,
    List<XpCurveSummary> xpCurves,
    List<DifficultyScalar> difficulty,
    int constantCount
) {}
