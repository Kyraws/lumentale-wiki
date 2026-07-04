package com.lumentale.wiki.creature;

import java.util.*;

/**
 * Pure region-resolution logic, with no database dependency, so the rules can be
 * unit-tested against synthetic data ({@code RegionResolverTest}).
 *
 * {@link RegionIndex} is the data-loading shell: it reads the inputs from Postgres
 * into a {@link RegionData} and calls {@link #resolve}. Carried over from v2 —
 * the rules are unchanged by the schema redesign (it works on guid strings).
 *
 * Rules (in order):
 *   1. own N/S wild spawns give the exact region(s);
 *   2. any spawn in a hub/center map → BOTH;
 *   3. no wild spawn → inherit the connected group's regions (same-species
 *      variants + evolution chain), else boss-encounter regions, else BOTH;
 *   4. starters (dex 1–20) → BOTH;
 *   5. forward-propagate along evolution edges to a fixpoint.
 */
public final class RegionResolver {

    /** Everything the resolution needs, decoupled from how it's loaded. */
    public record RegionData(
        List<String> forms,
        Map<String, String> speciesOf,
        Map<String, Integer> dexOf,
        Map<String, Set<String>> directSpawns,
        Set<String> hubForms,
        List<String[]> evoEdges,
        Map<String, Set<String>> bossRegions
    ) {}

    private RegionResolver() {}

    public static Map<String, List<String>> resolve(RegionData d) {
        Map<String, String> uf = new HashMap<>();
        for (String f : d.forms()) uf.put(f, f);
        Map<String, List<String>> bySpecies = new HashMap<>();
        for (var e : d.speciesOf().entrySet())
            bySpecies.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        for (var grp : bySpecies.values())
            for (int i = 1; i < grp.size(); i++) union(uf, grp.get(0), grp.get(i));
        for (String[] e : d.evoEdges())
            if (uf.containsKey(e[0]) && uf.containsKey(e[1])) union(uf, e[0], e[1]);

        Map<String, Set<String>> compNS = new HashMap<>();
        for (String f : d.forms()) {
            Set<String> s = compNS.computeIfAbsent(find(uf, f), k -> new HashSet<>());
            Set<String> dir = d.directSpawns().get(f);
            if (dir != null) s.addAll(dir);
        }

        Map<String, Set<String>> resolved = new HashMap<>();
        for (String f : d.forms()) {
            Set<String> r = new HashSet<>();
            Set<String> dir = d.directSpawns().get(f);
            if (dir != null) r.addAll(dir);
            if (d.hubForms().contains(f)) { r.add("north"); r.add("south"); }
            if (r.isEmpty()) {
                r.addAll(compNS.getOrDefault(find(uf, f), Set.of()));
                Set<String> b = d.bossRegions().get(d.speciesOf().get(f));
                if (b != null) r.addAll(b);
                if (r.isEmpty()) { r.add("north"); r.add("south"); }
            }
            if (d.dexOf().getOrDefault(f, 999) <= 20) { r.clear(); r.add("north"); r.add("south"); }
            resolved.put(f, r);
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (String[] e : d.evoEdges()) {
                Set<String> pre = resolved.get(e[0]), evo = resolved.get(e[1]);
                if (pre != null && evo != null && evo.addAll(pre)) changed = true;
            }
        }

        Map<String, List<String>> out = new HashMap<>();
        for (var en : resolved.entrySet()) {
            List<String> ordered = new ArrayList<>(2);
            if (en.getValue().contains("north")) ordered.add("north");
            if (en.getValue().contains("south")) ordered.add("south");
            out.put(en.getKey(), List.copyOf(ordered));
        }
        return out;
    }

    private static String find(Map<String, String> uf, String x) {
        String r = x; while (!uf.get(r).equals(r)) r = uf.get(r);
        while (!uf.get(x).equals(r)) { String n = uf.get(x); uf.put(x, r); x = n; }
        return r;
    }
    private static void union(Map<String, String> uf, String a, String b) {
        String ra = find(uf, a), rb = find(uf, b); if (!ra.equals(rb)) uf.put(ra, rb);
    }
}
