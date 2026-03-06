# Project Research Summary

**Project:** OutlinerGod v0.8 — React Web Client
**Domain:** SPA web client integrated into existing Fastify v5 / Android monorepo
**Researched:** 2026-03-06
**Confidence:** HIGH

## Executive Summary

OutlinerGod v0.8 adds a React SPA web client to an already-complete Fastify v5 backend and Android app. The web client is a thin layer — it consumes the existing `/api/*` endpoints without any backend schema changes. The recommended approach is a `web/` directory at the repo root, built by Vite 7, served by the existing Fastify process via `@fastify/static` with an SPA catch-all. No second container, no nginx sidecar, no CORS configuration in production. The web client holds data in React state backed by TanStack Query; there is no local database and no offline capability.

The most consequential technical decisions are: (1) use Tiptap v2 with a custom extension to intercept Enter/Tab/Shift+Tab at the keyboard shortcut level — this is the only clean way to prevent ProseMirror's default paragraph-split behavior without fighting the editor internals; (2) use the same flat-list-with-depth-integers tree rendering pattern as Android, driven by `@dnd-kit/core` + `@dnd-kit/sortable`, and read horizontal drag delta to determine reparenting — no new algorithm is needed; (3) port `backend/src/hlc/hlc.ts` verbatim rather than reimplementing, as any format deviation silently breaks cross-device lexicographic comparison.

The two highest-risk areas are contenteditable cursor stability and HLC format parity. Cursor jumping on re-render is a well-documented React/contenteditable footgun that will make the editor unusable if not addressed from the start — the answer is Tiptap (which manages its own DOM) rather than raw contenteditable. HLC parity is lower risk but must be validated with test vectors before wiring sync. Google OAuth presents a moderate risk: the existing backend `verifyIdToken` must verify against the same web client ID used by the browser GIS library; creating a separate GCP credential without updating the backend audience array will cause 401 failures on every web login while Android continues to work normally.

## Key Findings

### Recommended Stack

The web client should use React 19 + Vite 7 + TypeScript 5 (via the standard `create vite` template), Tailwind CSS v4 (zero-config with `@tailwindcss/vite` plugin), and React Router v7 for client-side routing across the four screens (login, document list, editor, settings). Tiptap v2 provides per-node rich text with a proven keyboard shortcut interception API; `@dnd-kit/core` + `@dnd-kit/sortable` handle drag-and-drop using the same flat-list model already proven on Android. State management is intentionally minimal: TanStack Query v5 for server state, React context for auth, and module-level variables for the pending-changes queue — no Redux, no Zustand.

**Core technologies:**
- React 19 + Vite 7: UI framework + build tool — latest stable, sub-50ms HMR, `create vite` template scaffolds instantly
- TypeScript 5: type safety — bundled with Vite template, matches backend tsconfig pattern
- Tailwind CSS v4: styling — no config file, `@tailwindcss/vite` plugin, fastest incremental builds for custom outliner UI
- React Router v7: SPA routing — familiar API, no framework overhead, right-sized for 4 screens
- Tiptap v2 (`@tiptap/react`, `@tiptap/starter-kit`): per-node rich text — `addKeyboardShortcuts` returning `true` intercepts Enter/Tab cleanly; ProseMirror foundation is battle-tested
- `@dnd-kit/core` 6.3.1 + `@dnd-kit/sortable` 10.0.0: drag-and-drop — modular, pointer + touch sensors, same flat-list-tree pattern as Android
- `@react-oauth/google` 0.13.4: Google OAuth — wraps modern GIS SDK, `credential` field on callback is the id_token JWT
- TanStack Query v5: server state — loading/error, background refetch, cache invalidation
- `fractional-indexing` (rocicorp): sort_order — same `generateKeyBetween` algorithm as Android; `generateKeyBetween(null, null)` returns `"aV"` — confirmed match
- `@fastify/static` 9.0.0: static file serving — Fastify v5 compatible (v8.x is for v4); serves `web/dist` at `/`

### Expected Features

