package com.lumentale.wiki.creature;

import com.lumentale.wiki.common.AssetResolver;
import com.lumentale.wiki.creature.dto.EvoNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Evolution-lineage logic for a form: direct pre-evolutions and the full,
 * branch-aware evolution line as ordered stages (depth 0 = root).
 *
 * The graph walks (reverse-evolution closure, longest-path depth) are pure logic
 * given the edge data — the kind of thing that silently breaks on a data change
 * with no test to catch it, so they're isolated here.
 *
 * v3 changes: PKs are uuid (params bound as {@link UUID}); the old {@code menu_art}
 * column is gone, so node art is resolved through the hybrid {@link AssetResolver};
 * {@code target_form_guid} is a uuid, so the {@code <> ''} guard is dropped.
 */
@Service
public class EvolutionService {

    private final JdbcTemplate jdbc;
    private final AssetResolver assets;

    public EvolutionService(JdbcTemplate jdbc, AssetResolver assets) {
        this.jdbc = jdbc;
        this.assets = assets;
    }

    /** Direct pre-evolutions: forms whose evolution targets this guid. */
    public List<EvoNode> evolvesFrom(UUID guid) {
        return jdbc.query(
            "SELECT fe.form_guid, s.name AS species, f.variant_name, fe.method_class, fe.level " +
            "FROM form_evolution fe JOIN form f ON f.guid=fe.form_guid " +
            "JOIN species s ON s.guid=f.species_guid WHERE fe.target_form_guid=? ORDER BY s.name",
            (rs, i) -> {
                String fg = rs.getString("form_guid");
                Object lvl = rs.getObject("level");
                return new EvoNode(fg, rs.getString("species"), rs.getString("variant_name"),
                    assets.art("form", UUID.fromString(fg), "menu_art"), false,
                    rs.getString("method_class"), lvl == null ? null : String.valueOf(lvl));
            },
            guid);
    }

    /** The full evolution line as ordered stages (depth 0 = root), branch-aware. */
    public List<List<EvoNode>> evoChain(UUID guid) {
        // 1. connected component (forward + backward over form_evolution)
        Set<String> comp = new LinkedHashSet<>();
        Deque<String> dq = new ArrayDeque<>(); dq.add(guid.toString());
        while (!dq.isEmpty()) {
            String c = dq.poll();
            if (!comp.add(c)) continue;
            UUID cu = UUID.fromString(c);
            for (String t : jdbc.queryForList(
                    "SELECT target_form_guid FROM form_evolution " +
                    "WHERE form_guid=? AND target_form_guid IS NOT NULL", String.class, cu))
                if (!comp.contains(t)) dq.add(t);
            for (String p : jdbc.queryForList(
                    "SELECT form_guid FROM form_evolution WHERE target_form_guid=?", String.class, cu))
                if (!comp.contains(p)) dq.add(p);
        }
        if (comp.isEmpty()) return List.of();
        String in = placeholders(comp.size());
        Object[] args = comp.stream().map(UUID::fromString).toArray();

        // 2. edges within the component → parents + incoming edge label
        Map<String, List<String>> parents = new HashMap<>();
        Map<String, String[]> edge = new HashMap<>();   // child → {method_class, level}
        jdbc.query(
            "SELECT form_guid, target_form_guid, method_class, level " +
            "FROM form_evolution WHERE form_guid IN (" + in + ") AND target_form_guid IS NOT NULL",
            (RowCallbackHandler) rs -> {
                String f = rs.getString("form_guid"), t = rs.getString("target_form_guid");
                if (!comp.contains(t)) return;
                parents.computeIfAbsent(t, k -> new ArrayList<>()).add(f);
                Object lvl = rs.getObject("level");
                edge.putIfAbsent(t, new String[]{ rs.getString("method_class"),
                    lvl == null ? null : String.valueOf(lvl) });
            }, args);

        // 3. depth of each node = longest path from a root
        Map<String, Integer> depth = new HashMap<>();
        for (String n : comp) computeDepth(n, parents, depth, new HashSet<>());

        // 4. node display info (art resolved via the hybrid resolver, no menu_art column)
        Map<String, String[]> info = new HashMap<>();   // guid → {species, variant}
        jdbc.query(
            "SELECT f.guid, s.name AS species, f.variant_name " +
            "FROM form f JOIN species s ON s.guid=f.species_guid WHERE f.guid IN (" + in + ")",
            (RowCallbackHandler) rs -> info.put(rs.getString("guid"),
                new String[]{ rs.getString("species"), rs.getString("variant_name") }),
            args);

        // 5. group by depth into ordered stages
        int maxD = 0; for (int d : depth.values()) maxD = Math.max(maxD, d);
        List<List<EvoNode>> stages = new ArrayList<>();
        String self = guid.toString();
        for (int d = 0; d <= maxD; d++) {
            List<String> nodes = new ArrayList<>();
            for (String g : comp) if (depth.getOrDefault(g, 0) == d) nodes.add(g);
            nodes.sort(Comparator.comparing(g -> {
                String[] m = info.get(g); return m == null ? "" : String.valueOf(m[0]);
            }));
            List<EvoNode> stage = new ArrayList<>();
            for (String g : nodes) {
                String[] m = info.getOrDefault(g, new String[]{ null, null });
                String[] e = edge.get(g);
                stage.add(new EvoNode(g, m[0], m[1],
                    assets.art("form", UUID.fromString(g), "menu_art"), g.equals(self),
                    e == null ? null : e[0], e == null ? null : e[1]));
            }
            stages.add(stage);
        }
        return stages;
    }

    private int computeDepth(String n, Map<String, List<String>> parents,
                             Map<String, Integer> memo, Set<String> seen) {
        Integer cached = memo.get(n);
        if (cached != null) return cached;
        List<String> ps = parents.get(n);
        int d = 0;
        if (ps != null && !ps.isEmpty() && seen.add(n)) {
            for (String p : ps) d = Math.max(d, computeDepth(p, parents, memo, seen) + 1);
            seen.remove(n);
        }
        memo.put(n, d);
        return d;
    }

    private static String placeholders(int n) {
        return String.join(",", Collections.nCopies(n, "?"));
    }
}
