# Phase 19: Drag-and-Drop - Research

**Researched:** 2026-03-06
**Domain:** React drag-and-drop with tree reparenting — @dnd-kit/core + @dnd-kit/sortable
**Confidence:** HIGH (core library API verified against official docs and source; workarounds for pointer position verified against official GitHub discussions)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Switch NodeEditorPage from recursive nested `NodeRow` renderer to a flat list with depth-based `paddingLeft`
- Flat list = DFS-ordered array of `{ node: TreeNode, depth: number }` — collapsed nodes' descendants excluded
- Visual appearance identical to current nested look (same indentation) but DOM is a single flat list
- DnD logic and flat row renderer extracted to `web/src/editor/FlatNodeList.tsx`; NodeEditorPage stays focused on data loading and sync
- `reorderNode()` pure function added to `treeHelpers.ts`
  - Signature: `reorderNode(roots: TreeNode[], dragId: string, targetIndex: number, newDepth: number): TreeNode[]`
  - Computes new `parent_id` from context, updates `sort_order` via `recomputeSortOrders()` on affected siblings
- When a collapsed parent is dragged, its hidden children move with it — tree integrity always maintained
- `sort_order` and `parent_id` updated on dragged node only; children's `parent_id` chain remains intact
- All affected nodes (dragged node + all descendants) queued into `pendingChanges` on drop for sync push
- Mouse X position (absolute, relative to page left edge) determines target depth during drag
  - Formula: `targetDepth = Math.floor(mouseX / 24)` — each 24px = one indent level
  - Depth clamped: `max = nodeAbove.depth + 1`; can always drop at depth 0
  - New parent resolution: if `targetDepth == nodeAbove.depth + 1` → nodeAbove becomes parent; else walk up nodeAbove's ancestor chain to find node at `targetDepth - 1`
- Blue horizontal insertion line rendered between rows at the projected drop position
- Insertion line indented by `targetDepth * 24px` to show resulting depth live
- Original dragged row stays at ~30% opacity during drag (not removed from list)
- `☰` grip icon at left edge of each row, visible on hover; drag only initiates from grip icon
- Grip icon positioned to left of bullet glyph (order: ☰ ● [collapse arrow] [input])
- Ghost shows node text content; if node has children: badge `(+N)`
- Ghost positioned with cursor above it (ghost offset slightly below cursor) so blue insertion line remains visible
- dnd-kit `DragOverlay` used for the ghost
- On drop: call `reorderNode()` to update tree state, then call `queueChange()` on dragged node and all its descendants
- No new backend endpoint — existing `POST /api/sync/push` handles `sort_order` and `parent_id` field updates
- Library: `@dnd-kit/core` + `@dnd-kit/sortable`
- New files: `web/src/editor/FlatNodeList.tsx`, `web/src/editor/FlatNodeList.test.tsx`

### Claude's Discretion
- Exact Tailwind classes for grip icon, insertion line, and ghost styling
- Whether `reorderNode()` receives a flat array or tree roots (whichever is simpler to implement correctly)
- Exact `DragOverlay` offset configuration
- How to handle drag over the dragged node's own descendants (no-op or snap to safe position)
- Test file structure (one test file for `reorderNode` in `treeHelpers.test.ts`, one for `FlatNodeList` in `FlatNodeList.test.tsx`)

### Deferred Ideas (OUT OF SCOPE)
- Touch/mobile drag support — v0.8 is desktop-first; touch DnD not required
- Keyboard-based reordering (Alt+Arrow to move nodes) — v0.9
- Multi-node selection and drag — v0.9
- Undo for drag operations — v0.9
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| EDIT-08 | User can drag nodes to reorder them | dnd-kit flat-list sortable with custom depth projection covers full reorder + reparent requirement |
</phase_requirements>

---

## Summary

This phase adds drag-to-reorder and drag-to-reparent to the web node editor. The implementation follows the same flat-list approach used by Workflowy and Linear: a DFS-ordered array of `{ node: TreeNode, depth: number }` items rendered in a single `DndContext` + `SortableContext`. The heavy lifting for reparenting comes from tracking `delta.x` in `onDragMove` (the official dnd-kit approach from their own SortableTree example) and converting horizontal displacement into a `targetDepth`.

