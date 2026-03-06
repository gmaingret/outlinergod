# Architecture Research

**Domain:** React web client integration into existing Fastify v5 monorepo
**Researched:** 2026-03-06
**Confidence:** HIGH

## Standard Architecture

### System Overview

```
Browser (https://notes.gregorymaingret.fr)
    |
    | HTTPS (reverse proxy / Caddy / nginx in front of Docker)
    v
┌─────────────────────────────────────────────────────────────┐
│              Docker container  :3000                         │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                  Fastify v5 (ESM)                    │   │
│  │                                                      │   │
│  │  GET /api/*  ───────► API route handlers             │   │
│  │  POST /api/* ───────► (auth, sync, docs, nodes…)     │   │
│  │                                                      │   │
│  │  GET /health ───────► health check (unchanged)       │   │
│  │                                                      │   │
│  │  GET /assets/* ─────► @fastify/static (hashed files) │   │
│  │  GET /* (other) ────► @fastify/static → index.html   │   │
│  └──────────────────────────────────────────────────────┘   │
│                            │                                 │
│                     ┌──────┴──────┐                          │
│                     │  SQLite DB  │  /data/outlinergod.db    │
│                     └─────────────┘                          │
└─────────────────────────────────────────────────────────────┘
                         │ (named volume)
                    outlinergod-data
```

The web client is built by Vite into `web/dist/`. During the Docker build, that compiled output is copied into the image. Fastify serves it at `/`. No second container, no nginx sidecar, no CORS configuration needed.

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| `web/` (new) | React + Vite source; builds to `web/dist/` | Vite 6, React 19, TypeScript, TanStack Query |
| Fastify `@fastify/static` (new registration) | Serve `web/dist/` at `/`, SPA fallback | `setNotFoundHandler` for non-API 404s |
| Existing `/api/*` routes | All backend logic — unchanged | Already complete |
| `web/src/auth/` | Google GSI init, token exchange with `/api/auth/google` | `@react-oauth/google` or raw GSI script |
| `web/src/sync/` | HLC clock (ported from `backend/src/hlc/hlc.ts`), pull/push | Direct fetch to `/api/sync/changes` |
| `web/src/store/` | In-memory state (TanStack Query cache) | No local SQLite; server is source of truth |

---

## Recommended Project Structure

```
outlinergod/
├── android/                        # Unchanged
├── backend/                        # Unchanged
│   ├── src/
│   │   └── index.ts                # ADD: @fastify/static registration here
│   └── Dockerfile                  # ADD: COPY ../web/dist ./web-dist
├── web/                            # NEW — Vite + React source
│   ├── index.html                  # Vite entry point
│   ├── vite.config.ts              # build.outDir: '../backend/web-dist'
│   ├── tsconfig.json
│   ├── package.json                # pnpm workspace member
│   └── src/
│       ├── main.tsx                # React root
│       ├── router.tsx              # TanStack Router or React Router v7
│       ├── auth/
│       │   ├── GoogleSignIn.tsx    # GSI button component
│       │   ├── AuthContext.tsx     # JWT + refresh token state
│       │   └── api.ts              # POST /api/auth/google, /api/auth/refresh
│       ├── sync/
│       │   ├── hlc.ts              # Port of backend/src/hlc/hlc.ts (browser)
│       │   └── syncClient.ts       # pull() and push() wrappers
│       ├── documents/
│       │   ├── DocumentList.tsx
│       │   └── api.ts
│       ├── editor/
│       │   ├── NodeEditor.tsx      # Main editor screen
│       │   ├── NodeRow.tsx         # Single node row
│       │   ├── useNodeTree.ts      # DFS flat list from nodes array
│       │   └── dnd.ts             # dnd-kit tree drag logic
│       └── shared/
│           ├── apiClient.ts        # fetch wrapper: injects Bearer, handles 401 refresh
│           └── types.ts            # NodeSyncRecord, DocumentSyncRecord shared types
├── docker-compose.yml              # ADD: web build step or pre-built copy
├── pnpm-workspace.yaml             # ADD: packages: [backend, web]
└── package.json                    # root workspace package.json (if not existing)
```

### Structure Rationale

