# Phase 19: Drag-and-Drop - Context

**Gathered:** 2026-03-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Add drag-to-reorder and drag-to-reparent to the existing web node editor. Users drag any node (by a grip handle) to a new vertical position and horizontal depth. The entire subtree (including collapsed children) moves with the parent. After a drop, the new sort_order and parent_id values are synced to the server via the existing push pipeline. No new backend endpoints — this is a pure frontend change.

</domain>

<decisions>
## Implementation Decisions

### Render architecture
- Switch NodeEditorPage from the recursive nested `NodeRow` renderer to a flat list with depth-based `paddingLeft`
- Flat list = DFS-ordered array of `{ node: TreeNode, depth: number }` — collapsed nodes' descendants excluded from array
- Visual appearance is identical to the current nested look (same indentation) but DOM is a single flat list
- DnD logic and the flat row renderer extracted to `web/src/editor/FlatNodeList.tsx`; NodeEditorPage stays focused on data loading and sync
- `reorderNode()` pure function added to `treeHelpers.ts` alongside `insertSiblingNode`, `indentNode`, `outdentNode`
  - Signature: `reorderNode(roots: TreeNode[], dragId: string, targetIndex: number, newDepth: number): TreeNode[]`
  - Computes new `parent_id` from context, updates `sort_order` via `recomputeSortOrders()` on affected siblings

### Subtree drag behavior
- When a collapsed parent is dragged, its hidden children move with it — tree integrity always maintained
- `sort_order` and `parent_id` updated on the dragged node only; children's `parent_id` chain remains intact (only the root of the dragged subtree gets a new `parent_id`)
- All affected nodes (dragged node + all descendants) queued into `pendingChanges` on drop for sync push

### Horizontal reparenting mechanic
- Horizontal displacement from drag start (`delta.x` from dnd-kit `onDragMove`) determines the target depth during drag
- Formula: `targetDepth = activeItem.depth + Math.round(delta.x / 24)` — each 24px = one indent level
- Depth is clamped: `max = nodeAbove.depth + 1` (can never be more than one level deeper than the node above the drop target)
- Can always drop at depth 0 (root level)
- New parent resolution:
  - If `targetDepth == nodeAbove.depth + 1` → nodeAbove becomes the parent
  - If `targetDepth <= nodeAbove.depth` → walk up nodeAbove's ancestor chain to find the node at `targetDepth - 1`

### Drop indicator
- Blue horizontal insertion line rendered between rows at the projected drop position
- Insertion line is indented by `targetDepth * 24px` to show the resulting depth live
- Original dragged row stays in place at ~30% opacity during drag (not removed from list)
- Drop indicator updates in real time as the user moves the cursor

### Drag handle
- `☰` grip icon rendered at the left edge of each row, visible on hover
- Drag only initiates from the grip icon — prevents accidental drags when clicking into the text input
- Grip icon positioned to the left of the bullet glyph (order: ☰ ● [collapse arrow] [input])

### Drag ghost
- Ghost shows the node's text content
- If the node has children (collapsed or expanded), a badge appended: e.g., `(+3)`
- Ghost positioned with cursor above it (ghost offset slightly below cursor) so the blue insertion line remains visible
- dnd-kit `DragOverlay` used for the ghost — Claude handles exact CSS

### Sync after drop
- On drop: call `reorderNode()` to update tree state, then call `queueChange()` on the dragged node (and all its descendants) to flush via the existing 2s debounced `flushPendingChanges()` pipeline
- No new backend endpoint needed — existing `POST /api/sync/push` handles node `sort_order` and `parent_id` field updates

### DnD library
- `@dnd-kit/core` + `@dnd-kit/sortable` — Claude's standard choice for React DnD in 2024+; handles flat list sorting, custom collision detection, and `DragOverlay` for the ghost

### Claude's Discretion
- Exact Tailwind classes for the grip icon, insertion line, and ghost styling
- Whether `reorderNode()` receives a flat array or tree roots (whichever is simpler to implement correctly)
- Exact `DragOverlay` offset configuration
- How to handle drag over the dragged node's own descendants (no-op or snap to safe position)
- Test file structure (one test file for `reorderNode` in `treeHelpers.test.ts`, one for `FlatNodeList` in `FlatNodeList.test.tsx`)

</decisions>

<specifics>
## Specific Ideas

- Flat list approach mirrors how Workflowy, Linear, and Notion handle outliner DnD — the visual result is nested but the DOM is flat
- Horizontal depth via mouse X is the Workflowy mechanic — predictable and learnable
- Grip icon visible on hover only (not always visible) keeps the UI clean for reading

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `treeHelpers.ts`: `buildTree`, `filterSubtree`, `insertSiblingNode`, `indentNode`, `outdentNode`, `recomputeSortOrders` — `reorderNode()` added here
- `NodeEditorPage.tsx`: `queueChange()` / `flushPendingChanges()` already wired — drop calls `queueChange()` on affected nodes
- `getOrCreateDeviceId()` in `web/src/auth/api.ts` — already used for sync push; no change needed
- `web/src/sync/hlc.ts` — `hlcGenerate()` already ported; `toSyncRecord()` in NodeEditorPage produces the right payload shape

### Established Patterns
- Direct `fetch()` with `Authorization: Bearer` — no change for sync
- Vitest + Testing Library + jsdom for tests — `treeHelpers.test.ts` already has pure function tests to extend
- Tailwind v4 utility classes, no config file
- `recomputeSortOrders()` uses `'a' + i.toString().padStart(4, '0')` format — call after every reorder

### Integration Points
- `NodeEditorPage.tsx`: replace `{nodes.map(node => <NodeRow .../>)}` with `<FlatNodeList nodes={flatNodes} onReorder={handleReorder} .../>`
- `treeHelpers.ts`: add `reorderNode()` export + corresponding tests in `treeHelpers.test.ts`
- `web/package.json`: add `@dnd-kit/core` and `@dnd-kit/sortable` (not yet installed)
- New file: `web/src/editor/FlatNodeList.tsx` (component + DnD setup)
- New file: `web/src/editor/FlatNodeList.test.tsx` (DnD interaction tests)

</code_context>

<deferred>
## Deferred Ideas

- Touch/mobile drag support — v0.8 is desktop-first; touch DnD not required
- Keyboard-based reordering (Alt+Arrow to move nodes) — v0.9
- Multi-node selection and drag — v0.9
- Undo for drag operations — v0.9

</deferred>

---

*Phase: 19-drag-and-drop*
*Context gathered: 2026-03-06*
