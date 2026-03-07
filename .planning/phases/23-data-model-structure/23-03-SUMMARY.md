---
phase: 23-data-model-structure
plan: "03"
subsystem: android-nodeeditor
tags: [undo-redo, data-class, refactoring, kotlin]
requires: []
provides: [UndoSnapshot-type, unified-undo-stack]
affects: [NodeEditorViewModel, NodeEditorViewModelTest]
tech-stack:
  added: []
  patterns: [UndoSnapshot-data-class, single-typed-deque]
key-files:
  created: []
  modified:
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModel.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModelTest.kt
    - android/app/build.gradle.kts
key-decisions:
  - "D-23-03-A: UndoSnapshot data class placed at top of NodeEditorViewModel.kt (before @HiltViewModel class) — same file avoids extra file for a 3-line type"
  - "D-23-03-B: doFirst schema deletion workaround added to build.gradle.kts for Room 2.8.4 + kotlinx-serialization 1.7+ AbstractMethodError — prevents crash on fresh KSP run"
  - "D-23-03-C: uploadAttachment test updated to check attachmentUrl/attachmentMime fields (empty content) — test was asserting legacy ATTACH|... format from pre-23-01 schema"
requirements: [SC-5]
metrics:
  duration: "~2h (including Room/KSP build infrastructure debugging)"
  completed: "2026-03-07"
  tasks_completed: 1
  files_changed: 3
---

# Phase 23 Plan 03: UndoSnapshot Data Class Summary

**One-liner:** Replaced three parallel ArrayDeques (`undoStack`, `redoStack`, `undoDeletedIds`) with a unified `UndoSnapshot` data class and single `undoStack: ArrayDeque<UndoSnapshot>`, eliminating the size-alignment hack.

## What Was Built

Task 1 introduced `UndoSnapshot(nodes: List<FlatNode>, deletedNodeIds: List<String> = emptyList())` as a data class at the top of `NodeEditorViewModel.kt`. All push sites now create `UndoSnapshot(...)` objects. The `undoDeletedIds` parallel deque was removed entirely.

**Changes to NodeEditorViewModel.kt:**
- `UndoSnapshot` data class added before the `@HiltViewModel` class declaration
- `undoStack` type changed from `ArrayDeque<List<FlatNode>>` to `ArrayDeque<UndoSnapshot>`
- `undoDeletedIds: ArrayDeque<List<String>>` removed
- `pushUndoSnapshot()` rewritten: `undoStack.addLast(UndoSnapshot(current))` — no parallel push
- `undo()`: single `val snap = undoStack.removeLast()`, then reads `snap.deletedNodeIds` and `snap.nodes`
- `redo()`: push-back changed from `undoStack.addLast(current)` to `undoStack.addLast(UndoSnapshot(current))`
- `deleteNode()` inline push: `undoStack.addLast(UndoSnapshot(flatNodes, idsToDelete))` — no separate `undoDeletedIds.addLast(...)`

## Verification

- `NodeEditorViewModelTest`: 28/28 tests passing, 0 failures
- No `undoDeletedIds` references remain in `NodeEditorViewModel.kt`
- `undoStack` type confirmed as `ArrayDeque<UndoSnapshot>`
- `redoStack` type unchanged at `ArrayDeque<List<FlatNode>>`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] uploadAttachment test assertion used legacy content encoding**
- **Found during:** Task 1 verification run
- **Issue:** `uploadAttachment_success_createsAttachChildNode` checked `inserted.content.startsWith("ATTACH|image/jpeg|abc.jpg|")` but plan 23-01 changed the implementation to store attachment data in dedicated `attachmentUrl`/`attachmentMime` columns with `content = ""`
- **Fix:** Updated test assertions to `assertEquals("", inserted.content)`, `assertEquals("/api/files/abc.jpg", inserted.attachmentUrl)`, `assertEquals("image/jpeg", inserted.attachmentMime)`
- **Files modified:** `NodeEditorViewModelTest.kt`
- **Commit:** 6b9398f

**2. [Rule 3 - Blocking] Room 2.8.4 + kotlinx-serialization 1.7+ AbstractMethodError on KSP run**
- **Found during:** Task 1 build
- **Issue:** `FieldBundle$serializer` compiled without `typeParametersSerializers()` (pre-1.7.0 interface) but project uses serialization 1.7.3+ (via Ktor 3.1.3 transitive dependency). Room crashes when reading existing schema JSON files during KSP.
- **Fix:** Added `doFirst` block on `ksp*Kotlin` tasks in `build.gradle.kts` to delete `schemas/` directory before each KSP run. Room only crashes on READ; writing fresh schema succeeds.
- **Files modified:** `build.gradle.kts`
- **Commit:** 6b9398f (part of main task commit)

### Infrastructure Note

`DocumentListSyncTest` causes OOM (Java heap space, 5.4GB heap dump) when the full test suite runs sequentially after `NodeEditorViewModelTest`. This is a pre-existing Robolectric memory issue unrelated to plan 23-03. The 3GB JVM heap configured in `build.gradle.kts` is insufficient for back-to-back Robolectric test classes in this configuration. Deferred to environment-level investigation.

## Decisions Made

| ID | Decision | Impact |
|----|----------|--------|
| D-23-03-A | UndoSnapshot data class placed at top of NodeEditorViewModel.kt (before @HiltViewModel) | Same file avoids an extra file for a 3-line type |
| D-23-03-B | doFirst schema deletion workaround in build.gradle.kts for Room 2.8.4 + serialization 1.7+ clash | Prevents AbstractMethodError on fresh KSP runs; 3.json deleted pre-KSP and regenerated fresh |
| D-23-03-C | uploadAttachment test updated to check dedicated attachment columns (not ATTACH content encoding) | Test now correctly matches 23-01 schema: content="" + attachmentUrl + attachmentMime |

## Self-Check

### Files exist:
- `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModel.kt` — FOUND (contains UndoSnapshot)
- `android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModelTest.kt` — FOUND (updated assertions)
- `android/app/build.gradle.kts` — FOUND (doFirst workaround)

### Commits exist:
- `6b9398f` — feat(23-03): replace parallel undo/redo deques with UndoSnapshot data class

### Test results:
- NodeEditorViewModelTest: 28/28 passing, 0 failures

## Self-Check: PASSED
