# LumenDex — frontend-v3

The web wiki UI for **LumenTale: Memories of Trey**, built on **backend-v3** (the
redesigned `wiki-db` schema). Keeps frontend-v2's "2.5D handheld" pixel look and
extends it to cover the whole v3 API.

- **Stack:** React 19 · Vite 8 · Tailwind 4 (`@theme` tokens) · react-router 7 · TypeScript.
- **Design:** cream pixel panels on a deep-indigo bezel, `Press Start 2P` display
  font, chunky borders, hard shadows, element/emotion type colors. Tokens +
  component classes live in `src/index.css`.

## Run

```bash
npm install
npm run dev      # http://localhost:5175 (proxies /api + /data → backend-v3 :8083)
npm run build    # tsc -b && vite build  → dist/
```

Backend-v3 must be running on `:8083` (the dev server proxies `/api` and `/data`
to it, so the app makes same-origin calls and image `src`s are just `/data/...`).

## Pages (34 components)

Carried over from v2 (updated for the v3 API): **Home, Dex + Creature detail,
World map + Map detail, Story, Moves + Move detail, Items + Item detail,
Trainers + Trainer detail, Types**.

New in v3: **Bosses + detail (with battle-graph viz), Cards + detail, Furniture +
detail, Camps + detail, Squadrons + detail, Quests + detail (state-machine graph),
Scene reader, Achievements, Tutorials + detail, Mechanics (formulas / constants /
XP curves / difficulty), Logic Graphs (behavior trees / timelines / minigames),
Quirks, About (dex stats)**.

Navigation: a small primary bar (Home · Dex · World · Story · Moves · Items ·
Types) plus a grouped **"More"** dropdown for the rest — keeps the bar uncluttered
as the section count grew to ~18.

## Architecture (same seams as v2)

- `lib/api.ts` — `useApi<T>(path)` hook over a tiny in-memory cache (data is static).
- `lib/types.ts` — shared response types; page-specific detail shapes live in their
  page files (defined by inspecting the live API).
- `lib/game.ts` — vocabulary helpers: element/emotion colors, stat grading,
  effectiveness/multiplier tints, confidence colors, formatting.
- `components/` — `Sprite` (pixel art + fallback), `Badge` (type/emotion/region/tag),
  `StatBar`, `States` (skeleton/error/empty), `Icons` (SVG, no emoji), `Shell` (nav).
- One file per page in `pages/`; thin, data-driven, accessible (focus rings, alt
  text, color-never-alone, reduced-motion).

## Status

All 34 pages build clean (TypeScript strict, zero errors) and were smoke-tested
headless against the live seeded backend — **33/33 routes render with no console
or runtime errors**. Screenshots of key pages are in `screenshots/`.

Notes carried from the data layer: creature/move/furniture/achievement *detail*
endpoints return raw extracted records (read defensively); some art resolves only
once the asset GUID cache is extracted (`<Sprite>` shows a graceful fallback
meanwhile); int64 graph `path_id`s are handled as exact strings (JSON number
precision) in the Logic Graphs page.