No FEATURES.md was produced. Feature scope is extracted from STACK.md and ARCHITECTURE.md.

**Must have (table stakes):**
- Google Sign-In via GIS (same credential flow as Android) — users have no other login path
- Document list (create, rename, delete) — core navigation screen
- Node editor with flat-list tree rendering, collapse/expand, zoom-in on glyph tap
- Inline bold/italic formatting via Tiptap (visible in editor)
- Enter = new sibling node; Tab/Shift+Tab = indent/outdent
- Drag-and-drop reordering with subtree-aware reparenting (horizontal drag delta)
- Sync pull on document open + debounced push on edit (uses existing `/api/sync/changes`)
- Settings screen (theme, density) — parity with Android

**Should have (competitive differentiators):**
- Note field per node (multi-line, Enter = newline, not new node)
- Zoom navigation with browser Back support (push `?rootNodeId=` query param)
- Silent session restore on page reload via refresh token
- Breadcrumb / "zoomed to" banner when viewing a subtree

**Defer (v2+):**
- Offline support / IndexedDB caching — explicitly out of scope ("no offline PWA")
- Search (FTS4 is Android-local only; no server-side search endpoint)
- Bookmarks screen (Android-specific workflow)
- File/image upload UI
- WorkManager-equivalent periodic background sync (browser has no equivalent; push-on-edit is sufficient)

### Architecture Approach

The web client lives at `web/` (parallel to `android/` and `backend/`), builds via Vite to `web/dist/`, and is copied into the Docker image via a two-stage Dockerfile. Fastify registers `@fastify/static` after all `/api/*` routes, with `wildcard: false`, and uses `setNotFoundHandler` to return `index.html` for any non-`/api/` 404. The web client is another "device" from the backend's perspective — it uses the same sync endpoints, the same JWT auth, and a stable `device_id` stored in `localStorage`. During development, Vite's dev proxy forwards `/api` to `localhost:3000`; in production, everything is same-origin.

**Major components:**
1. `web/src/auth/` — GSI initialization, token exchange with `/api/auth/google`, JWT in memory, refresh token in `localStorage`, silent restore on mount
2. `web/src/sync/hlc.ts` — verbatim port of `backend/src/hlc/hlc.ts`; must produce identical `<13-digit-ms>-<5-digit-counter>-<deviceId>` format
3. `web/src/editor/` — flat-list tree from DFS, one Tiptap instance per node row, dnd-kit for reordering, horizontal delta for reparenting
4. `web/src/shared/apiClient.ts` — fetch wrapper that injects Bearer token, handles 401 → refresh → retry
5. Fastify `backend/src/index.ts` — add `@fastify/static` registration + `setNotFoundHandler` (two lines, no route changes)

### Critical Pitfalls

1. **`@fastify/static` wildcard vs. SPA catch-all** — registering the static plugin with `wildcard: true` (default) adds its own `GET /*` route; any subsequent explicit catch-all throws "Route already declared" at startup and deep links return 404. Fix: `wildcard: false` + `setNotFoundHandler`. Must be the first thing tested in Phase 1 before any React routing is built.

2. **Google OAuth audience mismatch** — `verifyIdToken({ audience: GOOGLE_CLIENT_ID })` in `auth.ts` must match the client ID passed to `google.accounts.id.initialize`. If a new GCP credential is created for the browser without updating the backend audience, all web logins return 401 while Android continues to work. Fix: reuse the existing web client ID for both; if a separate browser credential is ever created, pass an array to `verifyIdToken`.

3. **GIS credential field mapping** — the GIS `CredentialResponse` object uses `response.credential`, not `response.id_token`. The backend field name is `id_token`. Sending `{ id_token: response.id_token }` sends `undefined` and gets a 400. Fix: always write `{ id_token: response.credential }` with an explicit comment.

