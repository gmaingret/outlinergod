---
phase: 16-auth
verified: 2026-03-06T11:00:00Z
status: human_needed
score: 16/16 must-haves verified
human_verification:
  - test: "Open http://localhost:5173/editor/test in a fresh private window (no localStorage)"
    expected: "Immediately redirected to http://localhost:5173/login. Login card shows OutlinerGod title, Your self-hosted notes tagline, and Google sign-in button."
    why_human: "Browser navigation + Google OAuth popup cannot be automated in CI"
  - test: "On the login page, click Sign in with Google and complete the OAuth flow"
    expected: "After OAuth completes, user lands on http://localhost:5173/ showing the document list placeholder. No error shown."
    why_human: "Real Google OAuth credential flow requires a browser session and valid GCP Web client ID"
  - test: "After signing in, press F5 to refresh the page"
    expected: "User remains on http://localhost:5173/ — NOT redirected to /login. refresh_token is present in localStorage (DevTools > Application > Local Storage)."
    why_human: "Silent refresh on mount requires a live backend /api/auth/refresh call with a real refresh_token"
  - test: "On the login page, open the Google sign-in popup and close/cancel it"
    expected: "Sign-in failed. Please try again. appears in red below the button."
    why_human: "Error path requires triggering the GIS popup cancellation in a real browser"
---

# Phase 16: Auth Verification Report

**Phase Goal:** Users can sign in with Google in the browser using the same account as on Android, and stay signed in across page refreshes.
**Verified:** 2026-03-06T11:00:00Z
**Status:** human_needed — All automated checks passed (16/16 must-haves). 4 items require human browser verification.
**Re-verification:** No — initial verification.

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `cd web && pnpm test --run` exits 0 (6/6 tests pass) | VERIFIED | Test run output: 2 files, 6 tests, all green |
| 2 | vitest.config.ts exists with jsdom environment and @vitejs/plugin-react | VERIFIED | File exists 11 lines; `environment: 'jsdom'`, `plugins: [react()]` confirmed |
| 3 | test-setup.ts imports @testing-library/jest-dom and calls afterEach(cleanup) | VERIFIED | Both present on lines 1-5 |
| 4 | AuthContext.test.tsx covers AUTH-01 (login) and AUTH-02 (silent refresh) | VERIFIED | 4 tests in file: 3 AUTH-02 cases + 1 AUTH-01 case |
| 5 | ProtectedRoute.test.tsx covers AUTH-03 (redirect to /login) | VERIFIED | 2 tests: unauthenticated redirect + authenticated access |
| 6 | AuthProvider reads refresh_token from localStorage on mount and calls POST /api/auth/refresh | VERIFIED | Lines 34-57 of AuthContext.tsx: useEffect reads localStorage, fetches /api/auth/refresh |
| 7 | Silent refresh succeeds — accessToken set in state, new refresh_token stored in localStorage | VERIFIED | Lines 45-51: `.then(data => { localStorage.setItem(...); setState({accessToken: data.token, ...}) })` |
| 8 | Silent refresh fails — accessToken null, refresh_token cleared from localStorage, isLoading false | VERIFIED | Lines 53-55: `.catch(() => { localStorage.removeItem(...); setState({...null...}) })` |
| 9 | login(idToken) calls POST /api/auth/google with { id_token, device_id } and stores refresh_token | VERIFIED | Lines 61-69: fetch('/api/auth/google', body with id_token + device_id), localStorage.setItem('refresh_token', ...) |
| 10 | Backend audience array accepts both GOOGLE_CLIENT_ID and GOOGLE_WEB_CLIENT_ID | VERIFIED | backend/src/routes/auth.ts line 25: array with filter(Boolean) |
| 11 | User can navigate to /login and see the login card (LoginPage exists and is routed) | VERIFIED (code) | App.tsx line 18: `<Route path="/login" element={<LoginPage />} />` |
| 12 | Navigating to /editor/:id without auth redirects to /login (ProtectedRoute guard) | VERIFIED (code) | ProtectedRoute.tsx lines 16-17: `!state.accessToken → <Navigate to="/login" replace>` |
| 13 | isLoading=true shows spinner, preventing flash-to-login during silent refresh | VERIFIED (code) | ProtectedRoute.tsx lines 8-13: isLoading guard before accessToken check |
| 14 | main.tsx wraps App in GoogleOAuthProvider with VITE_GOOGLE_CLIENT_ID | VERIFIED | main.tsx line 10: `clientId={import.meta.env.VITE_GOOGLE_CLIENT_ID ?? ''}` |
| 15 | Dockerfile web-builder stage accepts VITE_GOOGLE_CLIENT_ID as ARG (baked before build) | VERIFIED | Dockerfile lines 8-10: `ARG VITE_GOOGLE_CLIENT_ID` → `ENV VITE_GOOGLE_CLIENT_ID=...` → `RUN pnpm run build` |
| 16 | docker-compose.yml passes VITE_GOOGLE_CLIENT_ID build arg to web-builder stage | VERIFIED | docker-compose.yml line 7: `VITE_GOOGLE_CLIENT_ID: ${VITE_GOOGLE_CLIENT_ID}` under build.args |

