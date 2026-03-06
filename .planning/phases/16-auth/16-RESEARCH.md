# Phase 16: Auth - Research

**Researched:** 2026-03-06
**Domain:** Google Identity Services (GIS), React auth context, JWT token management, protected routes
**Confidence:** HIGH

## Summary

Phase 16 implements browser-based Google Sign-In using the same backend the Android app already uses. The backend already accepts `POST /api/auth/google` (body: `{ id_token: string }`) and `POST /api/auth/refresh` (body: `{ refresh_token: string }`), and returns JWTs. The web client only needs to: render a GIS button, exchange the `id_token` for backend tokens, store the refresh token in `localStorage`, hold the access token in React state, silently refresh on page reload, and gate protected routes.

The standard library for this is `@react-oauth/google` (wraps Google Identity Services SDK). It provides a `<GoogleLogin>` component that renders the Google-branded button and calls `onSuccess` with `{ credential: string }` — where `credential` IS the `id_token`. FedCM became mandatory for all GIS traffic in August 2025; `@react-oauth/google` supports it via the `use_fedcm_for_button` prop and handles it automatically for the button flow.

Protected routes use React Router v7's `<Outlet>` pattern with an `AuthContext` that holds the access token in memory and reads the refresh token from `localStorage` on startup to perform a silent `POST /api/auth/refresh`.

**Primary recommendation:** Use `@react-oauth/google` + React Router v7 + `AuthContext` with access token in memory and refresh token in `localStorage`.

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `@react-oauth/google` | 0.13.4 | GIS button + credential response | Only maintained React wrapper for GIS; 0 runtime deps; FedCM-ready |
| `react-router-dom` | 7.x | Client-side routing + protected routes | Stable v7 (non-breaking upgrade from v6); `<Outlet>` pattern for auth guards |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `vitest` | already available (backend uses it) | Unit/integration tests for auth context and hooks | Required per project testing rules |
| `@testing-library/react` | latest | Render AuthContext + ProtectedRoute in tests | Unit tests for all auth logic |
| `@testing-library/jest-dom` | latest | DOM matchers (toBeInTheDocument) | Paired with @testing-library/react |
| `jsdom` | latest | Browser environment for vitest | Required for react component tests |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `@react-oauth/google` | Raw `window.google.accounts.id.initialize()` | More control but 100+ lines of boilerplate; library already handles FedCM migration |
| `@react-oauth/google` | `google-one-tap` npm package | No React wrapper, less maintained |
| React Router v7 | React Router v6 | v7 is the current release; non-breaking upgrade; same API |
| Access token in memory | Access token in `localStorage` | `localStorage` is XSS-vulnerable; memory token lost on refresh (handled by silent refresh) |

**Installation:**
```bash
cd web
pnpm add @react-oauth/google react-router-dom
pnpm add -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom
```

Note on React 19 peer deps: `@react-oauth/google` 0.13.4 declares `react@^18` peer dependency. With React 19 (which the project uses), install with `--legacy-peer-deps` flag or pnpm's `peerDependencyRules`. The library works at runtime with React 19 — this is a declaration lag, not a functional issue.

```bash
pnpm add @react-oauth/google --legacy-peer-deps
```

Or add to `web/package.json`:
```json
"pnpm": {
  "peerDependencyRules": {
    "allowedVersions": {
      "@react-oauth/google>react": "19"
    }
  }
}
```

## Architecture Patterns

### Recommended Project Structure

```
web/src/
├── auth/
│   ├── AuthContext.tsx       # createContext, AuthProvider, useAuth hook
│   ├── AuthContext.test.tsx  # unit tests for auth context logic
│   └── api.ts                # fetchWithAuth, silentRefresh, logout calls
├── components/
│   ├── ProtectedRoute.tsx    # <Outlet> guard — redirects to /login if not authed
│   └── LoginPage.tsx         # GoogleLogin button + onSuccess handler
├── App.tsx                   # BrowserRouter + Routes (protected + public)
└── main.tsx                  # GoogleOAuthProvider wraps App
```

### Pattern 1: AuthContext (access token in memory, refresh token in localStorage)

**What:** React context holds the access token in state (lost on refresh). On mount, reads `refresh_token` from `localStorage` and calls `POST /api/auth/refresh` to silently restore session. Stores new refresh token back to `localStorage`.

