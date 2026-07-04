package com.lumentale.wiki.mechanics;

import com.lumentale.wiki.mechanics.dto.XpCurveDetail.LevelExp;

import java.util.List;

/**
 * Pure helpers over a precomputed {@code xp_level_exp} table — no DB, so the
 * milestone lookups are unit-tested ({@code XpCurvesTest}). Kept tiny on purpose:
 * the curve is evaluated at seed time into the level→exp table, so the wiki just
 * reads it; this only picks milestone rows out of that list.
 */
public final class XpCurves {

    private XpCurves() {}

    /** Exp required to reach {@code level} on this curve, or {@code null} if not in the table. */
    public static Long expAt(List<LevelExp> levels, int level) {
        if (levels == null) return null;
        for (LevelExp le : levels) if (le.level() == level) return le.exp();
        return null;
    }
}
