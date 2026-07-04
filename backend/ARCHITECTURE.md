# LumenTale Wiki — backend-v3 architecture & design

backend-v3 is a from-scratch rebuild of the wiki API on the **redesigned
`wiki-db` schema** (81 tables, 13 modules, `uuid` PKs, hybrid jsonb graphs,
two-hop asset resolution, 8-language localization). The **vision is unchanged**
from v2 — a read-mostly JSON API, one package per page, camelCase + null-omitted
bodies, blanket `/api` caching, uniform errors. What changes is **reach**: the
new schema unlocks pages v2 could not build.

> Status: **whole backend implemented** — every page slice (creatures, moves,
> items, cards, furniture, bosses, trainers, camps, squadrons, world map, quests,
> story, achievements, tutorials, mechanics, logic graphs, types/quirks/meta,
> localization) on the shared seam, each with a modular FK-ordered seeder. 145
> source files, 7 pure-logic test classes (25 tests, all green). Only the asset
> resolver endpoint is deferred (its Addressables GUID cache isn't extracted —
> §6). Not yet run against a seeded DB; validated by compile + pure tests + per-
> seeder source dry-runs.

---

## 1. What carries over (the vision)

| Principle | How |
|---|---|
| Read-mostly JSON API | All routes under `/api`, GET-only. |
| One package per page | `creature/`, `boss/`, … each a thin `Controller → Service → Repository`. |
| camelCase, null-omitted | Jackson `non_null`; check key presence, not value. |
| Everything cacheable | Blanket `Cache-Control: public, max-age=3600` on `/api` (data is static post-seed). |
| Uniform errors | `@RestControllerAdvice` → `{status,error,message,path}`, `no-store`. 404 typed, 400 for malformed guid, 500 logged. |
| Pure logic extracted + tested | `RegionResolver`, `TypeChartService` are DB-free and unit-tested. |
| Shared toolkit | `common/`: asset URLs, localization, raw-record fetch, reference enums, guid validation. |
| Startup indexes | Static-post-seed data (regions, stat distributions, enum maps) computed once at boot. |

## 2. What's new (the reachable goals the redesign unlocks)

| New capability | Backed by (wiki-db module) | v2 status |
|---|---|---|
| **Two-axis type chart** — elemental *and* emotion | `form_weakness` (M2) + `emotion_chart` (M1) | single axis |
| **Bosses** as a first-class page + their **battle graphs** | `boss` (M7), `boss_battle_graph`/`boss_graph_skill` (M10) | not a page |
| **Mechanics** — real formulas / constants / XP curves | `formula`, `game_constant`, `xp_curve` (M9) | hardcoded `Guides` |
| **Logic graphs** — behavior trees, timelines, minigames | `behavior_tree`, `timeline_director`, `minigame_*` (M10) | absent |
| **DB-resolved assets** (two-hop manifest) | `asset`, `asset_guid`, `entity_asset` (M11) | filesystem only |
| **8-language localization** | `localization`, `loc_key` (M12) | partial `?lang=` |
| **Cards / Camps / Squadrons / Achievements / Tutorials** | M4, M7, M8, M6 | partial / absent |

## 3. Three decisions specific to v3

**3.1 `uuid` entity keys.** PKs are native `uuid` (v2 was `text`). Path-var guids
are validated once at the edge (`common/Guids.require`) → a malformed guid is a
clean **400**, not a Postgres-cast **500** — and repositories bind
`java.util.UUID` directly (the driver maps it to the `uuid` column; no `::uuid`
cast litter). Unity **asset** GUIDs (32-hex, not UUIDs) stay `text`.

**3.2 Hybrid asset resolution** (`common/AssetResolver`). Two legs, in order:
1. **Filesystem** — the v2 convention `data/assets/<kind>/<guid>/<file>.png`. Fast
   path for already-exported art; served immutable under `/data/**`.
2. **Manifest** — otherwise resolve the entity's Addressables GUID via
   `entity_asset → asset_guid → asset.file` (the V11 two-hop) and return that
   file's `/data` URL. Reaches art the v2 export never laid down.

