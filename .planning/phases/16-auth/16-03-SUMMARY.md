---
phase: 16-auth
plan: 03
subsystem: auth
tags: [react, react-router, google-oauth, docker, vite, tailwind]

# Dependency graph
requires:
  - phase: 16-auth-01
    provides: "@react-oauth/google, react-router-dom, vitest test scaffold, ProtectedRoute.test.tsx"
  - phase: 16-auth-02
    provides: "AuthProvider, useAuth() hook, login/logout, silent refresh via refresh_token in localStorage"
provides:
  - "ProtectedRoute component (isLoading spinner, Navigate to /login, Outlet pattern)"
  - "LoginPage with Google sign-in card, error display, error-clears-on-retry"
  - "App.tsx: BrowserRouter + public /login + protected / and /editor/:id routes"
  - "main.tsx: GoogleOAuthProvider(VITE_GOOGLE_CLIENT_ID) > AuthProvider > App"
  - "Dockerfile web-builder: ARG + ENV VITE_GOOGLE_CLIENT_ID baked before pnpm run build"
  - "docker-compose.yml: build.args VITE_GOOGLE_CLIENT_ID forwarded from shell env"
affects: [17-documents, 18-editor]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "React Router v7 Outlet pattern for layout/guard routes"
    - "ProtectedRoute checks isLoading before redirecting (prevents flash-to-login on refresh)"
    - "Vite env var baked at Docker build time via ARG + ENV in web-builder stage"

key-files:
  created:
    - web/src/components/ProtectedRoute.tsx
    - web/src/components/LoginPage.tsx
    - web/.gitignore
  modified:
    - web/src/App.tsx
    - web/src/main.tsx
    - web/tsconfig.app.json
    - backend/Dockerfile
    - docker-compose.yml

key-decisions:
  - "D-WEB-08: tsconfig.app.json excludes test files — prevents tsc build failure from global.fetch (no @types/node in app tsconfig)"
  - "D-WEB-09: ProtectedRoute checks isLoading=true first, then accessToken=null — ensures silent refresh completes before any redirect"

patterns-established:
  - "Auth guard: isLoading → spinner, no accessToken → Navigate replace, accessToken → Outlet"
  - "LoginPage error: setError(null) on new attempt before await, setError on catch and onError"

requirements-completed: [AUTH-01, AUTH-02, AUTH-03]

# Metrics
duration: 15min
completed: 2026-03-06
---

# Phase 16 Plan 03: Auth Routing + Docker Wiring Summary

**ProtectedRoute guard + LoginPage with Google sign-in + App.tsx router + VITE_GOOGLE_CLIENT_ID baked into Docker via ARG/ENV**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-03-06T10:20:00Z
- **Completed:** 2026-03-06T10:35:00Z
- **Tasks:** 2 auto + 1 human-verify checkpoint
- **Files modified:** 7

## Accomplishments
- ProtectedRoute: isLoading spinner prevents flash-to-login, accessToken guard with Navigate replace, Outlet for authenticated content
- LoginPage: centered card with Google sign-in button, inline error display, error clears on retry attempt
- App.tsx: full BrowserRouter with /login public route, ProtectedRoute wrapping / and /editor/:id, catch-all to /
- main.tsx: GoogleOAuthProvider(VITE_GOOGLE_CLIENT_ID) > AuthProvider > App hierarchy
- Dockerfile updated to bake VITE_GOOGLE_CLIENT_ID at build time (ARG + ENV before pnpm run build)
- docker-compose.yml passes VITE_GOOGLE_CLIENT_ID from shell env to Docker build args

## Task Commits

Each task was committed atomically:

1. **Task 1: ProtectedRoute + LoginPage + App.tsx + main.tsx** - `7c9feb5` (feat)
2. **Task 2: Docker and env plumbing for VITE_GOOGLE_CLIENT_ID** - `426e73c` (feat)

## Files Created/Modified
- `web/src/components/ProtectedRoute.tsx` - Auth guard with isLoading spinner + Navigate to /login + Outlet
- `web/src/components/LoginPage.tsx` - Google sign-in card with error handling
- `web/src/App.tsx` - BrowserRouter with public /login + ProtectedRoute layout
- `web/src/main.tsx` - GoogleOAuthProvider > AuthProvider > App wiring
- `web/tsconfig.app.json` - Added exclude for test files (pre-existing tsc build failure fix)
- `backend/Dockerfile` - ARG + ENV VITE_GOOGLE_CLIENT_ID in web-builder stage
- `docker-compose.yml` - build.args: VITE_GOOGLE_CLIENT_ID: ${VITE_GOOGLE_CLIENT_ID}
- `web/.gitignore` - Added .env and .env.local exclusions

## Decisions Made
- D-WEB-08: Excluded test files from tsconfig.app.json — test files use `global.fetch` (Node.js global) but app tsconfig only includes `vite/client` types, causing pre-existing `tsc -b` failures. Excluding test files from app tsconfig is correct: vitest uses its own type handling.
- D-WEB-09: ProtectedRoute checks `isLoading` first then `!accessToken` — matches plan spec, ensures AuthProvider's silent refresh completes before any redirect decision.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed pre-existing tsc build failure from test files in app tsconfig**
- **Found during:** Task 1 (build verification)
- **Issue:** `pnpm build` failed with "Cannot find name 'global'" errors in test files — test files included in tsconfig.app.json `include: ["src"]` but app tsconfig has no `@types/node`
- **Fix:** Added `exclude: ["src/**/*.test.tsx", "src/**/*.test.ts", "src/test-setup.ts"]` to tsconfig.app.json
- **Files modified:** web/tsconfig.app.json
- **Verification:** `pnpm build` exits 0, `pnpm test --run` still exits 0 (6/6 tests pass)
- **Committed in:** 7c9feb5 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Pre-existing build failure unblocked by correctly excluding test files from app tsconfig. No scope creep.

## Issues Encountered
- `pnpm build` failed before fix due to test files being included in app TypeScript compilation. Fixed inline (Rule 3).

## User Setup Required

Before testing the full auth flow, the operator must:

1. **GCP Console** — Authorized JavaScript origins for the Web OAuth 2.0 client:
   - Add `http://localhost:5173`
   - Add `https://notes.gregorymaingret.fr`

2. **web/.env** (local dev):
   ```
   VITE_GOOGLE_CLIENT_ID=<your-web-oauth-client-id>
   ```

3. **Root .env** (production + backend dev):
   ```
   GOOGLE_WEB_CLIENT_ID=<same-web-oauth-client-id>
   VITE_GOOGLE_CLIENT_ID=<same-web-oauth-client-id>
   ```

4. Restart backend (`cd backend && pnpm dev`) and web dev server (`cd web && pnpm dev`) after setting env vars.

## Next Phase Readiness
- Phase 16 auth flow complete pending human verification checkpoint approval
- Phase 17 (Document List): import DocumentListPage from a real component instead of the placeholder in App.tsx
- Phase 18 (Node Editor): import NodeEditorPage from a real component instead of the placeholder in App.tsx
- ProtectedRoute, LoginPage, App router, and main.tsx are production-ready

---
*Phase: 16-auth*
*Completed: 2026-03-06*
