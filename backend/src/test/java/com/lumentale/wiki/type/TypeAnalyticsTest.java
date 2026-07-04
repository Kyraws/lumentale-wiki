package com.lumentale.wiki.type;

import com.lumentale.wiki.type.TypeAnalytics.Form;
import com.lumentale.wiki.type.dto.Defender;
import com.lumentale.wiki.type.dto.Offense;
import com.lumentale.wiki.type.dto.TypeCoverage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the coverage buckets, defender scoring, and offense ranking for the
 * redesign's four-relation elemental axis (WEAKNESS / RESISTANCE / NORMAL /
 * IMMUNITY — no REFLECT). No DB/Spring — the attacking-type list is injected just
 * as the service injects it from the reference index, so the logic is pinned on
 * synthetic forms here and runtime-verified later.
 */
class TypeAnalyticsTest {

    // The attacking-type axis the service would resolve from ReferenceIndex.
    private static final List<String> ELE = List.of("FIRE", "WATER", "GRASS", "ICE");

    // tank: weak FIRE, resist WATER, immune GRASS (ICE → NORMAL by default)
    private final Form tank = new Form("a", "Tanky", "Base Form", "X", "GEO", 100, 100,
        Map.of("FIRE", "WEAKNESS", "WATER", "RESISTANCE", "GRASS", "IMMUNITY"));
    // glass: weak to everything we test → poor defender
    private final Form glass = new Form("b", "Glassy", "Alt", "Y", "FIRE", 10, 10,
        Map.of("FIRE", "WEAKNESS", "WATER", "WEAKNESS", "GRASS", "WEAKNESS", "ICE", "WEAKNESS"));

    @Test
    void defenderScoreAndOrdering() {
        List<Defender> d = TypeAnalytics.defenders(ELE, List.of(glass, tank), 10);
        // tank: 2·1(immune) + 1(resist) − 2·1(weak) = 1 ; glass: −2·4 = −8
        assertEquals("a", d.get(0).guid());
        assertEquals(1, d.get(0).score());
        assertEquals(-8, d.get(1).score());
        assertEquals(2, d.size());
    }

    @Test
    void defenderLimitTruncates() {
        assertEquals(1, TypeAnalytics.defenders(ELE, List.of(glass, tank), 1).size());
    }

    @Test
    void coverageBucketsByReaction() {
        TypeCoverage fire = TypeAnalytics.coverage(ELE, List.of(tank, glass)).stream()
            .filter(c -> c.type().equals("FIRE")).findFirst().orElseThrow();
        assertEquals(2, fire.weakness().size());                 // both weak to FIRE

        TypeCoverage grass = TypeAnalytics.coverage(ELE, List.of(tank, glass)).stream()
            .filter(c -> c.type().equals("GRASS")).findFirst().orElseThrow();
        assertTrue(grass.immunity().contains("Tanky"));          // tank is immune to GRASS
        assertTrue(grass.weakness().contains("Glassy (Alt)"));   // variant shown for non-base

        TypeCoverage ice = TypeAnalytics.coverage(ELE, List.of(tank, glass)).stream()
            .filter(c -> c.type().equals("ICE")).findFirst().orElseThrow();
        assertTrue(ice.normal().contains("Tanky"));              // no entry → NORMAL
    }

    @Test
    void offenseRanksBySuperEffectiveDescending() {
        List<Offense> o = TypeAnalytics.offense(ELE, List.of(tank, glass));
        // FIRE hits both super-effectively (2) and should top the board.
        assertEquals("FIRE", o.get(0).type());
        assertEquals(2, o.get(0).superEffective());
        for (int i = 1; i < o.size(); i++)
            assertTrue(o.get(i - 1).superEffective() >= o.get(i).superEffective());
    }
}
