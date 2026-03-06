# Phase 15: Scaffold - Research

**Researched:** 2026-03-06
**Domain:** React + Vite scaffold, Fastify SPA serving, Docker multi-stage build
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**web/ location**
- Lives at project root (`web/`) alongside `android/` and `backend/` — not inside `backend/`
- Uses TypeScript (consistent with backend, Vite has first-class TS support)
- Standalone package: own `package.json`, own `node_modules/`. No pnpm workspace at root.

**Docker build strategy**
- `docker-compose.yml` context changes from `./backend` to `.` (project root)
- Add a root `.dockerignore` to exclude `android/`, `.git/`, `.planning/`, `node_modules/`, etc. — keeps build context lean
- Three-stage Dockerfile (BuildKit parallel-friendly):
  - Stage 1 (`web-builder`): node:20-alpine, installs web deps, runs `pnpm build` in `web/`
  - Stage 2 (`ts-builder`): existing backend TS compile stage (unchanged)
  - Stage 3 (`runner`): lean image, copies `dist/` from ts-builder and `web/dist` from web-builder
- React bundle lands at `/app/web/dist` in the runner image
- Fastify serves `/app/web/dist` via `@fastify/static`

**Fastify static serving**
- Replace the placeholder `GET /` route in `index.ts` with `@fastify/static` registration pointing at `/app/web/dist` (or `../web/dist` relative to `dist/` for dev)
- Full SPA fallback: serve `index.html` for any GET request that doesn't match `/api/*` or a real static file (deep links work from day one)
- Implementation: `setNotFoundHandler` or a catch-all `GET /*` route after static plugin registration

**Dev workflow**
- Standalone: `cd web && pnpm dev` starts Vite at localhost:5173
- Vite proxies both `/api/*` and `/health` to `localhost:3000` in `vite.config.ts`
- Backend runs separately: `cd backend && pnpm dev` in another terminal
- No root-level concurrently script — two terminals is the expected workflow

**Scaffold depth**
- Hello World only: minimal React page showing "OutlinerGod" text (no routing, no state, no components)
- Tailwind CSS installed and configured in the scaffold (Vite plugin) — later phases use it without setup
- React Router deferred to Phase 16 (Auth) — Phase 16 introduces routes and auth guard structure

### Claude's Discretion

- Tailwind via `@tailwindcss/vite` plugin (Tailwind v4 Vite-native approach if available, else v3 PostCSS)
- No specific scaffold references — open to standard Vite scaffold (`pnpm create vite web --template react-ts`)

### Deferred Ideas (OUT OF SCOPE)

