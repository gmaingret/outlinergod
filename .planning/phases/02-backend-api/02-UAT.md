---
status: diagnosed
phase: 02-backend-api
source: PLAN_PHASE2.md
started: 2026-03-03T00:00:00Z
updated: 2026-03-03T06:20:00Z
---

## Current Test

[testing complete]

## Tests

### 1. All backend tests pass
expected: Run `cd backend && pnpm test` from the project root. Every test suite should exit 0 with zero failures. Expect passing tests for: auth.test.ts, documents.test.ts, nodes.test.ts, sync.test.ts, files.test.ts, settings.test.ts, bookmarks.test.ts, export.test.ts (if present), plus all Phase 1 tests.
result: pass

### 2. Server starts and health responds
expected: Start the server locally (`cd backend && node dist/server.js` or rebuild first with `pnpm build`). Then `curl http://localhost:3000/health` should return `{"status":"ok","db":"ok","version":"...","uptime":...}` with HTTP 200.
result: issue
reported: "pnpm: command not found — pnpm is not installed on the Docker host server, so `pnpm build` fails and the server cannot be started directly"
severity: blocker

### 3. Protected routes reject unauthenticated requests
expected: With server running, call `curl -i http://localhost:3000/api/documents`. Response should be HTTP 401 with body `{"error":"Unauthorized"}`. Same result for `curl -i http://localhost:3000/api/sync/changes`.
result: pass

### 4. Auth endpoint exists and validates input
expected: `curl -i -X POST http://localhost:3000/api/auth/google -H "Content-Type: application/json" -d "{}"` should return HTTP 400 with `{"error":"Missing id_token"}` (not 404). The endpoint exists and validates input even without a valid token.
result: pass

### 5. Auth refresh endpoint validates input
expected: `curl -i -X POST http://localhost:3000/api/auth/refresh -H "Content-Type: application/json" -d "{}"` should return HTTP 400 with `{"error":"Missing refresh_token"}` (not 404).
result: pass

### 6. Docker image builds
expected: From the project root, run `docker compose build`. Should complete successfully (exit 0) with both builder and runner stages completing without errors.
result: pass

### 7. Backend deployed and accessible on server
expected: The Docker container is running on the home server. Run `curl http://192.168.1.50:3000/health` (or the configured port). Should return `{"status":"ok","db":"ok","version":"...","uptime":...}` with HTTP 200. If not yet deployed, skip this test.
result: pass

## Summary

total: 7
passed: 6
issues: 1
pending: 0
skipped: 0

## Gaps

- truth: "Backend server can be started on the Docker host with `node dist/server.js` and responds to HTTP"
  status: failed
  reason: "User reported: pnpm: command not found — pnpm is not installed on the Docker host server, so `pnpm build` fails and the server cannot be started directly"
  severity: blocker
  test: 2
  root_cause: "Documentation gap, not a code bug. The server is designed to run exclusively via `docker compose up -d --build`. The Dockerfile is multi-stage: pnpm/tsc run only in the builder stage, dist/ is never committed to git, and the host has no pnpm. The UAT criterion tested a deployment path that was never intended. The correct deployment command is `docker compose up -d --build` which already works (confirmed by tests 3-7)."
  artifacts:
    - path: "backend/Dockerfile"
      issue: "Multi-stage build — pnpm only inside builder stage, no host-side build path intended"
    - path: "docker-compose.yml"
      issue: "Correct deployment mechanism exists and works, just undocumented for human operators"
  missing:
    - "Project-level README.md documenting two execution paths: dev (pnpm dev) and production (docker compose up -d --build)"
  debug_session: ""
