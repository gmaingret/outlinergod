---
gsd_state_version: 1.0
milestone: v0.8
milestone_name: — web-client
status: verifying
stopped_at: Phase 16 complete — all AUTH requirements verified in live Docker deployment
last_updated: "2026-03-06T09:47:53.374Z"
last_activity: "2026-03-06 — 16-03 verified: Google sign-in, refresh persistence, unauthenticated redirect all confirmed in live deployment"
progress:
  total_phases: 19
  completed_phases: 12
  total_plans: 31
  completed_plans: 30
  percent: 97
---

# OutlinerGod Project State

## Current Position

Phase: 16 — Auth (COMPLETE)
Plan: 16-03 VERIFIED — human approval received (Docker deployment at http://192.168.1.50:3000)
Status: Phase 16 fully done — AUTH-01, AUTH-02, AUTH-03 all verified live in production Docker
Last activity: 2026-03-06 — 16-03 verified: Google sign-in, refresh persistence, unauthenticated redirect all confirmed in live deployment

Progress: [██████████] 97% (v0.8: Phase 16 complete, ready for Phase 17)

**Core value:** Self-hosted, offline-first outliner that works identically on Android and in the browser — your notes stay on your server.
**Current focus:** v0.8 web-client — React + Vite web client at https://notes.gregorymaingret.fr

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-06)

## v0.8 Milestone Phases

| Phase | Name | Requirements | Status |
|-------|------|--------------|--------|
| 15 | Scaffold | SETUP-01, SETUP-02, SETUP-03 | Complete |
| 16 | Auth | AUTH-01, AUTH-02, AUTH-03 | Complete (verified in Docker) |
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
| D-WEB-TEST-01 | vitest.config.ts separate from vite.config.ts to avoid @tailwindcss/vite plugin conflicts in test mode | 16-01 | Test mode doesn't load Tailwind plugin; prevents plugin collision errors |
| D-WEB-TEST-02 | jsdom (not happy-dom) for broader @testing-library/react 16 compatibility | 16-01 | @testing-library/react 16 well-tested with jsdom |
| D-WEB-TEST-03 | globals:true in vitest config — no import boilerplate needed for describe/it/expect in test files | 16-01 | Consistent with jest-dom style; cleaner test files |
| D-WEB-05 | login() uses data.user?.id ?? decodeUserId(data.token) — fallback decodes userId from JWT sub claim | 16-02 | Defensive: handles auth response where user field may be absent |
| D-WEB-06 | GOOGLE_WEB_CLIENT_ID added to root .env.example (not backend/.env) — docker-compose env_file: .env at project root | 16-02 | Env lives at project root; backend/.env does not exist |
| D-WEB-07 | POST /api/auth/google now uses body.device_id ?? 'web-default' (was hardcoded 'default') | 16-02 | Web client sends device_id in body; backend now stores it correctly |
| D-WEB-08 | tsconfig.app.json excludes test files — prevents tsc build failure from global.fetch without @types/node | 16-03 | pnpm build exits 0; vitest still handles test types independently |
| D-WEB-09 | ProtectedRoute checks isLoading first then !accessToken — ensures silent refresh completes before redirect | 16-03 | Prevents flash-to-login on page refresh when valid refresh_token is in localStorage |

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

Next action: Phase 17 — Document List (DOC-01..04)
Stopped at: Phase 16 complete — all AUTH requirements verified in live Docker deployment
