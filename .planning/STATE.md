---
gsd_state_version: 1.0
milestone: v0.8
milestone_name: web-client
status: roadmap_ready
last_updated: "2026-03-06T00:00:00.000Z"
last_activity: 2026-03-06 — Roadmap created for v0.8 web-client (5 phases, 20/20 requirements mapped)
progress:
  total_phases: 5
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
---

# OutlinerGod Project State

## Current Position

Phase: 15 — Scaffold (not started)
Plan: —
Status: Roadmap complete, ready for Phase 15 planning
Last activity: 2026-03-06 — Roadmap v0.8 created

Progress: [----------] 0/5 phases complete

**Core value:** Self-hosted, offline-first outliner that works identically on Android and in the browser — your notes stay on your server.
**Current focus:** v0.8 web-client — React + Vite web client at https://notes.gregorymaingret.fr

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-06)

## v0.8 Milestone Phases

| Phase | Name | Requirements | Status |
|-------|------|--------------|--------|
| 15 | Scaffold | SETUP-01, SETUP-02, SETUP-03 | Not started |
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
- Git remote: https://github.com/gmaingret/outlinergod.git (branch: master)
- Docker server: root@192.168.1.50, deploy path: /root/outlinergod
- Domain: https://notes.gregorymaingret.fr

## Accumulated Decisions (v0.8 scope)

| ID | Decision | Phase-Plan | Impact |
|----|----------|------------|--------|
| — | No decisions yet — roadmap just created | — | — |

## Key Architecture Notes (v0.8 web-client)

- Web client lives at `web/` (parallel to `android/` and `backend/`)
- Vite 7 builds to `web/dist/`; Fastify serves via `@fastify/static` with `wildcard: false` + `setNotFoundHandler`
- Two-stage Docker build: Node builder stage compiles React; final stage copies `web/dist/` into the image
- Auth: JWT in memory, refresh token in `localStorage`; device_id stable UUID in `localStorage`
- HLC: port `backend/src/hlc/hlc.ts` verbatim — must produce identical `<13-digit-ms>-<5-digit-counter>-<deviceId>` format
- Content format decision pending: Tiptap HTML vs. Android markdown markers (`**bold**`) — resolve before Phase 18
- CRITICAL PITFALLS:
  - `@fastify/static` wildcard conflict: use `wildcard: false` + `setNotFoundHandler` (not `wildcard: true`)
  - Google OAuth audience: `verifyIdToken` must accept the web client ID; update backend audience array if new credential created
  - GIS credential field: `{ id_token: response.credential }` — NOT `response.id_token` (undefined)
  - Contenteditable cursor: use Tiptap (manages own DOM); never raw `<div contenteditable>`
  - HLC parity: validate with test vectors against `backend/src/hlc/hlc.ts` before connecting to live data

## Session Continuity

Next action: `/gsd:plan-phase 15` — plan Phase 15: Scaffold
