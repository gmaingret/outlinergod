---
plan: 12-01
status: complete
date: 2026-03-04
subsystem: sync-correctness
tags: [NodeDao, getPendingChanges, userId, dead-code, tech-debt]
requires: [11-01, 11-02, 11-03, 11-04]
provides: [userId-filtered-node-pending-changes, removed-orphaned-model]
affects: [12-02, 12-03, 12-04]
tech-stack:
  added: []
  patterns: [userId-filter-on-all-DAO-pending-changes]
key-files:
  created: []
  modified:
    - android/app/src/main/java/com/gmaingret/outlinergod/db/dao/NodeDao.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncWorker.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModel.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListViewModel.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/db/dao/NodeDaoTest.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/sync/SyncWorkerTest.kt
  deleted:
    - android/app/src/main/java/com/gmaingret/outlinergod/network/model/CreateDocumentRequest.kt
decisions:
  - id: D32
    decision: "NodeDao.getPendingChanges now requires userId as first parameter, consistent with DocumentDao and BookmarkDao — correctness fix preventing cross-user node sync pollution"
    impact: "NodeDao, SyncWorker, DocumentListViewModel, NodeEditorViewModel"
metrics:
  duration: "~45 minutes"
  completed: "2026-03-04"
---

# Phase 12 Plan 01: Tech Debt Closure (TD-2, TD-4) — Summary

Close two tech debt items: delete orphaned CreateDocumentRequest.kt and add userId filter to NodeDao.getPendingChanges, making it consistent with DocumentDao and BookmarkDao.

## What Was Built

- Deleted `CreateDocumentRequest.kt` — orphaned dead code, document creation flows through sync path
- Updated `NodeDao.getPendingChanges` signature from `(sinceHlc, deviceId)` to `(userId, sinceHlc, deviceId)` with `AND user_id = :userId` in the WHERE clause
- Updated all 3 call sites: SyncWorker, NodeEditorViewModel, DocumentListViewModel
- Added `getPendingChanges_excludesNode_fromDifferentUser` test to NodeDaoTest
- Updated SyncWorkerTest mock from 2-arg to 3-arg

## Tasks Completed

| Task | Commit | Files |
|------|--------|-------|
| Task 1: Delete CreateDocumentRequest.kt (TD-2) | 8925547 | CreateDocumentRequest.kt (deleted) |
| Task 2: Add userId filter to NodeDao.getPendingChanges + all call sites | 13d1ba6 | NodeDao.kt, SyncWorker.kt, NodeEditorViewModel.kt, DocumentListViewModel.kt, NodeDaoTest.kt, SyncWorkerTest.kt, NodeEditorScreen.kt, NodeEditorViewModelTest.kt, DragAndDropTest.kt, NoteEditorTest.kt |

## Decisions Made

| ID | Decision | Impact |
|----|----------|--------|
| D32 | NodeDao.getPendingChanges requires userId as first parameter (matches DocumentDao/BookmarkDao pattern); prevents nodes from user B being included in user A's sync push when sharing a device | NodeDao, all 3 call sites |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Pre-existing compilation break in HEAD**

- **Found during:** Task 2 verification
- **Issue:** Commit 7a73f51 (12-02) changed NodeDao.getPendingChanges to 3-arg AND changed AppNavHost.kt to pass `rootNodeId`/`onZoomIn` to NodeEditorScreen, BUT did NOT update NodeEditorViewModel (left it with 2-arg call) and did NOT commit NodeEditorScreen.kt with the new parameters. HEAD failed to compile.
- **Fix:** Applied the userId fix to NodeEditorViewModel.kt and committed the WIP NodeEditorScreen.kt + NodeEditorViewModel.kt (with rootNodeId/onZoomIn feature) that were present in the working tree as WIP. These WIP files complete the AppNavHost integration. Also fixed NodeEditorViewModelTest which used `mapToFlatList(allNodes, "n1")` for expected value computation when it should use `filterSubtree` first.
- **Files modified:** NodeEditorViewModel.kt, NodeEditorScreen.kt, NodeEditorViewModelTest.kt, DragAndDropTest.kt, NoteEditorTest.kt
- **Commit:** 13d1ba6

**2. [Rule 1 - Bug] NodeEditorViewModelTest incorrect expected value for zoom test**

- **Found during:** Task 2 test run
- **Issue:** `loadDocument withRootNodeId scopes flatList to subtree of that node` test computed `zoomedFlatNodes = mapToFlatList(allNodes, "n1")` which includes orphan nodes (n0, n1, n4) because mapToFlatList treats nodes with unknown parent IDs as orphans. The assertion `assertTrue(flatNodes.none { id == "n0" })` then failed.
- **Fix:** The stash contained a corrected version using `NodeEditorViewModel.filterSubtree(nodes, "n1")` before `mapToFlatList`. This correctly computes the expected [n2, n3] result.
- **Files modified:** NodeEditorViewModelTest.kt (already fixed in WIP)
- **Commit:** 13d1ba6

## Issues Encountered

- The 12-02 commit was committed in an incomplete state (AppNavHost referenced NodeEditorScreen parameters that didn't exist in the committed NodeEditorScreen). WIP working tree changes (not yet committed from a previous session) contained the completion of this work.
- Stash management was required to properly resolve the WIP/committed file state mismatch.
- The final task 2 commit includes more changes than the plan specified because the plan was executed in a context where prior commits had left the project in a non-compilable state.

## Tests

292 tests, 0 failures (up from 290 at end of Phase 11; 2 new WIP tests for rootNodeId feature now also pass).
