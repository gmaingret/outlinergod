---
phase: "04"
plan: "04"
subsystem: android-ui-theme
tags: [android, compose, settings, theme, density, hilt, room, flow]

dependency-graph:
  requires:
    - "04-03: SettingsViewModel and SettingsScreen (writes settings to Room)"
    - "03-07: SettingsDao (Room DAO for settings entity)"
    - "03-12: AuthRepository (getAccessToken flow)"
  provides:
    - "Live theme (dark/light) driven by SettingsEntity.theme"
    - "Live density scale driven by SettingsEntity.density"
    - "OutlinerGodTheme accepts darkTheme + densityScale parameters"
    - "MainActivity injects SettingsDao and AuthRepository for settings observation"
  affects:
    - "All future screens inherit the live theme from OutlinerGodTheme"

tech-stack:
  added: []
  patterns:
    - "flatMapLatest on auth token flow to gate settings observation on login state"
    - "CompositionLocalProvider to override LocalDensity for density scaling"
    - "Hilt field injection in Activity (@Inject lateinit var)"

key-files:
  created: []
  modified:
    - android/app/src/main/java/com/gmaingret/outlinergod/MainActivity.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/theme/OutlinerGodTheme.kt

decisions:
  - id: D1
    decision: "Use flatMapLatest on getAccessToken() to gate settings observation"
    rationale: "When user is not logged in, accessToken is null and we emit flowOf(null) to avoid querying Room with an empty userId"
    alternatives: ["Always observe with a fixed fallback userId"]
  - id: D2
    decision: "Default theme mapping - null/unknown settings maps to dark theme"
    rationale: "App default is dark as established in SettingsEntity defaults (theme='dark')"
    alternatives: ["Use system default when settings null"]
  - id: D3
    decision: "densityScale: cozy=1.0, comfortable=0.95, compact=0.85"
    rationale: "Three density tiers matching the SettingsScreen FilterChip options"
    alternatives: ["Different scale values"]

metrics:
  duration: "38 minutes"
  completed: "2026-03-03"
  tasks-completed: 1
  tasks-total: 1
  test-count: 220
  tests-passing: 220
---

# Phase 04 Plan 04: Settings Theme Wiring Summary

**One-liner:** Live theme and density from Room SettingsDao observation in MainActivity via flatMapLatest on auth token flow.

## What Was Built

Wired the SettingsDao observation into MainActivity so that the OutlinerGodTheme receives live `darkTheme` and `densityScale` parameters sourced from the user's persisted settings.

### OutlinerGodTheme.kt changes
- Added `densityScale: Float = 1.0f` parameter
- Uses `CompositionLocalProvider` to provide a custom `Density` (base density multiplied by scale)
- `content` wrapped in `MaterialTheme { CompositionLocalProvider { ... } }` pattern

### MainActivity.kt changes
- Injects `SettingsDao` and `AuthRepository` via Hilt field injection
- Creates `settingsFlow` using `flatMapLatest` on `authRepository.getAccessToken()`: emits `settingsDao.getSettings(accessToken)` when logged in, `flowOf(null)` when not
- `@OptIn(ExperimentalCoroutinesApi::class)` on `onCreate` for `flatMapLatest`
- Inside `setContent`, collects `settingsFlow` as state and maps theme/density strings to typed values
- Passes `darkTheme` and `densityScale` to `OutlinerGodTheme`

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Wire SettingsDao to OutlinerGodTheme | 81091ac | MainActivity.kt, OutlinerGodTheme.kt |

## Test Results

220 tests, 220 passing, 0 failures.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] LoginScreen.kt missing CheckingSession branch in exhaustive when**

- **Found during:** clean build (`./gradlew clean test`)
- **Issue:** `when (state)` over `LoginUiState` sealed class was missing the `CheckingSession` branch added in commit c0c0631 for session-check logic; clean compilation caught it
- **Fix:** Already present in working tree from previous commit (c0c0631) - the clean build confirmed the source was correct; stale incremental build artifacts had masked the error
- **Files modified:** None (already correct in source)
- **Commit:** N/A (fix was pre-existing in c0c0631)

None of the plan's intended changes required deviation.

## Next Phase Readiness

- UAT gap 17 (settings screen controls do nothing) is now resolved
- Theme and density changes in SettingsScreen will now propagate live to the entire app
- No blockers for further UAT verification
