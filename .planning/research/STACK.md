# Stack Research

**Domain:** React + Vite web outliner client (adding to existing Fastify v5 backend)
**Researched:** 2026-03-06
**Confidence:** HIGH for build tooling/DnD/auth; MEDIUM for editor library (Tiptap vs Lexical tradeoff requires project judgment)

---

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| React | 19.x | UI framework | Latest stable; concurrent features, hooks-first; Vite 7 template targets React 19 |
| Vite | 7.x | Build tool + dev server | Native ESM, sub-50ms HMR, first-party React plugin; `npm create vite@latest -- --template react-ts` scaffolds instantly |
| TypeScript | 5.x (bundled with Vite) | Type safety | Matches backend tsconfig pattern; no separate install needed with Vite template |
| React Router v7 | 7.x | Client-side SPA routing | Familiar API, well-documented SPA mode, no framework overhead needed; TanStack Router adds type-safe URL params but overkill for 4-screen app |
| Tailwind CSS v4 | 4.x | Utility styling | No config file required in v4; `@tailwindcss/vite` plugin integrates directly; fastest incremental builds; fits custom outliner look without fighting a component library |

### Editor Library

**Recommendation: Tiptap v2 with custom Extension for key intercept**

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Tiptap | 2.x | Per-node rich text editor | React-first, `addKeyboardShortcuts` API intercepts Enter/Tab/Shift+Tab at the extension level by returning `true` (prevents default, stops propagation). ProseMirror foundation is battle-tested. Faster developer ramp than raw Lexical. |
| @tiptap/react | 2.x | React integration hooks | `useEditor` + `EditorContent` components; `editor.commands.*` API for programmatic bold/italic toggle |
| @tiptap/starter-kit | 2.x | Base extensions bundle | Bold, Italic, History (undo/redo) included; disable unwanted extensions (Heading, ListItem, etc.) via StarterKit config |

**Key intercept pattern:**
```typescript
// OutlinerEnterExtension.ts
import { Extension } from '@tiptap/core'

export const OutlinerKeymap = Extension.create({
  name: 'outlinerKeymap',
  addKeyboardShortcuts() {
    return {
      'Enter': ({ editor }) => {
        // Emit event / call prop — new sibling node creation is handled outside editor
        this.options.onEnter?.()
        return true  // prevents ProseMirror default (paragraph split)
      },
      'Tab': ({ editor }) => {
        this.options.onIndent?.()
        return true
      },
      'Shift-Tab': ({ editor }) => {
        this.options.onOutdent?.()
        return true
      },
    }
  },
})
```

Each outliner row renders its own `<EditorContent editor={useEditor({...})} />` — one Tiptap instance per node, same pattern as the Android per-node `BasicTextField`. This sidesteps complex document-model tree manipulation inside the editor.

### DnD Library

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| @dnd-kit/core | 6.3.1 | Drag sensor, context, events | Modular; pointer + touch sensors; DragOverlay for smooth rendering |
| @dnd-kit/sortable | 10.0.0 | Sortable list primitives | `useSortable` hook per row; `arrayMove` utility; same flat-list model as Android `reorderable 2.5.1` |
| @dnd-kit/modifiers | 6.x | Constrain drag axis | `restrictToVerticalAxis` for vertical-only drag; horizontal delta read separately for indent/outdent |
| @dnd-kit/utilities | 3.x | `CSS.Transform.toString` helper | Required for applying drag transform to rows |

**Tree reorder pattern:** Render tree as a flat array with `depth` integers (identical to Android). During drag, read `delta.x` from the drag event to compute indent/outdent. On drop, call the same `reorderNodes` + `recomputeSortOrders` logic as on Android — no new algorithm needed. Do NOT use `dnd-kit-sortable-tree` (last published 2 years ago, version 0.1.73, abandoned).

### Google OAuth

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| @react-oauth/google | 0.13.4 | Google Identity Services wrapper for React | Wraps the modern GIS SDK (not deprecated `GoogleSignInClient`). `useGoogleLogin` with `flow: 'implicit'` returns `credential` JWT. `GoogleLogin` button component available as fallback. |

**Auth flow to existing backend:**
```typescript
// The credential field from @react-oauth/google onSuccess IS the id_token JWT
// POST to /api/auth/google with body: { id_token: credentialResponse.credential }
// Backend already uses google-auth-library to verify — no backend changes needed
```

`GoogleOAuthProvider` wraps the app with `clientId` set to the **Web Application** OAuth 2.0 client ID from GCP (same project as Android client — must be a separate Web credential, not the Android credential).

