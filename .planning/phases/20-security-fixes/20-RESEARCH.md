# Phase 20: Security fixes - Research

**Researched:** 2026-03-06
**Domain:** Node.js/Fastify backend — authorization hardening, startup validation, background cleanup
**Confidence:** HIGH

## Summary

Phase 20 closes three discrete security bugs in the existing Fastify backend. All three fixes are surgical: they touch two files (`backend/src/routes/files.ts` and `backend/src/index.ts`) and add one new file (`backend/src/refresh-token-purge.ts` or inline helper). No new dependencies are required. No Android code, web client code, or database schema changes are involved.

The existing codebase already contains the exact patterns each fix should replicate. The `GET /files/:filename` ownership check (files.ts:188-193) is a word-for-word template for the DELETE ownership check. The `purgeTombstones()` function in `tombstone.ts` is the template for `purgeStaleRefreshTokens()`. The `startServer()` pattern for calling `purgeTombstones()` after migrations is the exact insertion point for both the JWT guard and the setInterval.

**Primary recommendation:** Treat each bug as a one-file edit. All three can ship in a single plan (one task, one commit) because they are independent and non-overlapping.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**DELETE /files/:filename ownership check**
- Return 403 Forbidden when user B tries to delete user A's file (consistent with GET /files/:filename)
- If the file exists on disk but has no DB record (orphaned), return 404 — can't verify ownership without a DB record
- Fix: look up DB record first (before `rm()`), check `row.user_id === req.user!.id`
- New test in `files.test.ts`: authenticated user B cannot delete user A's file — expects 403

**Refresh token purge**
- Purge criterion: `created_at < now - 90_days` (token age, not expiry offset)
- Scope: purge all stale tokens (both expired-by-time and revoked) in a single DELETE WHERE
- Frequency: every 24 hours via `setInterval` in `startServer()`
- Also called once at startup (alongside existing `purgeTombstones()`)

**JWT_SECRET startup guard**
- Validation lives in `startServer()` only — `buildApp()` stays test-friendly
- If secret is absent or < 32 characters: log `FATAL: JWT_SECRET must be at least 32 characters` then `process.exit(1)`
- Plain `console.error` + `exit(1)` — no thrown Error, clean message visible in Docker logs

### Claude's Discretion
- Exact SQL for the purge query
- Whether to extract purge logic into a helper function (e.g. `purgeStaleRefreshTokens()`) or inline it
- Order of ownership check vs. disk `stat()` in the updated DELETE handler

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| better-sqlite3 | 12.6.2 | Synchronous SQLite driver — already in use | Project-standard, all DB calls use it |
| Fastify v5 | 5.x | HTTP framework | CLAUDE.md mandates Fastify v5; no Express |
| Vitest | ^2 | Test runner | CLAUDE.md mandates vitest@^2 for backend |
| jose | already installed | JWT signing | CLAUDE.md mandates `jose` |
| node:fs/promises | built-in | File stat/rm | Already used in files.ts |

### No New Dependencies Required
All three fixes use code patterns and imports already present in the codebase. Zero new packages needed.

### Alternatives Considered
None applicable — all decisions are locked by CONTEXT.md.

## Architecture Patterns

### Pattern 1: Ownership check before destructive operation (DELETE handler)

