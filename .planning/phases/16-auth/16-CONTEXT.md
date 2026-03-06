# Phase 16: Auth - Context

**Gathered:** 2026-03-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Implement browser-based Google Sign-In using the same backend the Android app already uses. Users can sign in with Google, stay signed in across page refreshes via silent refresh, and are redirected to /login when not authenticated. React Router v7 and the AuthContext are introduced in this phase. No document list, no editor — auth wiring only.

</domain>

<decisions>
## Implementation Decisions

### OAuth credential configuration
- User already has a Web OAuth 2.0 client ID in GCP Console (separate from the Android client)
- Backend gets a second env var: `GOOGLE_WEB_CLIENT_ID` — the backend `auth.ts` audience changes from a single string to an array `[GOOGLE_CLIENT_ID, GOOGLE_WEB_CLIENT_ID]`, accepting both Android and web tokens
- Web app uses `VITE_GOOGLE_CLIENT_ID` (the web client ID) in `web/.env` for local dev
- Docker build: `VITE_GOOGLE_CLIENT_ID` passed as a build ARG in the Dockerfile web-builder stage and `docker-compose.yml` args section — Vite bakes it into the bundle at build time
- GCP authorized JavaScript origins: `https://notes.gregorymaingret.fr` AND `http://localhost:5173` (both prod + local dev)

### Session storage
- Access token: React state only (memory) — lost on refresh, restored by silent refresh
- Refresh token: `localStorage` under key `refresh_token`
- Device ID: stable UUID in `localStorage` under key `device_id` — generated on first visit, sent with every `/api/auth/google` call

### Login page design
- Layout: centered card on light gray background (`bg-gray-100` full page, white card with shadow)
- Card contents: "OutlinerGod" title + "Your self-hosted notes" tagline + Google sign-in button
- After successful sign-in: navigate to `/` (document list page)

### Auth failure UX
- GIS failure (user cancels, popup blocked): inline red text below the button — "Sign-in failed. Please try again."
- Backend exchange failure (`POST /api/auth/google` returns error): same inline message — no technical detail exposed
- Error text clears when the user clicks the button again to retry

### Session expiry behavior
- On page load: `AuthProvider` reads `localStorage.refresh_token`, calls `POST /api/auth/refresh`. If the token is expired or missing, clear localStorage and set `accessToken` to null — `ProtectedRoute` silently redirects to `/login`. No "session expired" message.
- Mid-session 401: `fetchWithAuth` helper detects 401 on any API call, calls `POST /api/auth/refresh` to get a new access token, updates context state, then retries the original request — user never sees a disruption. Matches the Android Ktor 401 interceptor pattern.
- If the refresh itself returns 401/403 (expired/revoked): clear tokens, redirect to `/login`

### Claude's Discretion
- Exact Tailwind class names and spacing within the layout decisions above
- How concurrent 401s during token refresh are handled (queue vs single inflight flag)
- Whether `vitest.config.ts` is a separate file or merged into `vite.config.ts`

</decisions>

<specifics>
## Specific Ideas

- The mid-session 401 auto-refresh pattern mirrors the Android `KtorClientFactory` 401 interceptor — same concept, same intent
- `web/.env` holds only `VITE_GOOGLE_CLIENT_ID`; backend secrets stay in the root `.env`
- The `device_id` localStorage UUID is the same pattern as Android's `AuthRepository.getDeviceId()`

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/App.tsx`: bare Hello World — will be replaced with `<BrowserRouter>` + `<Routes>` structure
- `web/src/main.tsx`: will be updated to wrap app in `<GoogleOAuthProvider>` and `<AuthProvider>`
- Tailwind v4 already configured via `@tailwindcss/vite` — no setup needed for login page styling

### Established Patterns
- Backend: `POST /api/auth/google` body: `{ id_token, device_id }` → response: `{ access_token, refresh_token, user_id }`
- Backend: `POST /api/auth/refresh` body: `{ refresh_token }` → response: `{ token, refresh_token }` (note: `token` not `access_token`)
- Backend: `audience` field in `auth.ts` currently accepts one `GOOGLE_CLIENT_ID` — must expand to array
- Android: Ktor 401 interceptor in `KtorClientFactory` — web `fetchWithAuth` mirrors this
- No test infrastructure in `web/` yet — Wave 0 must set up `vitest.config.ts`, `test-setup.ts`, and install `vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom`

### Integration Points
- `backend/.env` and `docker-compose.yml`: add `GOOGLE_WEB_CLIENT_ID`
- `backend/src/routes/auth.ts`: change audience from `process.env.GOOGLE_CLIENT_ID` to array
- `web/.env`: add `VITE_GOOGLE_CLIENT_ID=<web-client-id>`
- `Dockerfile` web-builder stage: add `ARG VITE_GOOGLE_CLIENT_ID`
- `docker-compose.yml`: add `args: [VITE_GOOGLE_CLIENT_ID]` under the web-builder build context
- `web/src/main.tsx`: wrap with `<GoogleOAuthProvider clientId={import.meta.env.VITE_GOOGLE_CLIENT_ID}>`
- `web/src/App.tsx`: introduce `<BrowserRouter>` + protected/public route structure

</code_context>

<deferred>
## Deferred Ideas

- None — discussion stayed within phase scope.

</deferred>

---

*Phase: 16-auth*
*Context gathered: 2026-03-06*