### State Management

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Zustand | 5.x | Global client state (auth token, current document, node tree) | Minimal boilerplate; replaces Android's Orbit MVI for web; no reducers; store-per-slice pattern; `persist` middleware for localStorage token storage |
| TanStack Query v5 | 5.x | Server state (document list fetch, sync pull/push) | Handles loading/error states, background refetch, cache invalidation; pairs cleanly with Zustand for client state separation |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| fractional-indexing | 0.3.x | `generateKeyBetween()` for sort_order | When creating/moving nodes — same algorithm as Android's `FractionalIndex.generateKeyBetween`; ensures consistent `aV` base key |
| axios or native fetch | — | HTTP client for API calls | Use native `fetch` via TanStack Query's `queryFn`; axios only if interceptor complexity warrants it |
| @fastify/static | 9.0.0 | Backend — serve `/web/dist` | Already in stack; upgrade to v9 for Fastify v5 compatibility |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `@vitejs/plugin-react` | Babel-based Fast Refresh | Use over `plugin-react-swc` for Tiptap compatibility (SWC + ProseMirror can have edge cases) |
| `@tailwindcss/vite` | Tailwind v4 Vite integration | Replaces PostCSS pipeline; single plugin in `vite.config.ts` |
| Vitest | Unit tests for web client | Already used in backend; use `vitest` + `@testing-library/react` |
| `vite-tsconfig-paths` | Import path aliases (`@/`) | Cleaner imports; matches backend `tsconfig` path convention |

---

## Installation

```bash
# Scaffold web client at /web inside project root
npm create vite@latest web -- --template react-ts
cd web

# Tailwind v4
npm install tailwindcss @tailwindcss/vite

# React Router
npm install react-router-dom

# Tiptap editor
npm install @tiptap/react @tiptap/core @tiptap/starter-kit

# DnD
npm install @dnd-kit/core @dnd-kit/sortable @dnd-kit/modifiers @dnd-kit/utilities

# Google OAuth
npm install @react-oauth/google

# State + server state
npm install zustand @tanstack/react-query

# sort_order utility
npm install fractional-indexing

# Dev tools
npm install -D vitest @testing-library/react @testing-library/user-event vite-tsconfig-paths
```

```typescript
// vite.config.ts — key settings for Fastify static serving
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import tsconfigPaths from 'vite-tsconfig-paths'

export default defineConfig({
  plugins: [react(), tailwindcss(), tsconfigPaths()],
  build: {
    outDir: '../web/dist',  // Fastify serves this directory
    emptyOutDir: true,
  },
  base: '/',  // required for SPA routing — all assets served from root
})
```

```typescript
// backend/src/app.ts — @fastify/static SPA serving pattern
import fastifyStatic from '@fastify/static'
import { join } from 'path'
import { fileURLToPath } from 'url'

const __dirname = fileURLToPath(new URL('.', import.meta.url))

// Register BEFORE API routes
await fastify.register(fastifyStatic, {
  root: join(__dirname, '../../web/dist'),
  prefix: '/',
  wildcard: false,  // required: lets setNotFoundHandler serve index.html
})

// SPA fallback — any non-/api/* route returns index.html
fastify.setNotFoundHandler((req, reply) => {
  if (!req.url.startsWith('/api/')) {
    return reply.sendFile('index.html')
  }
  reply.status(404).send({ error: 'Not Found' })
})
```

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Tiptap | Lexical (Meta) | If building document-level editor (single instance, complex node types). For outliner rows (many small editors), Tiptap's simpler `addKeyboardShortcuts` is faster to implement correctly |
| Tiptap | ProseMirror directly | If you need complete control over schema and command dispatch with no abstraction overhead — but 3-5x more implementation code |
| @dnd-kit core + sortable | dnd-kit-sortable-tree | Never: package is abandoned (last release 2 years ago, no Fastify v5 era updates) |
| @dnd-kit core + sortable | react-beautiful-dnd | Never: officially deprecated by Atlassian in favor of pragmatic-drag-and-drop |
| @react-oauth/google | Raw GIS script load | Only if you need FedCM customization or the npm package adds unacceptable bundle weight |
| Zustand | Redux Toolkit | RTK is warranted for large teams needing strict action traceability; overkill for single-dev self-hosted tool |
| Tailwind v4 | shadcn/ui + Radix | shadcn/ui is excellent but adds component opinions; outliner UI is custom enough that utility-only Tailwind is cleaner |
| React Router v7 | TanStack Router | TanStack Router excels at type-safe URL params across many routes; for 4 screens (login, doc list, editor, settings) it's over-engineered |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `react-google-login` (npm package) | Officially deprecated; uses old Google Sign-In API which was shut down | `@react-oauth/google` |
| `react-beautiful-dnd` | Deprecated by Atlassian; no maintenance | `@dnd-kit/core` + `@dnd-kit/sortable` |
| `dnd-kit-sortable-tree` | Abandoned; 0.1.73, no updates in 2 years | Implement flat-list tree pattern directly with `@dnd-kit/sortable` using `SortableTree.tsx` from dnd-kit official examples |
| `fastify-static` (unscoped) | Old unscoped package; Fastify v5 requires `@fastify/static` | `@fastify/static` v9.x |
| `@fastify/vite` | SSR integration plugin — adds SSR complexity not needed for pure SPA serving | `@fastify/static` with `setNotFoundHandler` SPA fallback |
| Quill / react-quill | Quill 2.0 launched 2024 but react-quill hasn't kept pace; limited plugin architecture for custom key intercept | Tiptap |
| Slate.js | Steep learning curve, brittle APIs, smaller community in 2025 vs Tiptap/Lexical | Tiptap |