4. **Contenteditable cursor jumping on re-render** — any `innerHTML` or `textContent` mutation to a `contenteditable` resets the caret. For an outliner, this makes typing impossible. Fix: use Tiptap (manages its own DOM; never re-rendered by React state changes) instead of raw `<div contenteditable>`. If raw contenteditable is used anywhere, treat it as uncontrolled and never write to the DOM from React state on the same tick as a user input event.

5. **HLC format parity** — any deviation in separator, padding, or counter width between the browser port and `backend/src/hlc/hlc.ts` silently breaks lexicographic comparison; a node edited on web and Android will merge incorrectly. Fix: copy `hlc.ts` verbatim, test with the same vectors used in Android's `HlcClockTest`.

## Implications for Roadmap

Based on combined research, a five-phase structure maps cleanly onto the dependency chain: serving infrastructure must precede auth, auth must precede the editor, the editor must precede sync wiring, and DnD is the final risk that builds on a working editor.

### Phase 1: Project Scaffold + Fastify Static Serving

**Rationale:** Everything else depends on the web client being served correctly by Fastify. This phase has the highest leverage — getting it wrong (wildcard conflict, wrong outDir, Vite proxy not matching production path) cascades into every subsequent phase. It must be proved with a real HTTP request to the production path before any React code is written.

**Delivers:** `web/` Vite scaffold with Tailwind v4 + React Router v7; Fastify `@fastify/static` registration with `wildcard: false` and `setNotFoundHandler`; two-stage Dockerfile updated; Vite dev proxy config for `/api`; `pnpm-workspace.yaml` updated; CI smoke test (`curl /health` and `curl /documents` both return 200 from the Docker container).

**Avoids:** Pitfall 1 (wildcard conflict), Pitfall "Vite proxy vs. production serving".

**Research flag:** Standard pattern — skip `/gsd:research-phase`. The `@fastify/static` SPA pattern is fully documented and the code snippets are already in ARCHITECTURE.md and PITFALLS.md.

### Phase 2: Google Auth + JWT Strategy

**Rationale:** Every screen except login requires a valid JWT. Auth is a hard dependency for all API calls. The OAuth audience question must be resolved before any backend integration work begins — discovering the audience mismatch after building the editor wastes significant time.

**Delivers:** `web/src/auth/` — GIS script load, `GoogleSignIn.tsx`, `AuthContext.tsx` (JWT in memory, refresh token in `localStorage`), `api.ts` (POST `/api/auth/google`, POST `/api/auth/refresh`); `web/src/shared/apiClient.ts` with 401 → refresh → retry; GCP web credential registered and `VITE_GOOGLE_CLIENT_ID` added to `.env`; silent session restore on page load; login screen with spinner on button click.

**Avoids:** Pitfall 2 (audience mismatch), Pitfall 3 (credential field mapping), Pitfall 7 (JWT in localStorage).

**Research flag:** Standard pattern for the auth flow itself. The one validation needed before coding: confirm the existing `GOOGLE_CLIENT_ID` in `.env` is the web client ID (not the Android credential); if it is, no backend change is needed.

### Phase 3: Node Editor — Tiptap + Tree Rendering

**Rationale:** The editor is the core of the product and the highest implementation risk. Contenteditable cursor stability (Pitfall 4) and IME composition (Pitfall 5) are best handled upfront by choosing Tiptap rather than raw contenteditable. The flat-list tree rendering (DFS, depth integers, collapse/expand, zoom) should be built and tested without DnD first — DnD adds its own complexity and should not be debugged alongside basic tree rendering.

**Delivers:** `web/src/editor/` — `useNodeTree.ts` (DFS flat list from nodes array), `NodeRow.tsx` (one Tiptap instance per node, `OutlinerKeymap` extension with Enter/Tab/Shift+Tab callbacks), `NodeEditor.tsx` (LazyColumn equivalent, zoom navigation via `?rootNodeId` query param); document list screen; settings screen; all screens wired into React Router.

**Uses:** Tiptap v2 (`@tiptap/react`, `@tiptap/starter-kit`), `fractional-indexing` for sort_order, TanStack Query for document/node fetch.

