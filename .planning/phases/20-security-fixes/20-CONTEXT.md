# Phase 20: Security fixes - Context

**Gathered:** 2026-03-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Close three security bugs in the backend:
1. `DELETE /api/files/:filename` — add ownership check so users can only delete their own files
2. `JWT_SECRET` startup guard — server refuses to start if the secret is absent or < 32 characters
3. Refresh token purge — background timer deletes stale tokens every 24 hours

No new features. Android app and web client are untouched.

</domain>

<decisions>
## Implementation Decisions

### DELETE /files/:filename ownership check
- Return **403 Forbidden** when user B tries to delete user A's file (matches the GET /files/:filename pattern — consistent behaviour)
- If the file exists on disk but has **no DB record** (orphaned), return **404** — can't verify ownership without a DB record, so deny
- The fix requires looking up the DB record first (before `rm()`) and checking `row.user_id === req.user!.id`
- New test in `files.test.ts`: authenticated user B cannot delete user A's file → expects 403

### Refresh token purge
- Purge criterion: `created_at < now − 90_days` — token age, not expiry offset
- Scope: purge **all stale tokens** (both expired-by-time and revoked) in a single `DELETE WHERE` clause
- Frequency: every **24 hours** via `setInterval` in `startServer()`
- Also called once at startup (alongside existing `purgeTombstones()`)

### JWT_SECRET startup guard
- Validation lives in **`startServer()` only** — `buildApp()` stays test-friendly (tests already set `process.env.JWT_SECRET`)
- If secret is absent or `< 32 characters`: log `FATAL: JWT_SECRET must be at least 32 characters` then `process.exit(1)`
- Plain `console.error` + `exit(1)` — no thrown Error, clean message visible in Docker logs

### Claude's Discretion
- Exact SQL for the purge query
- Whether to extract purge logic into a helper function (e.g. `purgeStaleRefreshTokens()`) or inline it
- Order of ownership check vs. disk `stat()` in the updated DELETE handler

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `purgeTombstones(sqlite)` in `backend/src/tombstone.ts`: pattern for a standalone purge function called at startup — same pattern to follow for `purgeStaleRefreshTokens()`
- `GET /files/:filename` ownership check (files.ts:188-193): exact pattern to replicate in DELETE — lookup row, check `row.user_id !== req.user!.id`, return 403

### Established Patterns
- `startServer()` (index.ts:118-147): already calls `purgeTombstones()` after migrations — good place to add JWT guard (before `buildApp()`) and schedule the refresh token purge interval
- `REFRESH_TOKEN_MS = 30 * 24 * 60 * 60 * 1000` in auth.ts — tokens are 30-day; 90-day purge threshold is `90 * 24 * 60 * 60 * 1000`
- `files.test.ts` has `seedUser()` and `seedFileRow()` helpers — cross-user ownership test can reuse these directly

### Integration Points
- DELETE handler in `files.ts:218-240` — add DB lookup + ownership check before the existing `stat()` + `rm()` calls
- `startServer()` in `index.ts:118` — add JWT_SECRET guard at top, add `setInterval` for purge after startup
- `files.test.ts` — add one new test in the `DELETE /api/files/:filename` describe block

</code_context>

<specifics>
## Specific Ideas

No specific references or examples — the success criteria and existing GET handler pattern fully specify all three fixes.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 20-security-fixes*
*Context gathered: 2026-03-06*
