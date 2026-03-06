# Phase 18: Node Editor + Sync - Context

**Gathered:** 2026-03-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Open a document from the list, display all its nodes as an editable nested tree, support inline formatting markers, keyboard shortcuts (Enter/Tab/Shift+Tab), zoom-into-subtree navigation, and bidirectional sync with the server (and by extension, Android). No search, bookmarks, file attachments, or toolbar in this phase — those are v0.9.

</domain>

<decisions>
## Implementation Decisions

### Formatting rendering
- Visible markers only — **word**, _word_, ~~word~~ stay as typed; no visual transformation applied
- Supported tokens: `**bold**`, `_italic_`, `~~strikethrough~~` (no backtick code)
- Paste from rich text (HTML): convert bold/italic/strikethrough HTML tags to their markdown marker equivalents on paste
- No formatting toolbar in v0.8 — users type markers directly

### Sync — load
- On mount: `GET /api/nodes?document_id=<id>` — full node list for the document
- Build tree in React state from `parent_id + sort_order` (same reconstruction logic as Android FlatNodeMapper, but for nested rendering)
- No HLC bookkeeping needed on the read side

### Sync — push
- Debounced push: ~2 seconds after the last keystroke, push pending changes via `POST /api/sync/push`
- Push payload uses full HLC-bearing node records (same protocol as Android)
- Web generates real HLC timestamps — port `hlc.ts` from `backend/src/hlc.ts` (~30 lines) into `web/src/sync/hlc.ts`
- Device ID: stable UUID in `localStorage` under key `device_id` (already used by auth — reuse `getOrCreateDeviceId()` from `web/src/auth/api.ts`)
- Changed nodes are tracked in a pending-changes set; flush clears it

### Sync status indicator
- Small text indicator in the page header: "Saved" / "Saving..." / "Offline"
- "Offline" when a push fails (network error); clears to "Saved" on next successful push

### Node row layout
- Nested tree with disclosure chevrons — NOT a flat list with depth padding
- Nodes with children: show a ▾ (expanded) or ▸ (collapsed) chevron glyph — clicking it collapses/expands
- Leaf nodes: show a ● bullet glyph — clicking it zooms into that node's subtree
- Nodes with children can also be zoomed by clicking the ▾/▸ glyph area after expanding (or via a separate click target — Claude's discretion on exact UX)
- Children rendered as DOM children of the parent row (true nested structure)
- Collapsed state (`collapsed` field) is respected on load; toggling updates local state and queues a push

### Keyboard shortcuts
- Enter in a node input: create a new sibling node immediately below, focus it (EDIT-03)
- Tab: indent current node — make it a child of the node immediately above it (EDIT-04)
- Shift+Tab: outdent current node — promote it one level up (EDIT-05)
- All three intercepted via `onKeyDown` with `event.preventDefault()` — one `<input>` per node

### Page header
- Left: ← Back link (navigates to `/`, the document list)
- Center/left: document title (read-only in this phase)
- Right: sync status indicator ("Saved ✓" / "Saving..." / "Offline")
- When zoomed into a subtree: Back navigates via browser history (query param approach); zoomed node's content shown as a breadcrumb heading below the header bar

### Zoom navigation
- URL encoding: `/editor/:docId?root=<nodeId>` — query param approach
- Clicking a glyph pushes a new history entry with `?root=<nodeId>`; browser Back zooms out
- When `?root` param is present: filter tree to only the subtree rooted at that node (BFS, same as Android `filterSubtree()`)
- Zoomed view shows the root node's content as a heading above the child list

### Claude's Discretion
- Exact Tailwind class choices (colors, spacing, indentation per nesting level)
- Whether the collapse toggle and zoom-in click are on the same glyph element or separate hit targets
- Exact debounce implementation (useRef timer vs. lodash.debounce)
- Error state display when initial node load fails
- How concurrent debounced pushes are serialized (queue vs. single inflight flag)

</decisions>

<specifics>
## Specific Ideas

- Nested tree layout mirrors standard outliner apps (Workflowy, Dynalist) — not the flat-list-with-indent approach used on Android
- HLC port: `backend/src/hlc.ts` is the reference implementation; the web version should be functionally identical so clocks stay compatible
- `getOrCreateDeviceId()` already exists in `web/src/auth/api.ts` — the web editor reuses this as its sync device ID

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `useAuth()` / `AuthContext` — provides `state.accessToken` for all API calls; already used in DocumentListPage
- `getOrCreateDeviceId()` in `web/src/auth/api.ts` — stable device UUID for HLC and sync push payload
- `web/src/App.tsx` — `NodeEditorPage` stub at `/editor/:id` is the integration point; replace with real component
- `backend/src/hlc.ts` — HLC implementation to port to `web/src/sync/hlc.ts`

### Established Patterns
- Direct `fetch()` with `Authorization: Bearer <token>` header — no API client layer
- Per-feature React state with `useEffect` for data loading (see `DocumentListPage.tsx`)
- Tailwind v4 via `@tailwindcss/vite` — no config file, utility classes only
- Test stack: Vitest + Testing Library + jsdom (see `DocumentListPage.test.tsx` for patterns)

### Integration Points
- `web/src/App.tsx`: replace `NodeEditorPage` stub with real import
- Backend endpoints ready:
  - `GET /api/nodes?document_id=<id>` — load all nodes
  - `GET /api/documents` — to fetch document title for the header
  - `POST /api/sync/push` — push changed nodes (takes `{ nodes: NodeSyncRecord[], documents: [], bookmarks: [], settings: null }`)
- Node shape from backend: `{ id, document_id, content, parent_id, sort_order, collapsed, deleted_at, content_hlc, parent_id_hlc, sort_order_hlc, collapsed_hlc, deleted_hlc, updated_at }`
- `device_id` and `last_sync_hlc` tracking: store in `localStorage` (keyed by document or global)

</code_context>

<deferred>
## Deferred Ideas

- Formatting toolbar (wrap selection in markers) — v0.9
- Search in the web editor — v0.9 (FTS4 is Android-specific; web needs different approach)
- Bookmarks, file attachments, export in web — v0.9
- Offline PWA / service worker — not in scope for v0.8 (self-hosted internal tool)
- Document title editing from the editor header — v0.9
- Backtick `code` formatting — not requested for v0.8

</deferred>

---

*Phase: 18-node-editor-sync*
*Context gathered: 2026-03-06*
