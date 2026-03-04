---
phase: 11-search-export-bookmarks
plan: "04"
subsystem: ui
tags: [orbit-mvi, search, fts5, compose, kotlin-coroutines, turbine, mockk]

# Dependency graph
requires:
  - phase: 11-01
    provides: SearchRepository.searchNodes() + FTS5 Room integration
  - phase: 03-android-setup
    provides: DocumentDao.getDocumentByIdSync()
  - phase: 04-android-core
    provides: Orbit MVI container, AuthRepository.getUserId()
provides:
  - SearchViewModel with debounced FTS5 query flow via viewModelScope
  - SearchScreen UI (Scaffold, TextField, LazyColumn results, state variants)
  - SEARCH route in AppNavHost + search IconButton in DocumentListScreen
  - SearchViewModelTest (6 tests, 578 total suite passing)
affects:
  - 12-integration-e2e

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Debounced search: queryFlow (MutableStateFlow) + debounce(400L) + distinctUntilChanged + flatMapLatest in viewModelScope.launch init{}"
    - "Orbit-test scheduler unification: share TestCoroutineScheduler between testDispatcher and runTest(testDispatcher) so viewModelScope and orbit-test TestScope use same virtual clock"
    - "Infinite flow in ViewModel: use viewModelScope.launch in init{} (not orbit intent/repeatOnSubscription) to avoid orbit-test joinIntents 1s wall-clock timeout"

key-files:
  created:
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/search/SearchViewModel.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/search/SearchScreen.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/search/SearchViewModelTest.kt
  modified:
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/navigation/AppRoutes.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/navigation/AppNavHost.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListScreen.kt

key-decisions:
  - "D29: viewModelScope.launch in init{} for queryFlow collection — orbit-test joinIntents waits 1s wall-clock for all orbit intents; repeatOnSubscription in onCreate causes timeout because the subscription coroutine is infinite"
  - "D30: Shared TestCoroutineScheduler pattern — create scheduler explicitly, pass to both StandardTestDispatcher and runTest(dispatcher) so viewModelScope and orbit TestScope share one virtual clock"
  - "D31: intent { reduce { newState } } called from within viewModelScope.launch — short-lived intents complete immediately; only the outer viewModelScope launch is non-orbit and not tracked by joinIntents"

patterns-established:
  - "SearchScreen: autofocus via FocusRequester in LaunchedEffect(Unit)"
  - "State variants: Idle (type hint) / Loading (spinner) / Success (LazyColumn) / NoResults (message) / Error (red message)"

# Metrics
duration: 45min
completed: 2026-03-04
---

# Phase 11 Plan 04: Search Screen Summary

**Orbit MVI SearchViewModel with 400ms debounced FTS5 search, SearchScreen Compose UI, and unified-scheduler test pattern for virtual-time control of debounced flows**

## Performance

- **Duration:** ~45 min
- **Started:** 2026-03-04T08:30:00Z
- **Completed:** 2026-03-04T09:15:00Z
- **Tasks:** 2 completed
- **Files modified:** 6

## Accomplishments

- SearchViewModel: `queryFlow` (MutableStateFlow) debounced 400ms, flatMapLatest into Loading/Success/NoResults/Error states, launched in `viewModelScope` init block
- SearchScreen: Scaffold + TopAppBar (back nav) + autofocused OutlinedTextField + LazyColumn results showing content/note/documentTitle with FocusRequester
- Navigation wired: SEARCH route in AppNavHost, search IconButton in DocumentListScreen TopAppBar
- 6 SearchViewModelTest cases passing; full suite 578 tests, 0 failures

## Task Commits

Each task was committed atomically:

1. **Task 1: SearchViewModel + SearchScreen + navigation wiring** - `2363616` (feat)
2. **Task 2: SearchViewModelTest** - `6f11fc3` (test)

**Plan metadata:** (pending this commit)

## Files Created/Modified

- `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/search/SearchViewModel.kt` - Orbit MVI ViewModel with debounced queryFlow, SearchResultItem, SearchUiState, SearchSideEffect
- `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/search/SearchScreen.kt` - Compose UI: TextField + state-based result display, side-effect navigation
- `android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/search/SearchViewModelTest.kt` - 6 unit tests covering all state transitions and side effect
- `android/app/src/main/java/com/gmaingret/outlinergod/ui/navigation/AppRoutes.kt` - Added SEARCH = "search" constant
- `android/app/src/main/java/com/gmaingret/outlinergod/ui/navigation/AppNavHost.kt` - Added SearchScreen composable + updated DocumentListScreen call
- `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListScreen.kt` - Added search IconButton in TopAppBar actions

