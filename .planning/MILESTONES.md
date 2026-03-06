# OutlinerGod — Milestones

## v0.4 — android-core (Shipped: 2026-03-03)

**Phases:** 01–08 | **Plans:** 14 | **Duration:** project start → 2026-03-03

**Delivered:** Full self-hosted Android outliner with Fastify backend, HLC-LWW sync, Room local DB, Orbit MVI UI, and background WorkManager sync — production-ready end-to-end.

**Key accomplishments:**
- Fastify v5 + Drizzle + SQLite backend with HLC timestamps, JWT auth, Google Sign-In, Docker deployment
- All CRUD + HLC sync endpoints (nodes, documents, bookmarks, settings, files)
- Room 2.8.4 + Hilt + Ktor client Android scaffold with HLC clock and LWW merge logic
- Full node editor: BasicTextField WYSIWYG, drag-and-drop (reorderable 2.5.1), indent/outdent, context menu
- Background WorkManager sync (15-min periodic, NetworkType.CONNECTED, exponential backoff)
- Closed all v0.4 audit gaps: HLC decimal format (GAP-1), userId/JWT separation (GAP-2), snake_case POST body (GAP-3), MainActivity settings key fix (GAP-A), settings sync push (GAP-B)

**Audit:** v0.4-MILESTONE-AUDIT.md — 9/9 requirements satisfied
**Archive:** milestones/v0.4-ROADMAP.md

---

## v0.9 — quality-hardening (Not started)

**Phases:** 20–26 | **Plans:** TBD | **Scope:** Security, tech debt, performance, test coverage, Android UX

**Goal:** No new features. Make the existing codebase production-safe, maintainable, and performant based on the 2026-03-06 codebase audit.

**Planned phases:**
- **Phase 20: Security fixes** — File DELETE ownership check, JWT_SECRET startup guard, refresh token purge
- **Phase 21: Sync architecture** — Extract SyncOrchestrator; eliminate 3-copy sync logic
- **Phase 22: Android performance** — `getNodeByIdSync` on keystroke, batch DnD updates, 62-sibling sort-order fix, SyncLogger debug guard
- **Phase 23: Data model & structure** — Proper attachment columns, FractionalIndex → util/, delete orphan hlc.ts, Room exportSchema=true, UndoSnapshot sealed class
- **Phase 24: Test coverage gaps** — File DELETE ownership test, debounce E2E test, FractionalIndex large-n, SyncWorker integration, search post-filter
- **Phase 25: Backend maintenance** — SettingsSyncRecord → merge.ts, periodic tombstone purge, max_hlc index, Node.js version alignment
- **Phase 26: Android UX redesign** — Breadcrumb home icon, last-opened doc on launch, remove back arrow, context-sensitive bottom toolbar (browse vs. edit mode), hamburger drawer (doc list + add + settings), glyph top-aligned

---

## v0.8 — web-client (Shipped: 2026-03-06)

**Phases:** 15–19 | **Plans:** 19 | **Duration:** 2026-03-06

**Delivered:** Full-featured web outliner at https://notes.gregorymaingret.fr — Google auth, document CRUD, node editor with WYSIWYG markdown, zoom navigation, sync with Android, and DnD reordering via dnd-kit.

**Key accomplishments:**
- React 19 + Vite 7 + Tailwind CSS 4 SPA served by the existing Fastify container (three-stage Docker build)
- Google OAuth via GIS `google.accounts.id.initialize`; JWT in memory, refresh token in localStorage
- HLC ported from backend TypeScript verbatim; produces identical `<13-digit-ms>-<5-digit-counter>-<deviceId>` format
- Node editor with BasicTextField-style contenteditable, Enter=new sibling, Tab/Shift+Tab indent/outdent, **bold**/*italic* live rendering
- dnd-kit SortableContext with horizontal-drag reparenting; reorderNode pure function handles subtree moves
- Zoom-in: glyph click pushes new route; Back zooms out. Sync on mount + 30s inactivity timer (shared with Android)

**Test suite:** 69 web tests (0 failures); Android 298/298; backend 267/267

---

## v0.6 — integration-polish (Shipped: 2026-03-05)

**Phases:** 09–13 | **Plans:** 12 | **Duration:** 2026-03-02 → 2026-03-05 (4 days)

**Delivered:** Advanced UX (swipe gestures, toolbar, density, logout), FTS4 search with structured operators, ZIP export, bookmarks, zoom-in navigation, backend hardening, and full integration closure — all 298 tests passing.

**Key accomplishments:**
- Client-side FTS4 search with structured operators (is:completed, color:X, in:note, in:title); result tap zooms in to matched node in context
- Swipe-to-complete (strike-through) and swipe-to-delete with 5s snackbar undo; persistent NodeActionToolbar above keyboard
- Zoom-in glyph tap navigation: filterSubtree BFS scopes editor to node subtree; each zoom = separate NavBackStackEntry, System Back zooms out
- Full-account ZIP export via FileProvider + ShareSheet; bookmarks screen with CRUD and node/document-type navigation
- Backend hardening: @fastify/rate-limit on auth routes only, purgeTombstones on startup; SyncWorkerIntegrationTest with real Room DB
- Closed all v0.6 audit gaps: search nodeId wiring (GAP-V1), NodeEditorScreen lifecycle sync (GAP-V2), bookmark zoom-in (TD-C), FTS4 docstring (TD-A), dead import (TD-B)

**Test suite:** 298/298 Android (0 failures), 267 backend (0 failures)
**Audit:** v0.6-MILESTONE-AUDIT.md — 13/13 requirements satisfied
**Archive:** milestones/v0.6-ROADMAP.md

---
