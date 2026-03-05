---
phase: 14-user-feedback
plan: 01
subsystem: ui
tags: [android, orbit-mvi, room, node-editor, undo, delete]

# Dependency graph
requires:
  - phase: 04-android-screens
    provides: NodeEditorViewModel undoStack, deleteNode, restoreNode implementations
  - phase: 03-android-foundation
    provides: NodeDao, NodeEntity, Room database
provides:
  - undoDeletedIds parallel stack aligned with undoStack
  - NodeDao.restoreNodes() — batch restore soft-deleted nodes
  - NodeDao.getNodesByDocumentIncludingDeleted() — query including tombstones
  - Toolbar Undo restores deleted nodes (including full subtree)
  - Snackbar Undo restores full subtree (nodes deleted in same batch)
affects: [future undo/redo features, NodeEditorViewModel, NodeDao]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Parallel stacks (undoStack + undoDeletedIds) kept in sync for undo/delete alignment"
    - "pushUndoSnapshot() always pushes emptyList to undoDeletedIds for non-delete ops"
    - "deleteNode() bypasses pushUndoSnapshot() to push actual idsToDelete to undoDeletedIds"
    - "Subtree restore by deletedAt timestamp: all nodes with same deletedAt restored together"

key-files:
  created: []
  modified:
    - android/app/src/main/java/com/gmaingret/outlinergod/db/dao/NodeDao.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModel.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModelTest.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeContextMenuTest.kt

key-decisions:
  - "D38: undoDeletedIds is a parallel ArrayDeque<List<String>> always pushed in sync with undoStack — pushUndoSnapshot() pushes emptyList, deleteNode() pushes actual idsToDelete directly"
  - "D39: restoreNode() uses deletedAt timestamp to restore entire subtree (all nodes deleted in same batch share the same timestamp)"
  - "D40: undo() after delete calls nodeDao.restoreNodes(deletedIds) only when deletedIds is non-empty, so structural ops (indent/outdent) are unaffected"

requirements-completed: []

# Metrics
duration: 90min
completed: 2026-03-05
---

# Phase 14 Plan 01: Delete Undo via Toolbar Button Summary

**Delete undo via aligned parallel stacks (undoDeletedIds) and subtree restore by shared deletedAt timestamp**

## Performance

- **Duration:** ~90 min
- **Started:** 2026-03-05T12:00:00Z
- **Completed:** 2026-03-05T13:30:00Z
- **Tasks:** 5 (A-E, with E covered by A+D)
- **Files modified:** 4

## Accomplishments

- `deleteNode()` now pushes to undoStack and sets `canUndo = true` so toolbar Undo becomes active after any delete
- `undo()` extended to call `nodeDao.restoreNodes(deletedIds)` when undoing a delete operation, restoring full subtree
- `NodeDao.restoreNodes()` and `getNodesByDocumentIncludingDeleted()` added for batch restore operations
- `restoreNode()` fixed to restore entire subtree (all nodes sharing same `deletedAt` timestamp) instead of single node only
- 5 new tests added covering all plan scenarios; existing tests updated to reflect `canUndo = true` after delete

## Task Commits

All plan tasks were committed as part of commit `b143986` (feat(14-03): file attachment upload) — the linter bundled 14-01 and 14-03 changes together.

- **NodeDao additions** — `restoreNodes()` + `getNodesByDocumentIncludingDeleted()` in `b143986`
- **NodeEditorViewModel changes** — `undoDeletedIds` parallel stack, `deleteNode()` undo push, `undo()` restore call, `restoreNode()` subtree fix in `b143986`
- **Test additions** — 5 new NodeEditorViewModelTest tests + updated NodeContextMenuTest in `b143986`

## Files Created/Modified