**Score:** 16/16 truths verified (automated). 4 of 16 also require human browser confirmation.

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/vitest.config.ts` | Vitest config with jsdom + @vitejs/plugin-react | VERIFIED | 11 lines, jsdom environment, globals:true, setupFiles wired |
| `web/src/test-setup.ts` | jest-dom matchers + afterEach cleanup | VERIFIED | 5 lines, imports jest-dom/vitest, afterEach(cleanup) |
| `web/src/auth/AuthContext.test.tsx` | Auth unit test stubs (AUTH-01, AUTH-02) | VERIFIED | 4 passing tests covering all three silent-refresh cases + login |
| `web/src/components/ProtectedRoute.test.tsx` | ProtectedRoute test stub (AUTH-03) | VERIFIED | 2 passing tests: unauthenticated redirect + authenticated access |
| `web/src/auth/AuthContext.tsx` | AuthProvider, useAuth hook, AuthState type | VERIFIED | 89 lines, exports AuthProvider + useAuth, full state machine implemented |
| `web/src/auth/api.ts` | getOrCreateDeviceId helper | VERIFIED | 7 lines, localStorage.getItem/setItem('device_id'), crypto.randomUUID() |
| `backend/src/routes/auth.ts` | Dual audience Google token verification | VERIFIED | Line 25: `[GOOGLE_CLIENT_ID, GOOGLE_WEB_CLIENT_ID].filter(Boolean)` |
| `web/src/components/ProtectedRoute.tsx` | Auth guard using Outlet pattern | VERIFIED | 21 lines, isLoading spinner + Navigate replace + Outlet |
| `web/src/components/LoginPage.tsx` | Google sign-in button + error display | VERIFIED | 43 lines, GoogleLogin + error state + useAuth().login |
| `web/src/App.tsx` | BrowserRouter + Routes with protected/public structure | VERIFIED | 27 lines, /login public, ProtectedRoute wrapping / and /editor/:id |
| `web/src/main.tsx` | GoogleOAuthProvider wrapping App | VERIFIED | 16 lines, GoogleOAuthProvider > AuthProvider > App hierarchy |
| `backend/Dockerfile` | ARG VITE_GOOGLE_CLIENT_ID in web-builder stage | VERIFIED | Lines 8-9: ARG + ENV before pnpm run build on line 10 |
| `docker-compose.yml` | build args for VITE_GOOGLE_CLIENT_ID | VERIFIED | Line 7: `VITE_GOOGLE_CLIENT_ID: ${VITE_GOOGLE_CLIENT_ID}` |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `web/vitest.config.ts` | `web/src/test-setup.ts` | setupFiles config key | WIRED | Line 9: `setupFiles: './src/test-setup.ts'` |
| `web/src/auth/AuthContext.tsx` | `/api/auth/refresh` | fetch in useEffect on mount | WIRED | Line 39: `fetch('/api/auth/refresh', ...)` inside useEffect([]) |
| `web/src/auth/AuthContext.tsx` | `/api/auth/google` | fetch in login() | WIRED | Line 61: `fetch('/api/auth/google', ...)` inside login() |
| `web/src/auth/api.ts` | localStorage | getOrCreateDeviceId | WIRED | Lines 2+5: localStorage.getItem + localStorage.setItem('device_id') |
| `backend/src/routes/auth.ts` | process.env.GOOGLE_WEB_CLIENT_ID | audience array in verifyIdToken | WIRED | Line 25: `[process.env.GOOGLE_CLIENT_ID, process.env.GOOGLE_WEB_CLIENT_ID].filter(...)` |
| `web/src/App.tsx` | `web/src/components/ProtectedRoute.tsx` | Route element prop | WIRED | Line 19: `<Route element={<ProtectedRoute />}>` |
| `web/src/main.tsx` | import.meta.env.VITE_GOOGLE_CLIENT_ID | GoogleOAuthProvider clientId prop | WIRED | Line 10: `clientId={import.meta.env.VITE_GOOGLE_CLIENT_ID ?? ''}` |
| `backend/Dockerfile` | VITE_GOOGLE_CLIENT_ID | ARG + ENV in web-builder stage | WIRED | Lines 8-10: ARG declared, ENV set, build runs after |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| AUTH-01 | 16-01, 16-02, 16-03 | User can sign in with Google (same account as on Android) | SATISFIED (code) | login() in AuthContext.tsx posts to /api/auth/google; LoginPage.tsx wires GoogleLogin.onSuccess → login(); backend accepts both Android + Web client IDs via dual audience |
| AUTH-02 | 16-01, 16-02, 16-03 | User stays logged in after refreshing the browser | SATISFIED (code) | AuthProvider useEffect reads refresh_token on mount, calls /api/auth/refresh, stores new tokens; ProtectedRoute shows spinner during isLoading preventing premature redirect |
| AUTH-03 | 16-01, 16-03 | User is redirected to login page if not authenticated | SATISFIED (code) | ProtectedRoute.tsx: `!state.accessToken → <Navigate to="/login" replace>`; App.tsx routes / and /editor/:id through ProtectedRoute |

All three requirements are mapped, implemented, and tested. No orphaned requirements.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `web/src/App.tsx` | 6-11 | DocumentListPage + NodeEditorPage are placeholder components | INFO | Intentional — PLAN explicitly specifies these as Phase 17/18 placeholders. ProtectedRoute and LoginPage are fully implemented. |

No blocker or warning anti-patterns found.

---

### Human Verification Required

These items cannot be automated because they require a real browser session, a live backend, and a valid GCP Web OAuth 2.0 client ID configured in environment files.

**Prerequisites before running:**
1. GCP Console: add `http://localhost:5173` to Authorized JavaScript origins for the Web OAuth 2.0 client
2. `web/.env`: set `VITE_GOOGLE_CLIENT_ID=<your-web-client-id>`
3. Root `.env`: set `GOOGLE_WEB_CLIENT_ID=<same-web-client-id>`
4. Restart backend (`cd backend && pnpm dev`) and web dev server (`cd web && pnpm dev`)

