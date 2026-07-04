# LumenTale Wiki — backend-v3

Read-mostly wiki API for *LumenTale: Memories of Trey*, rebuilt on the
**redesigned `wiki-db` schema** (the `anidex3` database). Same vision as v2, new
reach: a two-axis type chart, bosses + their battle graphs, a real mechanics
layer, DB-resolved assets, and 8-language localization.

> **Status:** the **whole backend is implemented** — every page slice on the
> shared Controller→Service→Repository seam, each with a modular FK-ordered
> seeder (145 source files, 25 pure-logic tests, all green; compiles clean). Only
> the asset-resolver endpoint is deferred (Addressables GUID cache not extracted).
> Full design + diagrams in [`ARCHITECTURE.md`](ARCHITECTURE.md) / [`graph.md`](graph.md).

## Why a v3

v2 connected to the old `anidex2` schema and served assets only from the
filesystem. The `wiki-db` redesign adds layers v2 never modelled (mechanics,
logic graphs, the full asset manifest) and promotes the per-form weakness blob +
the global emotion chart into queryable tables. v3 adopts that schema, **owns it
via Flyway**, and seeds it — so the new pages become reachable.

## What's implemented

- **Scaffold** — Spring Boot 3.5.4 / Java 17, pure JDBC, Flyway (points at
  `../wiki-db/migrations`), actuator, caching. Runs on **:8083**.
- **`common/`** — hybrid `AssetResolver` (filesystem → DB manifest two-hop),
  `ReferenceIndex` (startup enum maps), `LocalizationResolver`, `RawRecordService`,
  `JsonPrune`, `Guids` (uuid edge-validation).
- **`error/` `config/` `web/`** — uniform errors, CORS + `/data` + blanket cache,
  API index at `/`.
- **`creature/`** — reference slice: dex grid + detail with regions, S–F stat
  grades, the **two-axis type chart**, spawns, used-by, evolution chain.
- **`boss/`** — reference slice: list + detail + **whole battle-graph jsonb read**
  with the `boss_graph_skill` rollup resolved to named moves.
- **`mechanics/`** — formulas, named constants, difficulty scalars, XP-curve
  metadata (curated from `native/FORMULAS.md` + `constants.json`).
- **`logicgraph/`** — behavior trees, cutscene timelines, minigames; each a
  whole-graph jsonb-document read (seeded from `logic-graphs/*.json`).
- **`seed/Seeder`** — idempotent, FK-ordered backfill from `data/seed/*.json`
  plus the staged `phase4-complete/` layers.
- **Tests** — pure logic (`TypeChartServiceTest`, `RegionResolverTest`,
  `XpCurvesTest`, `EmotionChartDataTest`), no DB.

## Run

```bash
createdb anidex3
./gradlew bootRun     # :8083 — Flyway applies wiki-db/migrations, Seeder backfills
# GET http://localhost:8083/                     API index
# GET http://localhost:8083/api/creatures
# GET http://localhost:8083/api/bosses/{guid}/graph
```

To skip Flyway (DDL applied out-of-band): `LUMENTALE_FLYWAY_ENABLED=false`.
To skip seeding: `LUMENTALE_SEED_ON_EMPTY=false`.

## Test

```bash
./gradlew test        # pure unit tests on the extracted helpers (no DB required)
```

## Notes / honest gaps

- The `wiki-db` schema is **not seeded upstream yet**; v3 is structurally
  validated (compiles, pure logic tested, SQL written against the V1–V13 DDL) and
  runtime-verified once a fresh `anidex3` is seeded.
- `phase4-complete/` (repo root) is now staged. The **boss battle-graph layer is
  wired against the real source** (`logic-graphs/battle_graphs.json`) — 51 graph
  rows + 210 strong-skill rollups, validated by a dry-run parse. The
  **emotion chart is resolved & seeded** from the decompiled native function
  (constants resolved to 0.8/1.0/1.2, translated into the forms code system,
  tested) — lighting up the emotion axis of the creature type chart. The
  The **mechanics layer is seeded** from `native/FORMULAS.md` + `constants.json`:
  9 formulas, 12 named constants (FK-linked), 3 difficulty scalars, and 6
  `xp_curve` rows (with `form.exp_curve_type` wired) — fully populating
  `GET /api/mechanics`. Two honest residuals remain: `xp_level_exp` stays empty
  (the per-curve AnimationCurve base sample is unresolved, so per-level exp isn't
  numerically verifiable), and `asset_guid` isn't seedable yet (the asset manifest
  lacks an extracted Addressables-GUID cache to complete the two-hop).
- Remaining pages (moves, items, world, quests, story, mechanics, …) are designed
  in `ARCHITECTURE.md §7` and land on the same Controller→Service→Repository seams.