## Decisions Made

**D29 - viewModelScope.launch for infinite flow collection**

Orbit-test v10 calls `joinIntents()` at teardown with a 1-second wall-clock timeout. `repeatOnSubscription` inside `onCreate` creates a coroutine that never completes, causing `OrbitTimeoutCancellationException`. Solution: launch the `queryFlow` collection in `viewModelScope.launch` inside `init {}` — this is outside orbit's intent system, so `joinIntents()` does not wait for it.

**D30 - Shared TestCoroutineScheduler for unified virtual time**

`Dispatchers.setMain(testDispatcher)` controls `viewModelScope`. But `runTest` creates its own TestScope with a separate scheduler. If the schedulers are different, `testScheduler.advanceTimeBy(500)` inside `viewModel.test(this)` advances the orbit TestScope clock but NOT the `viewModelScope` debounce clock — the debounce never fires. Fix: create `testScheduler = TestCoroutineScheduler()` explicitly, create `testDispatcher = StandardTestDispatcher(testScheduler)`, and pass `testDispatcher` to `runTest(testDispatcher)`. Now both contexts share one scheduler.

**D31 - intent { reduce { } } from within viewModelScope launch**

Each debounced emission calls `intent { reduce { newState } }`. These short-lived intents complete synchronously after the reduction — `joinIntents()` has no trouble waiting for them. The outer `viewModelScope.launch` block is not orbit-managed and causes no teardown issues.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] SearchViewModel refactored from repeatOnSubscription to viewModelScope.launch**
- **Found during:** Task 2 (SearchViewModelTest)
- **Issue:** `repeatOnSubscription` in `onCreate` caused orbit-test `joinIntents()` 1s wall-clock timeout — all 4 debounce tests failed with `OrbitTimeoutCancellationException: Timed out waiting for remaining intents to complete for 1s`
- **Fix:** Moved queryFlow collection to `viewModelScope.launch` in `init {}`, calling `intent { reduce { } }` per emission
- **Files modified:** SearchViewModel.kt
- **Verification:** All 6 tests pass; `./gradlew test` exits 0 with 578/578 tests passing
- **Committed in:** `6f11fc3` (Task 2 commit)

**2. [Rule 1 - Bug] Test scheduler unification**
- **Found during:** Task 2 (SearchViewModelTest)
- **Issue:** `testDispatcher.scheduler` (from `@Before`) and `testScheduler` (from `runTest`) were separate `TestCoroutineScheduler` instances — advancing one did not advance the other, so debounce virtual time never progressed inside orbit's TestScope
- **Fix:** Create shared `TestCoroutineScheduler()`, pass to both `StandardTestDispatcher(testScheduler)` and `runTest(testDispatcher)`, use `testScheduler.advanceTimeBy/advanceUntilIdle` throughout
- **Files modified:** SearchViewModelTest.kt
- **Verification:** All debounce-related tests now advance correctly
- **Committed in:** `6f11fc3` (Task 2 commit)

**3. [Rule 1 - Bug] "empty query returns to Idle" test redesigned**
- **Found during:** Task 2 test analysis
- **Issue:** Original test called `onQueryChanged("")` from initial Idle state — `distinctUntilChanged()` suppressed the duplicate `""` value, no state change emitted, `expectState(Idle)` hung
- **Fix:** Redesigned as "empty query after search returns to Idle" — first set a query, observe NoResults, then clear query and observe Idle transition
- **Files modified:** SearchViewModelTest.kt
- **Verification:** Test passes correctly
- **Committed in:** `6f11fc3` (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (all Rule 1 - Bug fixes during test implementation)
**Impact on plan:** All fixes necessary for test correctness. ViewModel behavior is unchanged; only the internal coroutine launch mechanism was adjusted for testability.

## Issues Encountered

**Orbit-test v10 + infinite flow testing is non-trivial.** The combination of:
- `repeatOnSubscription` (orbit's "while subscribed" API)
- Turbine wall-clock timeouts (3s for items, 1s for intents)
- Separate `TestCoroutineScheduler` instances for `viewModelScope` vs orbit TestScope

...required careful architectural choices. The established pattern (D29 + D30) is reusable for any ViewModel with background flow collection.

## Next Phase Readiness

- Search screen fully functional and tested
- Navigation from DocumentList → Search → NodeEditor works
- Phase 11 plans 01-04 complete (FTS5, Export, Bookmarks, Search)
- Ready for Phase 12 integration/E2E testing

---
*Phase: 11-search-export-bookmarks*
*Completed: 2026-03-04*
