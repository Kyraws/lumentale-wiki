package com.lumentale.wiki.story;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure tests for the city → track classifier (no DB). Pins the north/south fork
 * geography, the unknown→other fallback, the rank ordering, and the path filter.
 */
class StoryGeographyTest {

    @Test
    void knownCities_mapToTheirTrack() {
        // ground-truthed against game_map.region + the v2 spine data
        assertEquals(StoryGeography.NORTH, StoryGeography.trackOf("arsilia"));
        assertEquals(StoryGeography.NORTH, StoryGeography.trackOf("speranova"));
        assertEquals(StoryGeography.NORTH, StoryGeography.trackOf("volteria"));
        assertEquals(StoryGeography.SOUTH, StoryGeography.trackOf("mirasilva"));
        assertEquals(StoryGeography.SOUTH, StoryGeography.trackOf("memorenia"));
        // the game starts in Borgo Iride (chapter 0)
        assertEquals(StoryGeography.PROLOGUE, StoryGeography.trackOf("borgoiride"));
        // project rule: Magnolia is the CENTER — shared by both runs
        assertEquals(StoryGeography.CENTER, StoryGeography.trackOf("magnolia"));
        assertEquals(StoryGeography.HUB,    StoryGeography.trackOf("shared"));
    }

    @Test
    void caseInsensitive() {
        assertEquals(StoryGeography.NORTH, StoryGeography.trackOf("Volteria"));
        assertEquals(StoryGeography.SOUTH, StoryGeography.trackOf("ALTIPETRA"));
    }

    @Test
    void mainNum_parsesFromSceneNames() {
        assertEquals(0.0,  StoryGeography.mainNumOf("MAIN_0_BI_Game Start Shorter"));
        assertEquals(3.01, StoryGeography.mainNumOf("MAIN_3.1_MoveUpstairs"));
        assertEquals(8.05, StoryGeography.mainNumOf("MAIN_8.5_BR_NightInterlude"));
        assertEquals(2.0,  StoryGeography.mainNumOf("MG_Main_2_Ander"));
        assertEquals(12.0, StoryGeography.mainNumOf("AP_Main_12_VsMorsiver"));
        assertNull(StoryGeography.mainNumOf("AL_Caserma_LecternDX1"));
        assertNull(StoryGeography.mainNumOf(null));
    }

    @Test
    void chapterOf_reflectsPlayOrder() {
        assertEquals(0, StoryGeography.chapterOf("borgoiride"));
        assertTrue(StoryGeography.chapterOf("mirasilva") < StoryGeography.chapterOf("altipetra"));
        assertTrue(StoryGeography.chapterOf("arsilia") < StoryGeography.chapterOf("speranova"));
        assertNull(StoryGeography.chapterOf("nowhere"));
    }

    @Test
    void unknownOrBlank_isOther_neverNull() {
        assertEquals(StoryGeography.OTHER, StoryGeography.trackOf("squadronsystem"));
        assertEquals(StoryGeography.OTHER, StoryGeography.trackOf("areasandinter"));
        assertEquals(StoryGeography.OTHER, StoryGeography.trackOf("nowhere"));
        assertEquals(StoryGeography.OTHER, StoryGeography.trackOf(null));
        assertEquals(StoryGeography.OTHER, StoryGeography.trackOf("  "));
    }

    @Test
    void trackRank_centerLeads_thenPaths_thenEndgame() {
        // the center (starting hub) comes before both paths; endgame hub after them
        assertTrue(StoryGeography.trackRank(StoryGeography.CENTER) < StoryGeography.trackRank(StoryGeography.SOUTH));
        assertTrue(StoryGeography.trackRank(StoryGeography.SOUTH) < StoryGeography.trackRank(StoryGeography.HUB));
        assertTrue(StoryGeography.trackRank(StoryGeography.HUB)   < StoryGeography.trackRank(StoryGeography.OTHER));
        // unknown track sinks to the bottom alongside other
        assertTrue(StoryGeography.trackRank("???") >= StoryGeography.trackRank(StoryGeography.OTHER));
    }

    @Test
    void pathFilter_showsOwnTrackPlusShared_hidesOpposite() {
        // both / null → everything
        assertTrue(StoryGeography.pathAllows("both", StoryGeography.NORTH));
        assertTrue(StoryGeography.pathAllows(null, StoryGeography.SOUTH));

        // north path: north + center + shared visible, south hidden
        assertTrue(StoryGeography.pathAllows("north", StoryGeography.NORTH));
        assertTrue(StoryGeography.pathAllows("north", StoryGeography.CENTER));
        assertTrue(StoryGeography.pathAllows("north", StoryGeography.HUB));
        assertTrue(StoryGeography.pathAllows("north", StoryGeography.OTHER));
        assertFalse(StoryGeography.pathAllows("north", StoryGeography.SOUTH));

        // south path: south + center visible, north hidden
        assertTrue(StoryGeography.pathAllows("south", StoryGeography.SOUTH));
        assertTrue(StoryGeography.pathAllows("south", StoryGeography.CENTER));
        assertFalse(StoryGeography.pathAllows("south", StoryGeography.NORTH));
    }
}
