---
gsd_state_version: 1.0
milestone: v0.7
milestone_name: user-feedback
status: in_progress
stopped_at: "Completed 14-03-PLAN.md"
last_updated: "2026-03-05T00:00:00.000Z"
last_activity: "2026-03-05 - Executed 14-03: file attachments upload"
progress:
  total_phases: 14
  completed_phases: 13
  total_plans: 15
  completed_plans: 15
---

# OutlinerGod Project State

## Current Position

Status: v0.7 IN PROGRESS — Phase 14 complete (3/3 plans executed)
Last activity: 2026-03-05 — Completed 14-03 file attachments upload (313/313 tests passing)

**Core value:** Self-hosted Android outliner with offline-first HLC-LWW sync
**Current focus:** Phase 14 complete — user feedback features done

## Project Reference

See: .planning/MILESTONES.md (updated 2026-03-05)
See: .planning/ROADMAP.md (collapsed — next milestone TBD)

## Open Tech Debt (for next milestone planning)

- TD-D: NodeActionToolbar button set (8 buttons) diverged from Phase 10 plan spec (documentation drift)
- TD-E: Drag/swipe gesture conflict (longPressDraggableHandle inside SwipeToDismissBox) — needs device testing
- 12-05-SUMMARY.md never written (plan implemented in commit d798e35, code verified in 12-VERIFICATION.md)

## Key Environment

- Java: Android Studio JBR at /c/Program Files/Android/Android Studio/jbr (JDK 21)
- ANDROID_HOME: /c/Users/gmain/AppData/Local/Android/Sdk
- Run tests: export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && export ANDROID_HOME="/c/Users/gmain/AppData/Local/Android/Sdk" && cd android && ./gradlew test
- Git remote: https://github.com/gmaingret/outlinergod.git (branch: master)

## Accumulated Decisions (v0.6 scope — see milestones/v0.6-ROADMAP.md for full list)

| ID | Decision | Phase-Plan | Impact |
|----|----------|------------|--------|
| D23 | Density scale direction: compact=1.0f (tightest), comfortable=1.10f, cozy=1.20f (widest) | 09-01 | MainActivity densityScale |
| D24 | getRefreshToken(): Flow<String?> added to AuthRepository interface + impl | 09-01 | SettingsViewModel.logout() |
| D25 | logout() is fail-open: postSideEffect(NavigateToLogin) on success, failure, and catch | 09-01 | SettingsViewModel, SettingsScreen |
| D26 | longPressDraggableHandle on Surface (full row), not NodeRow glyph | 09-01 | NodeEditorScreen |
| D29 | viewModelScope.launch in init{} for infinite queryFlow collection | 11-04 | SearchViewModel |
| D30 | Shared TestCoroutineScheduler pattern for shared virtual clock | 11-04 | SearchViewModelTest |
| D32 | Rate limit scoped inside auth plugin (not buildApp global) | 12-03 | auth.ts plugin registration |
| D35 | filterSubtree collects descendants only before mapToFlatList | 12-02 | NodeEditorViewModel.filterSubtree |
| D36 | Zoom-in uses Navigation back stack (each zoom = separate NavBackStackEntry) | 12-02 | AppNavHost, NodeEditorScreen |
| D37 | In orbit-test, onScreenPaused() must be called before advanceUntilIdle() to cancel 30s inactivity timer | 13-01 | NodeEditorViewModelTest |
| D38 | undoDeletedIds parallel stack always pushed in sync with undoStack; deleteNode pushes actual ids, pushUndoSnapshot pushes emptyList | 14-01 | NodeEditorViewModel |
| D39 | restoreNode() uses deletedAt timestamp as batch key — all nodes with same timestamp restored together (subtree restore) | 14-01 | NodeEditorViewModel.restoreNode |
| D40 | undo() only calls restoreNodes() when deletedIds non-empty, so structural ops (indent/outdent) are unaffected | 14-01 | NodeEditorViewModel.undo |
| D41 | snackbarHostState and scope hoisted to composable level so ShowError works from any status branch | 14-03 | NodeEditorScreen |
| D42 | ksp.incremental.intermodule=false added to fix KSP FileAlreadyExistsException on Windows | 14-03 | gradle.properties |
