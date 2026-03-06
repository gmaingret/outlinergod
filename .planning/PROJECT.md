# OutlinerGod

## What This Is

Self-hosted Android outliner with offline-first HLC-LWW sync, backed by a Fastify/SQLite Docker container. Starting v0.8, a React web client serves the same experience at a browser URL. Each Google account gets an independent data space; sync is pull-based using per-field Hybrid Logical Clocks.

## Core Value

Self-hosted, offline-first outliner that works identically on Android and in the browser — your notes stay on your server.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

- ✓ Fastify v5 + Drizzle + SQLite backend with HLC timestamps, JWT auth, Google Sign-In, Docker deployment — v0.4
- ✓ All CRUD + HLC sync endpoints (nodes, documents, bookmarks, settings, files) — v0.4
- ✓ Room 2.8.4 + Hilt + Ktor Android scaffold with HLC clock and LWW merge logic — v0.4
- ✓ Full node editor: BasicTextField WYSIWYG, DnD (reorderable 2.5.1), indent/outdent, context menu — v0.4
- ✓ Background WorkManager sync (15-min periodic, NetworkType.CONNECTED, exponential backoff) — v0.4
- ✓ Density scale, logout, full-row drag handle — v0.6
- ✓ Swipe-to-complete and swipe-to-delete with snackbar undo — v0.6
- ✓ Persistent NodeActionToolbar above keyboard — v0.6
- ✓ Client-side FTS4 search with structured operators — v0.6
- ✓ Full-account ZIP export via ShareSheet — v0.6
- ✓ Bookmarks screen CRUD with navigation — v0.6
- ✓ Zoom-in glyph navigation (BFS subtree scoping, back-stack) — v0.6
- ✓ Backend hardening: rate limiting, tombstone purge — v0.6
- ✓ Delete undo via toolbar (full subtree restore) — v0.7
- ✓ Drag reparenting fix (node adopts depth of surrounding context) — v0.7
- ✓ File attachments — picker, upload to backend, `[filename](url)` appended to content — v0.7

### Active

<!-- Current scope. Building toward these. -->

- [ ] React + Vite web client served by existing Fastify (@fastify/static)
- [ ] Google SSO auth in browser (web OAuth flow, same JWT backend)
- [ ] Document list with create/rename/delete
- [ ] Node editor: Enter=new sibling, Tab=indent, Shift+Tab=outdent, inline WYSIWYG formatting
- [ ] Zoom navigation (click bullet to zoom into subtree)
- [ ] Node DnD reordering in web editor
- [ ] Sync: pull on page load, push on change

### Out of Scope

<!-- Explicit boundaries. Includes reasoning to prevent re-adding. -->

- Search in web client — defer to v0.9; FTS4 is Android Room specific, needs different web approach
- Bookmarks in web client — defer to v0.9; core editor first
- File attachments in web client — defer to v0.9; upload flow more complex on web
- Export in web client — defer to v0.9
- Tags/people/dates in web client — defer to v0.9+
- Offline PWA / service worker — not required for self-hosted internal tool
- Mobile-responsive / native app feel — web is desktop-first

## Context

- Backend: Fastify v5, ESM, pnpm, Drizzle + better-sqlite3, deployed via Docker at root@192.168.1.50
- Android: Kotlin + Jetpack Compose, Orbit MVI, Room, Hilt, Ktor, reorderable 2.5.1
- Domain: https://notes.gregorymaingret.fr — Fastify will serve React static bundle at root, API at /api/*
- Google OAuth: two clients in same GCP project — Android (package+SHA1) + Web (serverClientId). Web client will need a new OAuth 2.0 Web Application credential.
- sort_order: TEXT fractional index (never float). Tree reconstructed from parent_id + sort_order.
- Sync: per-field HLC-LWW. Client pulls since last HLC, pushes pending. Deletion wins.

## Constraints

- **Tech stack**: React + Vite for web client — already decided
- **Serving**: @fastify/static serves `/web/dist` at root — one container, no CORS
- **Auth**: Same JWT backend; web must obtain Google ID token and exchange via /api/auth/google
- **Compatibility**: Must not break existing Android sync endpoints

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| sort_order is TEXT (fractional index) | Conflict-free reordering without floats | ✓ Good |
| HLC decimal format: `<13-digit-wall>-<5-digit-counter>-<deviceId>` | Matches backend hlc.ts exactly | ✓ Good |
| Deletion wins in sync conflicts | Simplicity; no conflict archive needed | ✓ Good |
| FTS4 (not FTS5) for Android search | Device SQLite may lack FTS5 | ✓ Good |
| React + Vite for web client | Largest ecosystem, best editor library options | — Pending |
| Fastify @fastify/static for web serving | One container, no CORS, simpler deployment | — Pending |

## Current Milestone: v0.8 web-client

**Goal:** Deliver a full-featured web outliner at https://notes.gregorymaingret.fr — auth, document CRUD, node editor with WYSIWYG, DnD, zoom navigation, and sync.

**Target features:**
- React + Vite web client served via Fastify
- Google SSO (web OAuth), document list CRUD
- Node editor: Enter=sibling, Tab=indent, inline formatting, zoom, DnD
- Sync: pull on load, push on change

---
*Last updated: 2026-03-06 after v0.8 milestone start*
