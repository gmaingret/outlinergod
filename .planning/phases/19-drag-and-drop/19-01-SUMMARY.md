---
phase: 19-drag-and-drop
plan: 01
subsystem: ui
tags: [react, dnd-kit, tree, drag-and-drop, pure-functions, vitest]

# Dependency graph
requires:
  - phase: 18-node-editor
    provides: treeHelpers.ts with buildTree, indentNode, outdentNode, recomputeSortOrders

provides:
  - FlatNode interface (node, depth, parentId) exported from treeHelpers.ts
  - flattenTree() DFS traversal respecting collapsed state
  - reorderNode() tree mutation updating parent_id and sort_order
  - @dnd-kit/core@6.3.1, @dnd-kit/sortable@10.0.0, @dnd-kit/utilities@3.2.2 installed

affects:
  - 19-02-PLAN (FlatNodeList component consumes flattenTree and reorderNode)

# Tech tracking
tech-stack:
  added:
    - "@dnd-kit/core@6.3.1"
    - "@dnd-kit/sortable@10.0.0"
    - "@dnd-kit/utilities@3.2.2"
  patterns:
    - TDD (RED tests written first, then GREEN implementation)
    - Pure function tree mutation (same in-place pattern as indentNode/outdentNode)
    - FlatNode as intermediate representation for DnD layer

key-files:
  created: []
  modified:
    - web/package.json
    - web/pnpm-lock.yaml
    - web/src/editor/treeHelpers.ts
    - web/src/editor/treeHelpers.test.ts

key-decisions:
  - "D-WEB-19: reorderNode re-flattens tree after removal to resolve targetIndex against updated tree (avoids off-by-one from the removed node)"
  - "D-WEB-20: reorderNode appends dragged node into parent then recomputeSortOrders — position derived by counting new parent's children before targetIndex in flat list"

patterns-established:
  - "FlatNode.parentId enables O(1) ancestor lookup in getProjection() (next plan)"
  - "reorderNode trusts pre-clamped newDepth from caller; falls back to root if no valid parent at newDepth-1"

requirements-completed: [EDIT-08]

# Metrics
duration: 10min
completed: 2026-03-06
---

# Phase 19 Plan 01: Drag-and-Drop Pure Functions Summary

**dnd-kit packages installed and flattenTree()/reorderNode() pure functions added to treeHelpers.ts with 9 new tests (23 total passing)**

## Performance

- **Duration:** 10 min
- **Started:** 2026-03-06T14:00:00Z
- **Completed:** 2026-03-06T14:03:14Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Installed @dnd-kit/core, @dnd-kit/sortable, @dnd-kit/utilities — pnpm build exits 0
- Added FlatNode interface with node, depth, and parentId fields to treeHelpers.ts
- flattenTree() DFS traversal: respects collapsed state, carries parentId for O(1) ancestor lookup
- reorderNode() moves a node to targetIndex at newDepth: removes from old parent, resolves new parent by scanning backwards for depth-1 ancestor, inserts at correct position, recomputes sort_orders

## Task Commits

Each task was committed atomically:

1. **Task 1: Install dnd-kit packages** - `415d13a` (chore)
2. **Task 2: Add flattenTree() and reorderNode()** - `f94e1db` (feat, TDD)

**Plan metadata:** (docs commit, see below)

_Note: Task 2 used TDD — tests written first (RED, 9 failures), then implementation (GREEN, 23 pass). Auto-fixes applied to clean up unused code before final commit._

## Files Created/Modified
- `web/package.json` - Added @dnd-kit/core, @dnd-kit/sortable, @dnd-kit/utilities dependencies
- `web/pnpm-lock.yaml` - Updated lockfile
- `web/src/editor/treeHelpers.ts` - Added FlatNode interface, flattenTree(), reorderNode()
- `web/src/editor/treeHelpers.test.ts` - Added 9 new tests for flattenTree and reorderNode

## Decisions Made
- D-WEB-19: reorderNode re-flattens tree after removal to resolve targetIndex against updated flat list — avoids off-by-one errors caused by the removed node shifting indices
- D-WEB-20: Insert position within new parent derived by counting that parent's existing children appearing before targetIndex in the post-removal flat list; then recomputeSortOrders handles final ordering

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Removed unused collectSubtree helper and dead insertPos variable**
- **Found during:** Task 2 (build verification after GREEN)
- **Issue:** `collectSubtree` function and `insertPos` variable declared but never used — TypeScript strict mode (TS6133) caused `pnpm build` to exit with code 2
- **Fix:** Removed collectSubtree entirely (subtree traversal not needed for reorderNode); removed the first dead loop body containing insertPos, cleaned up the comment block
- **Files modified:** web/src/editor/treeHelpers.ts
- **Verification:** `pnpm build` exits 0 after removal; all 23 tests still pass
- **Committed in:** f94e1db (part of Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - unused code causing build failure)
**Impact on plan:** Necessary for correctness; no scope creep.

## Issues Encountered
None beyond the auto-fixed unused variable issue.

## Next Phase Readiness
- FlatNode, flattenTree, and reorderNode are ready for FlatNodeList.tsx (Plan 19-02)
- dnd-kit packages installed and importable
- All 23 treeHelpers tests passing; no regressions in existing buildTree, filterSubtree, insertSiblingNode, indentNode, outdentNode tests

---
*Phase: 19-drag-and-drop*
*Completed: 2026-03-06*