- **`web/` at repo root:** Parallel to `android/` and `backend/` — consistent monorepo layout. Not nested inside `backend/` because the web app is a separate build artifact.
- **`vite.config.ts` points outDir to `../backend/web-dist`:** Simplifies Dockerfile: `COPY web-dist ./web-dist` from the same build context. Alternatively, build in Docker multi-stage. Both are valid — the Docker approach is more hermetic.
- **`web/src/sync/hlc.ts` is a port, not a shared package:** The HLC code is ~90 lines. Making a shared workspace package adds Vite/TSConfig complexity for marginal benefit. Port it and keep both in sync manually.
- **No local DB in web client:** The web client holds data only in React state / TanStack Query cache. On page load, pull from server. No IndexedDB, no service worker. This is consistent with "no offline PWA" constraint.

---

## Architectural Patterns

### Pattern 1: @fastify/static with SPA Fallback

**What:** Register `@fastify/static` after all `/api/*` routes. Use `setNotFoundHandler` to return `index.html` for any 404 that is NOT under `/api/`. Fastify's route resolution means `/api/*` routes are matched first — `@fastify/static` never sees them.

**When to use:** Always — this is the only correct approach for history-mode SPA routing without a separate nginx.

**Trade-offs:** Simple and zero-overhead. The only risk is accidentally returning `index.html` for a genuinely missing API route if the URL doesn't start with `/api/`. The guard in the not-found handler prevents this.

**Implementation in `backend/src/index.ts`:**
```typescript
import fastifyStatic from '@fastify/static'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = fileURLToPath(new URL('.', import.meta.url))

// Inside buildApp(), after all app.register(createXxxRoutes...) calls:

// Serve React build. In production, web-dist/ sits next to dist/ in /app/.
// In development, this path won't exist and the plugin will noop gracefully
// (or skip registration with an env guard).
const webDistPath = join(__dirname, '../../web-dist')

await app.register(fastifyStatic, {
  root: webDistPath,
  prefix: '/',
  wildcard: false,       // don't auto-add wildcard — we handle 404 ourselves
  decorateReply: true,
})

// SPA fallback: any route not matched by /api/* or a real file → index.html
app.setNotFoundHandler((_req, reply) => {
  // Guard: never serve index.html for missing API routes
  if (_req.url.startsWith('/api/')) {
    return reply.status(404).send({ error: 'Not found' })
  }
  return reply.sendFile('index.html')
})
```

Note: `wildcard: false` requires Fastify to glob the `web-dist/` directory at startup and create explicit routes for each file. For a Vite build with hashed assets this is fine; it avoids the wildcard catching `/api/` misses. Alternatively use `wildcard: true` and rely solely on the guard in `setNotFoundHandler`.

### Pattern 2: Google OAuth Web Flow (GSI One Tap / Button)

**What:** Load the Google Identity Services script. On credential callback, `response.credential` contains a JWT ID token. POST that value as `{ id_token: response.credential }` to the existing `/api/auth/google` endpoint. The backend validates it using `google-auth-library` and returns `{ token, refresh_token, user }`. The web client stores the JWT in memory (React context) and the refresh token in an httpOnly cookie set by the backend, OR in localStorage with informed trade-off (see JWT Storage below).

**When to use:** This is the only web OAuth path compatible with the existing backend endpoint, which already expects field name `id_token`.

**Trade-offs:** GSI One Tap is fast UX. The ID token flow avoids the redirect-based OAuth 2.0 dance. Requires a new "Web Application" OAuth 2.0 client in the same GCP project (separate from the Android client). The Web client ID must be registered as an "Authorized JavaScript origin" for the domain.

**Implementation in `web/src/auth/GoogleSignIn.tsx`:**
```typescript
// Load GSI script in index.html:
// <script src="https://accounts.google.com/gsi/client" async></script>

// In component:
declare const google: {
  accounts: {
    id: {
      initialize(config: { client_id: string; callback: (r: { credential: string }) => void }): void
      renderButton(el: HTMLElement, opts: object): void
      prompt(): void
    }
  }
}

useEffect(() => {
  google.accounts.id.initialize({
    client_id: import.meta.env.VITE_GOOGLE_CLIENT_ID,
    callback: async (response) => {
      const res = await fetch('/api/auth/google', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id_token: response.credential }),
      })
      const data = await res.json()
      // Store data.token in AuthContext, data.refresh_token per JWT strategy
    },
  })
  google.accounts.id.renderButton(buttonRef.current!, { theme: 'outline', size: 'large' })
}, [])
```

### Pattern 3: JWT Storage Strategy

**What:** Store the 1-hour access JWT in React context (in-memory). Store the 30-day refresh token in `localStorage`. On page load, attempt a refresh via `/api/auth/refresh` using the stored refresh token to obtain a fresh JWT.

**When to use:** Self-hosted internal tool at a known domain. Not a public SaaS handling financial data.