The critical insight from the official dnd-kit SortableTree source is that horizontal depth is computed from `delta.x` (displacement from where the drag started), not from absolute `clientX`. This matters because it means the depth projection anchors to the drag start X position, not the page edge. The CONTEXT.md formula `Math.floor(mouseX / 24)` uses absolute page X — the research shows `delta.x` is more robust for this. The planner should clarify this distinction and use the delta approach.

Testing dnd-kit interactions in jsdom is not practical: the library calls `getBoundingClientRect()` which always returns zeros in jsdom, making sensor activation and collision detection inert. The recommended strategy (per the official dnd-kit GitHub issue #261) is to test the pure functions (`reorderNode`, flat list mapping) in Vitest and skip simulated drag interaction tests for the component.

**Primary recommendation:** Use `@dnd-kit/core` 6.3.1 + `@dnd-kit/sortable` 10.0.0 with a single flat `SortableContext`. Track `delta.x` in `onDragMove` to compute `targetDepth`. Spread `listeners` only on the `☰` grip handle element (not the full row). Use `DragOverlay` for the ghost. Skip drag interaction simulation in Vitest; test `reorderNode()` and the flat-list mapper as pure functions.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `@dnd-kit/core` | 6.3.1 (latest) | DndContext, sensors, DragOverlay | The canonical React DnD library since 2021; used by Linear, Atlassian, and others |
| `@dnd-kit/sortable` | 10.0.0 (latest) | useSortable hook, SortableContext, verticalListSortingStrategy | Official preset for sorted lists; provides transition and transform management |
| `@dnd-kit/utilities` | 3.2.2 (latest) | `CSS.Transform.toString()` utility | Converts transform object to CSS string; required for correct transform application |

### Supporting (already installed — no new packages)
| Library | Version | Purpose |
|---------|---------|---------|
| Vitest | 4.x | Unit tests for `reorderNode` and flat list mapper (pure function tests only) |
| @testing-library/react | 16.x | Component rendering tests (no drag simulation — see Pitfalls) |

**Installation:**
```bash
cd web && pnpm add @dnd-kit/core @dnd-kit/sortable @dnd-kit/utilities
```

Versions to install:
- `@dnd-kit/core@6.3.1`
- `@dnd-kit/sortable@10.0.0`
- `@dnd-kit/utilities@3.2.2`

---

## Architecture Patterns

### Recommended Project Structure
```
web/src/editor/
├── treeHelpers.ts          # Add reorderNode() + flattenTree() here
├── treeHelpers.test.ts     # Add tests for reorderNode() and flattenTree()
├── FlatNodeList.tsx        # DndContext + SortableContext + SortableNodeRow
└── FlatNodeList.test.tsx   # Pure function tests only (no drag simulation)
```

### Pattern 1: Flat List Representation

**What:** DFS traversal of the tree that respects `collapsed` state, producing `{ node: TreeNode, depth: number }[]`.

**When to use:** Always — this replaces the recursive `NodeRow` renderer.

```typescript
// Source: official dnd-kit SortableTree example + project conventions
export interface FlatNode {
  node: TreeNode
  depth: number
}

export function flattenTree(
  roots: TreeNode[],
  depth = 0,
  result: FlatNode[] = []
): FlatNode[] {
  for (const node of roots) {
    result.push({ node, depth })
    if (!node.collapsed && node.children.length > 0) {
      flattenTree(node.children, depth + 1, result)
    }
  }
  return result
}
```

### Pattern 2: DndContext + SortableContext Setup

**What:** Wrap the flat list in a single `DndContext` + `SortableContext`. Use `PointerSensor` with a distance activation constraint so clicks on input fields are not intercepted.

**When to use:** For the FlatNodeList component.

```typescript
// Source: https://dndkit.com/presets/sortable (official docs)
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  useSensor,
  useSensors,
  closestCenter,
  type DragStartEvent,
  type DragMoveEvent,
  type DragEndEvent,
} from '@dnd-kit/core'
import {
  SortableContext,
  verticalListSortingStrategy,
  useSortable,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'

// Inside FlatNodeList component:
const sensors = useSensors(
  useSensor(PointerSensor, {
    activationConstraint: {
      distance: 8, // 8px movement required before drag activates
    },
  })
)

const itemIds = flatNodes.map(fn => fn.node.id)

return (
  <DndContext
    sensors={sensors}
    collisionDetection={closestCenter}
    onDragStart={handleDragStart}
    onDragMove={handleDragMove}
    onDragEnd={handleDragEnd}
    onDragCancel={handleDragCancel}
  >
    <SortableContext items={itemIds} strategy={verticalListSortingStrategy}>
      {flatNodes.map((fn, index) => (
        <SortableNodeRow key={fn.node.id} flatNode={fn} index={index} ... />
      ))}
    </SortableContext>
    <DragOverlay dropAnimation={null}>
      {activeId ? <GhostRow flatNode={activeNode} /> : null}
    </DragOverlay>
  </DndContext>
)
```

### Pattern 3: Depth Projection via delta.x

**What:** Track the horizontal displacement from drag start (`delta.x`) to compute `targetDepth`. This is the approach used in the official dnd-kit SortableTree example.

**Critical distinction from CONTEXT.md:** CONTEXT.md uses `Math.floor(mouseX / 24)` with absolute page X. The official dnd-kit approach uses `delta.x` (displacement from drag start), which avoids anchor point issues when the grip handle is at different horizontal positions. The formula becomes:
```
dragDepth = Math.round(delta.x / 24)
targetDepth = clamp(activeItem.depth + dragDepth, 0, nodeAbove.depth + 1)
```

**Source:** `github.com/clauderic/dnd-kit/blob/master/stories/3 - Examples/Tree/SortableTree.tsx` — `handleDragMove` extracts `delta.x`; `getProjection()` converts it.

```typescript
const [offsetLeft, setOffsetLeft] = useState(0)
const [overId, setOverId] = useState<string | null>(null)

function handleDragMove({ delta }: DragMoveEvent) {
  setOffsetLeft(delta.x)
}

// Projection computed during render, not in the event handler:
const projected = activeId && overId
  ? getProjection(flatNodes, activeId, overId, offsetLeft, INDENT_WIDTH)
  : null
```

### Pattern 4: Drag Handle — Listeners on a Separate Element

**What:** Spread `listeners` only on the grip handle `button`, not on the full row. This prevents accidental drag when clicking the text input.

**Source:** https://dndkit.com (official docs on useDraggable handle pattern)

```typescript
// Inside SortableNodeRow:
const {
  attributes,
  listeners,
  setNodeRef,
  setActivatorNodeRef,
  transform,
  transition,
  isDragging,
} = useSortable({ id: flatNode.node.id })

const style: React.CSSProperties = {
  transform: CSS.Transform.toString(transform),
  transition,
  opacity: isDragging ? 0.3 : 1,
}

return (
  <div ref={setNodeRef} style={style} className="flex items-center py-1" ...>
    {/* Grip handle — listeners ONLY here */}
    <button
      ref={setActivatorNodeRef}
      {...listeners}
      {...attributes}
      className="opacity-0 group-hover:opacity-100 cursor-grab active:cursor-grabbing ..."
      aria-label="drag handle"
    >
      ☰
    </button>
    {/* Bullet glyph, collapse arrow, input — no listeners */}
    <button onClick={...}>●</button>
    ...
    <input type="text" ... />
  </div>
)
```

### Pattern 5: Drop Indicator (Blue Insertion Line)

**What:** A separate `div` rendered conditionally at the projected drop position, not using the default sortable animation.

**When to use:** During active drag only. The indicator appears between rows at the `overIndex` position, inset by `targetDepth * 24px`.

```typescript
// Rendered as a sibling element between node rows:
{isDragging && isDropTarget && (
  <div
    className="absolute left-0 right-0 h-0.5 bg-blue-500 pointer-events-none"
    style={{ paddingLeft: `${projected.depth * 24}px` }}
  />
)}
```

**Key:** Set `transition: null` in `useSortable` to disable the default "slide to make room" animation that would conflict with the custom drop indicator.

### Pattern 6: DragOverlay Ghost

**What:** A fixed-position element that follows the cursor, showing node text + child count badge.

```typescript
// Source: https://dndkit.com/api-documentation/draggable/drag-overlay (official docs)

// Always mounted; only children conditional:
<DragOverlay dropAnimation={null}>
  {activeId ? (
    <div className="bg-white border border-blue-400 shadow-lg rounded px-3 py-1 text-sm">
      {activeNode.node.content}
      {childCount > 0 && (
        <span className="ml-2 text-xs text-gray-500">(+{childCount})</span>
      )}
    </div>
  ) : null}
</DragOverlay>
```

**CRITICAL:** Keep `<DragOverlay>` always mounted (never conditionally render the component itself), only conditionally render its children. This is required for drop animations to work. Use `dropAnimation={null}` to disable the snap-back animation.

### Pattern 7: reorderNode() Pure Function

**What:** Takes the tree roots, drag node ID, target flat-list index, and new depth. Returns updated tree roots with `parent_id` and `sort_order` updated.

**Inputs and outputs:**

```typescript
// Signature locked by CONTEXT.md:
export function reorderNode(
  roots: TreeNode[],
  dragId: string,
  targetIndex: number,  // position in the flat (visible) node list
  newDepth: number
): TreeNode[]
```

**Algorithm:**
1. Build `flatNodes = flattenTree(roots)` to know current positions
2. Find the dragged node's subtree (all descendants included, even if collapsed)
3. Determine `newParentId` by looking at `flatNodes[targetIndex - 1]` and walking up ancestor chain to `newDepth - 1`
4. Remove dragged node from its current parent's children
5. Insert dragged node into new parent's children at the correct position
6. Call `recomputeSortOrders()` on both the old and new sibling lists

**Descendant collection for sync:** After `reorderNode()` returns, collect all descendants by DFS on the moved node to build the full `queueChange()` call set.

### Anti-Patterns to Avoid

- **Nested SortableContext:** Do not nest `SortableContext` inside each other for a tree. Cross-level dragging requires a single flat `SortableContext`. (Source: dnd-kit docs)
- **Listeners on the full row div:** Putting `...listeners` on the row `div` means clicking the text input starts a drag. Listeners must go on the grip handle only.
- **Conditional DragOverlay mount:** Do NOT do `{activeId && <DragOverlay>...</DragOverlay>}`. Always mount `DragOverlay`, conditionally render its children.
- **Raw `clientX` for depth:** Using absolute page X coordinates anchors depth to the page left edge, not to the drag start position. This means dragging from the right side of the page produces wrong depth values. Use `delta.x` from `onDragMove` instead, anchored to drag start.
- **Testing drag interactions in jsdom:** `getBoundingClientRect()` returns all zeros in jsdom. Sensor activation, collision detection, and position calculations all break silently. Do not write tests that simulate `fireEvent.pointerDown` / `fireEvent.pointerMove` expecting DnD to activate.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Drag activation with distance threshold | Custom pointermove listener checking delta | `PointerSensor` with `activationConstraint: { distance: 8 }` | Handles edge cases: pointer cancel, multi-touch, accessibility |
| Ghost element following cursor | CSS absolute positioning + transform tracking | `DragOverlay` from `@dnd-kit/core` | Handles scroll offsets, viewport edges, z-index, portal rendering |
| Accessible drag semantics | Custom ARIA attributes | `...attributes` from `useSortable` — includes `aria-roledescription`, `aria-describedby` | ARIA pattern is complex; dnd-kit has it right |
| CSS transform during drag | Manual style calculation | `CSS.Transform.toString(transform)` from `@dnd-kit/utilities` | Handles `scaleX`/`scaleY` and edge cases |
| Detecting drag over index | Tracking mouse Y manually | `overId` from `onDragOver` event via `SortableContext` | `closestCenter` collision detection handles this correctly |

**Key insight:** dnd-kit's primitives handle pointer event normalization, scroll compensation, accessibility, and transform math. The only custom logic needed is the depth projection (delta.x to targetDepth) and the tree mutation (reorderNode).

---

## Common Pitfalls

### Pitfall 1: jsdom DnD Testing — Silent Failures

**What goes wrong:** Tests that simulate drag events appear to run but no state changes occur. DnD context activates, but `onDragEnd` never fires because `getBoundingClientRect()` returns `{ width: 0, height: 0, top: 0, left: 0, ... }` in jsdom.

**Why it happens:** dnd-kit uses bounding rects extensively for collision detection and sensor activation constraints. All rects being zero means items are all "at the same position" and the distance activation constraint is never satisfied.

**How to avoid:** Do not test drag interactions in Vitest/jsdom. Test only:
- `reorderNode()` as a pure function (no DOM)
- `flattenTree()` as a pure function (no DOM)
- `FlatNodeList.tsx` render output (does the grip handle render, is aria-label correct) — no drag simulation

**Warning signs:** Test passes but `onDragEnd` callback mock was never called.

**Source:** github.com/clauderic/dnd-kit/issues/261 (official dnd-kit GitHub)

### Pitfall 2: delta.x vs clientX for Depth Calculation

**What goes wrong:** Using `e.clientX` (absolute page coordinate) means depth 0 requires the cursor to be at the extreme left of the viewport, and depth 3 requires the cursor to be at 72px from left. This makes reparenting nearly impossible unless the user drags all the way to the left.

**Why it happens:** `delta.x` is the displacement from where the drag started, not absolute position. If dragging starts at x=200 (where the grip handle is), then `delta.x = 0` means "same depth as start", `delta.x = -24` means "one level shallower", `delta.x = 24` means "one level deeper".

**How to avoid:** Use `onDragMove({ delta }) => setOffsetLeft(delta.x)`. Compute depth as `activeItem.depth + Math.round(delta.x / 24)`.

**Warning signs:** User must drag very far left to outdent; can never reach deep nesting.

### Pitfall 3: Dragging Collapsed Parent — Descendant Sync

**What goes wrong:** After a drag, only the dragged node's `sort_order` and `parent_id` are queued. The server syncs the change, but the Android app later receives a push from another device that shows descendants still under the old parent (because Android had cached the descendants' `parent_id`).

