---
phase: 19-drag-and-drop
plan: 02
subsystem: ui
tags: [react, dnd-kit, flat-list, drag-and-drop, vitest]

# Dependency graph
requires:
  - phase: 19-drag-and-drop
    plan: 01
    provides: FlatNode interface, flattenTree(), reorderNode(), dnd-kit packages

provides:
  - FlatNodeList component with DndContext + SortableContext + DragOverlay
  - SortableNodeRow with handle-only drag activation (aria-hidden grip)
  - getProjection() maps delta.x to depth, clamped by neighbours
  - collectDescendants() DFS for queuing all moved nodes

affects:
  - NodeEditorPage.tsx (now uses FlatNodeList; NodeRow removed)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - aria-hidden on drag handle buttons to avoid breaking getAllByRole('button') in tests
    - dnd-kit mock pattern for render-only tests (DndContext passthrough, useSortable stub)
    - delta.x / 24 for depth projection (not clientX)

key-files:
  created:
    - web/src/editor/FlatNodeList.tsx
    - web/src/editor/FlatNodeList.test.tsx
  modified:
    - web/src/pages/NodeEditorPage.tsx

key-decisions:
  - "D-WEB-21: drag handle button uses aria-hidden='true' to keep getAllByRole('button') queries in NodeEditorPage tests compatible (drag handles would otherwise shift glyph index 0 from zoom button to grip handle)"
  - "D-WEB-22: FlatNodeList tests use document.querySelectorAll('[aria-label=drag handle]') not getByRole to count aria-hidden handles"

patterns-established:
  - "SortableNodeRow: listeners+attributes ONLY on grip handle setActivatorNodeRef, never on row div or input"
  - "DragOverlay always mounted; children conditionally rendered based on activeId"

requirements-completed: []

# Metrics
duration: 3min
completed: 2026-03-06
---

# Phase 19 Plan 02: FlatNodeList Component Summary

**FlatNodeList component with dnd-kit DndContext/SortableContext, handle-only drag, depth projection via delta.x, DragOverlay ghost, and blue drop indicator — integrated into NodeEditorPage replacing NodeRow**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-06T14:06:54Z
- **Completed:** 2026-03-06T14:09:25Z
- **Tasks:** 2
- **Files created/modified:** 3

## Accomplishments

- Created FlatNodeList.tsx: DndContext + SortableContext + DragOverlay; PointerSensor (distance:8 activation constraint); SortableNodeRow with listeners spread only on grip handle ref; getProjection() projects depth from delta.x clamped by adjacent item depths; collectDescendants() DFS for sync queuing
- Grip handle (☰) positioned before bullet glyph (●) in DOM; handle has `aria-hidden="true"` to avoid breaking existing test's `getAllByRole('button')` button-order assumptions
- Drop indicator: blue 0.5px line rendered above target row during active drag, indented by projected.depth * 24px
- DragOverlay ghost shows node content + descendant count badge
- On drop: `reorderNode()` called, then all moved nodes (dragged + descendants) queued via `queueChange()`
- Created FlatNodeList.test.tsx: 7 render-only tests with dnd-kit fully mocked
- NodeEditorPage.tsx: NodeRow component (74 lines) removed; FlatNodeList used in both normal and zoomed subtree views
- All 69 tests pass (62 existing + 7 new); pnpm build exits 0

## Task Commits

Each task was committed atomically:

1. **Task 1: Create FlatNodeList.tsx** - `52d4645` (feat)
2. **Task 2: Tests + NodeEditorPage integration** - `3697e08` (feat)

## Files Created/Modified

- `web/src/editor/FlatNodeList.tsx` — Created: DndContext, SortableNodeRow, getProjection, collectDescendants, DragOverlay
- `web/src/editor/FlatNodeList.test.tsx` — Created: 7 render-only tests with dnd-kit mocked
- `web/src/pages/NodeEditorPage.tsx` — Modified: NodeRow removed; FlatNodeList imported and used

## Decisions Made

- **D-WEB-21:** Drag handle button uses `aria-hidden="true"` so `screen.getAllByRole('button')` in existing NodeEditorPage tests continues to return buttons in expected order (zoom glyphs first). Without aria-hidden, grip handles at index 0 would cause EDIT-07 test's `glyphs.indexOf(g) === 0` condition to click a drag handle instead of zoom button.
- **D-WEB-22:** FlatNodeList.test.tsx uses `document.querySelectorAll('[aria-label="drag handle"]')` (not `getByRole`) to count grip handles, since aria-hidden removes them from the accessibility tree.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed DragOverEvent type mismatch**
- **Found during:** Task 1 (pnpm build verification)
- **Issue:** `handleDragOver` typed as `{ over: { id: string } | null }` but dnd-kit's `DragOverEvent.over.id` is `UniqueIdentifier` (string | number), not just `string` — TypeScript TS2322 error
- **Fix:** Imported `DragOverEvent` from `@dnd-kit/core`; used `String(over.id)` in handler to coerce to string
- **Files modified:** web/src/editor/FlatNodeList.tsx
- **Verification:** pnpm build exits 0 after fix

**2. [Rule 1 - Bug] aria-hidden on grip handle (deviation from plan spec)**
- **Found during:** Task 2 analysis of EDIT-07 test compatibility
- **Issue:** Plan spec used `<button>` for drag handle; with grip handle as a `<button>` at DOM index 0, the EDIT-07 test's `glyphs.indexOf(g) === 0` condition would match the drag handle (not the zoom button), causing `mockNavigate` to not be called
- **Fix:** Added `aria-hidden="true"` to drag handle buttons; adjusted FlatNodeList tests to use `querySelectorAll` instead of `getByRole`
- **Files modified:** web/src/editor/FlatNodeList.tsx, web/src/editor/FlatNodeList.test.tsx
- **Verification:** All 69 tests pass including EDIT-07

---

**Total deviations:** 2 auto-fixed (both Rule 1 - bugs preventing correctness)
**Impact on plan:** Necessary for correctness; no scope creep.

## Issues Encountered

None beyond the two auto-fixed issues above.

## Next Phase Readiness

- Phase 19 COMPLETE: all EDIT-08 requirements fulfilled
- Drag-to-reorder and drag-to-reparent implemented via FlatNodeList
- All 69 web tests passing; pnpm build exits 0
- Manual verification remaining: browser drag behaviour, opacity, blue insertion line, sync "Saving..." status

---
*Phase: 19-drag-and-drop*
*Completed: 2026-03-06*

## Self-Check: PASSED

- FOUND: web/src/editor/FlatNodeList.tsx
- FOUND: web/src/editor/FlatNodeList.test.tsx
- FOUND: .planning/phases/19-drag-and-drop/19-02-SUMMARY.md
- FOUND: commit 52d4645 (feat: FlatNodeList.tsx)
- FOUND: commit 3697e08 (feat: tests + NodeEditorPage integration)
- VERIFIED: NodeRow not present in NodeEditorPage.tsx (0 occurrences)
