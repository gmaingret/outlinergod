# Phase 18: Node Editor + Sync — Research

**Researched:** 2026-03-06
**Domain:** React tree rendering, inline text editing, keyboard shortcuts, HLC sync
**Confidence:** HIGH

## Summary

Phase 18 builds the node editor page for the web client. The stack is fully decided: React 19 + Vite 7 + Tailwind v4, with direct `fetch()` calls and no state management library. The data model and sync protocol are the same as Android (HLC LWW, `POST /api/sync/push`), so the primary research task is identifying the correct React patterns for each sub-problem: nested tree rendering from a flat `parent_id + sort_order` list, `<input>` per node with `onKeyDown` interception, debounced sync push, and URL-based zoom navigation via react-router-dom v7.

The CONTEXT.md decisions lock all major choices. There are no alternative libraries to explore. Research confirms all locked choices are sound and provides verified implementation patterns from the existing codebase.

**Primary recommendation:** One `NodeEditorPage` component backed by pure helper functions (`buildTree`, `filterSubtree`, `insertSiblingNode`, `indentNode`, `outdentNode`) that are fully unit-testable outside React. The component calls `useEffect` on mount and a debounced `useRef`-timer flush on every content change.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Formatting rendering**
- Visible markers only — `**word**`, `_word_`, `~~word~~` stay as typed; no visual transformation
- Supported tokens: `**bold**`, `_italic_`, `~~strikethrough~~` (no backtick code)
- Paste from rich text (HTML): convert bold/italic/strikethrough HTML tags to markdown marker equivalents on paste
- No formatting toolbar in v0.8 — users type markers directly

**Sync — load**
- On mount: `GET /api/nodes?document_id=<id>` — full node list for the document
- Build tree in React state from `parent_id + sort_order` (same reconstruction logic as Android FlatNodeMapper, but for nested rendering)
- No HLC bookkeeping needed on the read side

**Sync — push**
- Debounced push: ~2 seconds after the last keystroke, push pending changes via `POST /api/sync/push`
- Push payload uses full HLC-bearing node records (same protocol as Android)
- Web generates real HLC timestamps — port `hlc.ts` from `backend/src/hlc/hlc.ts` (~30 lines) into `web/src/sync/hlc.ts`
- Device ID: stable UUID in `localStorage` under key `device_id` — reuse `getOrCreateDeviceId()` from `web/src/auth/api.ts`
- Changed nodes are tracked in a pending-changes set; flush clears it

**Sync status indicator**
- Small text indicator in page header: "Saved" / "Saving..." / "Offline"
- "Offline" when push fails (network error); clears to "Saved" on next successful push