Both legs yield the same `/data/...` string contract the frontend already uses —
wider coverage, no API change. The DB legs are `@Cacheable` (asset tables static).

**3.3 v3 owns schema + seed.** v2 connected to an externally-seeded DB. v3 runs
**Flyway** against the single source of truth — `wiki-db/migrations` (V1..V13,
via `spring.flyway.locations=filesystem:wiki-db/migrations`, no drift-prone copy)
— and a `CommandLineRunner` **Seeder** backfills from `data/seed/*.json`. See §6.

## 4. Package layout

```
common/    AssetResolver (hybrid), ReferenceIndex (startup enum maps),
           LocalizationResolver, RawRecordService, JsonPrune, Guids (uuid edge)
error/     ApiError, NotFoundException, BadRequestException, ApiExceptionHandler
config/    WebConfig (CORS, /data assets, blanket /api cache)
web/       RootController (API index)
seed/      Seeder (core, @Order 0) + SeedSupport (shared helpers) + one modular
           @Order'd *Seeder per domain (FK-ordered, idempotent) — see §6

ALL PAGE SLICES IMPLEMENTED (Controller → Service → Repository, same seam):
creature/  (RegionIndex+RegionResolver pure, StatGradeService, EvolutionService,
            CrossReferenceService, TypeChartService pure)
boss/      (whole-graph jsonb read + boss_graph_skill rollup + cross-links)
move/ item/ card/ furniture/ trainer/ camp/ squadron/ map/ (+MapGraph pure)
quest/ story/ (+StoryGeography pure) achievement/ tutorial/ mechanics/
logicgraph/ (behavior trees + timelines + minigames) type/ (+TypeAnalytics pure)
loc/ meta (in type/)

Deferred: asset/ resolver endpoint (Addressables GUID cache not extracted — §6).
```

`creature/` is the template every page copies; `boss/` proves the new
logic-graph + rollup + asset layers compose on the same seams.

## 5. API — implemented slices

All paths relative to `http://localhost:8083`. camelCase; **null fields omitted**.

### 5.1 Creatures (`creature/`)
```
GET /api/creatures               → CreatureSummary[]
GET /api/creatures/{guid}        → CreatureDetail
GET /api/species/{guid}/variants → raw form record[]
```
```ts
type CreatureSummary = { guid; species; variant; dex?; emo?; ele?;
  variants: number; hasLost: boolean; menuArt?; front?; regions: ('north'|'south')[] };

type CreatureDetail = {
  form: Record<string,unknown>;        // raw pruned record (siblings are computed)
  regions: ('north'|'south')[];
  statGrades: { stat; grade; pct: number; rank? }[];
  typeChart: {                          // NEW two-axis profile
    emotion?: string;
    elemental: { attacker; effectiveness }[];      // per-form elemental axis
    emotionOffense: { other; multiplier: number }[];
    emotionDefense: { other; multiplier: number }[];
  };
  spawns: { guid; name; region?; interior; levelMin?; levelMax? }[];
  usedBy: { kind:'trainer'|'boss'; guid; name; level? }[];
  evolvesFrom: EvoNode[];
  evoChain: EvoNode[][];                // stages, depth 0 = root
};
```

### 5.2 Bosses (`boss/`)
```
GET /api/bosses              → BossSummary[]
GET /api/bosses/{guid}       → BossDetail   (stats, kit, cross-links, graph pointer)
GET /api/bosses/{guid}/graph → BossGraph    (whole battle graph: one jsonb-doc row)
```
```ts
type BossDetail = { guid; internalName; display?; level?; ele?; emotion?;
  hiddenType?; expGiven?; targetBst?; extraHealthBars?;
  statsOverride?: unknown; ai?: unknown;
  originSpecies?: { guid; species }; form?: { guid; species; variant; menuArt? };
  skills: { moveGuid; moveName?; type?; level?; ord? }[];
  graph?: { graphName?; nodeCount?; present: boolean; note? } };

type BossGraph = { bossGuid; graphName?; assetGuid?; nodeCount?;
  nodes: unknown[]; edges: unknown[];            // the whole graph, read in one row
  strongSkills: { moveGuid; moveName?; targetForm?; targetFormula? }[];  // rollup→move
  note? };
```
The graph is read as a **single jsonb document** (the design's whole-graph-per-page
pattern), with only the cross-cutting `boss_graph_skill` rollup joined to `move`.