#### 1. AUTH-03: Unauthenticated redirect to /login

**Test:** Open http://localhost:5173/editor/test in a fresh private window (no localStorage)
**Expected:** Immediately redirected to http://localhost:5173/login. Login card shows "OutlinerGod" title, "Your self-hosted notes" tagline, and a Google sign-in button.
**Why human:** Browser navigation + GIS sign-in button rendering cannot be automated in CI

#### 2. AUTH-01: Google sign-in flow

**Test:** On the login page, click "Sign in with Google" and complete the OAuth flow with your Google account
**Expected:** After OAuth completes, user lands on http://localhost:5173/ showing "Document list — coming in Phase 17". No error shown.
**Why human:** Real Google OAuth credential flow requires a browser session and a valid GCP Web client ID

#### 3. AUTH-02: Stay signed in after page refresh

**Test:** After signing in (AUTH-01 above), press F5 to refresh the page
**Expected:** User remains on http://localhost:5173/ — NOT redirected to /login. `refresh_token` is present in DevTools > Application > Local Storage.
**Why human:** Silent refresh on mount requires a live backend /api/auth/refresh call with a real refresh_token

#### 4. AUTH-01 error path: cancelled popup

**Test:** On the login page, open the Google sign-in popup and close/cancel it without completing sign-in
**Expected:** "Sign-in failed. Please try again." appears in red below the button
**Why human:** Error path requires triggering GIS popup cancellation in a real browser

---

### Gaps Summary

No gaps found. All 16 automated must-haves pass. The phase is code-complete and test-complete. The 4 human verification items are the standard browser/OAuth integration checks that require a real GCP Web client ID and a running backend — these were always expected to be human-gated (Plan 16-03 included a `checkpoint:human-verify` task with the same checklist).

**Note on App.tsx placeholders:** `DocumentListPage` and `NodeEditorPage` inside App.tsx render stub text. This is intentional and explicitly called out in the plan — they will be replaced when Phases 17 and 18 ship. ProtectedRoute correctly guards them, and the auth flow itself is fully functional.

---

_Verified: 2026-03-06T11:00:00Z_
_Verifier: Claude (gsd-verifier)_