**When to use:** All React auth state management. Every API call reads `accessToken` from context.

```typescript
// Source: verified pattern from react-router-v7 docs + LogRocket
interface AuthState {
  accessToken: string | null;
  userId: string | null;
  isLoading: boolean;
}

const AuthContext = createContext<{
  state: AuthState;
  login: (idToken: string) => Promise<void>;
  logout: () => void;
  getAccessToken: () => string | null;
} | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>({
    accessToken: null,
    userId: null,
    isLoading: true,
  });

  // Silent refresh on mount: read refresh_token from localStorage
  useEffect(() => {
    const refreshToken = localStorage.getItem('refresh_token');
    if (!refreshToken) {
      setState(s => ({ ...s, isLoading: false }));
      return;
    }
    fetch('/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refresh_token: refreshToken }),
    })
      .then(r => r.ok ? r.json() : Promise.reject(r))
      .then(data => {
        // Backend returns { token, refresh_token } on refresh
        localStorage.setItem('refresh_token', data.refresh_token);
        setState({ accessToken: data.token, userId: parseUserId(data.token), isLoading: false });
      })
      .catch(() => {
        localStorage.removeItem('refresh_token');
        setState({ accessToken: null, userId: null, isLoading: false });
      });
  }, []);

  const login = async (idToken: string) => {
    const deviceId = getOrCreateDeviceId(); // from localStorage
    const res = await fetch('/api/auth/google', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id_token: idToken, device_id: deviceId }),
    });
    if (!res.ok) throw new Error('Auth failed');
    const data = await res.json();
    localStorage.setItem('refresh_token', data.refresh_token);
    setState({ accessToken: data.access_token, userId: data.user_id, isLoading: false });
  };

  const logout = () => {
    localStorage.removeItem('refresh_token');
    setState({ accessToken: null, userId: null, isLoading: false });
  };

  return (
    <AuthContext.Provider value={{ state, login, logout, getAccessToken: () => state.accessToken }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be inside AuthProvider');
  return ctx;
}
```

**CRITICAL field name:** The backend `POST /api/auth/google` returns `{ access_token, refresh_token, user_id }`. Confirmed from reading `backend/src/routes/auth.ts`. The `POST /api/auth/refresh` returns `{ token, refresh_token }` (note: `token` not `access_token`). Map accordingly.

### Pattern 2: ProtectedRoute with Outlet

**What:** Wrapper route element that checks `isLoading` and `accessToken`. Shows spinner during silent refresh, redirects to `/login` if unauthenticated, renders child routes via `<Outlet>` if authenticated.

**When to use:** Wrap all routes that require auth.

```typescript
// Source: verified pattern from React Router v7 docs
export function ProtectedRoute() {
  const { state } = useAuth();
  const location = useLocation();

  if (state.isLoading) {
    return <div className="flex min-h-screen items-center justify-center">Loading...</div>;
  }

  if (!state.accessToken) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
}
```

### Pattern 3: Google Sign-In Button

**What:** `@react-oauth/google`'s `<GoogleLogin>` renders the GIS button. The `onSuccess` callback receives `{ credential: string }` — this IS the id_token to send to the backend.

**When to use:** LoginPage only.

```typescript
// Source: @react-oauth/google README (github.com/MomenSherif/react-oauth)
import { GoogleLogin } from '@react-oauth/google';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="flex flex-col items-center gap-6">
        <h1 className="text-3xl font-bold">OutlinerGod</h1>
        <GoogleLogin
          onSuccess={async (credentialResponse) => {
            // credentialResponse.credential IS the id_token
            // NOT credentialResponse.id_token — that field doesn't exist
            if (!credentialResponse.credential) return;
            try {
              await login(credentialResponse.credential);
              navigate('/');
            } catch (e) {
              console.error('Login failed', e);
            }
          }}
          onError={() => console.error('Google sign-in failed')}
          use_fedcm_for_button
        />
      </div>
    </div>
  );
}
```

### Pattern 4: GoogleOAuthProvider in main.tsx

**What:** Must wrap the entire app (or at minimum the subtree that uses GoogleLogin).

