---
plan: 12-03
status: complete
date: 2026-03-04
subsystem: backend-hardening
tags: [rate-limiting, tombstone, fastify, sqlite, security]
requires: [12-01, 12-02]
provides: [auth-rate-limiting, tombstone-purge-on-startup]
affects: [12-04]
tech-stack:
  added: ["@fastify/rate-limit 10.3.0"]
  patterns: [scoped-plugin-rate-limiting, startup-maintenance-hooks]
key-files:
  created:
    - backend/src/tombstone.ts
    - backend/src/tombstone.test.ts
  modified:
    - backend/src/routes/auth.ts
    - backend/src/routes/auth.test.ts
    - backend/src/index.ts
    - backend/package.json
    - backend/pnpm-lock.yaml
decisions:
  - id: D32
    text: "Rate limit scoped inside auth plugin (not buildApp global) — sync push sends large batches and must never be throttled"
  - id: D33
    text: "purgeTombstones called after runMigrations() and before buildApp() in startServer() — ensures schema exists before purge runs"
metrics:
  duration: "18 minutes"
  completed: "2026-03-04"
---

# Phase 12 Plan 03: Backend Hardening (Rate Limiting + Tombstone Purge) — Summary

One-liner: Auth routes rate-limited at 10 req/min/IP via scoped @fastify/rate-limit; purgeTombstones() removes soft-deleted records older than 90 days on startup.

## What Was Built

- Installed `@fastify/rate-limit` 10.3.0 and registered it inside the auth plugin scope (not globally) to throttle only POST /auth/google and POST /auth/refresh at 10 requests per minute per IP
- Created `backend/src/tombstone.ts` with `purgeTombstones()` that deletes rows from nodes, documents, and bookmarks where `deleted_at IS NOT NULL AND deleted_at < cutoff` (cutoff = 90 days ago)
- Wired `purgeTombstones()` into `startServer()` after `runMigrations()` and before `buildApp()`
- Added rate limit test confirming 429 after 11th request to `/api/auth/google` (isolated app instance)
- Added 10 tombstone tests covering all 3 tables, old tombstone deletion, recent tombstone retention, active record retention, and empty-table safety

## Tasks Completed

| Task | Commit | Files |
|------|--------|-------|
| Task 1: Rate limiting on auth routes | a3b6284 | backend/package.json, pnpm-lock.yaml, src/routes/auth.ts, src/routes/auth.test.ts |
| Task 2: Tombstone purge on startup | 1a1be85 | backend/src/tombstone.ts, backend/src/tombstone.test.ts, backend/src/index.ts |

## Decisions Made

| ID | Decision | Impact |
|----|----------|--------|
| D32 | Rate limit registered inside auth plugin scope only (not buildApp global) — sync push sends large batches that must never be throttled | auth.ts plugin registration |
| D33 | purgeTombstones called after runMigrations() and before buildApp() in startServer() | index.ts startup order |

## Issues Encountered

None — plan executed exactly as written.

## Deviations from Plan

None — plan executed exactly as written.

## Tests

- Before: 257 tests passing (25 test files)
- After: 267 tests passing (26 test files)
- New tests: 1 rate limit test (429 assertion) + 10 tombstone tests
- All 267 tests pass with zero failures