- React Router setup — Phase 16 (Auth) introduces routing when real screens exist
- Any real UI components, auth guards, or page structure — all deferred to Phase 16+
- pnpm workspace at root — not needed for v0.8; revisit if monorepo tooling becomes valuable
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SETUP-01 | Web client builds and deploys inside the existing Docker container at 192.168.1.50 | Docker multi-stage build; Dockerfile restructure; docker-compose context change |
| SETUP-02 | Web app is accessible at https://notes.gregorymaingret.fr in a browser | Fastify @fastify/static serves web/dist; SPA fallback via setNotFoundHandler |
| SETUP-03 | /api/* routes continue to work for the Android app (no regression) | API routes registered before static plugin; wildcard: false avoids route interception |
</phase_requirements>

---

## Summary

Phase 15 is purely infrastructure — no application logic. The work is three distinct, well-understood operations: (1) scaffold a `web/` Vite + React + TypeScript project with Tailwind v4, (2) restructure the Dockerfile and docker-compose to build both frontend and backend in a single image, and (3) configure `@fastify/static` to serve the React SPA with proper deep-link fallback while leaving all `/api/*` routes untouched.

Every technology choice is locked. Vite 7 (current: 7.3.1) and `@tailwindcss/vite` 4.x are the relevant versions. The `@fastify/static` plugin is already in `backend/package.json` at v8.0.4 (Fastify 5 compatible). The only nuance is the `wildcard: false` + `setNotFoundHandler` pattern for SPA fallback — using `wildcard: true` (the default) conflicts with Fastify's route matching and breaks the API routes.

**Primary recommendation:** Use `pnpm create vite web --template react-ts` for the scaffold, Tailwind v4 via `@tailwindcss/vite`, and `wildcard: false` + `setNotFoundHandler` for SPA serving. Follow the three-stage Dockerfile pattern exactly as specified in CONTEXT.md.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| vite | 7.3.1 (latest) | Build tool + dev server | First-class React + TS; fastest HMR; built-in proxy |
| react | 19.x (via create-vite) | UI framework | Project decision |
| react-dom | 19.x | DOM rendering | Required companion |
| typescript | 5.x | Type checking | Consistent with backend |
| @tailwindcss/vite | 4.2.1 | Tailwind CSS as Vite plugin | v4 Vite-native — no PostCSS config needed |
| tailwindcss | 4.x | Utility CSS framework | Required by @tailwindcss/vite |

### Supporting (dev workflow only)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| @vitejs/plugin-react | 4.x (bundled with template) | React fast refresh | Automatically included by react-ts template |

### Already in Backend (no new packages needed)
| Library | Version | Purpose |
|---------|---------|---------|
| @fastify/static | 8.0.4 | Serve web/dist + SPA fallback |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| @tailwindcss/vite (v4) | tailwindcss v3 + postcss | v3 requires tailwind.config.js + postcss.config.js; v4 is zero-config. Use v4. |
| wildcard: false + setNotFoundHandler | wildcard: true (default) | wildcard: true adds a `GET /*` route that intercepts API calls in some Fastify configurations. Do NOT use. |

**Installation (web/):**
```bash
pnpm create vite web --template react-ts
cd web
pnpm install
pnpm add tailwindcss @tailwindcss/vite
```

---

## Architecture Patterns

### Recommended Project Structure
```
outlinergod/
├── android/               # Android app (unchanged)
├── backend/               # Fastify backend (unchanged except index.ts)
│   ├── Dockerfile         # Now renamed to backend/Dockerfile (moved context to root)
│   └── src/index.ts       # Remove placeholder GET /, add @fastify/static
├── web/                   # React + Vite (new)
│   ├── package.json       # Standalone — no pnpm workspace
│   ├── vite.config.ts     # Tailwind plugin + proxy config
│   ├── index.html         # Vite entry point
│   └── src/
│       ├── main.tsx       # React root
│       ├── App.tsx        # Hello World component
│       └── index.css      # @import "tailwindcss"
├── docker-compose.yml     # context: . (root), dockerfile: backend/Dockerfile
└── .dockerignore          # Root-level (new)
```

### Pattern 1: Three-Stage Dockerfile
**What:** Parallel BuildKit stages — web build and backend TS compile independently; lean runner copies artifacts from both.
**When to use:** Any project with separate frontend build step alongside a Node.js backend.

```dockerfile
# Source: CONTEXT.md locked decision
# Stage 1: Build React SPA
FROM node:20-alpine AS web-builder
RUN corepack enable && corepack prepare pnpm@latest --activate
WORKDIR /web
COPY web/package.json web/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY web/ ./
RUN pnpm run build

# Stage 2: Compile backend TypeScript
FROM node:20-alpine AS ts-builder
RUN corepack enable && corepack prepare pnpm@latest --activate
WORKDIR /app
RUN apk add --no-cache python3 make g++
COPY backend/package.json backend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY backend/tsconfig.json ./
COPY backend/src/ ./src/
RUN pnpm run build

# Stage 3: Lean runner
FROM node:20-alpine AS runner
WORKDIR /app
COPY --from=ts-builder /app/dist ./dist
COPY --from=ts-builder /app/node_modules ./node_modules
COPY --from=ts-builder /app/package.json ./
COPY backend/drizzle ./drizzle
COPY --from=web-builder /web/dist ./web/dist
RUN mkdir -p /data/uploads && chown -R node:node /data
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD node -e "require('http').get('http://localhost:3000/health',(r)=>process.exit(r.statusCode===200?0:1))"
USER node
CMD ["node", "dist/server.js"]
```

**Critical:** The Dockerfile now lives at `backend/Dockerfile` but its build context is the project root. All `COPY` paths must be relative to the root context (e.g. `COPY backend/src/ ./src/`).

### Pattern 2: Fastify SPA Serving (wildcard: false + setNotFoundHandler)
**What:** Register `@fastify/static` with `wildcard: false`, then use `setNotFoundHandler` to serve `index.html` for any unmatched GET. API routes registered before the static plugin are never affected.
**When to use:** Every SPA served by Fastify 5.

```typescript
// Source: @fastify/static README + STATE.md CRITICAL PITFALLS
import fastifyStatic from '@fastify/static'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = fileURLToPath(new URL('.', import.meta.url))

// In buildApp(), AFTER all /api/* route registrations:

// Production: web/dist copied to /app/web/dist in Docker image
// Dev: relative path from backend/dist/ up to web/dist
const webDistPath = process.env.WEB_DIST_PATH ??
  join(__dirname, '..', 'web', 'dist')  // dev fallback

void app.register(fastifyStatic, {
  root: webDistPath,
  wildcard: false,           // CRITICAL: do not use wildcard: true
})

app.setNotFoundHandler((_req, reply) => {
  return reply.sendFile('index.html')
})
```

**Why `wildcard: false`:** With `wildcard: true` (default), `@fastify/static` registers a `GET /*` wildcard route. In Fastify 5, this wildcard can shadow or conflict with routes registered later (or even the same instance in some configurations). Using `wildcard: false` globs the dist directory at startup and creates explicit routes for each file — the `setNotFoundHandler` then catches everything else and returns `index.html`.

**Ordering rule:** All `app.register(createXxxRoutes...)` calls with `/api` prefix MUST appear before the `fastifyStatic` registration. `setNotFoundHandler` goes last.

### Pattern 3: Vite Dev Proxy
**What:** `server.proxy` in `vite.config.ts` forwards `/api/*` and `/health` to the running Fastify backend during development.
**When to use:** Any Vite project where the dev server and API backend run on different ports.

```typescript
// Source: https://vite.dev/config/server-options
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:3000',
        changeOrigin: true,
      },
      '/health': {
        target: 'http://localhost:3000',
      },
    },
  },
})
```

### Pattern 4: Tailwind v4 CSS Import
```css
/* src/index.css — replaces the vite template's default CSS */
@import "tailwindcss";
```
No `tailwind.config.js` needed. No `postcss.config.js` needed. No `content` glob array needed. This is the entire Tailwind configuration for v4.

### Pattern 5: Root .dockerignore
```
android/
.git/
.planning/
.claude/
node_modules/
*.md
```
Keep `backend/` and `web/` included (they are needed). The `backend/` directory's own `.dockerignore` is now irrelevant since the context is the project root — only the root `.dockerignore` applies.

### Anti-Patterns to Avoid
- **`wildcard: true` (default) with SPA fallback:** Registers a `GET /*` route that can intercept API calls. Always set `wildcard: false` for SPA serving.
- **Registering `@fastify/static` before API routes:** Static handler or `setNotFoundHandler` might shadow API routes. Register all `/api/*` routes first.
- **Keeping `context: ./backend` in docker-compose.yml:** The three-stage Dockerfile needs root context to `COPY web/` files. The context MUST change to `.`.
- **Not updating COPY paths in Dockerfile:** When context moves from `./backend` to `.`, every `COPY` that previously said `COPY src/ ./src/` must become `COPY backend/src/ ./src/`.
- **Using pnpm workspace:** CONTEXT.md explicitly defers this. Keep `web/package.json` standalone.
- **React Router in this phase:** Deferred to Phase 16. App.tsx is a single component with "OutlinerGod" text.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SPA index.html fallback | Custom route matching | `setNotFoundHandler` + `reply.sendFile('index.html')` | Built into @fastify/static decoration |
| CSS build pipeline | PostCSS config | `@tailwindcss/vite` plugin | v4 handles everything in one plugin |
| Dev API proxy | Express middleware, CORS hacks | `server.proxy` in vite.config.ts | Native Vite feature, zero dependencies |
| Multi-stage Docker | Shell scripts, separate compose services | Docker BuildKit multi-stage FROM | Standard pattern, better caching |

**Key insight:** Every problem in this phase has a built-in solution in the chosen tools. The risk is over-engineering (adding unnecessary configuration) not under-engineering.

---

## Common Pitfalls

### Pitfall 1: wildcard: true breaks /api/* routes
**What goes wrong:** Enabling `wildcard: true` (the default) on `@fastify/static` registers a `GET /*` wildcard route. When Fastify resolves an incoming request, this wildcard may match before or instead of the API routes, returning static file 404s for valid API calls.
**Why it happens:** Fastify wildcard routes have broad match priority. `@fastify/static` with wildcard enabled is designed for simple static sites, not SPA + API hybrid servers.
**How to avoid:** Always use `wildcard: false` when serving a SPA alongside API routes. Rely on `setNotFoundHandler` for the index.html fallback.
**Warning signs:** `/api/documents` returns a 404 HTML page instead of JSON.

### Pitfall 2: Docker COPY paths break after context change
**What goes wrong:** After changing `docker-compose.yml` from `context: ./backend` to `context: .`, all `COPY` instructions in the Dockerfile that assume backend-root context will fail with "file not found".
**Why it happens:** Docker COPY paths are relative to the build context, not the Dockerfile location.
**How to avoid:** Update every COPY in the Dockerfile to include the `backend/` prefix: `COPY backend/package.json ./`, `COPY backend/src/ ./src/`, `COPY backend/drizzle ./drizzle`.
**Warning signs:** `docker build` fails with `COPY failed: file not found in build context`.

### Pitfall 3: WEB_DIST_PATH not set in production / wrong relative path in dev
**What goes wrong:** Fastify can't find the React bundle. Either the production env var is missing, or the dev relative path is computed from the wrong `__dirname`.
**Why it happens:** The backend `dist/` directory has a different depth relationship to `web/dist` depending on whether code is running from source or compiled output.
**How to avoid:** Use `process.env.WEB_DIST_PATH` with a default that computes relative to `import.meta.url` (ESM). In Docker, set `WEB_DIST_PATH=/app/web/dist` or rely on the hardcoded default path since the Dockerfile always copies to `/app/web/dist`.
**Warning signs:** Fastify starts but returns 404 for `/` in production; blank page in dev even though `pnpm build` succeeded.

### Pitfall 4: backend/.dockerignore is now the wrong file
**What goes wrong:** The existing `backend/.dockerignore` only excludes `node_modules/`, `dist/`, `.env`, and `*.test.ts`. With the build context now at the project root, this file is no longer read — Docker looks for `.dockerignore` at the context root.
**Why it happens:** Docker reads `.dockerignore` from the build context root, not the Dockerfile location.
**How to avoid:** Create a root-level `.dockerignore` that excludes everything large or irrelevant: `android/`, `.git/`, `.planning/`, `.claude/`, etc. The `backend/` and `web/` directories must remain included.
**Warning signs:** Docker build context is very large (multi-GB) causing slow builds.

### Pitfall 5: Vite template includes extra CSS/assets that break Tailwind
**What goes wrong:** The default `pnpm create vite` template includes `App.css`, `index.css` with Vite logo styles, and image assets. These conflict with Tailwind v4's `@import "tailwindcss"` approach.
**Why it happens:** The template is a starter, not a blank slate.
**How to avoid:** After scaffolding: delete `App.css`, `public/vite.svg`, `src/assets/`, replace `index.css` with just `@import "tailwindcss";`, simplify `App.tsx` to the Hello World component.
**Warning signs:** Tailwind classes don't apply; console shows CSS parse errors.

### Pitfall 6: Vite 7 requires Node.js 20.19+ or 22.12+
**What goes wrong:** Build fails with Node version error in Docker web-builder stage.
**Why it happens:** Vite 7 dropped support for older Node versions (previously 18+, now 20.19+).
**How to avoid:** Use `node:20-alpine` in the web-builder stage — Docker Hub's `node:20-alpine` tracks 20.x LTS which is at 20.19+. Verify with `node --version` if uncertain.
**Warning signs:** `pnpm run build` fails in Docker with "unsupported Node.js version".

---

## Code Examples

Verified patterns from official sources:

### Hello World App.tsx (scaffold only)
```tsx
// src/App.tsx — Phase 15 scope: prove the system works, nothing more
function App() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <h1 className="text-4xl font-bold text-gray-900">OutlinerGod</h1>
    </div>
  )
}

export default App
```

### Fastify @fastify/static registration in index.ts
```typescript
// Source: @fastify/static README + STATE.md CRITICAL PITFALLS
// Place AFTER all app.register(create*Routes(...)) calls

import fastifyStatic from '@fastify/static'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'

const __dirname = fileURLToPath(new URL('.', import.meta.url))

// In production Docker: /app/web/dist
// In dev (tsx src/server.ts): ../../web/dist relative to src/
const webDistPath = process.env.WEB_DIST_PATH
  ?? join(__dirname, '..', '..', 'web', 'dist')

void app.register(fastifyStatic, {
  root: webDistPath,
  wildcard: false,
})

app.setNotFoundHandler((_req, reply) => {
  return reply.sendFile('index.html')
})
```

### docker-compose.yml (updated)
```yaml
# Source: CONTEXT.md locked decision
services:
  backend:
    build:
      context: .
      dockerfile: backend/Dockerfile
    volumes:
      - outlinergod-data:/data
    ports:
      - "3000:3000"
    env_file: .env
    restart: unless-stopped

volumes:
  outlinergod-data:
```

### Root .dockerignore
```
android/
.git/
.planning/
.claude/
.agents/
*.md
web/node_modules/
backend/node_modules/
backend/dist/
```

### vite.config.ts (complete)
```typescript
// Source: https://vite.dev/config/server-options + Tailwind v4 official docs
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:3000',
        changeOrigin: true,
      },
      '/health': {
        target: 'http://localhost:3000',
      },
    },
  },
})
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Tailwind v3 + PostCSS config + content globs | Tailwind v4 + `@tailwindcss/vite` plugin | v4.0 released Jan 2025 | Zero config, no tailwind.config.js needed |
| `wildcard: true` + catch-all route for SPA | `wildcard: false` + `setNotFoundHandler` | @fastify/static v6+ | Avoids route conflicts in Fastify 5 |
| Vite requires Node 18+ | Vite 7 requires Node 20.19+ or 22.12+ | Vite 7 (2025) | Must use node:20-alpine (20.x tracks ≥ 20.19) |

**Deprecated/outdated:**
- `tailwind.config.js`: Not needed for Tailwind v4. The `@tailwindcss/vite` plugin auto-detects content.
- `postcss.config.js`: Not needed for Tailwind v4 + Vite.
- `content: [...]` glob array: Replaced by v4's automatic detection.

---

## Open Questions

1. **WEB_DIST_PATH in dev mode**
   - What we know: In Docker, the path is `/app/web/dist`. In dev, Fastify runs via `tsx src/server.ts` from `backend/` — so `import.meta.url` resolves to `backend/src/server.ts`, making the relative path `../../web/dist`.
   - What's unclear: Does the developer ever run Fastify from a compiled `dist/server.js` locally? If so, `__dirname` is `backend/dist/`, and the relative path changes to `../../web/dist` (same result, but worth verifying).
   - Recommendation: Use `process.env.WEB_DIST_PATH` as primary, with a relative fallback. Document dev setup in a comment.

2. **pnpm version pinning in web-builder stage**
   - What we know: The backend Dockerfile uses `corepack prepare pnpm@latest --activate`. This works but means the pnpm version may drift.
   - What's unclear: Should `web/` pin a specific pnpm version in `packageManager` field of its `package.json`?
   - Recommendation: Let `pnpm create vite` populate the `packageManager` field (it usually does in recent versions). Match whatever the backend uses for consistency.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Vitest ^2.0.0 (backend, already installed) |
| Config file | `backend/vitest.config.ts` (if exists) or inline |
| Quick run command | `cd backend && pnpm test` |
| Full suite command | `cd backend && pnpm test` |

**Note:** The `web/` package has no test framework in Phase 15 (Hello World only). Validation is manual browser check + Docker smoke test. The backend's existing Vitest suite proves SETUP-03 (no regression on /api/* routes).

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SETUP-01 | Docker image builds successfully with web/dist included | smoke | `docker compose build` (exits 0) | ❌ Wave 0 |
| SETUP-02 | Browser loads React page at / | manual | navigate to localhost:3000 after build | ❌ manual |
| SETUP-03 | /api/* routes return correct responses | regression | `cd backend && pnpm test` | ✅ existing suite |

### Sampling Rate
- **Per task commit:** `cd backend && pnpm test` (regression guard for SETUP-03)
- **Per wave merge:** `docker compose build && curl http://localhost:3000/health`
- **Phase gate:** All three requirements verified before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] Manual smoke test checklist: browser at localhost:3000 (shows "OutlinerGod"), deep link at localhost:3000/foo/bar (still shows "OutlinerGod"), `curl localhost:3000/api/` returns JSON
- No new test files to create — existing backend suite covers SETUP-03; SETUP-01/02 are verified by build + browser

*(No automated test infrastructure gaps — existing Vitest suite covers the only automated requirement.)*

---

## Sources

### Primary (HIGH confidence)
- `https://vite.dev/guide/` — Vite 7.3.1 docs, Node.js requirements, template list
- `https://vite.dev/config/server-options` — server.proxy configuration, exact option names
- `https://tailwindcss.com/docs` — Tailwind v4 installation with @tailwindcss/vite, CSS import syntax
- `https://github.com/fastify/fastify-static` — wildcard option, setNotFoundHandler SPA pattern, sendFile decoration
- `backend/package.json` — confirms @fastify/static 8.0.4 already installed (no new package needed)
- `backend/src/index.ts` — placeholder GET / route to remove, existing route registration order

### Secondary (MEDIUM confidence)
- `https://www.npmjs.com/package/@tailwindcss/vite` — version 4.2.1 confirmed current (2026-03-06)
- STATE.md CRITICAL PITFALLS section — wildcard: false pattern, verified by project architect

### Tertiary (LOW confidence)
- WebSearch results on three-stage Docker builds — pattern is standard and well-documented, but specific flags not verified against Docker Buildkit v2 docs

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Vite 7.3.1 and @tailwindcss/vite 4.2.1 versions confirmed via npm/official docs; @fastify/static already present in package.json
- Architecture: HIGH — three-stage Dockerfile pattern is standard Docker BuildKit; @fastify/static wildcard:false SPA pattern verified against official README
- Pitfalls: HIGH — wildcard conflict documented in STATE.md CRITICAL PITFALLS; Docker COPY path issue is mechanical/deterministic; others derived from tool behavior

**Research date:** 2026-03-06
**Valid until:** 2026-06-06 (90 days — Vite and Tailwind release frequently but breaking changes at minor versions are rare)