```typescript
// main.tsx
import { GoogleOAuthProvider } from '@react-oauth/google';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <GoogleOAuthProvider clientId={import.meta.env.VITE_GOOGLE_CLIENT_ID}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </GoogleOAuthProvider>
  </StrictMode>,
);
```

### Pattern 5: Router structure in App.tsx

```typescript
// App.tsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/" element={<DocumentListPage />} />
          <Route path="/editor/:id" element={<NodeEditorPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
```

### Anti-Patterns to Avoid

- **`credentialResponse.id_token`:** This field is `undefined`. The id_token is at `credentialResponse.credential`. Confirmed from the GIS SDK contract and `@react-oauth/google` README.
- **Storing access token in `localStorage`:** XSS-vulnerable. Keep it in React state only.
- **Rendering protected routes before `isLoading` resolves:** Causes flash of redirect to `/login` even for authenticated users. Always check `isLoading` first.
- **Calling `POST /api/auth/refresh` with the wrong field name:** The backend expects `{ refresh_token }`. Sending `{ refreshToken }` returns 400.
- **Using the Android client ID as `VITE_GOOGLE_CLIENT_ID`:** The web client needs the Web OAuth 2.0 client ID from GCP Console, not the Android client ID. The backend's `verifyIdToken` audience must include the web client ID — read `backend/src/routes/auth.ts` line: `audience: process.env.GOOGLE_CLIENT_ID`. If the backend was configured with only the Android client ID, you must update `GOOGLE_CLIENT_ID` in `.env` to the web client ID (or create a separate env var and update the backend audience array to accept both).
- **Skipping `use_fedcm_for_button` prop:** FedCM became mandatory for all One Tap and Sign-In button implementations in August 2025. Without this prop, the sign-in will still work in current Chrome but may degrade without notice in future releases. Add it from day one.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Google Sign-In button | Custom button calling `window.google.accounts.id` | `@react-oauth/google` `<GoogleLogin>` | FedCM handling, React strict mode safety, TypeScript types, CSP compliance |
| Client-side routing | Custom history management | React Router v7 `<BrowserRouter>` | SSR/SPA URL handling, `<Navigate>`, `useLocation` for redirect-after-login |
| JWT userId parsing | Implement JWT decode | `jwt-decode` or just `parseUserId` via `atob(token.split('.')[1])` | The backend returns `user_id` directly in the auth response — parse from JSON, not JWT |

**Key insight:** The backend already handles all token issuance, verification, and rotation. The web client only needs to exchange the GIS credential for a backend JWT — don't duplicate any token verification logic client-side.

## Common Pitfalls

### Pitfall 1: Wrong credential field from GIS response
**What goes wrong:** `credentialResponse.id_token` is `undefined`; `POST /api/auth/google` receives `{ id_token: undefined }` and returns 400.
**Why it happens:** The GIS SDK wraps the token in `credential`, not `id_token`. The old deprecated `gapi.auth2` library used different field names.
**How to avoid:** Always use `credentialResponse.credential`. This is confirmed in `backend/src/routes/auth.ts` which expects `body.id_token` — so the mapping is: `{ id_token: credentialResponse.credential }`.
**Warning signs:** 400 response from `/api/auth/google` with `{ error: 'Missing id_token' }`.

### Pitfall 2: Flash of unauthenticated state on refresh
**What goes wrong:** User refreshes the page; `accessToken` is null (it was in memory); `isLoading` is false before silent refresh completes; user sees login page for 200ms then gets redirected.
**Why it happens:** `isLoading` defaults to `false` without a concurrent silent refresh guard.
**How to avoid:** Initialize `isLoading: true`; only set to `false` after the silent refresh attempt completes (success OR failure).
**Warning signs:** Brief flash of `/login` redirect before landing on `/`.

### Pitfall 3: Google Client ID mismatch between Android and Web
**What goes wrong:** Backend configured with Android client ID; web client sends id_token issued to web client ID; `verifyIdToken` throws audience mismatch error.
**Why it happens:** Android and Web clients have different OAuth 2.0 client IDs in GCP Console. The backend `GOOGLE_CLIENT_ID` env var must match the client that issued the id_token.
**How to avoid:** The web client must use the Web OAuth 2.0 client ID (`VITE_GOOGLE_CLIENT_ID`). The backend audience in `backend/src/routes/auth.ts` (`audience: process.env.GOOGLE_CLIENT_ID`) must also be the Web client ID (or expanded to accept both). If Android and web use different client IDs, the backend needs to accept both in the audience array.
**Warning signs:** 401 from `/api/auth/google` with Google library throwing `Error: Token used too late` or `audience mismatch`.

