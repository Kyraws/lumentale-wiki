package com.lumentale.wiki.creature;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the hand-transcribed emotion chart against typos: it must be a full 5×5,
 * neutral on the diagonal (self vs self), and use only the three recovered
 * multipliers. A bad transcription would break the creature type chart silently —
 * this fails the build instead.
 */
class EmotionChartDataTest {

    private static final Set<Double> ALLOWED = Set.of(0.8, 1.0, 1.2);

    @Test
    void isFive_by_five() {
        assertEquals(5, EmotionChartData.FORMS_CODE.length);
        for (double[] row : EmotionChartData.FORMS_CODE) assertEquals(5, row.length);
    }

    @Test
    void diagonal_isNeutral() {
        for (int i = 0; i < 5; i++)
            assertEquals(1.0, EmotionChartData.FORMS_CODE[i][i], "self vs self must be neutral at code " + (i + 1));
    }

    @Test
    void onlyRecoveredMultipliers() {
        for (double[] row : EmotionChartData.FORMS_CODE)
            for (double v : row)
                assertTrue(ALLOWED.contains(v), "unexpected multiplier " + v);
    }

    @Test
    void hasAllThreeMultipliers() {
        Set<Double> seen = new java.util.HashSet<>();
        for (double[] row : EmotionChartData.FORMS_CODE) for (double v : row) seen.add(v);
        assertEquals(ALLOWED, seen, "chart should exercise super-effective, neutral, and resisted");
    }
}
