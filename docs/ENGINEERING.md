# Engineering source tour

## Problem and reusable summary

The project turns linked game records into browsable pages: a creature connects to its moves and spawn maps; maps connect to encounters; story scenes and quests expose graph-shaped progression. A usable interface needs both relational cross-links and variable graph payloads.

Reusable project summary:

> Developed a Java/Spring Boot and React/TypeScript fan wiki backed by PostgreSQL, with JDBC APIs, Flyway migrations, JSON import/curation tooling, and interactive map and story views. Modeled entity relationships alongside JSONB graph documents and implemented map-coordinate projection with optional tile calibration. Application code is distributed separately from proprietary game data.

This summarizes inspected functionality, without asserting measured speedups, production deployment or unaided authorship.

## Design choices and evidence

| Area | Implementation and tradeoff | Source |
| --- | --- | --- |
| API boundaries | Controllers delegate through services to JDBC repositories; explicit SQL exposes joins and JSONB handling without ORM entity mapping. | [Creature repository](../backend/src/main/java/com/lumentale/wiki/creature/CreatureRepository.java), [domain package](../backend/src/main/java/com/lumentale/wiki/creature) |
| Relational entities | UUID keys and typed relationships support creature variants, learnsets and cross-domain navigation. Flyway versions the hand-authored schema. | [Creature migration](../wiki-db/migrations/V2__creatures.sql), [cross-module constraints](../wiki-db/migrations/V13__foreign_keys.sql) |
| Graph data | Whole graphs are JSONB documents for page-sized reads; smaller relational rollups support cross-links such as boss skills to moves. This preserves flexible node payloads while keeping selected relationships queryable. | [Graph migration](../wiki-db/migrations/V10__logic_graphs.sql), [boss repository](../backend/src/main/java/com/lumentale/wiki/boss/BossRepository.java) |
| Data preparation | Python reads existing extraction/localization JSON, normalizes text and resolves names and GUID references into curated output. Java seeders backfill staged records when enabled. | [Curation script](../tools/build_curated_data.py), [seeders](../backend/src/main/java/com/lumentale/wiki/seed) |
| Map geometry | The frontend maps world X/Z coordinates onto tile percentages, flips Z for screen coordinates, and culls markers outside playable bounds. Optional calibrated centers/spans override the default tile frame. | [Map page](../frontend/src/pages/MapDetail.tsx), [calibration index](../backend/src/main/java/com/lumentale/wiki/map/TileCalibrationIndex.java) |
| Tile calibration | Python scores candidate frames by how many markers land on non-background pixels and adopts alternatives only above a margin. It needs existing tiles, seed geometry, NumPy and Pillow; it does not render tiles. | [Calibration script](../tools/calibrate_map_tiles.py) |
| Read-mostly performance | API and asset cache headers, response compression, startup indexes, and frontend response/in-flight request reuse reduce repeated work for static post-seed data. No measured latency or throughput is established here. | [Web configuration](../backend/src/main/java/com/lumentale/wiki/config/WebConfig.java), [properties](../backend/src/main/resources/application.properties), [frontend API helper](../frontend/src/lib/api.ts) |
| UI composition | Shared typed API helpers and loading/error/empty states support domain pages; map and story components present linked records. | [Frontend source](../frontend/src), [scene reader](../frontend/src/components/SceneReader.tsx) |

## Reverse engineering and rendering boundary

The repository includes consumers of recovered game structures: coordinate projection, domain mappings, mechanics metadata and graph-shaped records. These show how extracted information is represented and presented. They do not independently establish the completeness or correctness of the original reverse-engineering process.

The raw extractor, native decompilation and GPU map renderer are absent from this checkout. There is no comparable-input/hardware benchmark supporting the previously reported rendering-time reduction; no quantified speedup is claimed. The card UI's visual effect is separate from a GPU map renderer.

## Verification and limits

- [Map graph tests](../backend/src/test/java/com/lumentale/wiki/map/MapGraphAssemblerTest.java) exercise grouping, name resolution, conditions and ordering on synthetic input.
- [Quest flow tests](../backend/src/test/java/com/lumentale/wiki/quest/QuestFlowAnalyzerTest.java) and other [backend tests](../backend/src/test/java/com/lumentale/wiki) cover selected domain logic. Their presence is not a fresh passing run or seeded integration coverage.
- Run backend tests and the frontend TypeScript/Vite build using the [root instructions](../README.md#build-and-verification). Documentation review checked source and paths, without executing the application, importing game data or benchmarking rendering.
- No committed screenshot set or linked hosted demo was found. `frontend/shot.mjs` is a local browser utility with a hardcoded Linux browser path; it is not portable CI evidence or a supplied demo.
- Older architecture documents describe earlier versions and report historical results. This tour uses current checked-in code; it does not repeat historical test counts as current results.
- Full data-dependent functionality needs separately obtained compatible extracts and optional artwork. Preserve the [data-distribution boundary](../README.md#data-separately-supplied-never-redistributed-here); do not attach proprietary extracts or imagery to documentation or reviews.