### Pitfall 4: device_id not sent to backend
**What goes wrong:** Backend `POST /api/auth/google` stores `device_id` in `refresh_tokens` table. If not sent, the device_id column gets null or undefined, breaking refresh token rotation.
**Why it happens:** The Android `AuthRepository` generates and stores a stable device_id UUID; the web client needs to do the same.
**How to avoid:** Generate a stable UUID on first visit, store in `localStorage` under `device_id`. Send it alongside `id_token` in the auth request body.
**Warning signs:** Check `backend/src/routes/auth.ts` — confirm whether `device_id` is required in the body or has a fallback.

### Pitfall 5: `@react-oauth/google` peer dep conflict with React 19
**What goes wrong:** `pnpm add @react-oauth/google` fails with peer dependency error because the package declares `react@^18`.
**Why it happens:** Package maintainer hasn't updated peer dep declaration; React 19 works at runtime.
**How to avoid:** Use `--legacy-peer-deps` flag or configure pnpm `peerDependencyRules`.
**Warning signs:** `ERR_PNPM_PEER_DEP_ISSUES` during install.

### Pitfall 6: Vite env variable not prefixed with VITE_
**What goes wrong:** `process.env.GOOGLE_CLIENT_ID` is undefined in browser; GIS fails to initialize.
**Why it happens:** Vite only exposes env vars prefixed with `VITE_` to the browser bundle.
**How to avoid:** Use `VITE_GOOGLE_CLIENT_ID` in `.env` and `import.meta.env.VITE_GOOGLE_CLIENT_ID` in code.
**Warning signs:** `clientId` prop on `GoogleOAuthProvider` is empty string; Google logs error in console.

### Pitfall 7: Backend refresh token field name
**What goes wrong:** Silent refresh call fails because the backend returns `{ token }` (not `{ access_token }`) from `POST /api/auth/refresh`.
**Why it happens:** The two auth endpoints have inconsistent response shapes — confirmed by reading `backend/src/routes/auth.ts`:
- `POST /api/auth/google` → `{ access_token, refresh_token, user_id, ... }`
- `POST /api/auth/refresh` → `{ token, refresh_token }` (token = the new JWT)
**How to avoid:** Handle both response shapes in `AuthContext`. Use `data.access_token ?? data.token` or explicit branch per endpoint.
**Warning signs:** `setState` receives `accessToken: undefined` after silent refresh.

## Code Examples

### Device ID helper (localStorage-based stable UUID)

```typescript
// auth/api.ts
export function getOrCreateDeviceId(): string {
  const existing = localStorage.getItem('device_id');
  if (existing) return existing;
  // Generate a UUID v4 without crypto.randomUUID (for broader compat)
  const uuid = crypto.randomUUID();
  localStorage.setItem('device_id', uuid);
  return uuid;
}
```

### Silent refresh call (startup)

```typescript
// auth/AuthContext.tsx — inside useEffect on mount
const refreshToken = localStorage.getItem('refresh_token');
if (!refreshToken) {
  setState(s => ({ ...s, isLoading: false }));
  return;
}

fetch('/api/auth/refresh', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ refresh_token: refreshToken }),
})
  .then(r => r.ok ? r.json() : Promise.reject())
  .then(data => {
    // data.token = new access token; data.refresh_token = rotated refresh token
    localStorage.setItem('refresh_token', data.refresh_token);
    setState({
      accessToken: data.token,
      userId: data.user_id ?? null,
      isLoading: false,
    });
  })
  .catch(() => {
    localStorage.removeItem('refresh_token');
    setState({ accessToken: null, userId: null, isLoading: false });
  });
```

Note: `POST /api/auth/refresh` response confirmed from `backend/src/routes/auth.ts` — returns `{ token, refresh_token }`. The `user_id` is NOT returned by the refresh endpoint; it must be cached from login or extracted from the JWT payload via `atob`.

