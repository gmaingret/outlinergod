---
gsd_state_version: 1.0
milestone: v0.9
milestone_name: — quality-hardening
status: completed
stopped_at: Phase 26 context gathered
last_updated: "2026-03-06T16:38:19.817Z"
last_activity: "2026-03-06 — 19-02 complete: FlatNodeList with drag-to-reorder/reparent, 7 new tests, NodeRow removed, 69 total tests pass"
progress:
  total_phases: 26
  completed_phases: 15
  total_plans: 38
  completed_plans: 37
  percent: 97
---

# OutlinerGod Project State

## Current Position

Phase: 19 — Drag-and-Drop (COMPLETE)
Plan: 19-02 COMPLETE — FlatNodeList component with dnd-kit DndContext/SortableContext, 69 tests pass
Status: Phase 19 complete; all 38 plans done; v0.8 milestone complete
Last activity: 2026-03-06 — 19-02 complete: FlatNodeList with drag-to-reorder/reparent, 7 new tests, NodeRow removed, 69 total tests pass

Progress: [██████████] 97% (v0.8: all phases complete)

**Core value:** Self-hosted, offline-first outliner that works identically on Android and in the browser — your notes stay on your server.
**Current focus:** v0.8 web-client — React + Vite web client at https://notes.gregorymaingret.fr

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-06)

## v0.8 Milestone Phases

| Phase | Name | Requirements | Status |
|-------|------|--------------|--------|
| 15 | Scaffold | SETUP-01, SETUP-02, SETUP-03 | Complete |
| 16 | Auth | AUTH-01, AUTH-02, AUTH-03 | Complete (verified in Docker) |
| 17 | Document List | DOC-01, DOC-02, DOC-03, DOC-04 | Complete |
| 18 | Node Editor + Sync | EDIT-01..07, SYNC-01, SYNC-02 | Complete |
| 19 | Drag-and-Drop | EDIT-08 | Complete |

## Open Tech Debt (carried from v0.7)

- TD-D: NodeActionToolbar button set (8 buttons) diverged from Phase 10 plan spec — documentation drift only, no code issue
- TD-E: Drag/swipe gesture conflict (longPressDraggableHandle inside SwipeToDismissBox) — needs device testing

## Key Environment

- Java: Android Studio JBR at /c/Program Files/Android/Android Studio/jbr (JDK 21)
- ANDROID_HOME: /c/Users/gmain/AppData/Local/Android/Sdk
- Run Android tests: export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && export ANDROID_HOME="/c/Users/gmain/AppData/Local/Android/Sdk" && cd android && ./gradlew test
- Run backend tests: cd backend && pnpm test
- Run web dev: cd web && pnpm dev (HMR at localhost:5173, /api proxied to :3000)
- Run web build: cd web && pnpm build (output: web/dist/)
- Git remote: https://github.com/gmaingret/outlinergod.git (branch: master)
- Docker server: root@192.168.1.50, deploy path: /root/outlinergod
- Domain: https://notes.gregorymaingret.fr

## Accumulated Decisions (v0.8 scope)

