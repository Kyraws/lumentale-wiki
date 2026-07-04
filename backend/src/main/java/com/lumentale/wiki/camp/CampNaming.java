package com.lumentale.wiki.camp;

import java.util.Map;

/**
 * Derives human display fields for a camp from its internal codename.
 *
 * <p>The eight camps are stored under engine codenames of the form
 * {@code P<NN>_CampData} / {@code P01_CenterCampData}. There is no localized
 * "camp name" in the game — in-game they are simply the <i>Lumen Camp</i> in a
 * given overworld area (the lore term, loc key {@code TIP_CAMPS} →
 * "Lumen Camps"). The {@code NN} in the codename is the overworld <b>Area
 * number</b> the camp sits in, which we verified against {@code game_map}
 * (CENTER_AREA_05, SOUTH_AREA_09/11/13, NORTH_AREA_18/23/25) and against the
 * spawn regions of each camp's target Animon. We therefore name each camp by its
 * real location — region + area — rather than its codename.
 *
 * <p>This is presentation only: no data is fabricated. The codename stays in the
 * DB {@code camp.name} column and is still returned as {@code name}.
 */
public final class CampNaming {

    private CampNaming() {}

    /** Codename → region (Center / North / South), keyed by the {@code P<NN>} area. */
    private static final Map<Integer, String> AREA_REGION = Map.of(
        1, "Center", 5, "Center",
        9, "South", 11, "South", 13, "South",
        18, "North", 23, "North", 25, "North");

    /** Effect-class → short human label. */
    private static final Map<String, String> EFFECT_LABEL = Map.of(
        "ExpBoostCampEffect", "XP Boost",
        "MoneyBoostCampEffect", "Money Boost",
        "DamageBoostCampEffect", "Damage Boost",
        "DropBoostCampEffect", "Item Drop Boost",
        "CatchrateBoostCampEffect", "Catch Rate Boost",
        "LostCampEffect", "Lost Animon Lure");

    public record Naming(String displayName, String region, String area) {}

    /** Parse the leading {@code P<NN>} area number from a camp codename, or -1. */
    public static int areaNumber(String codename) {
        if (codename == null) return -1;
        int i = 1, n = codename.length();
        if (n < 2 || codename.charAt(0) != 'P') return -1;
        int v = 0; boolean any = false;
        while (i < n && Character.isDigit(codename.charAt(i))) { v = v * 10 + (codename.charAt(i) - '0'); i++; any = true; }
        return any ? v : -1;
    }

    public static Naming of(String codename) {
        int area = areaNumber(codename);
        String region = AREA_REGION.getOrDefault(area, "Talea");
        String areaLabel = area >= 0 ? "Area " + (area < 10 ? "0" + area : Integer.toString(area)) : null;
        // Note: the camps sit in the wilderness AREAS — the central ones are in the
        // CENTER region (the ring around Magnolia), NOT in the Magnolia hub itself.
        String display = areaLabel != null ? region + " Lumen Camp — " + areaLabel : "Lumen Camp";
        return new Naming(display, region, areaLabel);
    }

    public static String effectLabel(String effectClass) {
        return EFFECT_LABEL.getOrDefault(effectClass, effectClass);
    }
}
