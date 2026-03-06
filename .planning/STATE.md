---
gsd_state_version: 1.0
milestone: v0.8
milestone_name: — web-client
status: verifying
stopped_at: Completed 15-02-PLAN.md — Phase 15 scaffold complete, proceed to Phase 16 Auth
last_updated: "2026-03-06T08:39:31.997Z"
last_activity: "2026-03-06 — 15-02 complete: Docker image serves React SPA + Fastify API, verified on root@192.168.1.50"
progress:
  total_phases: 19
  completed_phases: 11
  total_plans: 28
  completed_plans: 27
  percent: 96
---

# OutlinerGod Project State

## Current Position

Phase: 15 — Scaffold (COMPLETE)
Plan: 15-02 complete
Status: Phase 15 complete — three-stage Docker build verified on production server
Last activity: 2026-03-06 — 15-02 complete: Docker image serves React SPA + Fastify API, verified on root@192.168.1.50

Progress: [██████████] 96% (v0.8: Phase 15 complete, 2/2 plans done)

**Core value:** Self-hosted, offline-first outliner that works identically on Android and in the browser — your notes stay on your server.
**Current focus:** v0.8 web-client — React + Vite web client at https://notes.gregorymaingret.fr

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-06)

## v0.8 Milestone Phases

| Phase | Name | Requirements | Status |
|-------|------|--------------|--------|
| 15 | Scaffold | SETUP-01, SETUP-02, SETUP-03 | Complete |
| 16 | Auth | AUTH-01, AUTH-02, AUTH-03 | Not started |
| 17 | Document List | DOC-01, DOC-02, DOC-03, DOC-04 | Not started |
| 18 | Node Editor + Sync | EDIT-01..07, SYNC-01, SYNC-02 | Not started |
| 19 | Drag-and-Drop | EDIT-08 | Not started |

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

Next action: Proceed to Phase 16 Auth (AUTH-01, AUTH-02, AUTH-03)
Stopped at: Completed 15-02-PLAN.md — Phase 15 scaffold complete, proceed to Phase 16 Auth
