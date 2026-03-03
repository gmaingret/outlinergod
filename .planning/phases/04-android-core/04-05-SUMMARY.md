---
phase: 04-android-core
plan: 05
status: complete
completed: 2026-03-03T10:41:41Z
---

# Phase 04 Plan 05: Gesture Modifier Fixes Summary

## What Was Built

Fixed two broken gesture interactions in NodeEditorScreen.kt by correcting modifier chain ordering. Horizontal drag for indent/outdent was silenced because `longPressDraggableHandle` inside `dragModifier` claimed all pointer events before `detectHorizontalDragGestures` could receive them; swapping the order gives the horizontal drag detector first refusal. Long-press context menu was silenced because BasicTextField consumes all pointer events internally, blocking ancestor-level `combinedClickable`; moving `detectTapGestures(onLongPress)` directly onto BasicTextField's own modifier chain bypasses this consumption.

## Deliverables

- `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorScreen.kt`: Fixed modifier chains on both glyph paths (IconButton + Box) and on BasicTextField for long-press detection

## Tasks Completed

| Task | Commit | Files |
|------|--------|-------|
| Task 1: Fix horizontal drag modifier order on both glyph paths | d40cd72 | NodeEditorScreen.kt |
| Task 2: Move long-press detection from Column ancestor into BasicTextField modifier | 0c9a946 | NodeEditorScreen.kt |

## Decisions Made

- D7: `pointerInput(detectHorizontalDragGestures)` must precede `then(dragModifier)` on glyph composables so that horizontal drag gets first refusal in gesture arbitration
- D8: Long-press on text content must use `detectTapGestures(onLongPress)` directly on BasicTextField's modifier rather than `combinedClickable` on an ancestor Column, because BasicTextField consumes pointer-down events before they bubble up

## Issues / Deviations

### Pre-existing flaky test (not introduced by this plan)

`BookmarkDaoTest` intermittently fails with `UncaughtExceptionsBeforeTest` when run alongside the full test suite. The failure is caused by a coroutine exception from an earlier test leaking into BookmarkDaoTest's scope. The test passes reliably when run in isolation or when the full suite is run fresh (no Gradle incremental cache). This is a pre-existing issue unrelated to the gesture modifier changes. The test suite exits 0 on a clean run (220 tests, 0 failures).

### UAT unblocked

UAT tests 13 (horizontal drag indent/outdent) and 14 (long-press context menu) are now unblocked for manual verification on device.
