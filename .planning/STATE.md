# OutlinerGod Project State

## Current Position

Phase: 05 of 06 (sync-integration-fixes)
Plan: 01 of 05 (in progress)
Status: In progress
Last activity: 2026-03-03 - Completed 05-01-PLAN.md (HLC decimal format + AuthRepository getUserId)

Progress: ████████░░░░░░░░ (completed: 01, 02, 03, 04 | pending: 05, 06)

Phase 05 plan progress: █░░░░ (1/5 plans complete)

## Accumulated Decisions

| ID | Decision | Phase-Plan | Impact |
|----|----------|------------|--------|
| D1 | sort_order is always TEXT (fractional indexing) | architecture | all DAOs |
| D2 | flatMapLatest on getAccessToken() for settings observation | 04-04 | MainActivity theme |
| D3 | Default theme null/unknown maps to dark | 04-04 | OutlinerGodTheme |
| D4 | ViewModel logic in companion object pure functions for JVM testability | 04-07 | all ViewModels |
| D5 | reorderable 2.5.1 (not 3.0.0 which doesn't exist) | 04-10 | DnD |
| D6 | Orbit MVI 10.0.0 for all ViewModels | 04-01 | all screens |
| D7 | Glyph horizontal drag uses PointerEventPass.Initial (capture phase) so it fires before longPressDraggableHandle (Main pass) | 04-05 | NodeEditorScreen gesture arbitration |
| D8 | Long-press on text content uses PointerEventPass.Initial on parent Column (not BasicTextField modifier); Initial pass precedes BasicTextField's text-selection handler (Main pass) | 04-05 | NodeEditorScreen long-press context menu |
| D9 | HLC format changed from hex to decimal: 13-digit decimal wall + 5-digit decimal counter (matches backend hlc.ts) | 05-01 | HlcClock, all sync records |
| D10 | Property test for HLC wall length uses range 1_000_000_000_000..9_999_999_999_999 (13-digit epoch window, covers 2001-2286) | 05-01 | HlcClockTest |

## Blockers / Concerns

- UAT gap: Empty document has no initial node (root node not created on document creation) - diagnosed in 04-04-UAT
- UAT test 16 (collapse/expand) was previously skipped; now unblocked — use Add Child via context menu to create a child node, then verify
- BookmarkDaoTest.observeAllActive_excludesOtherUsers has a flaky Robolectric/Room Invalidation Tracker race condition (pre-existing, passes on retry)

## Session Continuity

Last session: 2026-03-03T12:45:12Z
Stopped at: Completed 05-01-PLAN.md
Resume file: None