**Why it happens:** Descendants of a collapsed node are not visible in the flat list and are easy to forget. Their `parent_id` chain is correct in the tree structure, but they need a sync record to update the server's `updated_at` / HLC timestamps so the LWW merge works correctly.

**How to avoid:** After `reorderNode()`, DFS-collect all descendants of the dragged node and call `queueChange()` on each one. The descendants' `parent_id` values are unchanged, but their HLC timestamps get refreshed, ensuring conflict resolution favors the moved state.

**Warning signs:** After drag-and-drop, syncing to Android shows nodes reverting to pre-drag positions.

### Pitfall 4: SortableContext items Array Order

**What goes wrong:** Nodes render in correct visual order but drag sorting reverses or scrambles.

**Why it happens:** The `items` prop of `SortableContext` must match the render order exactly. If the flat list has been re-ordered but `items` array is stale, dnd-kit's internal position tracking is off.

**How to avoid:** Always derive `items` from the same `flatNodes` array that drives the map:
```typescript
const itemIds = flatNodes.map(fn => fn.node.id)
// Then:
<SortableContext items={itemIds} strategy={verticalListSortingStrategy}>
  {flatNodes.map((fn) => <SortableNodeRow key={fn.node.id} ... />)}
```

**Warning signs:** Items jump to wrong positions on drop.