The existing GET handler (files.ts:186-213) demonstrates the correct check order:
1. Validate filename pattern (400 if invalid)
2. Query DB for row by filename
3. If row exists and `row.user_id !== req.user!.id` → 403
4. Attempt disk `stat()` — if fails → 404
5. If no DB row → 404 (can't verify ownership = treat as not-found)
6. Proceed with operation

The DELETE handler currently skips steps 2-3 and 5. The fix inserts them before the existing `stat()` call.

**Correct order for DELETE (per CONTEXT.md decision):**
```
1. Validate filename pattern → 400
2. DB lookup (SELECT * FROM files WHERE filename = ?)
3. If row exists and row.user_id !== req.user!.id → 403   ← NEW
4. stat(fullPath) → 404 if disk miss
5. If no row → 404 (orphaned file, no ownership proof)     ← NEW (move existing guard here)
6. rm(fullPath)
7. DELETE FROM files WHERE filename = ?
8. 200 { deleted: true }
```

Note: CONTEXT.md says "orphaned file (exists on disk but no DB record) → 404". The current DELETE handler removes DB-orphan files without restriction. The fix closes this by requiring a DB record before deletion is allowed.

### Pattern 2: Standalone purge helper (tombstone.ts pattern)

`purgeTombstones()` is the reference implementation:
- Accepts `sqlite: InstanceType<typeof Database>`
- Returns `void`
- Uses synchronous `.prepare(...).run(cutoff)` — no async
- Logs changes via `console.log`
- Called from `startServer()` after migrations

`purgeStaleRefreshTokens()` should follow identical structure:
```typescript
export function purgeStaleRefreshTokens(sqlite: InstanceType<typeof Database>): void {
  const cutoff = Date.now() - 90 * 24 * 60 * 60 * 1000
  const result = sqlite
    .prepare('DELETE FROM refresh_tokens WHERE created_at < ?')
    .run(cutoff)
  if (result.changes > 0) {
    console.log(`Purged ${result.changes} stale refresh tokens`)
  }
}
```

The SQL clause `WHERE created_at < ?` covers both expired-by-time tokens and revoked tokens (per CONTEXT.md: "purge all stale tokens" in a single clause — no need to filter by `revoked`).

### Pattern 3: JWT_SECRET guard in startServer()

`startServer()` (index.ts:118-147) currently begins by creating the uploads directory. The JWT guard inserts before `buildApp()` is called, after migrations complete:

```typescript
export async function startServer(port: number, dbPath: string): Promise<void> {
  // ... mkdirSync(uploadsPath) ...
  const sqlite = createConnection(dbPath)
  try {
    await runMigrations(sqlite)
  } catch (err) { ... process.exit(1) }

  // ← JWT guard goes here (after migrations, before buildApp)
  const jwtSecret = process.env.JWT_SECRET ?? ''
  if (jwtSecret.length < 32) {
    console.error('FATAL: JWT_SECRET must be at least 32 characters')
    process.exit(1)
  }

  purgeTombstones(sqlite)
  purgeStaleRefreshTokens(sqlite)  // ← startup call
  setInterval(() => purgeStaleRefreshTokens(sqlite), 24 * 60 * 60 * 1000)  // ← 24h interval

  const app = buildApp(sqlite)
  // ...
}
```

`buildApp()` is NOT modified — it must remain test-friendly. Tests set `process.env.JWT_SECRET` in `beforeEach` and call `buildApp()` directly without going through `startServer()`.

### Pattern 4: Cross-user test in files.test.ts

The test file already has `seedUser()` and `seedFileRow()` helpers, plus `tokenA`/`tokenB` JWT tokens in `beforeEach`. The new test is:

```typescript
it('returns403_whenUserBDeletesUserAFile', async () => {
  seedFileRow(sqlite, validFilename, 'user-a')  // file owned by user-a

  const res = await app.inject({
    method: 'DELETE',
    url: `/api/files/${validFilename}`,
    headers: { authorization: `Bearer ${tokenB}` },  // user-b's token
  })

  expect(res.statusCode).toBe(403)
  expect(res.json()).toEqual({ error: 'Forbidden' })
  expect(vi.mocked(rm)).not.toHaveBeenCalled()  // file must NOT be deleted
})
```

The `rm` mock assertion is important: it verifies the handler aborts before touching disk.

### Anti-Patterns to Avoid

- **Modifying `buildApp()`**: The JWT guard must only live in `startServer()`. Adding it to `buildApp()` breaks all tests that don't set a 32-char secret.
- **Checking `revoked` in purge SQL**: CONTEXT.md says purge by `created_at` age only — a single clause covering all old tokens regardless of revocation state.
- **Calling `stat()` before DB lookup in DELETE**: The ownership check must happen before the disk operation. Reversing order allows timing-based information leakage (500 vs 403 depending on disk state).
- **Throwing an Error for JWT guard**: CONTEXT.md specifies `console.error` + `process.exit(1)`, not a thrown Error. Docker logs show the message; a throw would produce a stack trace instead.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Ownership verification | Custom session/ACL system | Single DB SELECT + user_id comparison | Pattern already exists in GET handler |
| Token cleanup schedule | cron library, node-schedule | `setInterval` in `startServer()` | Simple, no deps, fits existing pattern |
| Secret validation | Third-party env validator | `process.env.JWT_SECRET?.length < 32` + `process.exit(1)` | Two lines of vanilla JS |

## Common Pitfalls

### Pitfall 1: DELETE test currently passes without DB seed

The existing `returns200_deletedTrue_onSuccess` test in `files.test.ts` does NOT call `seedFileRow()` — it relies on `stat` being mocked to succeed and the DB `DELETE WHERE filename = ?` being a no-op. After the fix, this test will fail because the DB lookup will find no row and return 404.

**How to avoid:** Update `returns200_deletedTrue_onSuccess` to call `seedFileRow(sqlite, validFilename, 'user-a')` and use `tokenA` — verifying the happy path where the authenticated owner deletes their own file.

**Warning sign:** Running `pnpm test` after the fix without updating the existing DELETE test produces a 404 where the test expects 200.

### Pitfall 2: The 404-for-orphan case needs a separate test update

`returns404_whenFileNotFound` mocks `stat` to reject with ENOENT. With the fix, the DB lookup happens first. If no `seedFileRow` is called, the handler returns 404 before ever calling `stat`. The test still passes by accident (404 is returned, just from the DB branch instead of the disk branch). This is correct behavior but the test no longer exercises the stat-fail path. Optionally add a companion test that seeds the row but leaves the disk mock failing.

### Pitfall 3: setInterval holds the process open in tests

If any test ever calls `startServer()` directly (none currently do), the `setInterval` would prevent the test process from exiting cleanly. Since all tests use `buildApp()` directly, this is not a current issue. For safety, the interval could store the `NodeJS.Timeout` return value, but given no tests call `startServer()`, this is low priority.

### Pitfall 4: `created_at < ?` uses epoch milliseconds (not seconds)

The `refresh_tokens` table stores `created_at` as `integer` epoch milliseconds (same convention as all other tables). The purge cutoff must use `Date.now() - 90 * 24 * 60 * 60 * 1000`, not `Date.now() / 1000 - 90 * 86400`. Mixing ms/s units is a silent bug that either purges everything or nothing.

**Verification:** `REFRESH_TOKEN_MS = 30 * 24 * 60 * 60 * 1000` in auth.ts confirms ms convention.

## Code Examples

### DELETE handler fix (files.ts:218-240 replacement)

```typescript
// Source: mirrors GET /files/:filename pattern at files.ts:179-213
fastify.delete('/files/:filename', { preHandler: requireAuth }, async (req, reply) => {
  const { filename } = req.params as { filename: string }
  const uploadsPath = process.env.UPLOADS_PATH ?? '/tmp/uploads'

  if (!FILENAME_PATTERN.test(filename)) {
    return reply.status(400).send({ error: 'Invalid filename' })
  }

  // DB lookup first — needed to verify ownership before touching disk
  const row = sqlite
    .prepare('SELECT * FROM files WHERE filename = ?')
    .get(filename) as { filename: string; user_id: string } | undefined

  // Ownership check: another user's file → 403
  if (row && row.user_id !== req.user!.id) {
    return reply.status(403).send({ error: 'Forbidden' })
  }

  const fullPath = join(uploadsPath, filename)

  try {
    await stat(fullPath)
  } catch {
    return reply.status(404).send({ error: 'File not found' })
  }

  // No DB record = orphaned file, can't verify ownership → deny
  if (!row) {
    return reply.status(404).send({ error: 'File not found' })
  }

  await rm(fullPath)
  sqlite.prepare('DELETE FROM files WHERE filename = ?').run(filename)

  return reply.status(200).send({ deleted: true })
})
```

### purgeStaleRefreshTokens helper

```typescript
// New file: backend/src/refresh-token-purge.ts (or inline in tombstone.ts)
import type Database from 'better-sqlite3'

export function purgeStaleRefreshTokens(sqlite: InstanceType<typeof Database>): void {
  const cutoff = Date.now() - 90 * 24 * 60 * 60 * 1000
  const result = sqlite
    .prepare('DELETE FROM refresh_tokens WHERE created_at < ?')
    .run(cutoff)
  if (result.changes > 0) {
    console.log(`Purged ${result.changes} stale refresh tokens`)
  }
}
```

### startServer() additions (index.ts)

```typescript
// After runMigrations() success block, before buildApp():
const jwtSecret = process.env.JWT_SECRET ?? ''
if (jwtSecret.length < 32) {
  console.error('FATAL: JWT_SECRET must be at least 32 characters')
  process.exit(1)
}

purgeTombstones(sqlite)
purgeStaleRefreshTokens(sqlite)
setInterval(() => purgeStaleRefreshTokens(sqlite), 24 * 60 * 60 * 1000)
```

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| DELETE allows any authenticated user to delete any file | DELETE checks ownership via DB lookup before disk op | IDOR (Insecure Direct Object Reference) closed |
| JWT_SECRET silently defaults to empty in Docker misconfiguration | Server refuses to start if secret is absent or weak | Prevents silent JWT signature bypass |
| Refresh tokens accumulate forever in DB | 90-day-old tokens are purged on startup + every 24h | Keeps DB size bounded, matches tombstone cleanup pattern |

## Open Questions

1. **File location for `purgeStaleRefreshTokens`**
   - What we know: tombstone.ts is the established pattern file for startup purge helpers
   - What's unclear: whether to add it to tombstone.ts or create a new file (e.g. `auth-purge.ts`)
   - Recommendation: Claude's discretion per CONTEXT.md — both are valid; a new file named `refresh-token-purge.ts` keeps tombstone.ts focused on node/document/bookmark cleanup

2. **Existing DELETE test breakage**
   - What we know: `returns200_deletedTrue_onSuccess` doesn't seed a DB row, will 404 after fix
   - What's unclear: whether this is considered a test update or a test fix
   - Recommendation: treat as a necessary test update alongside the feature fix; add `seedFileRow(sqlite, validFilename, 'user-a')` to that test's setup

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Vitest ^2 |
| Config file | `backend/vitest.config.ts` (or `backend/package.json` vitest field) |
| Quick run command | `cd backend && pnpm test -- --reporter=verbose files.test.ts` |
| Full suite command | `cd backend && pnpm test` |

### Phase Requirements → Test Map

| Behavior | Test Type | Automated Command | File Exists? |
|----------|-----------|-------------------|-------------|
| DELETE returns 403 when user B deletes user A's file | unit (integration via inject) | `pnpm test -- files.test.ts` | ❌ New test needed in existing file |
| DELETE still returns 200 for file owner | unit | `pnpm test -- files.test.ts` | ✅ Exists — needs seedFileRow added |
| DELETE returns 404 for orphaned (no DB record) file | unit | `pnpm test -- files.test.ts` | ❌ New test needed |
| Server startup fails if JWT_SECRET absent | integration | Not easily automatable (requires process.exit mock) | ❌ New test in index.test.ts or startServer.test.ts |
| Server startup fails if JWT_SECRET < 32 chars | integration | Same | ❌ New test |
| purgeStaleRefreshTokens deletes tokens older than 90 days | unit | `pnpm test -- tombstone.test.ts` or new file | ❌ New test file |
| purgeStaleRefreshTokens does not delete recent tokens | unit | Same | ❌ New test |
| All existing backend tests still pass | regression | `pnpm test` | ✅ Existing suite |

### Sampling Rate
- **Per task commit:** `cd backend && pnpm test -- files.test.ts`
- **Per wave merge:** `cd backend && pnpm test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] New `it('returns403...')` block in `backend/src/routes/files.test.ts`
- [ ] Update `returns200_deletedTrue_onSuccess` to seed DB row (otherwise it 404s after fix)
- [ ] New test(s) for `purgeStaleRefreshTokens` — either in `tombstone.test.ts` or a new `refresh-token-purge.test.ts`
- [ ] Optional: test for JWT_SECRET guard in `startServer()` — may require mocking `process.exit`

## Sources

### Primary (HIGH confidence)
- Direct code inspection of `backend/src/routes/files.ts` — GET handler at lines 179-213, DELETE handler at lines 218-240
- Direct code inspection of `backend/src/index.ts` — `startServer()` at lines 118-147
- Direct code inspection of `backend/src/tombstone.ts` — `purgeTombstones()` pattern
- Direct code inspection of `backend/src/routes/auth.ts` — `REFRESH_TOKEN_MS`, refresh_tokens schema usage
- Direct code inspection of `backend/src/db/schema.ts` — `refreshTokens` table definition (`created_at` as integer epoch ms)
- Direct code inspection of `backend/src/routes/files.test.ts` — `seedUser()`, `seedFileRow()`, existing DELETE tests

### Secondary (MEDIUM confidence)
- CONTEXT.md — all locked decisions verified against code inspection

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies, all patterns directly observed in codebase
- Architecture: HIGH — exact insertion points identified with line numbers, patterns are verbatim replication
- Pitfalls: HIGH — test breakage is confirmed by reading existing test assertions
- SQL correctness: HIGH — `created_at` confirmed as integer epoch ms across all tables

**Research date:** 2026-03-06
**Valid until:** N/A — pure code inspection, not library-version-dependent
