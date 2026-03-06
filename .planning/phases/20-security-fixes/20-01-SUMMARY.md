---
phase: 20-security-fixes
plan: 01
subsystem: backend
tags: [security, idor, jwt, refresh-tokens, fastify]
dependency_graph:
  requires: []
  provides: [file-delete-ownership-check, jwt-secret-guard, refresh-token-purge]
  affects: [backend/src/routes/files.ts, backend/src/index.ts]
tech_stack:
  added: []
  patterns: [ownership-check-before-disk-op, startup-guard, setInterval-purge]
key_files:
  created:
    - backend/src/refresh-token-purge.ts
    - backend/src/refresh-token-purge.test.ts
  modified:
    - backend/src/routes/files.ts
    - backend/src/routes/files.test.ts
    - backend/src/index.ts
decisions:
  - D-SEC-01: DB ownership check placed before stat() in DELETE handler — ownership verified before any disk operation, preventing IDOR and disk enumeration
  - D-SEC-02: Orphaned file (disk-present, no DB record) returns 404 — no ownership proof available, safest response
  - D-SEC-03: JWT guard placed in startServer() before buildApp() — tests bypass startServer() and set JWT_SECRET in beforeEach, so buildApp() remains test-friendly
  - D-SEC-04: refresh_tokens schema uses `token` as PK (not `id`) and `expires_at` (not `token_hash`) — test helper updated to match actual schema
metrics:
  duration: "6 minutes"
  completed: "2026-03-06"
  tasks_completed: 3
  files_changed: 5
---

# Phase 20 Plan 01: Security Fixes Summary

Three surgical security hardening changes to the Fastify backend: IDOR fix on file deletion (ownership check before disk op), JWT_SECRET startup guard (process.exit(1) on absent/weak secret), and 90-day refresh token purge (startup + 24h interval).

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Fix DELETE /files/:filename ownership check + update tests | 3469d16 | backend/src/routes/files.ts, backend/src/routes/files.test.ts |
| 2 | purgeStaleRefreshTokens helper + unit tests | daf2ba0 | backend/src/refresh-token-purge.ts, backend/src/refresh-token-purge.test.ts |
| 3 | JWT_SECRET startup guard + purge wiring in startServer() | 934d7aa | backend/src/index.ts |

## Must-Haves Verified

- DELETE /api/files/:filename returns 403 when an authenticated user tries to delete another user's file — confirmed by `returns403_whenUserBDeletesUserAFile` test
- DELETE /api/files/:filename returns 404 when the file has no DB record (orphaned) — confirmed by `returns404_whenFileHasNoDbRecord` test
- DELETE /api/files/:filename still returns 200 when the authenticated owner deletes their own file — confirmed by updated `returns200_deletedTrue_onSuccess` test with seedFileRow
- Server exits with code 1 and logs FATAL message if JWT_SECRET absent or shorter than 32 characters — confirmed by code review of startServer() in index.ts
- Stale refresh tokens older than 90 days purged at startup and every 24 hours — confirmed by purge unit tests + startServer() wiring
- All existing backend tests still pass — 280/280 tests pass

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed wrong column names in insertRefreshToken test helper**
- **Found during:** Task 2 RED phase — SqliteError: table refresh_tokens has no column named id
- **Issue:** Plan spec described `refresh_tokens` columns as `(id, user_id, token_hash, created_at, device_id, revoked)` but the actual Drizzle schema uses `token` as the primary key (not `id`) and `expires_at` instead of `token_hash`
- **Fix:** Updated `insertRefreshToken` helper to use `(token, user_id, device_id, expires_at, created_at, revoked)`. Added `seedUser` helper called in `beforeEach` to satisfy the `user_id` foreign key constraint (foreign keys are ON in test DB via `createConnection`)
- **Files modified:** backend/src/refresh-token-purge.test.ts
- **Commit:** daf2ba0

## Test Results

```
Test Files: 26 passed (26)
Tests:      280 passed (280)
Duration:   ~9s
```

## Self-Check: PASSED
