---
phase: 02-backend-api
verified: 2026-03-03
status: code_confirmed_doc_gap
score: "6/7 UAT tests passed"
note: "Single failure (UAT test 2) is a documentation gap, not a code bug. Resolved by project README.md."
---

# Phase 02: Backend API - Verification Report

**Phase Goal:** Complete REST API surface (documents, nodes, sync, files, settings, bookmarks), integration tests, and Docker deployment
**Verified:** 2026-03-03
**Status:** code_confirmed_doc_gap
**Re-verification:** No - this is a retrospective stub archiving actual UAT results from 02-UAT.md

---

## UAT Results

| # | Test | Result | Notes |
|---|------|--------|-------|
| 1 | All backend tests pass (`pnpm test`) | PASS | All test suites exit 0; auth.test.ts, documents.test.ts, nodes.test.ts, sync.test.ts, files.test.ts, settings.test.ts, bookmarks.test.ts, plus all Phase 01 tests |
| 2 | Server starts and health responds (`pnpm build` then `node dist/server.js`) | ISSUE | `pnpm: command not found` on Docker host - see gap analysis below |
| 3 | Protected routes reject unauthenticated requests (HTTP 401) | PASS | `curl -i http://localhost:3000/api/documents` returned HTTP 401 `{"error":"Unauthorized"}` |
| 4 | Auth endpoint exists and validates input | PASS | POST `/api/auth/google` with empty body returned HTTP 400 `{"error":"Missing id_token"}` (not 404) |
| 5 | Auth refresh endpoint validates input | PASS | POST `/api/auth/refresh` with empty body returned HTTP 400 `{"error":"Missing refresh_token"}` (not 404) |
| 6 | Docker image builds | PASS | `docker compose build` completed successfully; both builder and runner stages completed without errors |
| 7 | Backend deployed and accessible on server | PASS | `curl http://192.168.1.50:3000/health` returned `{"status":"ok","db":"ok","version":"...","uptime":...}` with HTTP 200 |

**Score: 6/7 tests passed**

---

## Gap Analysis: UAT Test 2

**Test criterion:** "Start the server locally (`cd backend && node dist/server.js` or rebuild first with `pnpm build`). Then `curl http://localhost:3000/health`..."

**Failure report:** "pnpm: command not found - pnpm is not installed on the Docker host server, so `pnpm build` fails and the server cannot be started directly"

**Root cause:** Documentation gap, not a code bug.

The Dockerfile uses a multi-stage build: the `builder` stage installs pnpm and runs `tsc` to compile TypeScript; the compiled `dist/` output is copied into the lean `runner` stage. The `dist/` directory is never committed to git (it is gitignored). The Docker host therefore has no `dist/server.js` to run directly, and installing pnpm on the host to build manually was never the intended deployment path.

The UAT criterion tested a deployment path that was never intended. The correct and only supported production deployment command is:

```
docker compose up -d --build
```

This command handles the full build pipeline inside Docker and then launches the container. Tests 3-7 all passed, confirming the backend code is fully functional via this path.

**Affected artifacts:**
- `backend/Dockerfile`: Multi-stage build; pnpm only inside builder stage, no host-side build path intended
- `docker-compose.yml`: Correct deployment mechanism exists and works, but was undocumented for human operators

**Resolution:** Project-level `README.md` (created in plan 06-02, Task 2) explicitly documents:
1. Development path: `cd backend && pnpm install && pnpm dev` (requires pnpm)
2. Production path: `docker compose up -d --build` (sole supported production path; does NOT require pnpm on host)
3. Why `pnpm build` fails on Docker host: the multi-stage build keeps pnpm inside the builder stage only

---

## Verdict

Phase 02 code is fully functional. All 7 REST API endpoint groups (auth, documents, nodes, sync, files, settings, bookmarks) pass their integration tests and respond correctly from the Docker container running on the home server.

The single gap (UAT test 2) is a documentation issue: the test criterion described a deployment path that was never supported. The project README.md created in plan 06-02 closes this gap by making the Docker-only production deployment path explicit and explaining why host-side `pnpm build` is not a valid path.

---

_Stub created: 2026-03-03_
_Author: Claude (gsd-executor)_
