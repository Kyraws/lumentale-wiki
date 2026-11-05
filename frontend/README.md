# Frontend

React 19, TypeScript, Vite 8, Tailwind 4 and React Router 7. The UI provides creature and catalogue pages, map overlays, story/quest views and graph readers with shared loading, error and empty states.

## Run

Start the [backend](../backend/README.md) on port **8084**, then from `frontend/`:

```bash
npm ci
npm run dev
```

The development server uses `http://localhost:5177` and proxies `/api` and `/data` to the backend, as defined in [vite.config.ts](vite.config.ts). Use a Node.js version supported by the locked Vite 8 toolchain. A populated wiki requires [separately supplied game data](../README.md#data-separately-supplied-never-redistributed-here).

## Build

```bash
npm run build
npm run preview
```

The build runs TypeScript followed by Vite and writes `dist/`; preview defaults to port 4173. These are source-backed commands, not a fresh build or browser-test result.

## Source map

- [lib/api.ts](src/lib/api.ts): typed fetch/useApi helpers, response cache and in-flight request reuse.
- [lib/types.ts](src/lib/types.ts): shared response shapes.
- [components](src/components): shared navigation, sprites, status views and story/graph presentation.
- [pages](src/pages): domain pages, including [map projection](src/pages/MapDetail.tsx).
- [index.css](src/index.css): shared visual tokens and styles.

No screenshot set is committed. `shot.mjs` is a local screenshot/error-sweep utility tied to a specific Linux Chromium path; no portable browser test setup or current smoke-test pass is claimed. See the [engineering source tour](../docs/ENGINEERING.md) for evidence and limits.
