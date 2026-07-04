package com.lumentale.wiki.camp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins {@link CampService#formatPercent} — the camp effect's {0} percentage
 * substitution. The increment is stored as a {@code real} (float4), so widening it
 * to {@code double} introduces noise (0.1f → 0.10000000149…); ×100 must still render
 * "10", not "10.000000149011612". Whole and genuinely-fractional percentages are
 * preserved with trailing zeros trimmed.
 */
class CampServiceTest {

    @Test
    void floatNoiseFromRealColumnIsRoundedAway() {
        // 0.1f widened to double then ×100 — the exact artifact from the bug report.
        double pct = ((double) 0.1f) * 100.0;
        assertEquals("10", CampService.formatPercent(pct));
    }

    @Test
    void otherStoredIncrementsRenderCleanWholeNumbers() {
        assertEquals("15", CampService.formatPercent(((double) 0.15f) * 100.0));
        assertEquals("30", CampService.formatPercent(((double) 0.3f) * 100.0));
        assertEquals("20", CampService.formatPercent(((double) 0.2f) * 100.0));
        assertEquals("120", CampService.formatPercent(((double) 1.2f) * 100.0));
    }

    @Test
    void genuineFractionalPercentIsPreserved() {
        assertEquals("12.5", CampService.formatPercent(((double) 0.125f) * 100.0));
        assertEquals("2.5", CampService.formatPercent(((double) 0.025f) * 100.0));
    }

    @Test
    void zeroRendersAsZero() {
        assertEquals("0", CampService.formatPercent(0.0));
    }
}
