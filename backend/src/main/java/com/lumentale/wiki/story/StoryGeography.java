package com.lumentale.wiki.story;

import java.util.Map;

/**
 * Pure city → track classifier for the Story page (no database dependency, so the
 * rules are unit-tested against literal inputs — the v2 {@code StoryGeography}
 * role, retargeted to the redesigned schema).
 *
 * <p>In the v2 schema {@code story_scene} carried an authoritative {@code track}
 * column that the seed populated; the redesigned V6 {@code story_scene} drops it
 * and stores only {@code region} (which is now the city key directly, e.g.
 * {@code "magnolia"}, {@code "arsilia"} — no prefix to split). The Story page still
 * wants the north/south fork, so we recover the track from the city key here.
 *
 * <p>The mapping reflects the fixed LumenTale world layout (the two-path fork after
 * the prologue): the southern cities, the northern cities, the shared hub/prologue
 * areas, and a catch-all {@code other} for the system/meta scene buckets
 * ({@code squadronsystem}, {@code areasandinter}) that aren't a place on the map.
 * It's a small literal table on purpose — there is no track signal in the new data
 * to derive it from, so encoding the known geography (and pinning it with a test)
 * is the honest, self-documenting choice rather than inventing a heuristic.
 */
public final class StoryGeography {

    private StoryGeography() {}

    /** Track values, in display order; {@code other} sinks last. */
    public static final String PROLOGUE = "prologue";
    public static final String CENTER   = "center";
    public static final String SOUTH    = "south";
    public static final String NORTH    = "north";
    public static final String HUB      = "hub";
    public static final String OTHER    = "other";

    /**
     * city key (story_scene.region, lowercase) → track.
     *
     * GROUND-TRUTHED against {@code game_map.region} (each city's own maps carry
     * the N/S tag) + the v2 spine data — the earlier hand table had six cities on
     * the wrong path. Borgo Iride hosts the prologue (chapter 0); Magnolia is the
     * CENTER hub shared by both runs (project rule).
     */
    private static final Map<String, String> TRACK = Map.ofEntries(
        Map.entry("borgoiride", PROLOGUE),   // chapter 0 — the game starts here
        Map.entry("magnolia", CENTER),       // shared center hub (both runs)
        Map.entry("shared", HUB),            // shared endgame scenes
        // southern path (map data: altipetra/costalinda/memorenia/mirasilva = south)
        Map.entry("mirasilva", SOUTH),
        Map.entry("altipetra", SOUTH),
        Map.entry("costalinda", SOUTH),
        Map.entry("memorenia", SOUTH),
        // northern path (map data: arsilia/paradine/speranova = north; volteria per v2 spine)
        Map.entry("arsilia", NORTH),
        Map.entry("paradine", NORTH),
        Map.entry("volteria", NORTH),
        Map.entry("speranova", NORTH));

    /** Track display rank (lower sorts first); unknown/{@code other} sinks last. */
    private static final Map<String, Integer> RANK = Map.of(
        PROLOGUE, 0, CENTER, 1, SOUTH, 2, NORTH, 3, HUB, 4, OTHER, 5);

    /**
     * City play order — the quest-line chapter at which the city is entered
     * (recovered from the v2 spine data; ties = the same fork stage on each path).
     */
    private static final Map<String, Integer> CHAPTER = Map.ofEntries(
        Map.entry("borgoiride", 0),
        Map.entry("mirasilva", 2), Map.entry("arsilia", 2),
        Map.entry("areasandinter", 3),
        Map.entry("paradine", 4),
        Map.entry("shared", 5),
        Map.entry("altipetra", 6),
        Map.entry("volteria", 7), Map.entry("costalinda", 7),
        Map.entry("memorenia", 8),
        Map.entry("speranova", 9),
        Map.entry("magnolia", 10));

    /** The chapter a city is entered at, or null when it isn't on the spine. */
    public static Integer chapterOf(String region) {
        return region == null ? null : CHAPTER.get(region.toLowerCase());
    }

    private static final java.util.regex.Pattern MAIN_GLOBAL =
        java.util.regex.Pattern.compile("^MAIN_(\\d+)(?:[._](\\d+))?", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern MAIN_REGIONAL =
        java.util.regex.Pattern.compile("^[A-Za-z]+_Main_(\\d+)(?:[._](\\d+))?", java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Main-quest order parsed from the scene name — {@code MAIN_3.1_…} → 3.01,
     * {@code MG_Main_12_…} → 12 — or null for non-spine scenes. (The redesigned
     * V6 seed dropped the v2 {@code main_num} column data; the number only
     * survives in the names, so we recover it here.)
     */
    public static Double mainNumOf(String name) {
        if (name == null) return null;
        var m = MAIN_GLOBAL.matcher(name);
        if (!m.find()) {
            m = MAIN_REGIONAL.matcher(name);
            if (!m.find()) return null;
        }
        double v = Integer.parseInt(m.group(1));
        if (m.group(2) != null) v += Integer.parseInt(m.group(2)) / 100.0;
        return v;
    }

    /** Track for a city key (story_scene.region), never null — unknown → {@code other}. */
    public static String trackOf(String region) {
        if (region == null || region.isBlank()) return OTHER;
        return TRACK.getOrDefault(region.toLowerCase(), OTHER);
    }

    /** Track display rank; unknown/{@code other} sinks last. */
    public static int trackRank(String track) {
        return RANK.getOrDefault(track, RANK.size());
    }

    /**
     * The N/S filter for {@code GET /api/story/cities?path=}: a path shows its own
     * track plus the shared ones (hub/prologue/other); the opposite path's cities
     * are hidden. {@code both}/null shows everything.
     */
    public static boolean pathAllows(String path, String track) {
        if (path == null || path.isBlank() || path.equals("both")) return true;
        return track.equals(path) || track.equals(CENTER) || track.equals(HUB)
            || track.equals(PROLOGUE) || track.equals(OTHER);
    }
}
