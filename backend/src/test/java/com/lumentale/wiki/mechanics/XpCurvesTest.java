package com.lumentale.wiki.mechanics;

import com.lumentale.wiki.mechanics.dto.XpCurveDetail.LevelExp;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure tests for XP-curve milestone lookup (no DB). */
class XpCurvesTest {

    private static final List<LevelExp> CURVE = List.of(
        new LevelExp(1, 0), new LevelExp(50, 125_000), new LevelExp(100, 1_000_000));

    @Test
    void expAt_returnsMilestone_whenPresent() {
        assertEquals(125_000L, XpCurves.expAt(CURVE, 50));
        assertEquals(1_000_000L, XpCurves.expAt(CURVE, 100));
    }

    @Test
    void expAt_returnsNull_whenLevelAbsent() {
        assertNull(XpCurves.expAt(CURVE, 75));
    }

    @Test
    void expAt_nullTable_isNull() {
        assertNull(XpCurves.expAt(null, 50));
    }
}