---

## Stack Patterns by Variant

**For per-node editor instances (outliner rows):**
- Create one Tiptap `useEditor` per node row
- Disable all extensions except Bold, Italic, History in StarterKit
- Pass `onEnter`, `onIndent`, `onOutdent` callbacks into `OutlinerKeymap` extension options
- Use `editor.getHTML()` / `editor.setContent()` for persistence, keeping formatting as HTML stored in node `content` field

**For the node tree state:**
- Keep flat array of nodes in Zustand store with `depth` integers
- Sync pushes the flat array to backend via existing `/api/nodes` endpoints
- TanStack Query handles pull-on-load (`useQuery` on mount) and push-on-change (`useMutation`)

**For @fastify/static SPA routing:**
- Register `@fastify/static` with `wildcard: false` before API routes
- Use `setNotFoundHandler` to return `index.html` for all non-`/api/*` paths
- React Router handles client-side routes (`/docs`, `/docs/:id`, `/settings`, `/login`)

---

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| `@fastify/static@9.x` | Fastify v5 | v8.x and below are for Fastify v4. Always use scoped `@fastify/static` not `fastify-static` |
| `@tiptap/react@2.x` | React 18 + 19 | Tiptap v2 is compatible with React 19; no peer dependency conflicts expected |
| `@dnd-kit/core@6.3.1` | React 18 + 19 | Last published ~1 year ago; stable, no known React 19 issues |
| `@dnd-kit/sortable@10.0.0` | `@dnd-kit/core@6.3.1` | Must be installed together; version 10 of sortable pairs with core 6.x |
| `@react-oauth/google@0.13.4` | React 18 + 19 | Uses GIS SDK (accounts.google.com/gsi/client); not the deprecated `gapi` |
| `Vite@7.x` | Node.js 20.19+ or 22.12+ | Vite 7 dropped Node 18. Confirm backend Docker image uses Node 20+ (backend currently uses Node 24 — compatible) |
| `fractional-indexing` | Any | Same algorithm as Android `FractionalIndex.generateKeyBetween`; `generateKeyBetween(null, null)` returns `"aV"` — matches Android behavior |

---

## Sources

- [npmjs.com/package/lexical](https://www.npmjs.com/package/lexical) — version 0.41.0 confirmed (MEDIUM confidence, npm search result)
- [npmjs.com/package/@react-oauth/google](https://www.npmjs.com/package/@react-oauth/google) — version 0.13.4, credential field = id_token JWT (MEDIUM confidence, search result)
- [npmjs.com/package/@dnd-kit/core](https://www.npmjs.com/package/@dnd-kit/core) — 6.3.1; @dnd-kit/sortable 10.0.0 (MEDIUM confidence, search result)
- [npmjs.com/package/@fastify/static](https://www.npmjs.com/package/@fastify/static) — 9.0.0 latest, published ~12 days ago (MEDIUM confidence, search result)
- [vite.dev/blog/announcing-vite7](https://vite.dev/blog/announcing-vite7) — Vite 7 latest, requires Node 20.19+ (HIGH confidence, official docs)
- [tiptap.dev/docs/editor/extensions/custom-extensions/create-new/extension](https://tiptap.dev/docs/editor/extensions/custom-extensions/create-new/extension) — `addKeyboardShortcuts` returning `true` prevents default (HIGH confidence, official docs)
- [lexical.dev/docs/concepts/commands](https://lexical.dev/docs/concepts/commands) — `KEY_ENTER_COMMAND` / `KEY_TAB_COMMAND` registration pattern (HIGH confidence, official docs)
- [github.com/clauderic/dnd-kit SortableTree.tsx](https://github.com/clauderic/dnd-kit/blob/master/stories/3%20-%20Examples/Tree/SortableTree.tsx) — flat-list tree + horizontal delta pattern (HIGH confidence, official example)
- [tailwindcss.com/blog/tailwindcss-v4](https://tailwindcss.com/blog/tailwindcss-v4) — `@tailwindcss/vite` plugin, no config file (HIGH confidence, official announcement)
- [liveblocks.io/blog rich text editors 2025](https://liveblocks.io/blog/which-rich-text-editor-framework-should-you-choose-in-2025) — Tiptap vs Lexical tradeoffs (MEDIUM confidence, industry analysis)
- [github.com/fastify/fastify-static README](https://github.com/fastify/fastify-static/blob/main/README.md) — `wildcard: false` + `setNotFoundHandler` SPA pattern (HIGH confidence, official repo)

---

*Stack research for: OutlinerGod v0.8 React web client*
*Researched: 2026-03-06*
