package com.lumentale.wiki.creature;

import com.lumentale.wiki.creature.dto.TypeChart;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure tests for the new two-axis type-chart assembly. No DB — the schema isn't
 * seeded yet, so the logic is pinned against synthetic charts here and
 * runtime-verified later.
 */
class TypeChartServiceTest {

    private static final Map<Integer,String> ELE = Map.of(
        0, "NONE", 2, "ELECTRIC", 6, "VIRUS", 7, "ICE");

    @Test
    void elementalFrom_ordersByCode_skipsNone_andUnmappedAttackers() {
        Map<Integer,String> weak = Map.of(2, "WEAKNESS", 7, "WEAKNESS", 6, "RESISTANCE");
        List<TypeChart.EleReaction> out = TypeChartService.elementalFrom(weak, ELE);

        // NONE (0) dropped; ordered by code → ELECTRIC(2), VIRUS(6), ICE(7)
        assertEquals(List.of("ELECTRIC", "VIRUS", "ICE"), out.stream().map(TypeChart.EleReaction::attacker).toList());
        assertEquals("WEAKNESS", out.get(0).effectiveness());
        assertEquals("RESISTANCE", out.get(1).effectiveness());
    }

    @Test
    void build_slicesEmotionRowAndColumn_forTheFormsEmotion() {
        List<TypeChartService.EmotionCell> chart = List.of(
            new TypeChartService.EmotionCell("FUROR",  "MESTUS", 1.2),
            new TypeChartService.EmotionCell("MESTUS", "FUROR",  0.8),
            new TypeChartService.EmotionCell("FUROR",  "FUROR",  1.0),
            new TypeChartService.EmotionCell("SEREUM", "MESTUS", 0.8));

        TypeChart chart3 = TypeChartService.build("MESTUS", List.of(), chart);

        assertEquals("MESTUS", chart3.emotion());
        // offense = MESTUS as attacker → {FUROR ×0.8}
        assertEquals(1, chart3.emotionOffense().size());
        assertEquals("FUROR", chart3.emotionOffense().get(0).other());
        assertEquals(0.8, chart3.emotionOffense().get(0).multiplier());
        // defense = MESTUS as defender → {FUROR ×1.2, SEREUM ×0.8}
        assertEquals(2, chart3.emotionDefense().size());
        assertTrue(chart3.emotionDefense().stream().anyMatch(r -> r.other().equals("FUROR") && r.multiplier() == 1.2));
        assertTrue(chart3.emotionDefense().stream().anyMatch(r -> r.other().equals("SEREUM") && r.multiplier() == 0.8));
    }

    @Test
    void build_emotionlessForm_hasEmptyEmotionAxes() {
        TypeChart c = TypeChartService.build(null,
            List.of(new TypeChart.EleReaction("ICE", "WEAKNESS")),
            List.of(new TypeChartService.EmotionCell("FUROR", "MESTUS", 1.2)));

        assertNull(c.emotion());
        assertTrue(c.emotionOffense().isEmpty());
        assertTrue(c.emotionDefense().isEmpty());
        assertEquals(1, c.elemental().size());   // elemental axis still present
    }
}
