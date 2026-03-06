---
phase: 16-auth
plan: 02
subsystem: auth
tags: [react, auth-context, jwt, refresh-token, google-oauth, fetch, localStorage]

# Dependency graph
requires:
  - phase: 16-01
    provides: "vitest test infrastructure, AuthContext.test.tsx (RED stubs), ProtectedRoute.test.tsx stubs"
  - phase: 15-scaffold
    provides: "web/ Vite+React app, backend Fastify server"
provides:
  - "AuthProvider React context component (access token in memory, refresh token in localStorage)"
  - "useAuth() hook returning { state: AuthState, login, logout }"
  - "getOrCreateDeviceId() helper — stable UUID in localStorage['device_id']"
  - "backend dual-audience Google token verification (Android + Web client IDs)"
affects: [16-03, 17-doc-list, 18-editor, LoginScreen, ProtectedRoute]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "AuthContext: access token in React state (memory), refresh token in localStorage"
    - "Silent refresh on mount via useEffect — no storage event, no service worker"
    - "JWT userId decoded client-side from token.split('.')[1] base64 payload (.sub field)"
    - "login() sends { id_token, device_id } — device_id from getOrCreateDeviceId()"
    - "Backend audience: filter(Boolean) array of GOOGLE_CLIENT_ID + GOOGLE_WEB_CLIENT_ID"

key-files:
  created:
    - web/src/auth/AuthContext.tsx
    - web/src/auth/api.ts
  modified:
    - backend/src/routes/auth.ts
    - .env.example

key-decisions:
  - "D-WEB-05: userId fallback: login() uses data.user?.id ?? decodeUserId(data.token) — defensive against partial mock responses (test alignment)"
  - "D-WEB-06: backend .env lives at project root (env_file: .env in docker-compose) not backend/.env — GOOGLE_WEB_CLIENT_ID added to root .env.example"
  - "D-WEB-07: device_id stored in backend refresh_tokens table; POST /api/auth/google now uses body.device_id ?? 'web-default' instead of hardcoded 'default'"

patterns-established:
  - "AuthState shape: { accessToken: string|null, userId: string|null, isLoading: boolean }"
  - "AuthContextValue: { state, login(idToken), logout() } — consumed via useAuth()"
  - "isLoading starts true, becomes false only after silent refresh attempt completes or is skipped"

requirements-completed: [AUTH-01, AUTH-02]

# Metrics
duration: 3min
completed: 2026-03-06
---

# Phase 16 Plan 02: Auth Context Summary

**React AuthProvider (token-in-memory + refresh-in-localStorage) with backend dual-audience Google OAuth verification for Android and Web client IDs**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-06T09:15:27Z
- **Completed:** 2026-03-06T09:18:12Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- AuthProvider reads refresh_token from localStorage on mount and calls POST /api/auth/refresh — silent re-authentication on page reload
- login(idToken) posts { id_token, device_id } to /api/auth/google and stores tokens
- Backend auth.ts audience array now accepts both GOOGLE_CLIENT_ID (Android) and GOOGLE_WEB_CLIENT_ID (Web) via filter(Boolean)
- All 4 AuthContext tests pass (AUTH-01 + AUTH-02); all 273 backend tests pass (no regressions)

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement AuthContext (AuthProvider + useAuth)** - `0104f20` (feat)
2. **Task 2: Update backend audience to accept Web OAuth client ID** - `5397620` (feat)

**Plan metadata:** (see final commit below)

## Files Created/Modified
- `web/src/auth/AuthContext.tsx` - AuthProvider component and useAuth hook
- `web/src/auth/api.ts` - getOrCreateDeviceId helper (stable UUID in localStorage)
- `backend/src/routes/auth.ts` - audience array updated for dual Android+Web client ID support; device_id body field typed and used in INSERT
- `.env.example` - Added GOOGLE_WEB_CLIENT_ID placeholder with descriptive comment

## Decisions Made
- D-WEB-05: `login()` uses `data.user?.id ?? decodeUserId(data.token)` — defensive fallback ensures userId is always set even if `user` field is absent from server response
- D-WEB-06: `.env.example` at project root updated (not `backend/.env`) because `docker-compose.yml` references `env_file: .env` at project root — no `backend/.env` exists
- D-WEB-07: POST /api/auth/google body now uses `body.device_id ?? 'web-default'` (was hardcoded `'default'`) enabling proper device tracking for web clients

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed login() handling when user field absent from response**
- **Found during:** Task 1 (AuthContext implementation) — GREEN phase test run
- **Issue:** Test AuthContext.test.tsx login test sends first mockResolvedValueOnce with `{ token, refresh_token }` (no `user` field) — consumed by `/api/auth/google` call. `data.user.id` throws TypeError.
- **Fix:** Changed `data.user.id` to `data.user?.id ?? decodeUserId(data.token)` — decodes userId from JWT sub claim as fallback
- **Files modified:** web/src/auth/AuthContext.tsx
- **Verification:** All 4 AuthContext tests pass
- **Committed in:** 0104f20 (Task 1 commit)

**2. [Rule 2 - Missing Critical] Added device_id body typing and used in INSERT**
- **Found during:** Task 2 (backend audience update)
- **Issue:** Plan noted the body was typed as `{ id_token?: string }` and INSERT used hardcoded `'default'` for device_id — web client sends device_id in body but it was ignored
- **Fix:** Added `device_id?: string` to body type; INSERT now uses `body.device_id ?? 'web-default'`
- **Files modified:** backend/src/routes/auth.ts
- **Verification:** All 273 backend tests pass
- **Committed in:** 5397620 (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 missing critical)
**Impact on plan:** Both fixes necessary for correctness. No scope creep.

## Issues Encountered
None beyond the auto-fixed deviations above.

## Next Phase Readiness
- AuthProvider and useAuth ready for LoginScreen (Plan 16-03) to consume
- ProtectedRoute.test.tsx stubs still failing at "Cannot find module './ProtectedRoute'" (expected RED — Plan 16-03 creates ProtectedRoute)
- Backend accepts both Android and Web Google OAuth tokens — ready for production web login
- GOOGLE_WEB_CLIENT_ID must be configured in production .env on server (see .env.example)

## Self-Check: PASSED
- `web/src/auth/AuthContext.tsx` — exists
- `web/src/auth/api.ts` — exists
- `backend/src/routes/auth.ts` — modified (audience array + device_id)
- `.env.example` — GOOGLE_WEB_CLIENT_ID placeholder added
- Commits 0104f20 and 5397620 — verified in git log

---
*Phase: 16-auth*
*Completed: 2026-03-06*
