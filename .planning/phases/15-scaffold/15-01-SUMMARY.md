---
phase: 15-scaffold
plan: "01"
subsystem: ui
tags: [react, vite, tailwindcss, typescript, fastify, spa]

# Dependency graph
requires: []
provides:
  - web/ standalone Vite 7 + React 19 + TypeScript project with Tailwind v4
  - Hello World app at localhost:5173 via pnpm dev
  - web/dist/ build artifact served by Fastify
  - Fastify SPA serving with deep-link fallback
affects: [16-auth, 17-document-list, 18-node-editor-sync, 19-drag-drop]

# Tech tracking
tech-stack:
  added:
    - "Vite 7.3.1 (build tool)"
    - "React 19.2.4 + react-dom 19.2.4"
    - "@vitejs/plugin-react 5.1.4"
    - "tailwindcss 4.2.1"
    - "@tailwindcss/vite 4.2.1 (Tailwind v4 Vite plugin — no config file)"
    - "TypeScript 5.9.3"
  patterns:
    - "Tailwind v4 via @tailwindcss/vite: single @import in index.css, no tailwind.config.js"
    - "Fastify wildcard: false + setNotFoundHandler for SPA deep-link fallback"
    - "/api/* prefix guard in setNotFoundHandler to preserve 404 on unknown API paths"
    - "WEB_DIST_PATH env var for Docker override of dist path"

key-files:
  created:
    - web/package.json
    - web/pnpm-lock.yaml
    - web/vite.config.ts
    - web/index.html
    - web/tsconfig.json
    - web/tsconfig.app.json
    - web/tsconfig.node.json
    - web/src/main.tsx
    - web/src/App.tsx
    - web/src/index.css
  modified:
    - backend/src/index.ts

key-decisions:
  - "D-WEB-01: Tailwind v4 via @tailwindcss/vite — no tailwind.config.js or postcss.config.js required"
  - "D-WEB-02: wildcard: false on @fastify/static + setNotFoundHandler to prevent /api/* shadowing"
  - "D-WEB-03: /api/* prefix guard in setNotFoundHandler preserves 404 for unknown API routes"
  - "D-WEB-04: WEB_DIST_PATH env var allows Docker to override computed dist path"

patterns-established:
  - "SPA pattern: @fastify/static wildcard:false + setNotFoundHandler returns index.html for non-API paths"
  - "Tailwind v4: single @import tailwindcss in CSS entry point, plugin in vite.config.ts"

requirements-completed: [SETUP-02, SETUP-03]

# Metrics
duration: 4min
completed: 2026-03-06
---

# Phase 15 Plan 01: Web Scaffold Summary

**Vite 7 + React 19 + Tailwind v4 web client scaffolded in `web/` with Fastify serving the compiled SPA and deep-link fallback via `setNotFoundHandler`**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-06T09:13:23Z
- **Completed:** 2026-03-06T09:17:13Z
- **Tasks:** 2
- **Files modified:** 11 (10 created in web/, 1 modified in backend/)

## Accomplishments

- Created `web/` as a standalone Vite 7 project with React 19, TypeScript, and Tailwind v4 — no config file needed (Tailwind v4 plugin handles everything via `@tailwindcss/vite`)
- `pnpm build` in `web/` produces `web/dist/index.html` in 750ms (193kB JS, 4.6kB CSS gzip)
- Fastify now serves the React SPA for all non-`/api` paths with deep-link fallback; all 273 existing backend tests pass

## Task Commits

1. **Task 1: Scaffold web/ Vite + React + TypeScript + Tailwind v4** - `7e28dce` (feat)
2. **Task 2: Configure Fastify to serve the React SPA** - `501fca8` (feat)

## Files Created/Modified

- `web/src/App.tsx` - Hello World React component with Tailwind classes (OutlinerGod heading)
- `web/src/index.css` - Single-line `@import "tailwindcss"` (Tailwind v4 entry point)
- `web/vite.config.ts` - Tailwind plugin + `/api` and `/health` proxy to localhost:3000
- `web/src/main.tsx` - Standard Vite React entry point (already imported index.css)
- `web/index.html` - Vite HTML template
- `web/package.json` - Standalone project with react, react-dom, tailwindcss, @tailwindcss/vite
- `web/tsconfig.json`, `web/tsconfig.app.json`, `web/tsconfig.node.json` - TypeScript config
- `backend/src/index.ts` - Added @fastify/static with wildcard:false, setNotFoundHandler SPA fallback, removed placeholder GET /

## Decisions Made

- **Tailwind v4 via @tailwindcss/vite**: No `tailwind.config.js` or `postcss.config.js` created — Tailwind v4 plugin manages everything through a single `@import "tailwindcss"` in CSS.
- **wildcard: false on @fastify/static**: Using the default `wildcard: true` would register `GET /*` and shadow all `/api/*` routes. `wildcard: false` prevents this.
- **/api/* guard in setNotFoundHandler**: Unknown `/api/*` paths still return 404 JSON (not index.html). This preserves expected behavior for API clients and keeps the existing `buildApp_unknownRoute_returns404` test green.
- **WEB_DIST_PATH env var**: Docker production can set `WEB_DIST_PATH=/app/web/dist`; dev falls back to a path computed relative to `src/` pointing at `../../web/dist`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] setNotFoundHandler returned index.html for /api/* paths breaking existing test**
- **Found during:** Task 2 (Configure Fastify to serve the React SPA)
- **Issue:** `app.setNotFoundHandler` was returning `index.html` (200) for all unmatched paths including `/api/nonexistent`, causing `buildApp_unknownRoute_returns404` to fail (expected 404, got 200)
- **Fix:** Added `/api/` prefix check inside `setNotFoundHandler` — returns `{ error: 'Not Found' }` with 404 for unmatched API paths; all other paths get `index.html`
- **Files modified:** `backend/src/index.ts`
- **Verification:** `pnpm test` exits 0 with 273/273 passing
- **Committed in:** `501fca8` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - bug)
**Impact on plan:** Required for correct behavior — `/api/*` paths must remain 404 for API clients. No scope creep.

## Issues Encountered

None — build succeeded on first attempt.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `web/` scaffold complete; Phase 16 (Auth) can now add React Router, login page, and Google Sign-In flow
- `pnpm dev` in `web/` provides HMR at localhost:5173 with /api proxied to the backend
- Backend serves `web/dist/` for production Docker builds; set `WEB_DIST_PATH=/app/web/dist` in Docker image

## Self-Check: PASSED

- web/dist/index.html: FOUND
- web/src/App.tsx: FOUND
- web/src/index.css: FOUND
- web/vite.config.ts: FOUND
- commit 7e28dce: FOUND
- commit 501fca8: FOUND

---
*Phase: 15-scaffold*
*Completed: 2026-03-06*