**Why this over httpOnly cookies:** httpOnly cookies for refresh tokens require the Fastify backend to set `Set-Cookie` headers with `SameSite=Strict; Secure; HttpOnly`. That works but adds backend changes (cookie parsing plugin, CORS cookie config). Since this is self-hosted with one user/domain, the CSRF risk is minimal. localStorage for the refresh token is the simpler implementation.

**Why not localStorage for the access JWT:** Access JWTs in memory means XSS cannot exfiltrate a long-lived token — only the short-lived (1h) JWT is in memory, and the 30-day refresh token in localStorage is the higher-value target. This is an acceptable trade-off for a personal self-hosted tool.

**Implementation in `web/src/auth/AuthContext.tsx`:**
```typescript
const REFRESH_TOKEN_KEY = 'og_refresh_token'

// On mount: try to restore session
const refresh = localStorage.getItem(REFRESH_TOKEN_KEY)
if (refresh) {
  const res = await fetch('/api/auth/refresh', {
    method: 'POST',
    body: JSON.stringify({ refresh_token: refresh }),
  })
  if (res.ok) {
    const { token, refresh_token } = await res.json()
    setJwt(token)                               // in React state
    localStorage.setItem(REFRESH_TOKEN_KEY, refresh_token)
  } else {
    localStorage.removeItem(REFRESH_TOKEN_KEY)  // expired / revoked
  }
}
```

### Pattern 4: Sync Architecture (Web Client)

**What:** On document open, pull changes since `lastSyncHlc` (stored in localStorage per user ID). After any edit, debounce 2 seconds then push pending changes. The web client maintains an in-memory list of pending changes (nodes/documents modified since last push).

**Why this matches existing backend:** The existing `GET /api/sync/changes?since=X&device_id=Y` and `POST /api/sync/changes` endpoints are already complete and work for any client. The web client uses a stable `device_id` (UUID generated once, stored in localStorage).

**Sync data flow:**
```
Page load
    → GET /api/sync/changes?since={lastHlc}&device_id={webDeviceId}
    → Merge response into React state (LWW per field, same rules as Android)
    → Render

User edits node
    → Update local React state immediately (optimistic)
    → Add to pendingChanges queue
    → Debounce 2s
    → POST /api/sync/changes { device_id, nodes: pendingChanges }
    → On response: clear pendingChanges, update lastSyncHlc in localStorage
```

**HLC in browser:** Port `backend/src/hlc/hlc.ts` verbatim to `web/src/sync/hlc.ts`. The format is `<13-digit-ms>-<5-digit-counter>-<deviceId>`. `Date.now()` works in the browser. The module-level mutable state (`let lastMs, lastCounter`) is valid in a browser module singleton. No changes to the algorithm.

### Pattern 5: Flat-List Tree Rendering + DnD