**Avoids:** Pitfall 4 (cursor jumping — avoided entirely by using Tiptap), Pitfall 5 (IME composition — Tiptap handles composition events internally).

**Research flag:** Standard pattern. Tiptap `addKeyboardShortcuts` is fully documented. The `OutlinerKeymap` extension code is already in STACK.md.

### Phase 4: Sync Integration

**Rationale:** Sync is the riskiest integration point because it involves a ported module (HLC) that must produce bit-identical output to the backend implementation. Testing HLC parity before connecting it to real data avoids corrupted timestamps in the live database. Sync should be wired after the editor is working so that data shapes are known.

**Delivers:** `web/src/sync/hlc.ts` (verbatim port of `backend/src/hlc/hlc.ts`); `web/src/sync/syncClient.ts` (pull and push wrappers); pull on document open with `since_hlc` from `localStorage`; debounced 2s push after edits; `device_id` stable UUID in `localStorage`; LWW conflict merge applied to push response; tests comparing browser HLC output against backend test vectors.

**Avoids:** Pitfall 6 (HLC `performance.now()` / float format), Pitfall "HLC format parity", Pitfall "sync pushes full document instead of delta".

**Research flag:** Needs validation of HLC test vector parity before ship. Run `hlc.test.ts` vectors against the browser implementation manually. Otherwise standard pattern.

### Phase 5: Drag-and-Drop

**Rationale:** DnD is isolated from other concerns and can be added to a working editor without risk to auth or sync. The performance risk (re-rendering entire tree during drag) is well-understood and the prevention strategy (`React.memo` on `NodeRow`, stable `items` array) is straightforward. Placing DnD last means the editor is already stable when drag interactions are added.

**Delivers:** `@dnd-kit/core` + `@dnd-kit/sortable` integration; `web/src/editor/dnd.ts` (horizontal delta reparenting, `reorderNodes` + `recomputeSortOrders` port); `DragOverlay` with separate presentational clone component; subtree-aware reordering; performance validation with 100-node document.

**Uses:** `@dnd-kit/core` 6.3.1, `@dnd-kit/sortable` 10.0.0, `@dnd-kit/modifiers` (vertical axis constraint).

**Avoids:** Pitfall "DnD DragOverlay duplicate hook" (separate clone component), Pitfall "DnD performance with many nodes" (`React.memo`, stable keys).

**Research flag:** Standard pattern. The flat-list tree + horizontal delta approach is documented in the official dnd-kit `SortableTree.tsx` example and matches the Android implementation exactly.

### Phase Ordering Rationale

- Phases 1 → 2 → 3: hard dependency chain (serving → auth → editor). Nothing works without serving; no API calls work without auth; the editor is the product.
- Phase 4 after Phase 3: sync shapes depend on node data structures; HLC testing requires the node model to be stable.
- Phase 5 last: DnD is additive to a working editor; isolating it prevents DnD bugs from masking editor bugs.
- Auth and serving are separated (not combined into Phase 1) because the Fastify static wiring and the GCP credential setup are independent tracks that can be verified independently.

### Research Flags

Phases needing deeper research during planning:
- **Phase 4 (Sync):** Validate that the existing `/api/auth/refresh` endpoint accepts the JSON body `{ refresh_token, device_id }` format the web client will send. Inspect `backend/src/routes/auth.ts` to confirm field names before writing the `AuthContext` refresh call.

Phases with standard patterns (skip `/gsd:research-phase`):
- **Phase 1:** `@fastify/static` SPA serving — fully documented, code snippets in research files.
- **Phase 2:** GIS + Tiptap keyboard shortcuts — official docs, high confidence.
- **Phase 3:** Flat-list tree rendering — proven Android pattern, direct port.
- **Phase 5:** dnd-kit flat-list-tree — official SortableTree example is the reference implementation.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Core libraries verified via npm; Vite 7, Tiptap 2, dnd-kit versions confirmed. Tiptap vs. Lexical is MEDIUM — project judgment call; Tiptap recommended for per-row outliner pattern. |
| Features | MEDIUM | Inferred from STACK.md and ARCHITECTURE.md (no FEATURES.md produced). Feature scope is well-understood from Android parity, but web-specific UX (breadcrumb, zoom affordance) needs product review. |
| Architecture | HIGH | Backend code inspected directly. SPA serving pattern, OAuth flow, and sync data flow are all confirmed against actual source files. |
| Pitfalls | HIGH | Pitfalls sourced from official React/Fastify/GIS issue trackers and direct backend code inspection. Contenteditable and IME issues are documented with GH issue numbers. |

