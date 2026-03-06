# Phase 15: Scaffold - Context

**Gathered:** 2026-03-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Set up the `web/` React + Vite project at the project root, integrate it into the existing Docker build pipeline, configure Fastify to serve the React SPA (including deep-link fallback), and enable a hot-reload dev workflow. No auth, no routes, no real UI — just the build plumbing and a Hello World page that proves the system works end-to-end.

</domain>

<decisions>
## Implementation Decisions

### web/ location
- Lives at project root (`web/`) alongside `android/` and `backend/` — not inside `backend/`
- Uses TypeScript (consistent with backend, Vite has first-class TS support)
- Standalone package: own `package.json`, own `node_modules/`. No pnpm workspace at root.

### Docker build strategy
- `docker-compose.yml` context changes from `./backend` to `.` (project root)
- Add a root `.dockerignore` to exclude `android/`, `.git/`, `.planning/`, `node_modules/`, etc. — keeps build context lean
- Three-stage Dockerfile (BuildKit parallel-friendly):
  - Stage 1 (`web-builder`): node:20-alpine, installs web deps, runs `pnpm build` in `web/`
  - Stage 2 (`ts-builder`): existing backend TS compile stage (unchanged)
  - Stage 3 (`runner`): lean image, copies `dist/` from ts-builder and `web/dist` from web-builder
- React bundle lands at `/app/web/dist` in the runner image
- Fastify serves `/app/web/dist` via `@fastify/static`

### Fastify static serving
- Replace the placeholder `GET /` route in `index.ts` with `@fastify/static` registration pointing at `/app/web/dist` (or `../web/dist` relative to `dist/` for dev)
- Full SPA fallback: serve `index.html` for any GET request that doesn't match `/api/*` or a real static file (deep links work from day one)
- Implementation: `setNotFoundHandler` or a catch-all `GET /*` route after static plugin registration

### Dev workflow
- Standalone: `cd web && pnpm dev` starts Vite at localhost:5173
- Vite proxies both `/api/*` and `/health` to `localhost:3000` in `vite.config.ts`
- Backend runs separately: `cd backend && pnpm dev` in another terminal
- No root-level concurrently script — two terminals is the expected workflow

### Scaffold depth
- Hello World only: minimal React page showing "OutlinerGod" text (no routing, no state, no components)
- Tailwind CSS installed and configured in the scaffold (Vite plugin) — later phases use it without setup
- React Router deferred to Phase 16 (Auth) — Phase 16 introduces routes and auth guard structure

</decisions>

<specifics>
## Specific Ideas

- No specific references — open to standard Vite scaffold (`pnpm create vite web --template react-ts`)
- Tailwind via `@tailwindcss/vite` plugin (Tailwind v4 Vite-native approach if available, else v3 PostCSS)

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `@fastify/static` already in `backend/package.json` dependencies — no new package needed for serving
- Existing multi-stage Dockerfile builder pattern can be extended with a new web-builder stage

### Established Patterns
- Backend: ESM (`"type": "module"`), pnpm, node:20-alpine, TypeScript strict mode
- Dockerfile: multi-stage build, `node:20-alpine`, pnpm via corepack, `apk add python3 make g++` for native deps
- `backend/src/index.ts` has a placeholder `GET /` route that must be removed when static serving is registered

### Integration Points
- `docker-compose.yml`: `context: ./backend` → `context: .` (root); `dockerfile: backend/Dockerfile`
- `backend/src/index.ts`: remove placeholder `GET /` route, register `@fastify/static` + SPA fallback
- Fastify static root path: `/app/web/dist` in production, relative path for dev (or env variable)
- `/api/*` route prefix already covers all API routes — static serving just needs to not intercept those

</code_context>

<deferred>
## Deferred Ideas

- React Router setup — Phase 16 (Auth) introduces routing when real screens exist
- Any real UI components, auth guards, or page structure — all deferred to Phase 16+
- pnpm workspace at root — not needed for v0.8; revisit if monorepo tooling becomes valuable

</deferred>

---

*Phase: 15-scaffold*
*Context gathered: 2026-03-06*
