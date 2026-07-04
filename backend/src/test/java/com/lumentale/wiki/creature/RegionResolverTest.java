package com.lumentale.wiki.creature;

import com.lumentale.wiki.creature.RegionResolver.RegionData;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure tests for region resolution (no DB). Pins the rules the redesign carries
 * over unchanged: direct spawns, hub→both, evolution propagation, starter rule.
 */
class RegionResolverTest {

    @Test
    void directSpawn_givesExactRegion() {
        var d = new RegionData(
            List.of("a"), Map.of("a", "sp"), Map.of("a", 50),
            Map.of("a", Set.of("north")), Set.of(), List.of(), Map.of());
        assertEquals(List.of("north"), RegionResolver.resolve(d).get("a"));
    }

    @Test
    void hubSpawn_givesBothRegions() {
        var d = new RegionData(
            List.of("a"), Map.of("a", "sp"), Map.of("a", 50),
            Map.of(), Set.of("a"), List.of(), Map.of());
        assertEquals(List.of("north", "south"), RegionResolver.resolve(d).get("a"));
    }

    @Test
    void evolution_inheritsPreEvolutionRegion() {
        // base spawns north only; its evolution has no own spawn → inherits north
        var d = new RegionData(
            List.of("base", "evo"),
            Map.of("base", "sp1", "evo", "sp2"),
            Map.of("base", 80, "evo", 90),
            Map.of("base", Set.of("north")),
            Set.of(),
            List.<String[]>of(new String[]{ "base", "evo" }),
            Map.of());
        var out = RegionResolver.resolve(d);
        assertEquals(List.of("north"), out.get("evo"));
    }

    @Test
    void starterDexRange_forcesBoth() {
        var d = new RegionData(
            List.of("s"), Map.of("s", "sp"), Map.of("s", 5),
            Map.of("s", Set.of("north")), Set.of(), List.of(), Map.of());
        assertEquals(List.of("north", "south"), RegionResolver.resolve(d).get("s"));
    }
}