### Vitest setup for web/ (vitest.config.ts)

```typescript
// web/vitest.config.ts
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test-setup.ts',
  },
});
```

```typescript
// web/src/test-setup.ts
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';
afterEach(cleanup);
```

### Test pattern for AuthContext

```typescript
// auth/AuthContext.test.tsx
import { render, screen, waitFor } from '@testing-library/react';
import { AuthProvider, useAuth } from './AuthContext';
import { vi } from 'vitest';

// Mock fetch
global.fetch = vi.fn();

function TestConsumer() {
  const { state } = useAuth();
  return <div>{state.isLoading ? 'loading' : state.accessToken ? 'authed' : 'not-authed'}</div>;
}

it('shows not-authed when no refresh_token in localStorage', async () => {
  localStorage.clear();
  render(<AuthProvider><TestConsumer /></AuthProvider>);
  await waitFor(() => screen.getByText('not-authed'));
});

it('silently refreshes when refresh_token exists', async () => {
  localStorage.setItem('refresh_token', 'test-rt');
  (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
    ok: true,
    json: () => Promise.resolve({ token: 'new-at', refresh_token: 'new-rt' }),
  });
  render(<AuthProvider><TestConsumer /></AuthProvider>);
  await waitFor(() => screen.getByText('authed'));
});
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `gapi.auth2.getAuthInstance()` | Google Identity Services (GIS) | 2022 | Old API deprecated March 2023; must use GIS |
| `react-google-login` npm package | `@react-oauth/google` | 2022 | `react-google-login` is deprecated and unmaintained |
| `window.google.accounts.oauth2` popup flow | GIS Sign In With Google button (id_token flow) | 2022 | For backend auth, use token-based flow not OAuth code flow |
| Cookies for refresh token | `localStorage` for refresh token (self-hosted) | ongoing | HttpOnly cookie is MORE secure but requires backend `Set-Cookie` support; this project's backend doesn't set cookies — `localStorage` is the correct choice here |
| GIS without FedCM | GIS with FedCM (`use_fedcm_for_button`) | August 2025 | Mandatory migration; `@react-oauth/google` supports it via prop |

**Deprecated/outdated:**
- `react-google-login`: unmaintained, wraps deprecated gapi.auth2
- `gapi.auth2.getAuthInstance()`: deprecated March 2023, removed from GIS SDK
- One Tap without FedCM: became mandatory to use FedCM in August 2025

## Open Questions

1. **Backend `GOOGLE_CLIENT_ID` — web vs Android client ID**
   - What we know: Backend `auth.ts` sets `audience: process.env.GOOGLE_CLIENT_ID`. The `.env` currently has one value. Android uses `GOOGLE_CLIENT_ID` as the serverClientId.
   - What's unclear: Whether the existing `.env` `GOOGLE_CLIENT_ID` is the web client ID, Android client ID, or both. GCP requires separate OAuth 2.0 clients for Android (package+SHA1) and Web (JavaScript origins).
   - Recommendation: During task execution, inspect the GCP Console to confirm which client ID is in `.env`. If it's the Android client ID, the backend must be updated to accept the web client ID in the audience array, OR the `.env` must be switched to the web client ID (and Android updated if needed). The backend auth.ts `audience` field accepts a string or array — use an array to accept both.

2. **`device_id` in `/api/auth/google` body**
   - What we know: The backend `auth.ts` stores `device_id` from the request body into the `refresh_tokens` table.
   - What's unclear: Whether the `device_id` field is optional (has a fallback) in the backend code.
   - Recommendation: During task execution, check `auth.ts` lines that handle `body.device_id` — confirm whether it's required. Always send it from the web client to match the Android pattern.

3. **`user_id` after silent refresh**
   - What we know: `POST /api/auth/refresh` returns `{ token, refresh_token }` — no `user_id`. `POST /api/auth/google` returns `user_id`. The web client needs `user_id` for API calls.
   - What's unclear: Best approach — decode JWT payload (`sub` field) or call `GET /api/auth/me`.
   - Recommendation: Decode the JWT payload via `JSON.parse(atob(token.split('.')[1]))` to read the `sub` claim as `userId`. This avoids an extra round-trip. Alternatively, call `GET /api/auth/me` after refresh. Either is acceptable — choose decode for simplicity.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Vitest (to be set up in Wave 0 of this phase) |
| Config file | `web/vitest.config.ts` — does NOT exist yet (Wave 0 gap) |
| Quick run command | `cd web && pnpm test --run` |
| Full suite command | `cd web && pnpm test --run` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AUTH-01 | `login(idToken)` calls `/api/auth/google` with `{ id_token }` and stores refresh token | unit | `cd web && pnpm test --run src/auth/AuthContext.test.tsx` | ❌ Wave 0 |
| AUTH-02 | `AuthProvider` mounts → reads `localStorage.refresh_token` → calls `/api/auth/refresh` → sets `accessToken` | unit | `cd web && pnpm test --run src/auth/AuthContext.test.tsx` | ❌ Wave 0 |
| AUTH-03 | `ProtectedRoute` with null `accessToken` renders `<Navigate to="/login">` | unit | `cd web && pnpm test --run src/components/ProtectedRoute.test.tsx` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `cd web && pnpm test --run`
- **Per wave merge:** `cd web && pnpm test --run`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `web/vitest.config.ts` — Vitest config with jsdom environment
- [ ] `web/src/test-setup.ts` — jest-dom import + afterEach cleanup
- [ ] `web/src/auth/AuthContext.test.tsx` — covers AUTH-01, AUTH-02
- [ ] `web/src/components/ProtectedRoute.test.tsx` — covers AUTH-03
- [ ] Framework install: `cd web && pnpm add -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom`
- [ ] Add `"test": "vitest"` to `web/package.json` scripts

## Sources

### Primary (HIGH confidence)
- `backend/src/routes/auth.ts` — confirmed exact field names for request/response bodies (`id_token`, `refresh_token`, `token`, `access_token`, `user_id`, `device_id`)
- `github.com/MomenSherif/react-oauth/blob/master/packages/@react-oauth/google/README.md` — confirmed `credentialResponse.credential` is the id_token field; `use_fedcm_for_button` prop availability
- `developers.google.com/identity/gsi/web/guides/fedcm-migration` — confirmed FedCM mandatory migration August 2025
- `web/package.json` — confirmed React 19.2.0, Vite 7, Tailwind v4 (no axios — use native fetch)

### Secondary (MEDIUM confidence)
- `blog.logrocket.com/authentication-react-router-v7/` — `<Outlet>` pattern for protected routes; verified against React Router v7 docs
- `dev.to/ra1nbow1/building-reliable-protected-routes-with-react-router-v7` — `PrivateRoute` with `isLoading` state; verified against React Router v7 docs
- `npmjs.com/package/react-router-dom` — confirmed latest version 7.13.1

### Tertiary (LOW confidence)
- WebSearch result: `@react-oauth/google` v0.13.4 latest version, last published ~2 months ago — not independently verified from npm registry directly (403 on direct fetch)
- FedCM "mandatory August 2025" claim from WebSearch — partially confirmed by Google Developers Blog results but exact date not directly verified from primary source

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — confirmed from GitHub README and direct backend code inspection
- Architecture: HIGH — patterns verified from React Router v7 docs and confirmed against actual backend field names
- Pitfalls: HIGH — most are derived from reading actual backend code (`auth.ts`), not speculation
- FedCM timeline: MEDIUM — confirmed from multiple secondary sources; official primary doc partially verified

**Research date:** 2026-03-06
**Valid until:** 2026-04-06 (stable ecosystem; GIS/FedCM migration already complete)

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| AUTH-01 | User can sign in with Google (same account as on Android) | `@react-oauth/google` `<GoogleLogin>` → `credentialResponse.credential` → `POST /api/auth/google` `{ id_token }` → JWT stored in memory |
| AUTH-02 | User stays logged in after refreshing the browser | `AuthProvider` reads `localStorage.refresh_token` on mount → `POST /api/auth/refresh` → new access token in state; `isLoading` guards against flash |
| AUTH-03 | User is redirected to login page if not authenticated | `<ProtectedRoute>` with `<Outlet>` checks `accessToken`; renders `<Navigate to="/login" replace>` when null |
</phase_requirements>