### 5.3 Mechanics (`mechanics/`) & Logic graphs (`logicgraph/`)
```
GET /api/mechanics                   → overview (formulas + xpCurves + difficulty + constantCount)
GET /api/mechanics/constants         → Constant[]
GET /api/mechanics/formulas/{key}    → FormulaDetail (+ its constants + raw)
GET /api/mechanics/xp-curves/{type}  → XpCurveDetail (+ level→exp table, empty for now)

GET /api/behavior-trees   /{pathId}  → AI behavior trees (whole nodes/edges jsonb)
GET /api/timelines        /{pathId}  → cutscene directors (recursive tracks jsonb)
GET /api/minigames        /{pathId}  → minigame instances (+ fields blob)
```
`{pathId}` is a Unity int64 (`long`, may be negative); a non-numeric id is a
framework 400. Detail endpoints read one jsonb-document row each.

## 6. Seeding path

Flyway applies the DDL, then the seeders backfill. Properties:
`lumentale.seed.{on-empty,dir,phase4-dir}`.

- **Modular & flexible** — the core `Seeder` (`@Order(0)`) seeds the reference +
  creature + battle backbone; each later domain is its **own `@Component
  *Seeder`** (`CardSeeder`, `MapSeeder`, `StorySeeder`, …) ordered by `@Order`
  along the FK DAG (10 catalogue → 20 quest/trainer → 40 camp/squadron → 50 world
  → 60 story). Adding/removing a page = drop in / delete one seeder file; nothing
  else changes. Shared coercion/IO/FK helpers live in `seed/SeedSupport`.
- **Idempotent** — every step guards on its table being empty; reboots no-op.
- **FK-ordered** — enums → species → move → form → form_* → boss → boss_skill.
  Child rows pointing at not-yet-seeded parents (`form_spawn`→`game_map`,
  `form_drop`→`item`) are deferred to the world/item steps; a row whose required
  parent is missing from a *partial* seed is skipped with a logged count, so a
  slice seed never aborts on a V13 FK. (A full seed instead declares V13
  `NOT VALID` then `VALIDATE` — wiki-db/00-OVERVIEW §6.)
- **Derived where the source allows** — `ele_type`/`emotion_type`/`quirk` and the
  `species` rows are derived from `forms.json` (no separate species file).