**Overall confidence:** HIGH

### Gaps to Address

- **`/api/auth/refresh` request format:** Architecture.md describes the strategy but the exact field names the backend expects (`refresh_token`, `device_id`) need to be confirmed by reading `backend/src/routes/auth.ts` before Phase 2 coding begins. Low risk — a 10-minute code read resolves it.
- **GOOGLE_CLIENT_ID identity:** The `.env` file value for `GOOGLE_CLIENT_ID` must be confirmed as the web application credential (not the Android credential) before Phase 2. MEMORY.md notes: "GOOGLE_CLIENT_ID in local.properties and .env must be the web client ID, not the Android client ID" — this is already established practice but should be verified against the actual GCP Console before creating any new credentials.
- **Tiptap HTML vs. plain text storage:** STACK.md recommends storing node content as HTML (`editor.getHTML()`). The Android app stores content as plain text with `**bold**` markdown markers. Before Phase 3, decide whether the web client stores HTML (Tiptap-native) or markdown-with-markers (Android-compatible), since mixing formats will break cross-device rendering.

## Sources

### Primary (HIGH confidence)
- `backend/src/routes/auth.ts` — inspected directly; `id_token` field name, `verifyIdToken` audience
- `backend/src/index.ts` — inspected directly; existing route structure
- [vite.dev/blog/announcing-vite7](https://vite.dev/blog/announcing-vite7) — Vite 7, Node 20.19+ requirement
- [tiptap.dev — addKeyboardShortcuts](https://tiptap.dev/docs/editor/extensions/custom-extensions/create-new/extension) — returning `true` prevents default
- [github.com/clauderic/dnd-kit SortableTree.tsx](https://github.com/clauderic/dnd-kit/blob/master/stories/3%20-%20Examples/Tree/SortableTree.tsx) — flat-list tree + horizontal delta
- [tailwindcss.com/blog/tailwindcss-v4](https://tailwindcss.com/blog/tailwindcss-v4) — zero-config, `@tailwindcss/vite` plugin
- [github.com/fastify/fastify-static README](https://github.com/fastify/fastify-static/blob/main/README.md) — `wildcard: false` + `setNotFoundHandler`
- [fastify/fastify-static#64](https://github.com/fastify/fastify-static/issues/64) — wildcard catch-all conflict
- [facebook/react#2047](https://github.com/facebook/react/issues/2047) — contenteditable cursor jump
- [facebook/react#3926](https://github.com/facebook/react/issues/3926) — IME composition events
- [Google Developers — Verify the Google ID token on your server side](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token) — audience verification

### Secondary (MEDIUM confidence)
- [npmjs.com — @dnd-kit/core 6.3.1, @dnd-kit/sortable 10.0.0](https://www.npmjs.com/package/@dnd-kit/core) — version confirmation
- [npmjs.com — @react-oauth/google 0.13.4](https://www.npmjs.com/package/@react-oauth/google) — `credential` field name
- [npmjs.com — @fastify/static 9.0.0](https://www.npmjs.com/package/@fastify/static) — Fastify v5 compatibility
- [liveblocks.io — rich text editors 2025](https://liveblocks.io/blog/which-rich-text-editor-framework-should-you-choose-in-2025) — Tiptap vs. Lexical tradeoffs
- [cybersierra.co — JWT storage in React](https://cybersierra.co/blog/react-jwt-storage-guide/) — memory + cookie hybrid strategy

---
*Research completed: 2026-03-06*
*Ready for roadmap: yes*
