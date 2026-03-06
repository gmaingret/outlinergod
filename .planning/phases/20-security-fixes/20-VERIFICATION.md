---
phase: 20-security-fixes
verified: 2026-03-06T18:05:00Z
status: passed
score: 6/6 must-haves verified
re_verification: false
---

# Phase 20: Security Fixes Verification Report

**Phase Goal:** Close all security bugs identified in the codebase audit — no authenticated user can delete another user's files, JWT_SECRET is validated at startup, and stale refresh tokens are periodically purged.
**Verified:** 2026-03-06T18:05:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #   | Truth                                                                                                    | Status     | Evidence                                                                                                          |
| --- | -------------------------------------------------------------------------------------------------------- | ---------- | ----------------------------------------------------------------------------------------------------------------- |
| 1   | DELETE /api/files/:filename returns 403 when an authenticated user tries to delete another user's file   | VERIFIED   | `files.ts` lines 231-233: ownership check `row.user_id !== req.user!.id` returns 403 before any disk op          |
| 2   | DELETE /api/files/:filename returns 404 when the file has no DB record (orphaned file on disk)           | VERIFIED   | `files.ts` lines 244-246: after stat() succeeds, `if (!row)` guard returns 404                                   |
| 3   | DELETE /api/files/:filename returns 200 when the authenticated owner deletes their own file              | VERIFIED   | `files.ts` lines 248-252: `rm(fullPath)` + DB delete + `{ deleted: true }` on 200; test seeds row with `seedFileRow` |
| 4   | Server exits with code 1 and logs a FATAL message if JWT_SECRET absent or shorter than 32 characters    | VERIFIED   | `index.ts` lines 136-140: `jwtSecret.length < 32` → `console.error('FATAL: JWT_SECRET must be at least 32 characters')` + `process.exit(1)` |
| 5   | Stale refresh tokens (older than 90 days) are purged once at startup and every 24 hours thereafter      | VERIFIED   | `index.ts` lines 147-148: `purgeStaleRefreshTokens(sqlite)` + `setInterval(..., 24 * 60 * 60 * 1000)`           |
| 6   | All existing backend tests still pass after these changes                                                | VERIFIED   | Live test run: 26 test files, 280 tests, 0 failures                                                              |

**Score:** 6/6 truths verified

### Required Artifacts

| Artifact                                          | Expected                                             | Status     | Details                                                                                      |
| ------------------------------------------------- | ---------------------------------------------------- | ---------- | -------------------------------------------------------------------------------------------- |
| `backend/src/routes/files.ts`                     | DELETE handler with ownership check                  | VERIFIED   | Lines 218-253: complete implementation with ownership-first order; no stubs                  |
| `backend/src/routes/files.test.ts`                | Cross-user DELETE 403, orphaned-file 404, owner 200  | VERIFIED   | Lines 511-537: `returns403_whenUserBDeletesUserAFile` + `returns404_whenFileHasNoDbRecord` present and substantive |
| `backend/src/refresh-token-purge.ts`              | Exports `purgeStaleRefreshTokens()`                  | VERIFIED   | 16-line file; exports function; follows tombstone.ts pattern exactly                         |
| `backend/src/refresh-token-purge.test.ts`         | 4 boundary tests for purge behavior                  | VERIFIED   | Lines 42-100: all 4 tests present (91-day old deleted, 1-day retained, mixed, boundary)      |
| `backend/src/index.ts`                            | JWT guard + startup purge + interval in startServer() | VERIFIED  | Lines 135-148: guard before `buildApp()`, startup call, 24h setInterval                     |

### Key Link Verification

| From                                    | To                                             | Via                                               | Status  | Details                                                                               |
| --------------------------------------- | ---------------------------------------------- | ------------------------------------------------- | ------- | ------------------------------------------------------------------------------------- |
| `index.ts startServer()`                | `refresh-token-purge.ts purgeStaleRefreshTokens()` | direct call at startup + setInterval             | WIRED   | Line 21: import present; lines 147-148: called at startup and every 24h               |
| `files.ts DELETE handler`               | `files` table                                  | SELECT before stat() — ownership verified before disk op | WIRED | Lines 227-233: `SELECT * FROM files WHERE filename = ?` then ownership check, then stat() |

**Ownership-first ordering confirmed:** DB lookup (line 227) precedes `stat()` call (line 237). A cross-user request is rejected at line 233 before any disk enumeration occurs.

### Requirements Coverage

The PLAN frontmatter declares requirements SEC-01, SEC-02, SEC-03. These IDs do not exist in `.planning/REQUIREMENTS.md` — the ROADMAP.md entry for phase 20 notes `(security audit)` in place of formal requirement IDs, indicating these were defined as internal audit findings rather than tracked requirements. No orphaned REQUIREMENTS.md entries map to phase 20.

| Requirement | Source Plan | Description                                         | Status         | Evidence                                                                 |
| ----------- | ----------- | --------------------------------------------------- | -------------- | ------------------------------------------------------------------------ |
| SEC-01      | 20-01-PLAN  | IDOR: cross-user file deletion                      | SATISFIED      | Ownership check in DELETE handler + 403 test passing                     |
| SEC-02      | 20-01-PLAN  | JWT_SECRET startup guard                            | SATISFIED      | `jwtSecret.length < 32` guard in `startServer()` before `buildApp()`    |
| SEC-03      | 20-01-PLAN  | Refresh token accumulation (90-day purge)           | SATISFIED      | `purgeStaleRefreshTokens()` called at startup and on 24h interval        |

Note: SEC-01/02/03 are not registered in `.planning/REQUIREMENTS.md`. They are local to the phase plan and represent audit findings, not product requirements. No REQUIREMENTS.md cross-reference gap exists.

### Anti-Patterns Found

No blockers or warnings found.

The `return null` occurrences in `files.ts` (lines 52, 57, 65, 71, 87, 111) are all inside `handleFileUpload()` — legitimate early returns after sending an error response (400, 413, 404). Not stubs.

No TODO/FIXME/placeholder comments in any phase-modified file.

### Human Verification Required

**JWT_SECRET enforcement — runtime behavior**

- Test: Deploy with a JWT_SECRET shorter than 32 characters (e.g., `JWT_SECRET=short`) and start the Docker container
- Expected: Container exits immediately; `docker logs` shows `FATAL: JWT_SECRET must be at least 32 characters`; container does not accept connections
- Why human: `process.exit(1)` in `startServer()` cannot be exercised by backend unit tests (tests call `buildApp()` directly and set `JWT_SECRET` in `beforeEach`). The guard works as written but the actual container-exit behavior requires a live deployment test.

### Gaps Summary

No gaps. All six must-have truths are verified with substantive implementation and real test coverage. The test suite confirms 280/280 tests passing across 26 test files. Both key links are wired — the ownership check precedes disk operations in the DELETE handler, and `purgeStaleRefreshTokens` is imported and called correctly in `startServer()`.

The only item flagged for human verification is the JWT_SECRET container-exit behavior, which is an operational check rather than a code gap — the implementation is correct and the logic is straightforward.

---

_Verified: 2026-03-06T18:05:00Z_
_Verifier: Claude (gsd-verifier)_