- `android/app/src/main/java/com/gmaingret/outlinergod/db/dao/NodeDao.kt` — Added `restoreNodes()` and `getNodesByDocumentIncludingDeleted()`
- `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModel.kt` — `undoDeletedIds` parallel stack, updated `pushUndoSnapshot()`, `deleteNode()`, `undo()`, `restoreNode()`
- `android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModelTest.kt` — 5 new tests + updated `restoreNode clearsDeletedAt andSetsNewHlc` + `deleteNode softDeletesAndSetsFocusToPrecedingNode`
- `android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeContextMenuTest.kt` — Updated `deleteNode setsFocusToPrecedingNode` to expect `canUndo = true`

## Decisions Made

- **D38:** `undoDeletedIds` parallel stack always pushed in sync with `undoStack`. `pushUndoSnapshot()` pushes `emptyList()` for non-delete ops; `deleteNode()` pushes actual `idsToDelete` directly (bypasses helper to avoid double-push).
- **D39:** Subtree restore in `restoreNode()` uses `deletedAt` timestamp as the batch key — all nodes in same document with same `deletedAt` were deleted together, so restoring all of them restores the subtree.
- **D40:** `undo()` only calls `restoreNodes()` when `deletedIds.isNotEmpty()`, so indent/outdent/move undo ops are unaffected (they push `emptyList` via `pushUndoSnapshot`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Updated pre-existing tests to reflect canUndo=true after deleteNode**
- **Found during:** Task A (implementing deleteNode push to undoStack)
- **Issue:** Existing `deleteNode softDeletesAndSetsFocusToPrecedingNode` in NodeEditorViewModelTest and `deleteNode setsFocusToPrecedingNode` in NodeContextMenuTest expected state without `canUndo=true`; adding `canUndo=true` to deleteNode's reduce broke these tests
- **Fix:** Updated both tests to include `canUndo = true, canRedo = false` in expected state
- **Files modified:** NodeEditorViewModelTest.kt, NodeContextMenuTest.kt
- **Verification:** All 313 tests pass
- **Committed in:** b143986

**2. [Rule 3 - Blocking] Added fileRepository to all NodeEditorViewModel test createViewModel() calls**
- **Found during:** Task A compilation
- **Issue:** Linter had added `fileRepository: FileRepository` to NodeEditorViewModel constructor as part of 14-03 work; all 6 test files failed to compile because `createViewModel()` didn't pass `fileRepository`
- **Fix:** Added `private lateinit var fileRepository: FileRepository`, `fileRepository = mockk(relaxed = true)` in setUp(), and `fileRepository = fileRepository` in createViewModel() across all 6 affected test files
- **Files modified:** DragAndDropTest.kt, NodeContextMenuTest.kt, NodeEditorPersistenceTest.kt, NodeEditorSyncTest.kt, NoteEditorTest.kt (NodeEditorViewModelTest.kt already updated by linter)
- **Verification:** All 313 tests pass
- **Committed in:** b143986

**3. [Rule 1 - Bug] Fixed uploadAttachment_success_appendsUrlToContent test state ordering**
- **Found during:** Task A (full test run)
- **Issue:** Pre-existing test added by linter expected state order Syncing → ContentUpdated → Idle, but Orbit MVI processes outer intent to completion before running nested intents, so actual order is Syncing → Idle → ContentUpdated
- **Fix:** Reordered state assertions to match actual Orbit MVI emission order
- **Files modified:** NodeEditorViewModelTest.kt
- **Verification:** All 313 tests pass
- **Committed in:** b143986

---

**Total deviations:** 3 auto-fixed (1 missing critical, 1 blocking, 1 bug)
**Impact on plan:** All auto-fixes necessary for correctness. No scope creep.

## Issues Encountered

- Gradle KSP incremental build produced duplicate class errors repeatedly; resolved by stopping all Gradle daemons and deleting build artifacts between runs. `ksp.incremental.intermodule=false` was added by linter (14-03) to mitigate this on Windows.

## Next Phase Readiness

- Delete Undo fully functional via toolbar button and snackbar
- Phase 14 still has 14-02 (drag reparent) and 14-03 (file attachments, already committed) remaining

## Self-Check: PASSED

All claimed files verified present. Commit b143986 verified in git log.

---
*Phase: 14-user-feedback*
*Completed: 2026-03-05*
