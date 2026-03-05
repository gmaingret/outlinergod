---
plan: 13-01
phase: 13-v0.6-gap-closure
status: complete
completed: 2026-03-05
subsystem: android-ui-wiring
tags: [navigation, lifecycle, sync, search, bookmarks]

dependency-graph:
  requires: [12-integration-e2e]
  provides: [v0.6-gap-closure-complete]
  affects: []

tech-stack:
  added:
    - androidx.lifecycle:lifecycle-runtime-compose:2.9.0
  patterns:
    - DisposableEffect for lifecycle observer wiring in Compose
    - nodeId as zoom-in parameter propagated through search/bookmark navigation

key-files:
  created:
    - .planning/phases/13-v0.6-gap-closure/13-01-SUMMARY.md
  modified:
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/search/SearchViewModel.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/search/SearchScreen.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/bookmarks/BookmarkListViewModel.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/bookmarks/BookmarksScreen.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/navigation/AppNavHost.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/repository/SearchRepository.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListViewModel.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorScreen.kt
    - android/gradle/libs.versions.toml
    - android/app/build.gradle.kts
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/search/SearchViewModelTest.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/bookmarks/BookmarkListViewModelTest.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModelTest.kt

decisions:
  - id: D37
    decision: "onScreenPaused() must be called immediately after onScreenResumed() in orbit-test to cancel the inactivity timer before advanceUntilIdle() fires the 30s delay"
    impact: NodeEditorViewModelTest onScreenResumed/onScreenPaused tests

metrics:
  duration: ~45 minutes
  completed: 2026-03-05
---

# Phase 13 Plan 01: v0.6-gap-closure Summary

## One-liner
Closed all 5 v0.6 audit gaps: search/bookmark zoom-in wiring with nodeId, NodeEditorScreen lifecycle sync, FTS4 docstring fix, and dead SyncLogger import removal.

## What Was Built

This plan closed all 5 items identified in the v0.6 milestone audit:

**GAP-V1 (Critical): Search result navigation zoom-in**
Search results previously navigated to the document root instead of the matched node.
`SearchSideEffect.NavigateToNodeEditor` now carries `nodeId: String`. `SearchScreen` callback signature updated to `(String, String) -> Unit`. `AppNavHost` search composable passes nodeId to `AppRoutes.nodeEditor(documentId, nodeId)`.

**GAP-V2 (Critical): NodeEditorScreen lifecycle sync wiring**
`NodeEditorViewModel.onScreenResumed()` and `onScreenPaused()` existed but were never called from the UI. Added `DisposableEffect(lifecycleOwner)` with a `LifecycleEventObserver` that fires on `ON_RESUME` and `ON_PAUSE`. Uses `androidx.lifecycle.compose.LocalLifecycleOwner` (not the deprecated UI platform version). Added `lifecycle-runtime-compose:2.9.0` dependency.

**TD-C: Bookmark node zoom-in**
`BookmarkListSideEffect.NavigateToNodeEditor` now carries `nodeId: String?`. The "node" case in `onBookmarkTapped` passes `bookmark.targetNodeId`. `BookmarksScreen` callback updated to `(String, String?) -> Unit`. `AppNavHost` bookmarks composable passes nodeId.

**TD-A: SearchRepository docstring correction**
Fixed "FTS5 MATCH" → "FTS4 MATCH" and "ranked by bm25 relevance" → "ordered by last updated".

**TD-B: Dead import removal**
Removed `import com.gmaingret.outlinergod.util.SyncLogger` from `DocumentListViewModel.kt` (class doesn't exist).

## Deliverables

- `SearchSideEffect.NavigateToNodeEditor(documentId, nodeId)` — 2-arg constructor
- `BookmarkListSideEffect.NavigateToNodeEditor(documentId, nodeId?)` — 2-arg constructor with nullable nodeId
- `NodeEditorScreen` `DisposableEffect` wiring lifecycle to `onScreenResumed()`/`onScreenPaused()`
- `SearchRepository.kt` docstring corrected to FTS4 + last-updated ordering
- Dead `SyncLogger` import removed from `DocumentListViewModel`

## Tasks Completed

| Task | Commit | Files Changed |
|------|--------|---------------|
| Task 1: GAP-V1+TD-A+TD-B+TD-C | 0c2d237 | SearchViewModel, SearchScreen, BookmarkListViewModel, BookmarksScreen, AppNavHost, SearchRepository, DocumentListViewModel, SearchViewModelTest, BookmarkListViewModelTest |
| Task 2: GAP-V2 | 13ef4a3 | NodeEditorScreen, libs.versions.toml, build.gradle.kts, NodeEditorViewModelTest |

## Test Results

- Android: 298 tests, 0 failures
- 3 new tests added (1 SearchViewModelTest + 2 NodeEditorViewModelTest)
- 2 existing tests updated (SearchViewModelTest + BookmarkListViewModelTest) to assert 2-arg constructors

## Decisions Made

| ID | Decision | Impact |
|----|----------|--------|
| D37 | In orbit-test, `onScreenPaused()` must be called immediately after `onScreenResumed()` (before `advanceUntilIdle()`) to cancel the inactivity timer. `runTest`'s scheduler advances past `delay(30_000)`, causing a second sync cycle if the timer is not cancelled first. | NodeEditorViewModelTest |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Test timing issue in onScreenResumed/onScreenPaused tests**
- **Found during:** Task 2 test verification
- **Issue:** `resetInactivityTimer()` called in `onScreenResumed()` starts a `delay(30_000)` coroutine. `runTest`'s `advanceUntilIdle()` advances past this delay, causing a second `triggerSync()` cycle. This produced 4 state changes (Syncing, Error, Syncing, Error) instead of the expected 2.
- **Fix:** Call `onScreenPaused()` immediately after `onScreenResumed()` in the test (before `advanceUntilIdle()`) to cancel the timer, ensuring only the immediate sync cycle runs.
- **Files modified:** NodeEditorViewModelTest.kt
- **Commit:** 13ef4a3

## Next Phase Readiness

All 5 v0.6 audit items are closed. The v0.6 milestone is complete.
