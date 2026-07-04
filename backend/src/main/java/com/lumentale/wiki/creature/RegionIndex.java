package com.lumentale.wiki.creature;

import com.lumentale.wiki.creature.RegionResolver.RegionData;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Region (north/south) per FORM for the dex N/S filter — computed ONCE at startup
 * and handed to the pure {@link RegionResolver}; every request is a map lookup.
 *
 * Adapted to the redesigned schema: {@code form_evolution.target_form_guid} is a
 * uuid (so the old {@code <> ''} guard is dropped — only the NULL check remains),
 * and region still comes from {@code game_map.region} (NULL = hub → both).
 */
@Component
public class RegionIndex {

    private static final Logger log = LoggerFactory.getLogger(RegionIndex.class);

    private final JdbcTemplate jdbc;
    private Map<String, List<String>> regions = Map.of();

    public RegionIndex(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Regions for a form guid; empty list if the form is unknown. */
    public List<String> regionsFor(String formGuid) {
        return regions.getOrDefault(formGuid, List.of());
    }

    @PostConstruct
    public void build() {
        Map<String, Set<String>> direct = new HashMap<>();
        Set<String> hub = new HashSet<>();
        jdbc.query("SELECT fs.form_guid AS fg, gm.region AS rg FROM form_spawn fs " +
                   "JOIN game_map gm ON gm.guid = fs.map_guid",
            rs -> {
                String fg = rs.getString("fg"), rg = rs.getString("rg");
                if (rg == null) hub.add(fg);
                else direct.computeIfAbsent(fg, k -> new HashSet<>()).add(rg);
            });
        Map<String, String> species = new HashMap<>();
        Map<String, Integer> dexOf = new HashMap<>();
        List<String> all = new ArrayList<>();
        jdbc.query("SELECT guid, species_guid, dex FROM form",
            rs -> { species.put(rs.getString("guid"), rs.getString("species_guid"));
                    dexOf.put(rs.getString("guid"), rs.getInt("dex"));
                    all.add(rs.getString("guid")); });
        List<String[]> evoEdges = new ArrayList<>();
        jdbc.query("SELECT form_guid, target_form_guid FROM form_evolution " +
                   "WHERE target_form_guid IS NOT NULL",
            rs -> { evoEdges.add(new String[]{ rs.getString(1), rs.getString(2) }); });
        Map<String, Set<String>> bossNS = new HashMap<>();
        jdbc.query("SELECT origin_species_guid AS sg, internal_name AS nm FROM boss WHERE origin_species_guid IS NOT NULL",
            rs -> {
                String sg = rs.getString("sg"); String nm = rs.getString("nm").toLowerCase();
                Set<String> s = bossNS.computeIfAbsent(sg, k -> new HashSet<>());
                if (nm.contains("north") || nm.startsWith("nf_")) s.add("north");
                if (nm.contains("south") || nm.startsWith("sf_")) s.add("south");
            });

        this.regions = RegionResolver.resolve(
            new RegionData(all, species, dexOf, direct, hub, evoEdges, bossNS));
        log.info("RegionIndex built: {} forms resolved at startup (with evolution propagation)", regions.size());
    }
}
