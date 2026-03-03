---
phase: 04-android-core
plan: 02
subsystem: auth
tags: [kotlin, orbit-mvi, datastore, login, session, android]

# Dependency graph
requires:
  - phase: 04-android-core
    provides: LoginViewModel with Orbit MVI ContainerHost, AuthRepository with getAccessToken() Flow
provides:
  - LoginViewModel.checkExistingSession() reads persisted DataStore token and auto-navigates on restart
  - LoginUiState.CheckingSession sentinel state prevents Credential Manager from firing before session check
  - LoginScreen gates Credential Manager launch on Idle state only (not during session check)
affects: [04-android-core, UAT gap closure for restart auth persistence]

# Tech tracking
tech-stack:
  added: []
  patterns: [session check on composition via LaunchedEffect(Unit), Orbit MVI state gating of side effects]

key-files:
  created: []
  modified:
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/login/LoginViewModel.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/login/LoginScreen.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/login/LoginViewModelTest.kt

key-decisions:
  - "Session check is triggered by LoginScreen via LaunchedEffect(Unit), not in ViewModel init block, to keep Orbit MVI pure"
  - "CheckingSession is the initial state (not Idle) so that Credential Manager LaunchedEffect(state, retryCount) does not fire until session check completes"
  - "Credential Manager is gated with `if (state !is LoginUiState.Idle) return@LaunchedEffect` - simplest correct approach"
  - "AppNavHost.kt not modified - LOGIN remains startDestination; redirect happens inside LoginScreen"

patterns-established:
  - "Session check pattern: initial state = CheckingSession, LaunchedEffect(Unit) calls checkExistingSession(), side effects gate on Idle state"

# Metrics
duration: 35min
completed: 2026-03-03
---

# Phase 04 Plan 02: Auth Persistence (Session Check) Summary

**LoginViewModel now reads the persisted DataStore token on startup and auto-navigates to DocumentList if valid, fixing UAT gap 18 (app always shows login on restart).**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-03-03T09:00Z
- **Completed:** 2026-03-03T09:35Z
- **Tasks:** 2 completed
- **Files modified:** 3

## Accomplishments

- Added `LoginUiState.CheckingSession` sentinel state and changed initial container state from Idle to CheckingSession
- Added `checkExistingSession()` to LoginViewModel that reads `authRepository.getAccessToken().first()` and posts `NavigateToDocumentList` if token exists, or reduces to `Idle` if not
- Updated LoginScreen to call `checkExistingSession()` on mount via `LaunchedEffect(Unit)` and gate Credential Manager on `LoginUiState.Idle` only
- Added 3 new tests: `initialState is CheckingSession`, `checkExistingSession withToken navigatesToDocumentList`, `checkExistingSession withNoToken transitionsToIdle`
- Fixed pre-existing `emptyIdToken produces Idle not Error` test which had an empty body that was valid when initial state was Idle but now had an unconsumed state event since initial state is CheckingSession

## Task Commits

1. **Task 1: Add CheckingSession state and checkExistingSession()** - `4067267` (feat)
2. **Task 2: Gate Credential Manager on Idle + session check tests** - `c0c0631` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/login/LoginViewModel.kt` - Added `CheckingSession` state, changed initial state, added `checkExistingSession()` with `flow.first()` import
- `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/login/LoginScreen.kt` - Added `LaunchedEffect(Unit)` for session check, changed Credential Manager `LaunchedEffect(retryCount)` to `LaunchedEffect(state, retryCount)` with Idle guard, added `CheckingSession` UI branch
- `android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/login/LoginViewModelTest.kt` - Added `every { authRepository.getAccessToken() } returns flowOf(null)` to setUp(), deleted `initialState is Idle` test, added 3 new tests, fixed `emptyIdToken produces Idle not Error` to consume the Idle state

## Decisions Made

- **CheckingSession as initial state (not Idle):** This ensures the Credential Manager LaunchedEffect keyed on `state` does not fire on first composition before the session check has a chance to run. If we had kept Idle as initial state, the Credential Manager would immediately launch on mount, racing with the session check.
- **LaunchedEffect(Unit) for session check + LaunchedEffect(state, retryCount) for Credential Manager:** Cleanly separates concerns. Session check fires once on mount. Credential Manager only fires when state becomes Idle (either initially after failed session check, or after user retry).
- **AppNavHost.kt unchanged:** No navigation-level changes needed. The redirect happens inside LoginScreen via the existing NavigateToDocumentList side effect mechanism.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] LoginScreen did not compile after adding CheckingSession to sealed class**

- **Found during:** Task 1 verification (compileDebugKotlin)
- **Issue:** Adding `CheckingSession` to `LoginUiState` made the `when(state)` expression in LoginScreen non-exhaustive, blocking compilation
- **Fix:** Applied Task 2's LoginScreen changes (CheckingSession UI branch + LaunchedEffect restructuring) in the same compilation step
- **Files modified:** `LoginScreen.kt`
- **Verification:** `./gradlew :app:compileDebugKotlin --quiet` exited 0
- **Committed in:** `c0c0631` (part of Task 2 commit per plan commit rules)

**2. [Rule 1 - Bug] emptyIdToken test body was empty - uncaught Idle state event**

- **Found during:** Task 2 test run
- **Issue:** `emptyIdToken produces Idle not Error` had an empty test body. Previously valid when initial state was Idle (no state change to consume when reducing back to Idle). After changing initial state to CheckingSession, `handleGoogleSignIn("")` now emits `Idle` as a real state change, causing Turbine to report an unconsumed event.
- **Fix:** Added `expectState(LoginUiState.Idle)` to the test body
- **Files modified:** `LoginViewModelTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest --tests "...LoginViewModelTest"` - all 9 tests pass
- **Committed in:** `c0c0631` (included in Task 2 commit)

### Pre-existing Flakiness (Not Caused by This Plan)

`BookmarkDaoTest.observeAllActive_excludesOtherUsers` fails intermittently when run as part of the full suite (but passes in isolation). Root cause: `AppDatabaseTest` leaves background Room Flow coroutines that throw uncaught exceptions after the test ends, which then get caught by the subsequent `BookmarkDaoTest`. This is a cross-test pollution issue that predates this plan and is unrelated to LoginViewModel or LoginScreen changes.

- Debug variant: `./gradlew :app:testDebugUnitTest` - BUILD SUCCESSFUL (220 tests, 0 failures)
- Release variant: `./gradlew :app:testReleaseUnitTest` - 1 pre-existing BookmarkDaoTest flake