| ID | Decision | Phase-Plan | Impact |
|----|----------|------------|--------|
| D-WEB-01 | Tailwind v4 via @tailwindcss/vite — no tailwind.config.js or postcss.config.js | 15-01 | No config files needed; single @import in index.css |
| D-WEB-02 | @fastify/static with wildcard: false to prevent /api/* route shadowing | 15-01 | Critical: wildcard: true would intercept all /api calls |
| D-WEB-03 | /api/* prefix guard in setNotFoundHandler — unknown API paths return 404 not index.html | 15-01 | Preserves API contract; keeps test buildApp_unknownRoute_returns404 green |
| D-WEB-04 | WEB_DIST_PATH env var for Docker override of computed dist path | 15-01 | Docker sets /app/web/dist; dev falls back to ../../web/dist relative to src/ |
| D-DOCKER-01 | Three-stage Dockerfile (web-builder, ts-builder, runner) — context moved from ./backend to project root | 15-02 | Enables single image with both React SPA and Fastify API |
| D-DOCKER-02 | All backend COPY paths prefixed backend/ due to context change; root .dockerignore supersedes backend/.dockerignore | 15-02 | Required by context:. in docker-compose.yml |
| D-DOCKER-05 | WEB_DIST_PATH=/app/web/dist must be set in docker-compose.yml environment — path.join resolves to /web/dist (wrong) without it | 15-02 | Critical: container serves 404 on / without this env var |
| D-WEB-TEST-01 | vitest.config.ts separate from vite.config.ts to avoid @tailwindcss/vite plugin conflicts in test mode | 16-01 | Test mode doesn't load Tailwind plugin; prevents plugin collision errors |
| D-WEB-TEST-02 | jsdom (not happy-dom) for broader @testing-library/react 16 compatibility | 16-01 | @testing-library/react 16 well-tested with jsdom |
| D-WEB-TEST-03 | globals:true in vitest config — no import boilerplate needed for describe/it/expect in test files | 16-01 | Consistent with jest-dom style; cleaner test files |
| D-WEB-05 | login() uses data.user?.id ?? decodeUserId(data.token) — fallback decodes userId from JWT sub claim | 16-02 | Defensive: handles auth response where user field may be absent |
| D-WEB-06 | GOOGLE_WEB_CLIENT_ID added to root .env.example (not backend/.env) — docker-compose env_file: .env at project root | 16-02 | Env lives at project root; backend/.env does not exist |
| D-WEB-07 | POST /api/auth/google now uses body.device_id ?? 'web-default' (was hardcoded 'default') | 16-02 | Web client sends device_id in body; backend now stores it correctly |
| D-WEB-08 | tsconfig.app.json excludes test files — prevents tsc build failure from global.fetch without @types/node | 16-03 | pnpm build exits 0; vitest still handles test types independently |
| D-WEB-09 | ProtectedRoute checks isLoading first then !accessToken — ensures silent refresh completes before redirect | 16-03 | Prevents flash-to-login on page refresh when valid refresh_token is in localStorage |
| D-WEB-10 | Skeleton test uses .animate-pulse CSS class — DocumentListPage must use this Tailwind class for loading placeholders | 17-01 | Test assertion uses DOM query selector, not ARIA |
| D-WEB-11 | Context menu on document row triggered by contextmenu event — fireEvent.contextMenu in tests | 17-01 | Consistent cross-browser; implementation must listen for onContextMenu |
| D-WEB-12 | Delete confirmation uses role="dialog" — implementation must use semantic dialog element or role attribute | 17-01 | Enables getByRole('dialog') assertion in tests |
| D-WEB-13 | async IIFE in useEffect for fetch — vitest mockReturnValueOnce returns plain object; await wraps in Promise.resolve but .then() throws "not a function" | 17-02 | All fetch calls in components must use async/await not .then() chains |
| D-WEB-14 | Empty state splits "No documents yet" into child <span> — RTL getByText exact-matches element text; full sentence in one element fails partial assertion | 17-02 | Use child spans when partial RTL text assertions are needed |
| D-WEB-15 | insertSiblingNode accepts pre-built TreeNode (not documentId); returns TreeNode[] roots — test file is authoritative over plan action text | 18-02 | NodeEditorPage must create the node object before calling insertSiblingNode |
| D-WEB-16 | jest shim in test-setup.ts delegates advanceTimersByTime to vi — RTL v16 only checks for jest global to detect fake timers, not vi | 18-03 | SYNC-02 waitFor tests complete in ~130ms instead of timing out (5s) |
| D-WEB-17 | Bullet glyph (zoom in) rendered before collapse arrow in DOM — glyphs[0] is always the zoom button (test expects navigate on first glyph) | 18-03 | EDIT-07 glyph click test passes; mockNavigate is called |
| D-WEB-18 | runAllTilesAsync typo in test file auto-fixed to runAllTimersAsync | 18-03 | SYNC-02 tests no longer throw TypeError on startup |
| D-WEB-19 | reorderNode re-flattens after removal to resolve targetIndex against updated flat list (avoids off-by-one) | 19-01 | FlatNodeList caller passes targetIndex into updated list, not stale pre-removal list |
| D-WEB-20 | reorderNode insert position derived by counting new parent's children before targetIndex in post-removal flat list | 19-01 | recomputeSortOrders handles final sort_order assignment after insertion |
| D-WEB-21 | drag handle button uses aria-hidden='true' to keep getAllByRole('button') queries compatible with existing EDIT-07 test | 19-02 | Grip handle (index 0 in DOM) would otherwise be found by glyphs.indexOf(g)===0 and clicked instead of zoom button |
| D-WEB-22 | FlatNodeList tests use document.querySelectorAll('[aria-label=drag handle]') not getByRole to count aria-hidden handles | 19-02 | aria-hidden removes handles from accessibility tree; querySelectorAll still finds them |

## Key Architecture Notes (v0.8 web-client)

- Web client lives at `web/` (parallel to `android/` and `backend/`)
- Vite 7 builds to `web/dist/`; Fastify serves via `@fastify/static` with `wildcard: false` + `setNotFoundHandler`
- Three-stage Docker build: web-builder (Vite/React SPA), ts-builder (TypeScript/Fastify), runner (production image with both artifacts)
- Auth: JWT in memory, refresh token in `localStorage`; device_id stable UUID in `localStorage`
- HLC: port `backend/src/hlc/hlc.ts` verbatim — must produce identical `<13-digit-ms>-<5-digit-counter>-<deviceId>` format
- Content format decision pending: Tiptap HTML vs. Android markdown markers (`**bold**`) — resolve before Phase 18
- CRITICAL PITFALLS:
  - `@fastify/static` wildcard conflict: use `wildcard: false` + `setNotFoundHandler` (not `wildcard: true`) — CONFIRMED in 15-01
  - Google OAuth audience: `verifyIdToken` must accept the web client ID; update backend audience array if new credential created
  - GIS credential field: `{ id_token: response.credential }` — NOT `response.id_token` (undefined)
  - Contenteditable cursor: use Tiptap (manages own DOM); never raw `<div contenteditable>`
  - HLC parity: validate with test vectors against `backend/src/hlc/hlc.ts` before connecting to live data
  - WEB_DIST_PATH in docker-compose.yml: MUST be `/app/web/dist` — path.join from __dirname (/app/dist) gives wrong /web/dist without it — CONFIRMED in 15-02

## Session Continuity

Next action: Start v0.9 milestone — run `/gsd:plan-phase 20` to begin Phase 20 (Security fixes)
Stopped at: Phase 26 context gathered
