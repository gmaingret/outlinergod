---
phase: 09-android-ux-polish
plan: 01
subsystem: ui
tags: [android, compose, density, logout, drag-and-drop, orbit-mvi, datastore]

# Dependency graph
requires:
  - phase: 08-settings-sync-push
    provides: SettingsViewModel, SettingsScreen, AuthRepository with logout(), DataStore-backed settings
  - phase: 04-android-core
    provides: NodeEditorScreen with ReorderableItem drag-and-drop infrastructure
provides:
  - Corrected density scale (compact=1.0f tightest, cozy=1.20f widest)
  - Logout button in SettingsScreen with error-color styling and fail-open navigation
  - getRefreshToken() on AuthRepository interface + impl
  - logout() Orbit MVI intent in SettingsViewModel
  - Drag handle on full node row Surface (not just glyph)
affects: [10-android-node-actions, 11-search-export-bookmarks]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "fail-open logout: navigate to LoginScreen whether network call succeeds or fails"
    - "longPressDraggableHandle on Surface wrapper (full row) rather than glyph element"

key-files:
  created: []
  modified:
    - android/app/src/main/java/com/gmaingret/outlinergod/MainActivity.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/repository/AuthRepository.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/repository/impl/AuthRepositoryImpl.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/settings/SettingsViewModel.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/settings/SettingsScreen.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorScreen.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/settings/SettingsViewModelTest.kt

key-decisions:
  - "D23: compact=1.0f (tightest), comfortable=1.10f, cozy=1.20f (widest) — corrects inverted scale from original implementation"
  - "D24: getRefreshToken() added to AuthRepository interface (reads REFRESH_TOKEN_KEY from DataStore)"
  - "D25: logout() is fail-open — postSideEffect(NavigateToLogin) on both onSuccess and onFailure, and in catch"
  - "D26: longPressDraggableHandle on Surface wrapper (full row) not on NodeRow glyph — improves DnD discoverability"

patterns-established:
  - "Fail-open auth: navigation to LoginScreen always proceeds regardless of network logout result"
  - "Full-row drag: longPressDraggableHandle belongs on the Surface, not on internal glyph elements"

# Metrics
duration: 12min
completed: 2026-03-03
---

# Phase 09 Plan 01: Density-Logout-Drag-Handle Summary

**Three surgical UX fixes: corrected density scale direction (compact=1.0f to cozy=1.20f), fail-open Logout button with error styling in SettingsScreen, and drag handle moved to full node row Surface for better discoverability.**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-03-03T21:01:24Z
- **Completed:** 2026-03-03T21:13:00Z
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments
- Fixed inverted density scale: compact (1.0f, tightest) through cozy (1.20f, widest) — previously compact was 0.85f and cozy was 1.0f (backwards)
- Added full logout flow: `getRefreshToken()` on `AuthRepository` interface + impl, `logout()` Orbit MVI intent in `SettingsViewModel`, `OutlinedButton` with error color in `SettingsScreen`, 2 new test cases
- Moved `longPressDraggableHandle` from glyph element to `Surface` wrapper — long-pressing anywhere on a node row now activates drag

## Task Commits

Each task was committed atomically:

1. **Task 1: Fix density scale direction in MainActivity** - `ff38a22` (fix)
2. **Task 2: Add logout — AuthRepository, SettingsViewModel, SettingsScreen, tests** - `5c511da` (feat)
3. **Task 3: Move drag handle to full node row Surface** - `155e27d` (feat)

**Plan metadata:** *(pending docs commit)*

## Files Created/Modified
- `android/app/src/main/java/com/gmaingret/outlinergod/MainActivity.kt` - Density scale values corrected: compact=1.0f, comfortable=1.10f, cozy=1.20f
- `android/app/src/main/java/com/gmaingret/outlinergod/repository/AuthRepository.kt` - Added `getRefreshToken(): Flow<String?>` method to interface
- `android/app/src/main/java/com/gmaingret/outlinergod/repository/impl/AuthRepositoryImpl.kt` - Implemented `getRefreshToken()` reading `REFRESH_TOKEN_KEY` from DataStore
- `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/settings/SettingsViewModel.kt` - Added `logout()` Orbit MVI intent (fail-open pattern)
- `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/settings/SettingsScreen.kt` - Added `OutlinedButton` Logout at bottom of LazyColumn with `error` content color; added `ButtonDefaults`, `OutlinedButton` imports
- `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorScreen.kt` - Moved `longPressDraggableHandle` to `Surface` modifier; removed `dragModifier` parameter from `NodeRow`; removed `.then(dragModifier)` from both glyph modifier chains
- `android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/settings/SettingsViewModelTest.kt` - Added `logout withStoredToken navigatesToLogin` and `logout withNetworkFailure stillNavigatesToLogin` tests

## Decisions Made

- **D23: Density scale direction** — compact=1.0f (tightest, current default experience), comfortable=1.10f, cozy=1.20f (widest). Corrects original inverted implementation where cozy=1.0f (no change) and compact=0.85f (smaller than system default).
- **D24: getRefreshToken() on AuthRepository** — Added to interface so SettingsViewModel can retrieve the stored refresh token before calling `logout()`. REFRESH_TOKEN_KEY was already in AuthRepositoryImpl companion object.
- **D25: Fail-open logout** — `logout()` posts `NavigateToLogin` side effect whether the API call succeeds, fails, or throws (no refresh token stored). User is never stuck on SettingsScreen.
- **D26: Drag handle scope** — `longPressDraggableHandle` on the `Surface` wrapper (entire row) instead of the glyph (24dp icon area). Makes drag more discoverable and matches user expectation of long-pressing a list item.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- **Pre-existing flaky test:** `BookmarkDaoTest.observeAllActive_excludesOtherUsers` failed in the final full test run (1/236 tests). This is a documented pre-existing Robolectric/Room race condition unrelated to this plan's changes. The test passes on retry — confirmed by re-running it immediately after.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 09 Plan 01 complete. All 3 UX fixes shipped and tested.
- Density settings are now meaningful (selecting Compact gives smallest density, Cozy gives largest).
- Users can sign out from SettingsScreen — logout navigates to LoginScreen regardless of network state.
- Drag-and-drop is more discoverable — long-press anywhere on a node row initiates reorder.
- Phase 10 (android-node-actions) can proceed: node editor foundation is unchanged, only drag handle scope changed.

---
*Phase: 09-android-ux-polish*
*Completed: 2026-03-03*
