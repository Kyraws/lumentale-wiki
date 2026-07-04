package com.lumentale.wiki.squadron;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Presentation helpers for squadron member names.
 *
 * <p>Many squadron members are generic, unnamed roster fighters stored under
 * engine codenames of the form {@code <CITY>_<role>_<n>} (e.g.
 * {@code AL_Ranks_E_1}, {@code MG_AARI_Grunt_3}, {@code VT_MatchLumen_2}). These
 * NPCs genuinely have no proper name in the game — they are the ranked battle
 * opponents you fight to take a camp. Rather than show the raw codename we render
 * a clean role label (e.g. "Elite Rank 1", "AARI Grunt 3", "Challenge Lumen 2").
 *
 * <p>Real, proper-named trainers (Melia, Ernesto, …) and unprefixed names are
 * returned unchanged. {@code isCodename} lets the UI style anonymous roster
 * fighters differently from named characters.
 */
public final class SquadronNaming {

    private SquadronNaming() {}

    /** City prefix → readable city name (the first token of a member codename). */
    private static final Map<String, String> CITY = new LinkedHashMap<>();
    static {
        CITY.put("AL", "Arsilia");
        CITY.put("PD", "Paradine");
        CITY.put("VT", "Volteria");
        CITY.put("SP", "Speranova");
        CITY.put("MS", "Mirasilva");
        CITY.put("AP", "Altipetra");
        CITY.put("CL", "Costa Linda");
        CITY.put("MR", "Memorenia");
        CITY.put("MG", "Magnolia");
        CITY.put("CH", "Chakram");
        CITY.put("AM", "Anomali");
        CITY.put("GL", "Glacial");
        CITY.put("GE", "Geodron");
        CITY.put("HF", "Hellflamers");
    }

    /** role token → readable label. */
    private static final Map<String, String> ROLE = Map.of(
        "Ranks_E", "Elite Rank",
        "Ranks_ML", "Match Lumen Rank",
        "MatchLumen", "Challenge Lumen",
        "TournamentLumen", "Tournament Lumen",
        "AARI_Grunt", "AARI Grunt",
        "AARIP4_TrainingGrunt", "AARI Training Grunt");

    // <CITY>_<role>_<trailing>, role may itself contain an underscore.
    private static final Pattern ROSTER =
        Pattern.compile("^([A-Z]{2})_(Ranks_E|Ranks_ML|MatchLumen|TournamentLumen|AARI_Grunt|AARIP4_TrainingGrunt)_?(.*)$");

    public record Display(String name, boolean isCodename) {}

    public static Display of(String raw) {
        if (raw == null || raw.isBlank()) return new Display(raw, false);
        Matcher m = ROSTER.matcher(raw);
        if (m.matches()) {
            String role = ROLE.getOrDefault(m.group(2), m.group(2).replace('_', ' '));
            String tail = m.group(3) == null ? "" : m.group(3).trim();
            // Tail is usually a number (1,2,3) or a sub-name (Bucky, Nipo).
            String label = tail.isBlank() ? role : role + " " + tidyTail(tail);
            return new Display(label, true);
        }
        // Backer_Som / Backer_Isaac → "Som (Backer)".
        if (raw.startsWith("Backer_")) {
            return new Display(raw.substring("Backer_".length()) + " (Backer)", false);
        }
        // CampBoss_Pepper → "Pepper" (a real, named camp boss).
        if (raw.startsWith("CampBoss_")) {
            return new Display(raw.substring("CampBoss_".length()), false);
        }
        // <CITY>_<Name> for a single proper name (MG_Eman → "Eman", MG_Noemi → "Noemi").
        int us = raw.indexOf('_');
        if (us == 2 && CITY.containsKey(raw.substring(0, 2))) {
            String rest = raw.substring(3);
            // Only treat as a clean proper name when the remainder is a single
            // capitalized word (no further underscores, not a descriptor/quest tag).
            if (!rest.isEmpty() && !rest.contains("_")
                && Character.isUpperCase(rest.charAt(0))
                && rest.chars().allMatch(Character::isLetter)
                && !rest.equalsIgnoreCase("Lumen")) {
                return new Display(rest, false);
            }
        }
        // Anything still carrying an engine prefix/underscore is a codename.
        boolean looksLikeCodename = raw.contains("_") || raw.startsWith("---");
        return new Display(raw, looksLikeCodename);
    }

    /**
     * Readable label for the squadron {@code rank} tier. The eight player-region
     * squadrons + the enemy/special squadrons are tier 0; AARI and the Magnolia
     * (Lumen HQ) squadron are tier 1; the Player's Squadron is tier 2.
     */
    public static String rankLabel(Integer rank) {
        if (rank == null) return null;
        return switch (rank) {
            case 0 -> "Regional Squadron";
            case 1 -> "Special Squadron";
            case 2 -> "Player's Squadron";
            default -> "Tier " + rank;
        };
    }

    private static String tidyTail(String tail) {
        // Strip a leading numeric so "1a" -> "1a", but capitalize word tails.
        if (Character.isLetter(tail.charAt(0))) {
            return Character.toUpperCase(tail.charAt(0)) + tail.substring(1);
        }
        return tail;
    }
}