### Pitfall 5: Grip Handle touch-action

**What goes wrong:** On touch screens (not required for v0.8, but good to be aware of), dragging scrolls the page instead of moving the node.

**Why it happens:** Default browser behavior interprets touch as scroll.

**How to avoid:** Add `style={{ touchAction: 'none' }}` to the grip handle element. Do NOT add this to the full row — that would break text selection in the input.

### Pitfall 6: PointerSensor distance and Input Click Events

**What goes wrong:** Clicking into the text input starts a drag accidentally.

**Why it happens:** Without an activation constraint, any pointer down event starts a drag.

**How to avoid:** Configure `activationConstraint: { distance: 8 }` AND put `listeners` only on the grip handle button, not the row div. Both defenses are needed: the distance prevents micro-movement triggers, the handle restriction prevents input clicks entirely.

---

## Code Examples

### Complete useSortable Row Pattern

```typescript
// Source: https://dndkit.com/presets/sortable/usesortable (official docs)
import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'

function SortableNodeRow({ flatNode }: { flatNode: FlatNode }) {
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id: flatNode.node.id,
    transition: null,  // disable default slide animation; we use a custom drop indicator
  })

  return (
    <div
      ref={setNodeRef}
      style={{
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.3 : 1,
        paddingLeft: `${flatNode.depth * 24}px`,
      }}
      className="group flex items-center py-1"
    >
      <button
        ref={setActivatorNodeRef}
        {...listeners}
        {...attributes}
        className="opacity-0 group-hover:opacity-100 w-4 h-4 cursor-grab active:cursor-grabbing text-gray-400 flex-shrink-0"
        aria-label="drag handle"
        style={{ touchAction: 'none' }}
      >
        ☰
      </button>
      {/* bullet glyph, input, etc. — no listeners */}
    </div>
  )
}
```

