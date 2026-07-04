package com.lumentale.wiki.type;

import com.lumentale.wiki.type.dto.Defender;
import com.lumentale.wiki.type.dto.Offense;
import com.lumentale.wiki.type.dto.TypeCoverage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Pure elemental type-effectiveness analytics over the per-form weakness data —
 * no DB, no Spring, so the coverage buckets, defender scoring and offense ranking
 * are unit-testable on synthetic forms ({@code TypeAnalyticsTest}). {@link
 * TypeService} loads the forms (and the attacking-type list, resolved from {@link
 * com.lumentale.wiki.common.ReferenceIndex}) and calls these.
 *
 * <p>The redesign's {@code form_weakness.effectiveness} has four relations —
 * {@code WEAKNESS} / {@code RESISTANCE} / {@code NORMAL} / {@code IMMUNITY} — so
 * there is no REFLECT bucket (unlike v2); a missing entry is {@code NORMAL}.
 */
public final class TypeAnalytics {

    private TypeAnalytics() {}

    /**
     * A form reduced to what the analytics need; {@code w} maps an attacking ele
     * type label → its relation against this form.
     */
    public record Form(String guid, String species, String variant, String emo, String ele,
                       int def, int spd, Map<String, String> w) {}

    /** For each attacking ele type (in {@code eleTypes} order): how every form reacts, bucketed. */
    public static List<TypeCoverage> coverage(List<String> eleTypes, List<Form> forms) {
        List<TypeCoverage> out = new ArrayList<>();
        for (String atk : eleTypes) {
            List<String> weak = new ArrayList<>(), normal = new ArrayList<>(),
                resist = new ArrayList<>(), immunity = new ArrayList<>();
            for (Form f : forms) {
                switch (f.w().getOrDefault(atk, "NORMAL")) {
                    case "WEAKNESS"   -> weak.add(display(f));
                    case "RESISTANCE" -> resist.add(display(f));
                    case "IMMUNITY"   -> immunity.add(display(f));
                    default           -> normal.add(display(f));
                }
            }
            out.add(new TypeCoverage(atk, weak, normal, resist, immunity));
        }
        return out;
    }

    /** Best defenders by {@code 2·immune + resist − 2·weak}, descending. */
    public static List<Defender> defenders(List<String> eleTypes, List<Form> forms, int limit) {
        List<Defender> rows = new ArrayList<>();
        for (Form f : forms) {
            int weak = 0, resist = 0, immune = 0;
            for (String atk : eleTypes) switch (f.w().getOrDefault(atk, "NORMAL")) {
                case "WEAKNESS"   -> weak++;
                case "RESISTANCE" -> resist++;
                case "IMMUNITY"   -> immune++;
                default           -> { }
            }
            int score = 2 * immune + resist - 2 * weak;
            rows.add(new Defender(f.guid(), f.species(), f.variant(), f.emo(), f.ele(),
                score, weak, resist, immune, f.def(), f.spd()));
        }
        // Tie-break: bulk (def+spd), then def, then spd. (input is dex,guid-ordered
        // and List.sort is stable, so ties beyond that stay deterministic.)
        rows.sort(Comparator.comparingInt(Defender::score).reversed()
            .thenComparing(Comparator.comparingInt((Defender d) -> d.def() + d.spd()).reversed())
            .thenComparing(Comparator.comparingInt(Defender::def).reversed())
            .thenComparing(Comparator.comparingInt(Defender::spd).reversed()));
        return new ArrayList<>(rows.subList(0, Math.min(Math.max(limit, 0), rows.size())));
    }

    /** Offense leaderboard: how many forms each attacking type hits each way, ranked by super-effective. */
    public static List<Offense> offense(List<String> eleTypes, List<Form> forms) {
        List<Offense> out = new ArrayList<>();
        for (String atk : eleTypes) {
            int se = 0, neutral = 0, resisted = 0, immune = 0;
            for (Form f : forms) switch (f.w().getOrDefault(atk, "NORMAL")) {
                case "WEAKNESS"   -> se++;
                case "RESISTANCE" -> resisted++;
                case "IMMUNITY"   -> immune++;
                default           -> neutral++;
            }
            out.add(new Offense(atk, se, neutral, resisted, immune));
        }
        out.sort(Comparator.comparingInt(Offense::superEffective).reversed());
        return out;
    }

    private static String display(Form f) {
        return f.species() + (f.variant() == null || f.variant().equals("Base Form") ? "" : " (" + f.variant() + ")");
    }
}
