---
phase: "04"
plan: "03"
subsystem: android-ui
tags: [compose, gestures, backspace, drag-and-drop, indent, outdent, zws, text-field]

dependencies:
  requires:
    - "04-01: gap closure - empty document root node"
    - "04-02: gap closure - session check login"
  provides:
    - "ZWS sentinel for reliable backspace-on-empty node detection"
    - "Horizontal drag gesture on glyph for indent/outdent"
    - "Separated long-press gesture zones (context menu vs drag)"
  affects:
    - "04-04 and beyond: gesture interaction model is now stable"

tech-stack:
  added: []
  patterns:
    - "ZWS (zero-width space) sentinel pattern for empty TextFieldValue backspace detection"
    - "pointerInput + detectHorizontalDragGestures on glyph for swipe-to-indent"
    - "Gesture zone separation: outer Column (no clickable) + inner Column (combinedClickable on text area only)"

key-files:
  created: []
  modified:
    - "android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorScreen.kt"

decisions:
  - id: D1
    decision: "ZWS sentinel placed in textFieldValue, stripped before onContentChanged callback"
    rationale: "BasicTextField does not fire onValueChange when going from empty to empty — the ZWS gives the IME something to delete so backspace reliably triggers onValueChange"
  - id: D2
    decision: "combinedClickable moved from outer Column to inner Column wrapping BasicTextField only"
    rationale: "longPressDraggableHandle on glyph child was consuming the long-press before the parent Column's onLongClick could fire; separating zones fixes this"
  - id: D3
    decision: "Horizontal drag threshold set at 40dp"
    rationale: "40dp is enough to distinguish intentional swipe from micro-tremor, matches typical UX thresholds for one-shot actions"
  - id: D4
    decision: "pointerInput chained after dragModifier on glyph (both Box and IconButton)"
    rationale: "Glyph is already the drag handle — adding horizontal drag detection there keeps the gesture surface minimal and avoids the text area getting touch events meant for indent/outdent"

metrics:
  duration: "26 minutes"
  completed: "2026-03-03"
  tasks_completed: 2
  tasks_total: 2
---

# Phase 04 Plan 03: Gesture Fixes Summary

**One-liner:** ZWS sentinel fixes backspace-on-empty and separate gesture zones enable horizontal drag for indent/outdent via pointerInput on glyph.

## Objective

Fix 3 UAT gaps in NodeEditorScreen.kt that were blocking reliable editing:

- **Gap 10:** Backspace on empty node does nothing
- **Gap 13:** Horizontal drag for indent/outdent not wired
- **Gap 14:** Long-press always enters drag mode instead of context menu

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | ZWS backspace sentinel | 70fe27c | NodeEditorScreen.kt |
| 2 | Gesture zone separation + horizontal drag | d032a16 | NodeEditorScreen.kt |

## Implementation Details

### Task 1: ZWS Backspace Sentinel

**Problem:** `BasicTextField` does not fire `onValueChange` when the text field goes from empty to empty (e.g., user presses backspace on a node with no content). The existing code checked `oldText.isEmpty() && newText.isEmpty()` which was never reached because the callback wasn't invoked.

**Solution:**
- Added `private const val ZWS = "\u200B"` constant
- Initialize `textFieldValue` with ZWS when node content is empty: `val displayText = flatNode.entity.content.ifEmpty { ZWS }`
- In `onValueChange`: detect `oldText == ZWS && newText.isEmpty()` as the backspace-on-empty signal
- For normal edits: strip ZWS via `newText.replace(ZWS, "")` before calling `onContentChanged`
- When content becomes empty again: restore the ZWS sentinel
- Enter detection: compute `cleanPosition` by stripping ZWS before calling `onEnterPressed`

### Task 2: Gesture Zone Separation + Horizontal Drag

**Problem A (Gap 14 - Long-press context menu):**
The `combinedClickable(onLongClick = onLongPress)` was on the outer Column, but `longPressDraggableHandle` (the drag modifier) on the glyph child consumed the long-press event first. In Compose, child gesture handlers win over parent handlers.

**Fix:** Removed `combinedClickable` from the outer Column. Wrapped only the `BasicTextField` in an inner `Column` with `Modifier.weight(1f).combinedClickable(onLongClick = onLongPress)`. Long-pressing the text area now correctly fires the context menu. Long-pressing the glyph still initiates drag-to-reorder.

**Problem B (Gap 13 - Horizontal drag indent/outdent):**
No gesture detector existed for horizontal drag. `indentNode()` and `outdentNode()` existed in the ViewModel but nothing in the UI called them via gesture.

**Fix:**
- Added `onIndent: () -> Unit` and `onOutdent: () -> Unit` parameters to `NodeRow`
- Wired these to `viewModel.indentNode(flatNode.entity.id)` and `viewModel.outdentNode(flatNode.entity.id)` at the call site
- Added `pointerInput(flatNode.entity.id) { detectHorizontalDragGestures(...) }` on both the leaf node Box and the parent node IconButton
- Gesture logic: accumulates horizontal drag; fires `onIndent` at >40dp right, `onOutdent` at >40dp left; `fired` flag prevents multiple triggers per gesture

## Deviations from Plan

None - plan executed exactly as written.

## Test Results

- 220 tests completed, 1 pre-existing failure
- Failing test: `BookmarkDaoTest.observeAllActive_excludesOtherUsers` — this is a pre-existing flaky test caused by coroutine scope leakage from a preceding test in the same JVM run. The test passes when run in isolation (`./gradlew testDebugUnitTest --tests "...BookmarkDaoTest"`). This failure existed before our changes and is unrelated to NodeEditorScreen modifications.

## Next Phase Readiness

The three UAT gaps (10, 13, 14) are now fixed at the code level. Manual verification is required to confirm the gestures feel correct on device.

Remaining UAT gaps from 04-UAT.md:
- Gap 17: Settings screen controls don't change theme/density (OutlinerGodTheme not wired to SettingsDao)
- Gap 18: After app restart, user must log in again (no token check on startup)
