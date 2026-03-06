---
phase: 16-auth
plan: 01
subsystem: testing
tags: [vitest, react-testing-library, jsdom, react-router-dom, react-oauth-google]

# Dependency graph
requires:
  - phase: 15-scaffold
    provides: web/ Vite+React project with @vitejs/plugin-react already installed
provides:
  - Vitest test harness with jsdom environment and jest-dom matchers
  - Failing test stubs for AUTH-01 (login), AUTH-02 (silent refresh), AUTH-03 (protected route redirect)
  - react-router-dom and @react-oauth/google production deps installed
affects: [16-auth/16-02, 16-auth/16-03]

# Tech tracking
tech-stack:
  added:
    - vitest 4.0.18
    - "@testing-library/react 16.3.2"
    - "@testing-library/jest-dom 6.9.1"
    - "@testing-library/user-event 14.6.1"
    - jsdom 28.1.0
    - react-router-dom 7.13.1
    - "@react-oauth/google 0.13.4"
  patterns:
    - "global.fetch = vi.fn() + mockResolvedValueOnce per test for fetch-heavy components"
    - "StatusDisplay helper component pattern to read auth state in tests without exposing internals"
    - "beforeEach(() => { localStorage.clear(); vi.clearAllMocks() }) — standard auth test reset"

key-files:
  created:
    - web/vitest.config.ts
    - web/src/test-setup.ts
    - web/src/auth/AuthContext.test.tsx
    - web/src/components/ProtectedRoute.test.tsx
  modified:
    - web/package.json

key-decisions:
  - "D-WEB-TEST-01: vitest.config.ts is separate from vite.config.ts — avoids plugin conflicts with @tailwindcss/vite"
  - "D-WEB-TEST-02: jsdom environment (not happy-dom) — broader compatibility with testing-library/react 16"
  - "D-WEB-TEST-03: globals:true in vitest config — matches jest-dom import style, no explicit describe/it imports needed in test files"

patterns-established:
  - "Pattern 1: Test stubs import modules that don't yet exist — RED phase intentionally fails at 'Cannot find module'"
  - "Pattern 2: StatusDisplay helper reads auth state; avoids coupling tests to implementation details of AuthProvider render output"

requirements-completed: [AUTH-01, AUTH-02, AUTH-03]

# Metrics
duration: 12min
completed: 2026-03-06
---

# Phase 16 Plan 01: Auth Test Infrastructure Summary

**Vitest test harness with jsdom + jest-dom established; 4 AuthContext stubs pass (AUTH-01/02), ProtectedRoute stub in RED awaiting Plan 03**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-03-06T09:14:21Z
- **Completed:** 2026-03-06T09:26:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Installed vitest 4.0.18 + @testing-library/react + jsdom + jest-dom with proper jsdom environment config
- Installed production deps react-router-dom and @react-oauth/google into web/
- Created vitest.config.ts with jsdom environment, globals:true, setupFiles pointing to test-setup.ts
- Created test-setup.ts with jest-dom/vitest matchers and afterEach(cleanup)
- Created AuthContext.test.tsx with 4 tests covering AUTH-01 (login) and AUTH-02 (silent refresh) — all 4 pass
- Created ProtectedRoute.test.tsx with 2 tests covering AUTH-03 — correctly fails with "Cannot find module ./ProtectedRoute" (RED phase)

## Task Commits

Each task was committed atomically:

1. **Task 1: Install web test dependencies and configure Vitest** - `c550008` (chore)
2. **Task 2: Write failing test stubs for AuthContext and ProtectedRoute** - `fafdc56` (test)
3. **Fix: Correct AUTH-01 mock setup** - `b903a63` (fix)

## Files Created/Modified
- `web/vitest.config.ts` — Vitest config: jsdom environment, globals:true, setupFiles
- `web/src/test-setup.ts` — jest-dom/vitest matchers + afterEach(cleanup)
- `web/src/auth/AuthContext.test.tsx` — 4 tests: AUTH-02 (3 silent refresh cases) + AUTH-01 (login call)
- `web/src/components/ProtectedRoute.test.tsx` — 2 tests: AUTH-03 redirect + authenticated access
- `web/package.json` — added "test": "vitest" script

## Decisions Made
- D-WEB-TEST-01: vitest.config.ts separate from vite.config.ts to avoid @tailwindcss/vite plugin conflicts in test mode
- D-WEB-TEST-02: jsdom (not happy-dom) for broader @testing-library/react 16 compatibility
- D-WEB-TEST-03: globals:true eliminates need to import describe/it/expect in test files — consistent with jest-dom style
- AUTH-01 mock fix: removed spurious initial mockResolvedValueOnce from the test — on mount with no localStorage refresh_token, no fetch is called, so the initial mock would be consumed by the login() call with wrong shape

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] AUTH-01 test had spurious initial fetch mock**
- **Found during:** Task 2 verification
- **Issue:** Test had `mockResolvedValueOnce({ token, refresh_token })` (no `user` field) before the render, which was consumed by the login() call instead of the intended second mock. `data.user.id` would throw TypeError.
- **Fix:** Removed the initial mock — no refresh_token in localStorage means mount makes no fetch call, so the mock intended for login() was the first one consumed
- **Files modified:** web/src/auth/AuthContext.test.tsx
- **Verification:** All 4 AuthContext tests pass after fix
- **Committed in:** b903a63

---

**Total deviations:** 1 auto-fixed (Rule 1 — bug in test mock sequencing)
**Impact on plan:** Fix was necessary for test correctness. No scope creep.

### Contextual note: AuthContext.tsx pre-existed
Plan 02 (16-02) appears to have run concurrently or immediately after 16-01, creating `AuthContext.tsx` and `api.ts` before this plan's verification. As a result, AuthContext tests run against the real implementation (4/4 pass) rather than failing with "Cannot find module ./AuthContext". ProtectedRoute.tsx correctly does not exist yet — those tests remain RED awaiting Plan 03.

## Issues Encountered
- pnpm does not support `--legacy-peer-deps` flag (npm flag). @react-oauth/google installed without it — pnpm resolves peer deps differently and accepted the package without the flag.

## Next Phase Readiness
- Test harness fully operational — `pnpm test --run` runs without config errors
- AuthContext tests (AUTH-01, AUTH-02): 4/4 passing
- ProtectedRoute tests (AUTH-03): awaiting Plan 03 implementation of ProtectedRoute.tsx
- Plans 02 and 03 can run `pnpm test --run` as their acceptance gate

---
*Phase: 16-auth*
*Completed: 2026-03-06*