- **phase4 layers** seed from `phase4-complete/` (repo root). Now staged:
  - `boss_battle_graph` + `boss_graph_skill` — **wired against the real
    `logic-graphs/battle_graphs.json`**: 51 graph rows (36 scripted graphs, 15
    note-only), edges derived from inline node links (`next`/`true`/`false`), and
    the strong-skill rollup mapped to moves by name (210 rows, 0 unmatched). This
    fully powers `GET /api/bosses/{guid}/graph`.
  - `emotion_chart` — **resolved & seeded** from
    `BattleMath__GetEmotionalTypeEffectivenessMultiplier.c`: the three `_DAT_`
    return symbols resolve via `constants.json` (0.8 / 1.0 / 1.2), and the
    `EmoTypes`-indexed matrix is translated by name into the DB's forms code
    system so it joins `form.emotion_code` directly. Held as tested reference data
    (`creature/EmotionChartData`, invariants guarded by `EmotionChartDataTest`).
    This lights up the **emotion axis of the creature type chart**.
  - `xp_curve` — **6 curve rows seeded** (curve_type 0–5) from
    `AniCurve__GetExpForLevel.c`, with the 12 `_UNK_`/`_DAT_` constants resolved
    via `constants.json` and substituted into the expressions, and
    `form.exp_curve_type` wired to `level_curve` (FK verified: all forms covered).
    `xp_level_exp` is **left empty on purpose** — the per-curve base sample
    `AC(L)` (native fn @0x293010) is unresolved, so per-level exp isn't
    numerically verifiable; fabricating it would be inventing data (the
    "structurally complete, not numerically verified" limit, wiki-db §6). The
    XP-curve endpoint serves the curve metadata with an absent level table.
  - `formula` / `game_constant` / `difficulty_scalar` — **seeded** by curating
    `native/FORMULAS.md` (a well-structured doc): 9 formula rows with
    signature/expression/confidence (mirroring the doc's per-formula honesty),
    12 named tuning constants (FK-linked to their formula; emotion-multiplier
    `va` provenance resolved from `constants.json`, others null not guessed), and
    3 difficulty scalars. This fully populates `GET /api/mechanics`.
  - `behavior_tree` / `timeline_director` / `minigame_instance` — **seeded** from
    `logic-graphs/{behavior_trees,timelines,minigames}.json` (25 trees / 269 nodes,
    270 directors / 2920 clips, 17 minigames — counts match each source's own
    metadata). Each is a whole-graph jsonb document read by the `logicgraph/`
    slice. `minigame_prize` is skipped (prize tables are opaque config inside the
    instance `fields`, no resolved item GUIDs).
  - asset manifest (`assets/_manifests/shard_*.jsonl`) gives the `asset` rows, but
    the **Addressables GUID→bundle cache is not extracted to data**, so `asset_guid`
    (and thus the two-hop) can't be completed yet — the `AssetResolver` filesystem
    leg carries asset URLs in the meantime.

Each remaining domain is a new `seed*` step following the identical pattern,
enumerated in `Seeder.run()` so the gaps are explicit, not implied-complete.

## 7. Schema → page map (the build order from here)

| Page / package | Primary tables | Notes |
|---|---|---|
| creature ✅ | species, form, form_* , ele_type, emotion_type, emotion_chart | two-axis type chart |
| boss ✅ | boss, boss_skill, boss_battle_graph, boss_graph_skill | whole-graph jsonb read |
| move ✅ | move | learners via form_skill (`move/`) |
| item ✅ | item, crafting_recipe/_ingredient | recipe/drops cross-links (`item/`); shops/pickups await world seed |
| card ✅ | card, card_pool, card_pool_entry | depicts form (nullable) (`card/`) |
| map ✅ | game_map, map_* | exits/spawns/shops/battles; map-graph (`map/`) |
| quest ✅ / story ✅ | quest, quest_node/_transition, story_scene(+rollups), variable | hybrid graph (`quest/`, `story/`) |
| trainer ✅ / squadron ✅ / camp ✅ | trainer(+party/inv), squadron(+member), camp(+target/task) | (`trainer/` `squadron/` `camp/`) |
| achievement ✅ / furniture ✅ / tutorial ✅ | achievement, furniture, tutorial(+page) | catalogue |
| mechanics ✅ | formula, game_constant, xp_curve, difficulty_scalar | replaces hardcoded Guides (`mechanics/`) |
| logic graphs ✅ | behavior_tree, timeline_director, minigame_instance | jsonb-doc reads (`logicgraph/`) |
| types ✅ / loc ✅ / meta ✅ | (cross-cutting) | analytics (`type/`+TypeAnalytics pure), 8-lang loc (`loc/`), counts |
| assets ⏸ | asset, asset_guid, entity_asset | resolver in `common/`; endpoint deferred (no GUID cache) |

## 8. Running

```bash
# fresh DB matching the redesign
createdb anidex3
# then either let Flyway+Seeder run on boot…
./gradlew bootRun            # :8083 — applies wiki-db/migrations, seeds data/seed
# …or apply DDL by hand first (wiki-db/README) and set LUMENTALE_FLYWAY_ENABLED=false
./gradlew test               # pure-logic unit tests (no DB)
```
Runs on **:8083** alongside v1 (8080) and v2 (8082) during the migration.
