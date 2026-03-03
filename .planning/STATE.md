# OutlinerGod Project State

## Current Position

Phase: 04 of 04 (android-core)
Plan: 05 of 05 (gesture modifier fixes)
Status: Complete
Last activity: 2026-03-03 - Completed 04-05-PLAN.md

Progress: ████████████████ (completed: 04-01, 04-02, 04-03, 04-04, 04-05)

## Accumulated Decisions

| ID | Decision | Phase-Plan | Impact |
|----|----------|------------|--------|
| D1 | sort_order is always TEXT (fractional indexing) | architecture | all DAOs |
| D2 | flatMapLatest on getAccessToken() for settings observation | 04-04 | MainActivity theme |
| D3 | Default theme null/unknown maps to dark | 04-04 | OutlinerGodTheme |
| D4 | ViewModel logic in companion object pure functions for JVM testability | 04-07 | all ViewModels |
| D5 | reorderable 2.5.1 (not 3.0.0 which doesn't exist) | 04-10 | DnD |
| D6 | Orbit MVI 10.0.0 for all ViewModels | 04-01 | all screens |
| D7 | pointerInput(detectHorizontalDragGestures) must precede then(dragModifier) on glyph composables | 04-05 | NodeEditorScreen gesture arbitration |
| D8 | Long-press on text content uses detectTapGestures directly on BasicTextField modifier (not combinedClickable on ancestor) | 04-05 | NodeEditorScreen long-press context menu |

## Blockers / Concerns

- UAT gap: Empty document has no initial node (root node not created on document creation) - diagnosed in 04-04-UAT
- UAT tests 13 and 14 (horizontal drag, long-press context menu) are now unblocked for manual verification

## Session Continuity

Last session: 2026-03-03T10:41:41Z
Stopped at: Completed 04-05-PLAN.md
Resume file: None