### Depth Projection (official dnd-kit SortableTree approach)

```typescript
// Source: github.com/clauderic/dnd-kit — stories/3 - Examples/Tree/utilities.ts
const INDENT_WIDTH = 24

function getDragDepth(offset: number, indentationWidth: number): number {
  return Math.round(offset / indentationWidth)
}

function getProjection(
  flatNodes: FlatNode[],
  activeId: string,
  overId: string,
  dragOffset: number,  // delta.x from onDragMove
  indentationWidth: number
): { depth: number; parentId: string | null } {
  const overIndex = flatNodes.findIndex(fn => fn.node.id === overId)
  const activeIndex = flatNodes.findIndex(fn => fn.node.id === activeId)
  const activeItem = flatNodes[activeIndex]

  // Projected flat list after move
  const newItems = arrayMove(flatNodes, activeIndex, overIndex)
  const previousItem = newItems[overIndex - 1]
  const nextItem = newItems[overIndex + 1]

  const dragDepth = getDragDepth(dragOffset, indentationWidth)
  const projectedDepth = activeItem.depth + dragDepth
  const maxDepth = previousItem ? previousItem.depth + 1 : 0
  const minDepth = nextItem ? nextItem.depth : 0
  const depth = Math.min(Math.max(projectedDepth, minDepth), maxDepth)

  // Find parent at depth - 1 by walking back from previousItem
  let parentId: string | null = null
  if (depth > 0 && previousItem) {
    if (previousItem.depth === depth - 1) {
      parentId = previousItem.node.id
    } else {
      // Walk up the ancestor chain
      // Requires an ancestor lookup mechanism (parentId chain on FlatNode or from tree)
      parentId = findAncestorAtDepth(flatNodes, overIndex - 1, depth - 1)
    }
  }

  return { depth, parentId }
}
```

