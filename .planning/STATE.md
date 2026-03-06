---
gsd_state_version: 1.0
milestone: v0.8
milestone_name: web-client
status: defining_requirements
last_updated: "2026-03-06T00:00:00.000Z"
last_activity: 2026-03-06 — Milestone v0.8 started
progress:
  total_phases: 0
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
---

# OutlinerGod Project State

## Current Position

Phase: Not started (defining requirements)
Plan: —
Status: Defining requirements
Last activity: 2026-03-06 — Milestone v0.8 started

**Core value:** Self-hosted Android outliner with offline-first HLC-LWW sync
**Current focus:** v0.8 web-client — React + Vite web client at https://notes.gregorymaingret.fr

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-06)

## Open Tech Debt (carried from v0.7)

- TD-D: NodeActionToolbar button set (8 buttons) diverged from Phase 10 plan spec — documentation drift only, no code issue
- TD-E: Drag/swipe gesture conflict (longPressDraggableHandle inside SwipeToDismissBox) — needs device testing

## Key Environment

- Java: Android Studio JBR at /c/Program Files/Android/Android Studio/jbr (JDK 21)
- ANDROID_HOME: /c/Users/gmain/AppData/Local/Android/Sdk
- Run tests: export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && export ANDROID_HOME="/c/Users/gmain/AppData/Local/Android/Sdk" && cd android && ./gradlew test
- Git remote: https://github.com/gmaingret/outlinergod.git (branch: master)

## Accumulated Decisions (v0.7 scope — see milestones/ for full history)

| ID | Decision | Phase-Plan | Impact |
|----|----------|------------|--------|
| D38 | undoDeletedIds parallel stack always pushed in sync with undoStack | 14-01 | NodeEditorViewModel |
| D39 | restoreNode() uses deletedAt timestamp as batch key — subtree restore | 14-01 | NodeEditorViewModel.restoreNode |
| D40 | undo() only calls restoreNodes() when deletedIds non-empty | 14-01 | NodeEditorViewModel.undo |
| D41 | snackbarHostState and scope hoisted to composable level | 14-03 | NodeEditorScreen |
| D42 | ksp.incremental.intermodule=false added for KSP FileAlreadyExistsException on Windows | 14-03 | gradle.properties |
