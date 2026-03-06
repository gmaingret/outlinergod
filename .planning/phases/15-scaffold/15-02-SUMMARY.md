---
phase: 15-scaffold
plan: 02
subsystem: infra
tags: [docker, dockerfile, multi-stage-build, react, vite, fastify, spa]

# Dependency graph
requires:
  - phase: 15-01
    provides: "Fastify SPA serving (WEB_DIST_PATH, @fastify/static wildcard:false + setNotFoundHandler)"
provides:
  - Three-stage Dockerfile (web-builder + ts-builder + runner) building React SPA into the backend image
  - Root .dockerignore excluding android/, .git/, .planning/ from Docker build context
  - docker-compose.yml with context:. and explicit dockerfile path
  - WEB_DIST_PATH=/app/web/dist in docker-compose.yml env section (fixes path resolution in container)
affects:
  - "Phase 16 Auth (container deployment path)"
  - "All future phases requiring production deployment"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Multi-stage Docker build: web-builder compiles React SPA, ts-builder compiles TypeScript, runner copies both artifacts"
    - "Root .dockerignore pattern: exclude large directories (android/, .git/) from build context"
    - "docker-compose context: project root with explicit dockerfile: backend/Dockerfile"
    - "WEB_DIST_PATH env var in docker-compose.yml overrides the relative path computed by index.ts at runtime"

key-files:
  created:
    - .dockerignore
  modified:
    - backend/Dockerfile
    - docker-compose.yml

key-decisions:
  - "D-DOCKER-01: Three-stage Dockerfile (web-builder, ts-builder, runner) replaces two-stage build — context moved to project root"
  - "D-DOCKER-02: All backend COPY paths prefixed with backend/ because context is now project root, not ./backend"
  - "D-DOCKER-03: web/dist/ excluded from .dockerignore — web-builder stage compiles from source, not pre-built artifacts"
  - "D-DOCKER-04: Root .dockerignore supersedes backend/.dockerignore (Docker reads from context root)"
  - "D-DOCKER-05: WEB_DIST_PATH=/app/web/dist added to docker-compose.yml environment — Fastify's path.join resolves relative to /app/dist/ in the container, giving the wrong /web/dist path without the override"

patterns-established:
  - "Pattern 1: Multi-stage Docker: web-builder stage outputs to /web/dist, runner copies to /app/web/dist; docker-compose sets WEB_DIST_PATH=/app/web/dist to override the runtime path.join resolution"

requirements-completed: [SETUP-01, SETUP-02, SETUP-03]

# Metrics
duration: 25min
completed: 2026-03-06
---

# Phase 15 Plan 02: Docker Three-Stage Build Summary

**Three-stage Dockerfile packaging React SPA (web-builder) + TypeScript backend (ts-builder) into a single production container (runner), verified serving both the React SPA and Fastify API on root@192.168.1.50**

## Performance

- **Duration:** ~25 min (including human-verify checkpoint)
- **Started:** 2026-03-06T08:19:40Z
- **Completed:** 2026-03-06T09:31:48Z
- **Tasks:** 2/2
- **Files modified:** 3 (+ docker-compose.yml updated again by user)

## Accomplishments
- Rewrote `backend/Dockerfile` from two-stage to three-stage build with correct project-root-relative COPY paths
- Updated `docker-compose.yml` to use `context: .` and `dockerfile: backend/Dockerfile`
- Created root `.dockerignore` excluding `android/`, `.git/`, `.planning/`, `node_modules/`, `dist/` directories
- All 5 production curl checks passed on root@192.168.1.50 — container serves React SPA and Fastify API
- `WEB_DIST_PATH=/app/web/dist` env var added to docker-compose.yml to fix runtime path resolution in Docker

## Task Commits

1. **Task 1: Three-stage Dockerfile + .dockerignore + docker-compose.yml** - `2a335c9` (feat)
2. **Task 1 fix: WEB_DIST_PATH env var for Docker path resolution** - `b887d62` (fix, committed by user during verification)

**Plan metadata:** `28e82cc` (docs: checkpoint), final update below

## Files Created/Modified
- `backend/Dockerfile` - Three-stage build: web-builder (Vite/React), ts-builder (TypeScript/Fastify), runner (production image with both artifacts)
- `docker-compose.yml` - Context changed from `./backend` to `.`, explicit `dockerfile: backend/Dockerfile`, `WEB_DIST_PATH=/app/web/dist` added to environment
- `.dockerignore` - Root-level exclusions: android/, .git/, .planning/, .claude/, node_modules/, dist/

## Decisions Made
- D-DOCKER-01: Three-stage Dockerfile with project-root context replaces two-stage build
- D-DOCKER-02: All backend COPY paths now prefixed `backend/` due to context change
- D-DOCKER-03: `web/dist/` excluded from .dockerignore — always built from source inside Docker
- D-DOCKER-04: Root .dockerignore supersedes `backend/.dockerignore`
- D-DOCKER-05: WEB_DIST_PATH=/app/web/dist in docker-compose.yml env — path.join in index.ts resolves relative to /app/dist/ in the container, producing /web/dist (wrong) without the override

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Docker not in shell PATH — automated build verification skipped**
- **Found during:** Task 1 verification
- **Issue:** `docker` binary not available in the bash shell used by Claude Code
- **Fix:** Deferred to Task 2 human-verify checkpoint — user ran build + curl checks directly
- **Files modified:** None
- **Impact:** All file changes were correct; verification was the purpose of the checkpoint

**2. [Rule 1 - Bug] WEB_DIST_PATH missing from docker-compose.yml environment**
- **Found during:** Task 2 human-verify (production deployment)
- **Issue:** `path.join(__dirname, '../../web/dist')` in `backend/src/index.ts` resolves to `/web/dist` inside the container (where `__dirname` is `/app/dist`), but the React SPA is copied to `/app/web/dist`. Fastify could not find the static files.
- **Fix:** Added `WEB_DIST_PATH=/app/web/dist` to the `environment` section in `docker-compose.yml` — the env var overrides the computed path at startup
- **Files modified:** `docker-compose.yml`
- **Verification:** `curl http://localhost:3000/` returns React SPA HTML; `curl http://localhost:3000/foo/bar` returns same HTML (deep-link fallback confirmed)
- **Committed in:** `b887d62` (committed by user during verification)

---

**Total deviations:** 2 (1 environment constraint, 1 bug fix)
**Impact on plan:** Bug fix was required for correctness — container would have served API-only without the WEB_DIST_PATH override. No scope creep.

## Issues Encountered
- Docker CLI not in bash PATH in this shell environment — all Docker operations performed manually by user.
- Note on `/api/health` 404: the health route is registered at `/health` (no `/api` prefix by design). `/api/health` returning 404 is expected and correct. The check `curl http://localhost:3000/api/health` returns 404 JSON (not HTML), confirming the `/api/*` guard in setNotFoundHandler is working correctly.

## User Setup Required

None — production deployment already performed at `root@192.168.1.50:/root/outlinergod`. Container is running.

## Next Phase Readiness
- Phase 15 complete. All SETUP requirements satisfied: SETUP-01, SETUP-02, SETUP-03.
- Single container serves React SPA at all non-/api paths + Fastify API at /api/* and /health
- Proceed to Phase 16 Auth

---
*Phase: 15-scaffold*
*Completed: 2026-03-06*