Note: `arrayMove` is re-exported from `@dnd-kit/sortable`:
```typescript
import { arrayMove } from '@dnd-kit/sortable'
```

### FlatNodeList onDragMove Handler

```typescript
// Source: official dnd-kit SortableTree example
const [activeId, setActiveId] = useState<string | null>(null)
const [overId, setOverId] = useState<string | null>(null)
const [offsetLeft, setOffsetLeft] = useState(0)

function handleDragStart({ active }: DragStartEvent) {
  setActiveId(active.id as string)
  setOverId(active.id as string)
  setOffsetLeft(0)
}

function handleDragMove({ delta }: DragMoveEvent) {
  setOffsetLeft(delta.x)  // NOT clientX — delta is displacement from drag start
}

function handleDragOver({ over }: { over: { id: string } | null }) {
  setOverId(over?.id ?? null)
}

function handleDragEnd({ active, over }: DragEndEvent) {
  if (over && active.id !== over.id) {
    const projected = getProjection(flatNodes, active.id as string, over.id as string, offsetLeft, INDENT_WIDTH)
    const overIndex = flatNodes.findIndex(fn => fn.node.id === over.id)
    // Call reorderNode with tree roots, dragId, targetIndex, newDepth
    const newRoots = reorderNode(roots, active.id as string, overIndex, projected.depth)
    // Queue changes for dragged node + all descendants
    collectDescendants(newRoots, active.id as string).forEach(n => queueChange(n))
    onTreeChange(newRoots)
  }
  setActiveId(null)
  setOverId(null)
  setOffsetLeft(0)
}

function handleDragCancel() {
  setActiveId(null)
  setOverId(null)
  setOffsetLeft(0)
}
```

