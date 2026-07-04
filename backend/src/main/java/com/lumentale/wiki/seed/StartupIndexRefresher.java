package com.lumentale.wiki.seed;

import com.lumentale.wiki.common.ReferenceIndex;
import com.lumentale.wiki.creature.RegionIndex;
import com.lumentale.wiki.creature.StatGradeService;
import com.lumentale.wiki.map.MapGraphIndex;
import com.lumentale.wiki.map.MapStateGroupIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the {@code @PostConstruct} startup indexes AFTER the seeders run.
 *
 * Those indexes ({@link ReferenceIndex} enum maps, {@link RegionIndex} regions,
 * {@link StatGradeService} stat distributions, {@link MapGraphIndex} overworld
 * graph) load from the DB during bean init — which, on a fresh-DB boot, happens
 * BEFORE the {@code CommandLineRunner} seeders populate those tables, leaving the
 * indexes empty for that first run. Running last ({@code @Order} after every
 * seeder) and re-invoking each {@code build()} makes a fresh boot fully correct
 * with no manual restart. Idempotent: when seeding is disabled (already-seeded
 * DB) the {@code @PostConstruct} pass was already correct and this is skipped.
 */
@Component
@Order(1000)
public class StartupIndexRefresher implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupIndexRefresher.class);

    private final ReferenceIndex referenceIndex;
    private final RegionIndex regionIndex;
    private final StatGradeService statGrades;
    private final MapGraphIndex mapGraph;
    private final MapStateGroupIndex mapStateGroups;

    @Value("${lumentale.seed.on-empty:true}")
    private boolean seedOnEmpty;

    public StartupIndexRefresher(ReferenceIndex referenceIndex, RegionIndex regionIndex,
                                 StatGradeService statGrades, MapGraphIndex mapGraph,
                                 MapStateGroupIndex mapStateGroups) {
        this.referenceIndex = referenceIndex;
        this.regionIndex = regionIndex;
        this.statGrades = statGrades;
        this.mapGraph = mapGraph;
        this.mapStateGroups = mapStateGroups;
    }

    @Override
    public void run(String... args) {
        if (!seedOnEmpty) return;   // no seeding this boot → @PostConstruct indexes are already correct
        log.info("Rebuilding startup indexes against seeded data…");
        referenceIndex.build();   // enum code→label maps (used by every slice)
        regionIndex.build();      // form regions (union-find + evolution propagation)
        statGrades.build();       // per-stat population distributions
        mapGraph.build();         // overworld connectivity graph
        mapStateGroups.build();   // story-state variant groups (Iris Hamlet → Borgo Iride)
        log.info("Startup indexes rebuilt.");
    }
}
