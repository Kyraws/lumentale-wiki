# wiki-db — LumenTale Wiki database redesign

From-scratch Postgres schema that absorbs the full `phase4-complete` extract and
connects every game entity. Deliverable = **design doc + DDL + ER map** (no
seeding/running).

- **[`00-OVERVIEW.md`](00-OVERVIEW.md)** — the design: conventions, the ER
  connection map (Mermaid, grouped by module), source→table mapping, the
  two-axis type-chart modelling, and notes/gaps. **Start here.**
- **[`migrations/`](migrations/)** — `V1`…`V13` Flyway-style DDL (81 tables,
  50 cross-module FKs, 4 integrity CHECKs). `V13__foreign_keys.sql` is the
  connection layer.

## What changed vs the old `anidex2` schema
- Promotes `form` jsonb blobs to real relations (`form_hidden_type`,
  `form_weakness`) + a `quirk` catalogue.
- Adds the four phase4 layers the old schema never modelled: **type/emotion
  charts**, **formulas/constants/XP curves** (`V9`), **logic graphs** — boss
  battle graphs, behavior trees, timelines, minigames (`V10`), and the **full
  88k-row asset manifest** with two-hop GUID resolution (`V11`).

## Design choices (after review — see 00-OVERVIEW §7)
- `uuid` entity PKs; per-domain enum lookup tables (no OTLT); typed FK columns +
  CHECK instead of polymorphic refs; graph layers stored as **jsonb documents +
  rollups** (matches the wiki's whole-graph-per-page read pattern).

## Apply (fresh DB)
Point Flyway at `migrations/` on a new database, or for a quick check:
```bash
createdb anidex3
for f in migrations/V*.sql; do psql -d anidex3 -f "$f"; done
```
Validated statically (all FK targets resolve to PK/UNIQUE columns); not yet run
against a live server or seeded. See `00-OVERVIEW.md` §6 for the seeding plan.