### Test Strategy for FlatNodeList.test.tsx

```typescript
// Source: github.com/clauderic/dnd-kit/issues/261 recommendation — pure function tests only
import { describe, it, expect } from 'vitest'
import { flattenTree } from './treeHelpers'
import { render, screen } from '@testing-library/react'
import { FlatNodeList } from './FlatNodeList'

// GOOD: Test the pure function
describe('flattenTree', () => {
  it('excludes collapsed nodes descendants', () => { ... })
  it('includes visible children in DFS order', () => { ... })
})

// GOOD: Test rendered output (no drag simulation)
describe('FlatNodeList', () => {
  it('renders a grip handle for each node', () => {
    render(<FlatNodeList nodes={mockRoots} ... />)
    expect(screen.getAllByLabelText('drag handle')).toHaveLength(3)
  })
  it('renders grip handle to the left of bullet glyph', () => { ... })
})

// BAD — do not do this:
// fireEvent.pointerDown(gripHandle)
// fireEvent.pointerMove(gripHandle, { clientX: 100, clientY: 200 })
// expect(onReorder).toHaveBeenCalled()  // will never fire in jsdom
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Nested `SortableContext` per level | Single flat `SortableContext` with depth tracking | dnd-kit v5+ | Cross-level dragging now possible |
| `MouseSensor` | `PointerSensor` | dnd-kit v6 | Pointer Events API covers mouse + touch + pen |
| `react-beautiful-dnd` (Atlassian) | `@dnd-kit` | 2022 (react-beautiful-dnd maintenance ended) | react-beautiful-dnd is in maintenance mode only |
| `react-dnd` | `@dnd-kit` | 2022-2023 | dnd-kit has better TypeScript, performance, and accessibility |

**Package versions (npm registry, 2026-03-06):**
- `@dnd-kit/core`: 6.3.1
- `@dnd-kit/sortable`: 10.0.0
- `@dnd-kit/utilities`: 3.2.2

**Deprecated/outdated:**
- `MouseSensor` from `@dnd-kit/core`: Use `PointerSensor` instead (covers mouse, touch, pen)
- `react-beautiful-dnd`: Atlassian archived it in 2023; no React 18/19 support
- Nested SortableContext for multi-level trees: Use flat list + depth projection

---

## Open Questions

1. **delta.x vs absolute mouseX for depth calculation**
   - What we know: CONTEXT.md specifies `Math.floor(mouseX / 24)` with absolute page X. The official dnd-kit SortableTree example uses `delta.x` (displacement from drag start).
   - What's unclear: The two approaches produce the same result only when the drag starts at x=0 (left edge). For a grip handle positioned at depth 2 (x=48px), drag start gives delta.x=0 (correct: "same depth") but mouseX=48 gives targetDepth=2 (also coincidentally correct for this specific case).
   - Recommendation: Use `delta.x` (the official approach). It is more robust across different initial positions. Planner should note this deviation from the CONTEXT.md formula and confirm with discretion.

2. **Ancestor lookup for newParentId in reorderNode()**
   - What we know: The `FlatNode` type as defined in CONTEXT.md contains only `{ node: TreeNode, depth: number }`. Finding the ancestor at `depth - 1` requires walking backwards through `flatNodes`.
   - What's unclear: Whether `FlatNode` should carry `parentId` directly (like the official dnd-kit example's `FlattenedItem`) for O(1) lookup.
   - Recommendation: Add `parentId: string | null` to `FlatNode` during the `flattenTree()` DFS pass. This makes ancestor lookup trivial and avoids re-traversal.

3. **Subtree drag prevention (no-drop on own descendants)**
   - What we know: CONTEXT.md leaves this to Claude's discretion. The official dnd-kit SortableTree filters out descendant IDs from the `SortableContext` items array during a drag.
   - Recommendation: In `handleDragMove` / `handleDragEnd`, check if `overId` is a descendant of `activeId`; if so, treat as a no-op (revert to original position). Collect descendant IDs once in `handleDragStart` using a DFS on the active node's subtree.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Vitest 4.x |
| Config file | `web/vitest.config.ts` |
| Quick run command | `cd web && pnpm test --run` |
| Full suite command | `cd web && pnpm test --run` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| EDIT-08 | `reorderNode()` correctly updates parent_id and sort_order | unit | `cd web && pnpm test --run treeHelpers` | ❌ Wave 0 — add to `treeHelpers.test.ts` |
| EDIT-08 | `flattenTree()` produces DFS order respecting collapsed state | unit | `cd web && pnpm test --run treeHelpers` | ❌ Wave 0 — add to `treeHelpers.test.ts` |
| EDIT-08 | FlatNodeList renders grip handles with correct aria-labels | render | `cd web && pnpm test --run FlatNodeList` | ❌ Wave 0 — create `FlatNodeList.test.tsx` |
| EDIT-08 | Drag interaction (actual DnD) | manual-only | N/A — jsdom cannot simulate dnd-kit sensors | manual |

**Note on manual-only:** dnd-kit uses `getBoundingClientRect()` for sensor activation and collision detection. jsdom returns all-zeros for `getBoundingClientRect()`, meaning `PointerSensor` with `activationConstraint: { distance: 8 }` never activates in jsdom. Drag interaction tests must be verified manually in the browser.

### Sampling Rate
- **Per task commit:** `cd web && pnpm test --run`
- **Per wave merge:** `cd web && pnpm test --run`
- **Phase gate:** All pure function tests green + manual drag verification in browser before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `web/src/editor/FlatNodeList.test.tsx` — covers render assertions for EDIT-08
- [ ] Add `flattenTree` and `reorderNode` tests to `web/src/editor/treeHelpers.test.ts`
- [ ] Framework install: `cd web && pnpm add @dnd-kit/core@6.3.1 @dnd-kit/sortable@10.0.0 @dnd-kit/utilities@3.2.2`

---

## Sources

### Primary (HIGH confidence)
- `github.com/clauderic/dnd-kit/blob/master/stories/3 - Examples/Tree/SortableTree.tsx` — official tree example with `onDragMove({ delta })` pattern and `getProjection()` algorithm
- `dndkit.com/presets/sortable` — `SortableContext`, `useSortable`, `verticalListSortingStrategy` API
- `dndkit.com/api-documentation/sensors/pointer` — `PointerSensor` with `activationConstraint`
- `github.com/dnd-kit/docs/blob/master/api-documentation/draggable/usedraggable.md` — `setActivatorNodeRef` for drag handle pattern
- `github.com/dnd-kit/docs/blob/master/api-documentation/draggable/drag-overlay.md` — `DragOverlay` API, always-mounted requirement, `dropAnimation`
- npm registry (direct query) — `@dnd-kit/core@6.3.1`, `@dnd-kit/sortable@10.0.0`, `@dnd-kit/utilities@3.2.2` confirmed current versions

### Secondary (MEDIUM confidence)
- `github.com/clauderic/dnd-kit/issues/261` — jsdom testing limitation and recommendation to test only pure functions / mocked events
- `dndkit.com/presets/sortable` — `closestCenter` collision detection recommended for sortable lists
- `github.com/clauderic/dnd-kit/discussions/222` — confirmed pointer coordinates not exposed in public API; `delta.x` from `onDragMove` is the supported approach

### Tertiary (LOW confidence)
- `dev.to/fupeng_wang/react-dnd-kit-implement-tree-list-drag-and-drop-sortable-225l` — December 2024 article; corroborates flat list + ancestor tracking approach

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — npm registry queried directly for versions; official docs confirm API
- Architecture: HIGH — official dnd-kit SortableTree example is the canonical reference; key patterns (delta.x, handle-only listeners, always-mounted DragOverlay) all verified against official sources
- Pitfalls: HIGH — jsdom limitation confirmed via official GitHub issue; delta.x vs clientX derived from source analysis; others are standard dnd-kit gotchas well-documented in issues

**Research date:** 2026-03-06
**Valid until:** 2026-06-06 (dnd-kit is stable; API unlikely to change in 3 months)