**What:** Same approach as Android. Convert the nodes array (from server) into a flat list with depth integers via DFS. Render as a virtualized list. Use `dnd-kit` for drag reordering. Horizontal drag distance determines reparenting (same logic as Android's `pointerInput`).

**When to use:** Matches the established pattern; keeps mental model consistent across platforms.

**Library:** `@dnd-kit/core` + `@dnd-kit/sortable`. The community package `dnd-kit-sortable-tree` provides a ready-made tree DnD implementation built on dnd-kit. Evaluate it first before building custom.

**Sort order:** Use the same fractional-indexing library (`fractional-indexing` from rocicorp) already used in `FractionalIndex.generateKeyBetween()` in Android. The npm package `fractional-indexing` (rocicorp) is the browser equivalent.

---

## Data Flow

### Request Flow

```
User navigates to https://notes.gregorymaingret.fr/documents/abc
    ↓
Fastify receives GET /documents/abc
    ↓
No /api/* match, no static file match → setNotFoundHandler
    ↓
reply.sendFile('index.html')  [web-dist/index.html]
    ↓
Browser loads index.html → loads /assets/main.[hash].js
    ↓
React Router matches /documents/abc → DocumentEditorPage
    ↓
useEffect: GET /api/documents/abc (JWT in Authorization header)
    ↓
Fastify: requireAuth → query SQLite → JSON response
    ↓
React state updated → render
```

### State Management

```
AuthContext (JWT, user profile)
    ↓ (provide to all children)
TanStack Query (server state cache per document/node list)
    ↔ fetch() to /api/* with JWT header
    ↔ invalidate on sync push completion

pendingChanges[] (module-level or React ref, NOT Query cache)
    ↓ debounce 2s
    → POST /api/sync/changes
    → clear queue
```

No Redux, no Zustand, no Jotai. TanStack Query handles server state. React context handles auth. Local module state handles pending changes queue. This is intentionally minimal — the web client is a thin layer over the existing API.

### Key Data Flows

1. **Auth flow:** GSI callback → `response.credential` (JWT ID token) → POST `/api/auth/google` → receive `{ token, refresh_token }` → store in AuthContext + localStorage.
2. **Initial load:** Mount → check localStorage for refresh token → POST `/api/auth/refresh` → restore session without re-login.
3. **Document open:** Navigate to editor → GET `/api/sync/changes?since={hlc}&device_id={id}` → merge into local React state → render flat node list.
4. **Node edit:** Keypress → optimistic state update → add to pendingChanges → debounce → POST `/api/sync/changes` → merge conflicts back → update lastSyncHlc.
5. **Zoom navigation:** Click bullet → push new route `/documents/{docId}?rootNodeId={nodeId}` → same editor, filter tree to subtree → browser Back = zoom out.

---

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 1 user (self-hosted) | Current approach is correct. SQLite handles this trivially. |
| 2-10 users | Same. SQLite WAL mode already enabled. No changes needed. |
| 10+ users | SQLite starts to strain under concurrent writes. Migrate to Postgres. Not in scope. |

This is a single-user self-hosted tool. Scaling is not a design concern for v0.8.

---

## Integration Points

### Existing Backend — What Changes

| Location | Change | Risk |
|----------|--------|------|
| `backend/src/index.ts` | Add `@fastify/static` registration + `setNotFoundHandler` | LOW — additive only, existing routes unchanged |
| `backend/Dockerfile` | Add `COPY web-dist ./web-dist` (built in prior stage or pre-built) | LOW |
| `docker-compose.yml` | Add build step for web client OR pre-build and commit `web/dist` | LOW |
| `.env` | Add `VITE_GOOGLE_CLIENT_ID` (web OAuth client ID) — web-only var, not used by backend | NONE — separate namespace |
| GCP Console | Register new "Web Application" OAuth 2.0 client for the domain | External dependency |

### Existing Backend — What Does NOT Change

- All `/api/*` route handlers: unchanged.
- `/api/auth/google` already accepts `{ id_token }` — the web client sends exactly this.
- `/api/sync/changes` GET and POST: unchanged. Web client is just another device.
- JWT secret and validation: unchanged. Same HS256 tokens.
- SQLite schema: unchanged.
- Android app: unchanged and unaffected.

### New External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Google Identity Services | Load `accounts.google.com/gsi/client` script; callback returns `response.credential` (JWT ID token) | Requires Web Application OAuth client in GCP; set Authorized JS origins to `https://notes.gregorymaingret.fr` |
| Google OAuth (GCP Console) | Create new Web Application credential; do NOT reuse Android credential | Android credential has package+SHA1 restriction; web credential needs JS origin authorization |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `web/` → `backend/` | HTTP fetch over localhost (same container) — no CORS | Vite dev server proxies `/api` to `localhost:3000` during development |
| `web/src/sync/hlc.ts` → `backend/src/hlc/hlc.ts` | Manual port (copy + adapt) | Must keep format `<13-digit-ms>-<5-digit-counter>-<deviceId>` identical. Test both together. |
| `web/src/shared/types.ts` → `backend` sync record types | Types duplicated in web (not shared via workspace package) | Acceptable for v0.8 scope. Revisit if types diverge significantly. |

---

## Docker Build Strategy

Two options. Recommend Option A for v0.8:

**Option A — Two-stage Dockerfile (hermetic, recommended):**
```dockerfile
# Stage 0: build web client
FROM node:20-alpine AS web-builder
RUN corepack enable && corepack prepare pnpm@latest --activate
WORKDIR /web
COPY web/package.json web/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY web/ ./
RUN pnpm run build   # outputs to dist/

# Stage 1: build backend (existing)
FROM node:20-alpine AS builder
# ... (existing backend build unchanged) ...

# Stage 2: runner (existing, modified)
FROM node:20-alpine AS runner
WORKDIR /app
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json ./
COPY drizzle ./drizzle
COPY --from=web-builder /web/dist ./web-dist   # ADD THIS LINE
# ... rest unchanged ...
```

`vite.config.ts` `build.outDir` stays as default `dist` — Docker copies it to `web-dist` in the image. Fastify reads from `web-dist/`.

**Option B — Pre-build web and commit dist:** Build locally, commit `web/dist/` to git, `COPY web/dist ./web-dist` in Dockerfile. Avoids Docker multi-stage complexity but pollutes git history. Not recommended.

---

## Development Workflow

During development, run two processes:

```bash
# Terminal 1: backend
cd backend && pnpm dev   # ts-node/tsx watching src/

# Terminal 2: web client
cd web && pnpm dev       # Vite dev server on :5173
```

`web/vite.config.ts` proxy config:
```typescript
export default defineConfig({
  server: {
    proxy: {
      '/api': 'http://localhost:3000',
      '/health': 'http://localhost:3000',
    },
  },
})
```

This avoids CORS entirely during development. In production, everything is same-origin (Fastify serves both).

---

## Anti-Patterns

### Anti-Pattern 1: Registering @fastify/static Before API Routes

**What people do:** Register `@fastify/static` at the top of `buildApp()`, before API routes.
**Why it's wrong:** Fastify registers routes in order. If the static plugin has a wildcard, it can intercept requests intended for API routes.
**Do this instead:** Register all `app.register(createXxxRoutes...)` calls first, then register `@fastify/static` last.

### Anti-Pattern 2: Serving index.html for /api/ 404s

**What people do:** `setNotFoundHandler` that unconditionally calls `reply.sendFile('index.html')`.
**Why it's wrong:** A typo in an API URL (e.g. `/api/sync/changess`) returns HTML instead of a JSON error. The fetch client gets confused and may throw a parse error with no useful message.
**Do this instead:** Guard on `req.url.startsWith('/api/')` — return `{ error: 'Not found' }` JSON for API paths, `index.html` for everything else.

### Anti-Pattern 3: Sharing the Android OAuth Client ID for the Web Client

**What people do:** Reuse `GOOGLE_CLIENT_ID` (the Android credential) in the web GSI initialization.
**Why it's wrong:** The Android credential is restricted to the package name + SHA-1 fingerprint. Google will reject ID tokens generated by the web app when verified against this client ID.
**Do this instead:** Create a separate "Web Application" OAuth 2.0 credential in GCP. Add both client IDs to the backend's `defaultVerifyGoogle` — or use Google's multi-audience verification. Simplest: the backend verifies against the Web client ID for web-originated tokens (same `/api/auth/google` endpoint; add `GOOGLE_WEB_CLIENT_ID` env var).

### Anti-Pattern 4: Reimplementing HLC Instead of Porting

**What people do:** Write a new HLC implementation for the browser from scratch.
**Why it's wrong:** Any deviation in format (padding, separator, counter width) silently breaks lexicographic comparison against server-stored HLC strings. A node edited on Android and the web will use HLCs that compare incorrectly.
**Do this instead:** Copy `backend/src/hlc/hlc.ts` verbatim to `web/src/sync/hlc.ts`. Only change: remove the `resetHlcForTesting` export if desired. Test that `hlcGenerate('web-device')` output matches the format of HLCs stored by Android.

### Anti-Pattern 5: Optimistic Updates Without Conflict Resolution

**What people do:** Update React state immediately on edit, push to server, but do not apply server's conflict response.
**Why it's wrong:** If Android and web edit the same node concurrently, the server returns the merged (winning) value in the push response `conflicts` field. Ignoring it means the web shows stale data.
**Do this instead:** After POST `/api/sync/changes`, merge `response.conflicts` back into local state using the same LWW rule (higher HLC wins). Mirror the Android `NodeMerge.kt` logic.

---

## Sources

- [@fastify/static GitHub — wildcard and setNotFoundHandler patterns](https://github.com/fastify/fastify-static)
- [Google Identity Services — display button, response.credential field](https://developers.google.com/identity/gsi/web/guides/display-button)
- [JWT storage: httpOnly cookies vs localStorage — OWASP recommendation for hybrid approach](https://dev.to/cotter/localstorage-vs-cookies-all-you-need-to-know-about-storing-jwt-tokens-securely-in-the-front-end-15id)
- [dnd-kit — tree drag and drop for React](https://dndkit.com/)
- [dnd-kit-sortable-tree — community tree component](https://github.com/Shaddix/dnd-kit-sortable-tree)
- [fractional-indexing (rocicorp) — browser-compatible fractional index](https://github.com/rocicorp/fractional-indexing)
- [React + dnd-kit tree implementation pattern](https://dev.to/fupeng_wang/react-dnd-kit-implement-tree-list-drag-and-drop-sortable-225l)
- [Fastify SPA routing — GitHub issue discussion](https://github.com/fastify/help/issues/74)

---
*Architecture research for: React web client integration into Fastify v5 monorepo*
*Researched: 2026-03-06*
