package com.lumentale.wiki;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * backend-v3 — the LumenTale wiki API over the redesigned {@code wiki-db} schema.
 *
 * Same vision as v2 (read-mostly JSON API, one package per page, camelCase +
 * null-omitted bodies, blanket /api caching, uniform errors). The new reach the
 * schema unlocks: a real Mechanics layer (formulas/constants/xp curves), the
 * logic-graph pages (boss battle graphs, behavior trees, timelines, minigames),
 * DB-resolved assets, the two-axis type chart, and bosses/cards/camps as
 * first-class pages. Caching is enabled for the reference/localization indexes.
 */
@SpringBootApplication
@EnableCaching
public class LumenTaleWikiApplication {
    public static void main(String[] args) {
        SpringApplication.run(LumenTaleWikiApplication.class, args);
    }
}
