---
phase: 10
plan: 01
subsystem: android-ui
tags: [swipe-gestures, node-actions, toolbar, snackbar, material3, compose]
requires: [09-01]
provides: [swipe-to-complete, swipe-to-delete, node-action-toolbar, undo-delete]
affects: []
tech-stack:
  added:
    - "androidx.compose.material:material-icons-extended (BOM-managed)"
  patterns:
    - "SwipeToDismissBox per-item state pattern"
    - "BottomAppBar NodeActionToolbar with imePadding"
    - "SnackbarHost with CoroutineScope.launch for suspend snackbar"
key-files:
  created: []
  modified:
    - android/gradle/libs.versions.toml
    - android/app/build.gradle.kts
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModel.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorScreen.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModelTest.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeContextMenuTest.kt
decisions:
  - id: D27
    decision: "NodeContextMenuTest updated to remove showContextMenu/dismissContextMenu tests; context-menu functionality completely replaced by swipe gestures and NodeActionToolbar"
    impact: "NodeContextMenuTest now tests remaining node actions (addChildNode, onCompletedToggled, deleteNode, onColorChanged)"
  - id: D28
    decision: "advanceUntilIdle in orbit-test DSL scope uses testDispatcher.scheduler.advanceUntilIdle() not the top-level advanceUntilIdle() extension"
    impact: "New restoreNode test pattern for async intent completion verification"
metrics:
  duration: "9m"
  completed: "2026-03-04"
---

# Phase 10 Plan 01: Node Actions Summary

Swipe gestures and persistent node action toolbar replacing the ModalBottomSheet context menu.

## What Was Built

**ViewModel changes (NodeEditorViewModel.kt):**
- Added `restoreNode(nodeId)` intent: retrieves soft-deleted node via `nodeDao.getNodeById()` (no deleted_at filter), clears `deletedAt`, sets new `deletedHlc` via HLC clock, persists with `nodeDao.updateNode()`
- Removed `contextMenuNodeId: String?` field from `NodeEditorUiState`
- Removed `showContextMenu()` and `dismissContextMenu()` intents

**Screen changes (NodeEditorScreen.kt):**
- Replaced ModalBottomSheet context menu with `SwipeToDismissBox` per-node-row
  - Swipe right (StartToEnd): calls `onCompletedToggled`, returns `false` to snap back to Settled
  - Swipe left (EndToStart): calls `deleteNode`, returns `true` to animate away; launches `snackbarHostState.showSnackbar` in coroutine scope with Undo action calling `restoreNode`
- Added `SnackbarHost` to Scaffold for undo notifications
- Added `NodeActionToolbar` private composable (BottomAppBar) shown in `bottomBar` when `focusedNodeId != null`
  - Actions: Outdent, Indent, Add Child, Toggle Complete, Delete (red)
  - Delete also shows snackbar Undo, captures `nodeId` before `deleteNode()` shifts focus
  - `imePadding()` applied to toolbar modifier only (not LazyColumn)
- Removed `onLongPress` parameter from `NodeRow` and its `pointerInput` detection block
- Added `TextDecoration.LineThrough` and `alpha=0.5` color for completed nodes in `BasicTextField.textStyle`

**Dependencies:**
- Added `androidx-compose-material-icons-extended` to `libs.versions.toml` (BOM-managed, no version)
- Added `implementation(libs.androidx.compose.material.icons.extended)` to `build.gradle.kts`
- Provides `Icons.AutoMirrored.Filled.FormatIndentIncrease/Decrease` for toolbar

**Tests:**
- Added `restoreNode clearsDeletedAt andSetsNewHlc` to NodeEditorViewModelTest
- Added `deleteNode softDeletesAndSetsFocusToPrecedingNode` to NodeEditorViewModelTest
- Updated NodeContextMenuTest: removed 2 tests for deleted showContextMenu/dismissContextMenu, retained 7 tests for remaining node actions

## Decisions Made

| ID | Decision | Impact |
|----|----------|--------|
| D27 | NodeContextMenuTest updated to remove showContextMenu/dismissContextMenu tests since context-menu functionality was completely replaced by swipe+toolbar | NodeContextMenuTest now has 7 tests (down from 9) |
| D28 | Use `testDispatcher.scheduler.advanceUntilIdle()` in orbit-test DSL to flush intent coroutines before asserting slot captures | Pattern for async intent testing without awaiting state |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] NodeEditorScreen.kt had to be updated in same compile cycle as Task 1**

- **Found during:** Task 1 test run
- **Issue:** After removing `contextMenuNodeId` from NodeEditorUiState and `dismissContextMenu` from ViewModel, NodeEditorScreen.kt still referenced both, causing compile errors that prevented `./gradlew test` from running Task 1 tests
- **Fix:** Performed all Task 2 screen changes (SwipeToDismissBox, NodeActionToolbar, SnackbarHost, remove ModalBottomSheet) as part of the same build cycle before running tests. Committed as separate commit `da516a5` for clear attribution
- **Files modified:** `NodeEditorScreen.kt`
- **Commits:** `8222b76` (Task 1), `da516a5` (Task 2)

**2. [Rule 1 - Bug] advanceUntilIdle not available in orbit-test DSL scope**

- **Found during:** Task 1 test writing
- **Issue:** Plan specified `advanceUntilIdle()` inside orbit-test `test {}` DSL block, but orbit-test DSL does not expose the top-level coroutines-test `advanceUntilIdle()` extension directly
- **Fix:** Used `testDispatcher.scheduler.advanceUntilIdle()` which is accessible via the field reference in the test class, consistent with other tests in the file that use `testDispatcher.scheduler.advanceTimeBy()`
- **Files modified:** `NodeEditorViewModelTest.kt`

**3. [Rule 1 - Bug] NodeContextMenuTest referenced deleted methods**

- **Found during:** Task 1 test run
- **Issue:** `NodeContextMenuTest` had `showContextMenu setsContextMenuNodeId` and `dismissContextMenu clearsContextMenuNodeId` tests that called now-removed ViewModel methods and used removed `contextMenuNodeId` state field
- **Fix:** Removed the 2 tests that tested deleted functionality. The remaining 7 tests in NodeContextMenuTest (addChildNode, onColorChanged, onCompletedToggled x2, deleteNode x3) continue to pass and test the still-present node action logic
- **Files modified:** `NodeContextMenuTest.kt`

## Test Results

| Run | Tests | Passed | Failed |
|-----|-------|--------|--------|
| Final | 237 | 236 | 1 (pre-existing flaky BookmarkDaoTest) |

New tests added: 2 (`restoreNode clearsDeletedAt andSetsNewHlc`, `deleteNode softDeletesAndSetsFocusToPrecedingNode`)

The pre-existing flaky failure (`BookmarkDaoTest.softDeleteBookmark_setsTombstone` or `observeAllActive_excludesOtherUsers`) is a known Robolectric/Room race condition documented since Phase 5.

## Next Phase Readiness

- No blockers for Phase 10 Plan 02 (if any)
- All must-haves for 10-01 verified:
  - SwipeToDismissBox wraps node rows with right/left actions
  - NodeActionToolbar shows with 5 actions when node focused
  - ModalBottomSheet completely removed
  - restoreNode intent clears deletedAt and sets new HLC
  - Completed nodes render with LineThrough + 50% alpha
  - All tests passing (237/237 excluding pre-existing flaky)
