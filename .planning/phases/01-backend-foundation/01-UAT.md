---
status: testing
phase: 01-backend-foundation
source: PLAN_PHASE1.md
started: 2026-03-02T00:00:00Z
updated: 2026-03-02T00:00:00Z
---

## Current Test

number: 1
name: All backend tests pass
expected: |
  Run `cd backend && pnpm test` from the project root. All test suites should run and exit 0 with zero failures. You should see green test results for: index.test.ts, vitest.test.ts, connection.test.ts, migrate.test.ts, hlc.test.ts (or hlc.ts tests), merge.test.ts, auth.test.ts (middleware), health.test.ts, and smoke.test.ts.
awaiting: user response

## Tests

### 1. All backend tests pass
expected: Run `cd backend && pnpm test` from the project root. All test suites should run and exit 0 with zero failures. You should see green test results for: index.test.ts, vitest.test.ts, connection.test.ts, migrate.test.ts, hlc.test.ts (or hlc.ts tests), merge.test.ts, auth.test.ts (middleware), health.test.ts, and smoke.test.ts.
result: [pending]

### 2. Health endpoint responds
expected: Start the backend dev server (`cd backend && node --import tsx/esm src/server.ts` or `pnpm dev` if configured). Then run `curl http://localhost:3000/health`. The response should be JSON with `"status":"ok"`, `"db":"ok"`, a `"version"` string, and an `"uptime"` number. HTTP status should be 200.
result: [pending]

### 3. Database migrations run automatically
expected: When the server starts (step above), a SQLite database file is created (at the path in .env or :memory: in tests). The schema should contain all 7 tables: `users`, `refresh_tokens`, `documents`, `nodes`, `bookmarks`, `settings`, `files`. You can verify with: `sqlite3 <db-path> ".tables"` or by checking that the drizzle migration SQL files exist at `backend/drizzle/*.sql`.
result: [pending]

### 4. Auth middleware rejects unauthenticated requests
expected: With the server running, call a protected endpoint (e.g., `curl http://localhost:3000/api/documents`). The response should be `{"error":"Unauthorized"}` with HTTP 401. No server crash should occur.
result: [pending]

### 5. Docker image builds
expected: From the project root, run `docker compose build`. The build should complete successfully (exit 0) in two stages: builder (compiles TypeScript) and runner (production image). No errors in the output.
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0

## Gaps

[none yet]