**Node row layout**
- Nested tree with disclosure chevrons — NOT a flat list with depth padding
- Nodes with children: `▾` (expanded) or `▸` (collapsed) chevron — clicking collapses/expands
- Leaf nodes: `●` bullet glyph — clicking zooms into that node's subtree
- Nodes with children can also be zoomed (Claude's discretion on exact UX)
- Children rendered as DOM children of the parent row (true nested structure)
- Collapsed state respected on load; toggling updates local state and queues a push

**Keyboard shortcuts**
- Enter: create new sibling node immediately below, focus it (EDIT-03)
- Tab: indent — make current node child of the node immediately above (EDIT-04)
- Shift+Tab: outdent — promote one level up (EDIT-05)
- All intercepted via `onKeyDown` with `event.preventDefault()` — one `<input>` per node

**Page header**
- Left: ← Back link (navigates to `/`)
- Center/left: document title (read-only)
- Right: sync status indicator
- When zoomed: Back navigates via browser history; zoomed node's content shown as breadcrumb heading

**Zoom navigation**
- URL encoding: `/editor/:docId?root=<nodeId>` — query param
- Clicking a glyph pushes new history entry with `?root=<nodeId>`; browser Back zooms out
- When `?root` param present: BFS filter to subtree rooted at that node
- Zoomed view shows root node content as heading above child list

### Claude's Discretion
- Exact Tailwind class choices (colors, spacing, indentation per nesting level)
- Whether collapse toggle and zoom-in click are on the same glyph element or separate hit targets
- Exact debounce implementation (useRef timer vs. lodash.debounce)
- Error state display when initial node load fails
- How concurrent debounced pushes are serialized (queue vs. single inflight flag)

### Deferred Ideas (OUT OF SCOPE)
- Formatting toolbar (wrap selection in markers) — v0.9
- Search in the web editor — v0.9
- Bookmarks, file attachments, export in web — v0.9
- Offline PWA / service worker — not in scope for v0.8
- Document title editing from the editor header — v0.9
- Backtick `code` formatting — not requested for v0.8
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| EDIT-01 | User can open a document and read all its nodes | `GET /api/nodes?document_id=<id>` → `buildTree()` from `parent_id + sort_order` |
| EDIT-02 | User can type in a node to edit its content | `<input>` per node with `onChange`, pending-changes set, debounced push |
| EDIT-03 | Pressing Enter creates a new sibling node below | `onKeyDown` Enter → `insertSiblingNode()` + `crypto.randomUUID()` + `POST /api/sync/push` |
| EDIT-04 | Pressing Tab indents a node | `onKeyDown` Tab → `indentNode()` — reparent to previous sibling |
| EDIT-05 | Pressing Shift+Tab outdents a node | `onKeyDown` Shift+Tab → `outdentNode()` — reparent to grandparent |
| EDIT-06 | Bold, italic, strikethrough render inline as typed | Visible markers in `<input>` value; no VisualTransformation needed (plain text) |
| EDIT-07 | Click bullet glyph zooms into a node's subtree | `useNavigate` + `?root=<nodeId>` query param + `filterSubtree()` BFS |
| SYNC-01 | Document loads with latest server data | `useEffect` on mount, `GET /api/nodes?document_id=<id>` with `Authorization` header |
| SYNC-02 | Changes made in browser sync to server | Debounced `POST /api/sync/push` with full `NodeSyncRecord` records + HLC timestamps |
</phase_requirements>

---

## Standard Stack

### Core (already installed — no new dependencies needed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| React | 19.2.0 | Component tree, state, effects | Already in web/ |
| react-router-dom | 7.13.1 | `/editor/:id?root=<nodeId>` routing + history navigation | Already in web/ |
| Tailwind v4 | 4.2.1 | Styling via utility classes | Already in web/ — no config file |
| Vitest | 4.0.18 | Unit tests for pure helpers + component tests | Already in web/ |
| @testing-library/react | 16.3.2 | Component testing | Already in web/ |
| @testing-library/user-event | 14.6.1 | Keyboard event simulation | Already in web/ |

### No New Dependencies Required

All required functionality (UUID generation, HLC, debounce) is either:
- Already in the project (`getOrCreateDeviceId()`, `crypto.randomUUID()`)
- A direct port from `backend/src/hlc/hlc.ts` (~92 lines)
- Implementable with `useRef` timer (no lodash needed)

**Do NOT add:** lodash, tiptap, prosemirror, slate, draft-js, contenteditable wrappers, or any rich-text editor library. The decision is plain `<input>` with visible markers.

**Installation:**
```bash
# No new packages needed
```

---

## Architecture Patterns

### Recommended File Structure

```
web/src/
├── sync/
│   └── hlc.ts              # Port of backend/src/hlc/hlc.ts
├── pages/
│   ├── NodeEditorPage.tsx   # Main page component
│   └── NodeEditorPage.test.tsx
└── editor/
    ├── treeHelpers.ts        # buildTree, filterSubtree, insertSiblingNode, indentNode, outdentNode
    └── treeHelpers.test.ts   # Pure unit tests for all tree operations
```

Rationale: separating pure tree manipulation functions into `treeHelpers.ts` allows them to be unit-tested without rendering, following the exact same pattern used in the Android app (`FlatNodeMapper` as a pure function tested in isolation).

### Data Types

```typescript
// Source: backend/src/merge.ts + backend/src/routes/nodes.ts

// What GET /api/nodes returns (toNodeResponse shape)
interface NodeResponse {
  id: string
  document_id: string
  content: string
  note: string
  parent_id: string | null
  sort_order: string
  completed: boolean
  color: number
  collapsed: boolean
  deleted_at: number | null
  created_at: number
  updated_at: number
}

// What POST /api/sync/push accepts (NodeSyncRecord shape)
interface NodeSyncRecord {
  id: string
  document_id: string
  content: string
  content_hlc: string
  note: string
  note_hlc: string
  parent_id: string | null
  parent_id_hlc: string
  sort_order: string
  sort_order_hlc: string
  completed: number      // 0 or 1 (integer, not boolean)
  completed_hlc: string
  color: number
  color_hlc: string
  collapsed: number      // 0 or 1 (integer, not boolean)
  collapsed_hlc: string
  deleted_at: number | null
  deleted_hlc: string
  device_id: string
}
```

**CRITICAL:** `POST /api/sync/push` expects `completed` and `collapsed` as integers (0/1), not booleans. The GET response returns booleans, so conversion is required when building push records.

### Pattern 1: Tree Build from Flat List

```typescript
// Source: analysis of backend/src/routes/nodes.ts + Android FlatNodeMapper pattern

interface TreeNode extends NodeResponse {
  children: TreeNode[]
}

function buildTree(nodes: NodeResponse[]): TreeNode[] {
  const map = new Map<string, TreeNode>()
  const roots: TreeNode[] = []

  for (const n of nodes) {
    map.set(n.id, { ...n, children: [] })
  }

  for (const n of nodes) {
    const node = map.get(n.id)!
    if (n.parent_id && map.has(n.parent_id)) {
      map.get(n.parent_id)!.children.push(node)
    } else {
      roots.push(node)
    }
  }

  // Sort by sort_order (lexicographic — TEXT fractional index)
  const sortChildren = (nodes: TreeNode[]) => {
    nodes.sort((a, b) => a.sort_order < b.sort_order ? -1 : a.sort_order > b.sort_order ? 1 : 0)
    for (const n of nodes) sortChildren(n.children)
  }
  sortChildren(roots)

  return roots
}
```

**Key insight:** `sort_order` is TEXT fractional indexing — lexicographic comparison is correct. Do NOT convert to numbers. This is a hard rule from CLAUDE.md.

### Pattern 2: Filter Subtree (Zoom)

```typescript
// BFS from root node — matches Android filterSubtree() behavior
function filterSubtree(roots: TreeNode[], rootNodeId: string): TreeNode | null {
  const queue: TreeNode[] = [...roots]
  while (queue.length) {
    const node = queue.shift()!
    if (node.id === rootNodeId) return node
    queue.push(...node.children)
  }
  return null
}
```

### Pattern 3: Zoom Navigation with react-router-dom v7

```typescript
// Source: web/src/App.tsx + react-router-dom v7 docs
import { useNavigate, useSearchParams } from 'react-router-dom'

// Inside NodeEditorPage:
const [searchParams] = useSearchParams()
const navigate = useNavigate()
const rootNodeId = searchParams.get('root') // null = full document

// On glyph click:
function handleGlyphClick(nodeId: string) {
  navigate(`?root=${nodeId}`)  // pushes history entry; Back = zoom out
}
```

`useSearchParams` and `navigate('?root=...')` are both from react-router-dom v7 (already installed). No additional library needed. Browser Back automatically pops the history entry.

### Pattern 4: Enter / Tab / Shift+Tab Keyboard Handling

```typescript
// One <input> per node, onKeyDown intercepted
function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>, nodeId: string) {
  if (e.key === 'Enter') {
    e.preventDefault()
    insertSiblingNode(nodeId)      // creates new node, updates state, focuses new input
  }
  if (e.key === 'Tab' && !e.shiftKey) {
    e.preventDefault()
    indentNode(nodeId)             // reparents to previous sibling
  }
  if (e.key === 'Tab' && e.shiftKey) {
    e.preventDefault()
    outdentNode(nodeId)            // reparents to grandparent
  }
}
```

Focus management: use `inputRefs` map (`useRef<Map<string, HTMLInputElement>>`) to call `.focus()` on the newly created node's input after state update. Use `useEffect` or `useLayoutEffect` to focus after state settles.

### Pattern 5: Debounced Sync Push

```typescript
// useRef timer pattern (no lodash required)
const pendingChanges = useRef<Map<string, TreeNode>>(new Map())
const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

function queueChange(node: TreeNode) {
  pendingChanges.current.set(node.id, node)
  if (debounceTimer.current) clearTimeout(debounceTimer.current)
  debounceTimer.current = setTimeout(() => {
    flushPendingChanges()
  }, 2000)
}

async function flushPendingChanges() {
  if (pendingChanges.current.size === 0) return
  const toFlush = new Map(pendingChanges.current)
  pendingChanges.current.clear()

  setSyncStatus('saving')
  try {
    await fetch('/api/sync/push', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify({
        device_id: getOrCreateDeviceId(),
        nodes: Array.from(toFlush.values()).map(toSyncRecord),
        documents: [],
        bookmarks: [],
        settings: null,
      }),
    })
    setSyncStatus('saved')
  } catch {
    pendingChanges.current = new Map([...pendingChanges.current, ...toFlush]) // re-queue on failure
    setSyncStatus('offline')
  }
}
```

### Pattern 6: HLC Port

The file `web/src/sync/hlc.ts` is a direct copy of `backend/src/hlc/hlc.ts`. It is functionally identical — same format, same `hlcGenerate` / `hlcReceive` / `hlcParse` / `hlcCompare` / `resetHlcForTesting` exports. This ensures clocks stay compatible across Android, backend, and web.

### Pattern 7: Fetch Pattern (Established — from DocumentListPage.tsx)

```typescript
// Source: web/src/pages/DocumentListPage.tsx — established project pattern
useEffect(() => {
  if (!state.accessToken) return
  ;(async () => {
    try {
      const res = await fetch(`/api/nodes?document_id=${docId}`, {
        headers: { Authorization: `Bearer ${state.accessToken}` },
      })
      if (res.ok) {
        const data: { nodes: NodeResponse[] } = await res.json()
        setNodes(buildTree(data.nodes))
      }
    } catch {
      setLoadError(true)
    } finally {
      setIsLoading(false)
    }
  })()
}, [state.accessToken, docId])
```

**CRITICAL:** Use `async/await`, not `.then()` chains. Decision D-WEB-13 confirmed that `.then()` on mocked fetch values causes test failures in this project.

### Pattern 8: New Node Creation

When Enter is pressed in a node:
1. Generate new `id = crypto.randomUUID()` (built-in browser API, no import)
2. Generate `sort_order` between current node's sort_order and next sibling's sort_order using fractional indexing
3. The simplest approach for sort_order in this phase: assign `current_sort_order + "_"` + `Date.now()` as a temporary ordering — but this can drift. Better: use a simple in-memory fractional index increment.
4. Generate HLC timestamps for all fields via `hlcGenerate(deviceId)`
5. Insert into local React state tree immediately (optimistic), then queue for debounced push

**Sort order for new nodes:** The backend uses TEXT fractional indexing ("aV" for first, then keys between). For new sibling insertion, a safe approach is to re-assign sort orders to all siblings after mutation using an ordered string sequence — the same `recomputeSortOrders()` approach used in Android. Simple enough for v0.8: assign `String.fromCharCode(97 + index)` + "0" for positions 0-25, or use a fixed-format incrementing key like `"a" + index.toString().padStart(4, '0')`.

For this phase, the recommended approach is: on any structural change (Enter/Tab/Shift+Tab), recompute all sort_orders for affected siblings using their array index — identical to Android's `recomputeSortOrders()`.

### Anti-Patterns to Avoid

- **Using `contenteditable` div for text input:** Cannot be controlled by React. Cursor position management becomes a nightmare. The decision is plain `<input>` per node.
- **Building a single mega-component:** Split `NodeEditorPage` (data + state) from `NodeRow` (rendering). `NodeRow` receives `node`, `onContentChange`, `onKeyDown`, `onGlyphClick`, `onCollapseToggle` as props.
- **Global state for nodes:** React `useState` in `NodeEditorPage` is sufficient. No context, no Redux, no Zustand.
- **Parsing sort_order as float:** sort_order is TEXT. Lexicographic comparison is correct. Converting to float breaks ordering.
- **Sending booleans to sync/push:** The backend `NodeSyncRecord` uses `completed: number` and `collapsed: number` (0/1). The GET response returns booleans. Must convert on push.
- **Using `.then()` chains in useEffect:** Use async IIFE pattern (D-WEB-13).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| UUID generation | Custom UUID function | `crypto.randomUUID()` | Built into modern browsers; already used in `getOrCreateDeviceId()` |
| HLC timestamps | New HLC implementation | Port `backend/src/hlc/hlc.ts` verbatim | Format compatibility is critical; the backend validates HLC format |
| Device ID | New device ID storage | `getOrCreateDeviceId()` from `web/src/auth/api.ts` | Already stable in localStorage; same key used by auth |
| Route parsing | Custom URL parsing | `useSearchParams()` from react-router-dom | Already installed; handles history correctly |
| Fractional sort ordering | Custom sort key algo | Recompute all sibling sort orders on mutation | Avoids edge cases with between-key generation |

**Key insight:** Every hard problem in this phase already has a solved implementation somewhere in the codebase. The work is wiring them together correctly.

---

## Common Pitfalls

### Pitfall 1: boolean vs. integer in sync push payload
**What goes wrong:** The sync push route validates node records. `completed` and `collapsed` must be integers (0/1), not booleans. The GET `/api/nodes` response returns them as booleans. Sending `completed: true` to `POST /api/sync/push` will be accepted by the route but stored as `1` — actually this particular route's validation checks `typeof n.completed !== 'boolean'` in the `/batch` endpoint only. However the `sync/push` route uses `NodeSyncRecord` which types `completed` as `number`. The safest approach is to always convert: `completed: node.completed ? 1 : 0`.

**How to avoid:** Define a `toSyncRecord(node: TreeNode): NodeSyncRecord` conversion function and test it explicitly.

### Pitfall 2: Sort order comparison with string coercion
**What goes wrong:** `sort_order` values like `"aV"`, `"a0"`, `"b"` are TEXT fractional indexes. They sort correctly as strings but NOT as numbers. Any `parseFloat(sort_order)` call returns `NaN`. The sort in `buildTree` must use string comparison: `a.sort_order < b.sort_order ? -1 : 1`.

**Warning signs:** Nodes appearing in wrong order after load; all nodes collapsing to root level.

### Pitfall 3: Orphaned nodes after indent/outdent
**What goes wrong:** Tab (indent) makes the current node a child of the node immediately above it in the flat traversal order. If the "node above" is from a different subtree branch, the resulting parentage is semantically wrong. The correct implementation traverses the rendered tree in DFS order to find the preceding sibling.

**How to avoid:** Implement `indentNode` and `outdentNode` as pure functions on the tree structure with explicit tests for boundary conditions (first child, last child, root-level node).

### Pitfall 4: Debounce timer not cleared on unmount
**What goes wrong:** If the user navigates away before the 2-second debounce fires, the timeout callback runs after unmount. In React 18+ (Strict Mode double-invoke), this causes state updates on unmounted components.

**How to avoid:**
```typescript
useEffect(() => {
  return () => {
    if (debounceTimer.current) clearTimeout(debounceTimer.current)
    flushPendingChanges() // flush on unmount
  }
}, [])
```

### Pitfall 5: Focus management after state update
**What goes wrong:** Calling `input.focus()` synchronously after `setState()` focuses the old DOM element. React has not re-rendered yet. The input for the new node does not exist in the DOM.

**How to avoid:** Store the ID of the node that needs focus in a `useRef`, then in a `useEffect` that runs after render, look up the ref map and call `.focus()`.

### Pitfall 6: `useSearchParams` with react-router-dom v7
**What goes wrong:** `useSearchParams` is available in react-router-dom v7 (already installed). However, `navigate('?root=nodeId')` in v7 is relative navigation — it appends to the current path. This is correct behavior for `?root=` param. Do not use `navigate(`/editor/${docId}?root=${nodeId}`)` as that creates a new history entry with the full path — still works, but then Back navigates to document list rather than zooming out.

**How to avoid:** Use `navigate({ search: `?root=${nodeId}` })` to push a query-param-only history entry. This ensures Back zooms out (pops `?root`) rather than navigating to the document list.

**Verified:** react-router-dom v7 `navigate` with object form `{ search: '...' }` pushes a new entry preserving the current pathname — confirmed in react-router-dom v7 docs API.

### Pitfall 7: HLC module state between tests
**What goes wrong:** `backend/src/hlc/hlc.ts` uses module-level mutable state (`lastMs`, `lastCounter`). The web port will have the same pattern. Tests that call `hlcGenerate` will accumulate counter state across test cases.

**How to avoid:** Export `resetHlcForTesting()` in the web port (identical to backend) and call it in `beforeEach` in HLC tests.

### Pitfall 8: Paste event with HTML content
**What goes wrong:** The decision requires converting bold/italic/strikethrough HTML tags to markdown markers on paste. The `paste` event fires before the content is inserted. The `clipboardData.getData('text/html')` contains the HTML; `clipboardData.getData('text/plain')` contains the raw text.

**How to avoid:** Listen to `onPaste` on each `<input>`, call `e.preventDefault()`, get HTML from `e.clipboardData.getData('text/html')`, parse with `DOMParser`, walk the DOM tree, convert `<b>`/`<strong>` → `**`, `<em>`/`<i>` → `_`, `<s>`/`<del>`/`<strike>` → `~~`, insert resulting string into the input value manually.

---

## Code Examples

### Verified: Sync Push Request Shape

```typescript
// Source: backend/src/routes/sync.ts — handleSyncPush function
// Required fields at top-level:
{
  device_id: string,        // required — returns 400 if missing
  nodes: NodeSyncRecord[],  // can be []
  documents: [],
  bookmarks: [],
  settings: null,
}
```

### Verified: Node GET Response Shape

```typescript
// Source: backend/src/routes/nodes.ts — toNodeResponse()
{
  id: string,
  document_id: string,
  content: string,
  note: string,
  parent_id: string | null,
  sort_order: string,        // TEXT — lexicographic fractional index
  completed: boolean,        // toNodeResponse() converts 1→true
  color: number,
  collapsed: boolean,        // toNodeResponse() converts 1→true
  deleted_at: number | null,
  created_at: number,
  updated_at: number,
}
// Note: HLC fields are NOT returned by GET /api/nodes
// They must be generated fresh by the web client on push
```

**Critical finding:** `GET /api/nodes` does NOT return HLC fields (`content_hlc`, etc.). The web client must generate all HLC values using `hlcGenerate(deviceId)` when creating push records. This means the web client cannot do proper LWW merge with existing server data — it can only push its own changes and let the server merge. This is correct behavior for a new client creating/modifying nodes.

### Verified: Sync Push Node Record Construction

```typescript
// Every field must be present for the server's NodeSyncRecord type
function toSyncRecord(node: TreeNode, deviceId: string): NodeSyncRecord {
  const hlc = hlcGenerate(deviceId)
  return {
    id: node.id,
    document_id: node.document_id,
    content: node.content,
    content_hlc: hlc,
    note: node.note,
    note_hlc: hlc,
    parent_id: node.parent_id,
    parent_id_hlc: hlc,
    sort_order: node.sort_order,
    sort_order_hlc: hlc,
    completed: node.completed ? 1 : 0,    // boolean → integer
    completed_hlc: hlc,
    color: node.color,
    color_hlc: hlc,
    collapsed: node.collapsed ? 1 : 0,    // boolean → integer
    collapsed_hlc: hlc,
    deleted_at: node.deleted_at,
    deleted_hlc: '',
    device_id: deviceId,
  }
}
```

**Note:** Generating one HLC per node push (not per field) is correct for new edits. All fields get the same HLC. On the server, per-field LWW will use this HLC against stored HLCs.

### Verified: useSearchParams in Tests

```typescript
// Source: DocumentListPage.test.tsx — MemoryRouter pattern
// For NodeEditorPage tests that use searchParams:
render(
  <AuthProvider>
    <MemoryRouter initialEntries={['/editor/doc-1?root=node-5']}>
      <Routes>
        <Route path="/editor/:id" element={<NodeEditorPage />} />
      </Routes>
    </MemoryRouter>
  </AuthProvider>
)
```

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| contenteditable + range API | `<input>` per node (for non-formatting editors) | Dramatically simpler; cursor stable |
| Global rich-text editor (Tiptap) | Plain inputs + visible markers | Correct for this use case — no formatting toolbar in v0.8 |
| Float sort_order | TEXT fractional indexing ("aV", "b0") | Lexicographic sorting correct and stable |
| Polling sync | Debounced push on keystroke | Android pattern; simpler than websockets |

**Deprecated/outdated:**
- `React.createRef` for input focus: use `useRef<Map<string, HTMLInputElement>>` pattern
- `.then()` chains in useEffect: use async IIFE (D-WEB-13, confirmed in Phase 17)

---

## Open Questions

1. **Per-field HLC vs. single HLC per push record**
   - What we know: The server does per-field LWW. Each field has its own HLC column.
   - What's unclear: Should the web client generate one HLC per changed field, or one HLC for all fields of a changed node?
   - Recommendation: Generate one HLC per push operation (i.e., one call to `hlcGenerate` per node being pushed), assign that HLC to all modified fields. This is safe because all modifications happen in the same "operation." The server will accept any HLC that wins against the stored HLC.

2. **`last_sync_hlc` tracking**
   - What we know: The Android app tracks `last_sync_hlc` per document in a DataStore key. The web client currently stores nothing in localStorage beyond `device_id` and `refresh_token`.
   - What's unclear: Should the web client track `last_sync_hlc` for efficient pull (using `GET /api/sync/changes?since=...`)?
   - Recommendation: For v0.8, skip pull sync entirely. The page reloads fresh data on every mount via `GET /api/nodes`. Only push is implemented. This matches the CONTEXT.md "sync — load" decision which says "On mount: GET /api/nodes." No `last_sync_hlc` needed.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Vitest 4.0.18 |
| Config file | `web/vitest.config.ts` |
| Quick run command | `cd web && pnpm test --run` |
| Full suite command | `cd web && pnpm test --run` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| EDIT-01 | buildTree reconstructs nested structure from flat list | unit | `cd web && pnpm test --run treeHelpers` | ❌ Wave 0 |
| EDIT-01 | NodeEditorPage shows node content after GET resolves | component | `cd web && pnpm test --run NodeEditorPage` | ❌ Wave 0 |
| EDIT-02 | Typing in a node input updates value and queues pending change | component | `cd web && pnpm test --run NodeEditorPage` | ❌ Wave 0 |
| EDIT-03 | Enter in input creates new sibling node below | unit (insertSiblingNode) + component | `cd web && pnpm test --run` | ❌ Wave 0 |
| EDIT-04 | Tab indents node (becomes child of node above) | unit (indentNode) + component | `cd web && pnpm test --run` | ❌ Wave 0 |
| EDIT-05 | Shift+Tab outdents node (promotes one level) | unit (outdentNode) + component | `cd web && pnpm test --run` | ❌ Wave 0 |
| EDIT-06 | Bold/italic/strikethrough visible markers show in input | component | `cd web && pnpm test --run NodeEditorPage` | ❌ Wave 0 |
| EDIT-07 | Glyph click navigates to ?root=nodeId URL | component | `cd web && pnpm test --run NodeEditorPage` | ❌ Wave 0 |
| SYNC-01 | GET /api/nodes called on mount with correct document_id | component | `cd web && pnpm test --run NodeEditorPage` | ❌ Wave 0 |
| SYNC-02 | POST /api/sync/push called after 2s debounce with correct payload | component (fake timers) | `cd web && pnpm test --run NodeEditorPage` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `cd web && pnpm test --run`
- **Per wave merge:** `cd web && pnpm test --run`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `web/src/editor/treeHelpers.test.ts` — covers EDIT-01 tree building, EDIT-03 insertSiblingNode, EDIT-04 indentNode, EDIT-05 outdentNode, filterSubtree BFS
- [ ] `web/src/pages/NodeEditorPage.test.tsx` — covers EDIT-01 through EDIT-07, SYNC-01, SYNC-02
- [ ] `web/src/sync/hlc.ts` — HLC port (not a test file, but a Wave 0 prerequisite)
- [ ] `web/src/sync/hlc.test.ts` — HLC port validation against backend test vectors

---

## Sources

### Primary (HIGH confidence)
- `backend/src/routes/nodes.ts` — `toNodeResponse()` shape confirmed; GET `/api/nodes` response fields verified
- `backend/src/routes/sync.ts` — `handleSyncPush` confirmed; push payload schema verified including `device_id` required field
- `backend/src/merge.ts` — `NodeSyncRecord` interface confirmed; `completed`/`collapsed` are integers
- `backend/src/hlc/hlc.ts` — HLC implementation read in full; port strategy confirmed
- `web/src/pages/DocumentListPage.tsx` — established project patterns verified (async IIFE, fetch, useAuth)
- `web/src/auth/api.ts` — `getOrCreateDeviceId()` confirmed present and reusable
- `web/src/App.tsx` — `/editor/:id` route stub confirmed; replacement point identified
- `web/vitest.config.ts` — test config verified; jsdom environment, globals: true, setupFiles confirmed
- `web/package.json` — dependency versions confirmed; no new dependencies needed

### Secondary (MEDIUM confidence)
- react-router-dom v7 `useSearchParams` + `navigate({ search: '...' })` — verified via installed version and established usage in ProtectedRoute/DocumentListPage

### Tertiary (LOW confidence)
- None — all claims verified from source code in the repository

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already installed and in use
- Architecture patterns: HIGH — verified against existing codebase (DocumentListPage, hlc.ts, nodes.ts, sync.ts)
- Pitfalls: HIGH for boolean/integer conversion, sort_order string comparison, debounce cleanup (verified from source). MEDIUM for paste handling (CONTEXT.md decision, implementation approach standard)
- Sync protocol: HIGH — read sync.ts and merge.ts directly

**Research date:** 2026-03-06
**Valid until:** 2026-04-06 (stable stack; no fast-moving dependencies)
