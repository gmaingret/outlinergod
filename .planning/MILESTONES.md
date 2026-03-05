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
