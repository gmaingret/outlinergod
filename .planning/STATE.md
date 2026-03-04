# OutlinerGod Project State

## Current Position

Phase: 11 of 12 (search-export-bookmarks) — COMPLETE
Plan: 04 of 4 (all complete)
Status: v0.5 complete — Phase 11 complete (13/13 must-haves verified, 289 tests passing)
Last activity: 2026-03-04 - Phase 11 executed and verified: FTS5 search, ZIP export, bookmarks screen, search screen UI

Progress: ███████████░ (completed: 01-11 | planned: 12-integration-e2e)

## Accumulated Decisions

| ID | Decision | Phase-Plan | Impact |
|----|----------|------------|--------|
| D1 | sort_order is always TEXT (fractional indexing) | architecture | all DAOs |
| D2 | flatMapLatest on getAccessToken() for settings observation | 04-04 | MainActivity theme — SUPERSEDED by D17 |
| D3 | Default theme null/unknown maps to dark | 04-04 | OutlinerGodTheme |
| D4 | ViewModel logic in companion object pure functions for JVM testability | 04-07 | all ViewModels |
| D5 | reorderable 2.5.1 (not 3.0.0 which doesn't exist) | 04-10 | DnD |
| D6 | Orbit MVI 10.0.0 for all ViewModels | 04-01 | all screens |
| D7 | Glyph horizontal drag uses PointerEventPass.Initial (capture phase) | 04-05 | NodeEditorScreen |
| D8 | Long-press on text content uses PointerEventPass.Initial on parent Column | 04-05 | NodeEditorScreen |
| D9 | HLC format: 13-digit decimal wall + 5-digit decimal counter | 05-01 | HlcClock, sync records |
| D10 | HLC property test wall range: 1_000_000_000_000..9_999_999_999_999 | 05-01 | HlcClockTest |
| D11 | getUserId() for DAO queries; getAccessToken() only for Ktor/LoginViewModel | 05-02 | ViewModels, SyncWorker |
| D12 | CreateDocumentRequest uses @SerialName for snake_case POST body | 05-02 | DocumentListViewModel |
| D13 | Phase 01 VERIFICATION status is code_confirmed_unverified | 06-02 | Audit trail |
| D14 | Phase 02 VERIFICATION status is code_confirmed_doc_gap | 06-02 | Audit trail |
| D15 | FractionalIndex.generateKeyBetween(null, null) returns "aV" not "aP" | 06-01 | sort-order tests |
| D16 | syncStatus field removed from entities (Option B: drop Kotlin field, keep SQLite column) | 06-01 | Room entity schema |
| D17 | GAP-A fix: MainActivity must call getUserId() (UUID) not getAccessToken() (JWT) for settingsDao.getSettings(); JWT string never matches UUID-keyed settings rows | 07-01 | MainActivity theme/density |
| D18 | SyncConstants object in sync package holds LAST_SYNC_HLC_KEY; all callers import from SyncConstants — no duplication across companion objects | 07-01 | SyncWorker, DocumentListViewModel, NodeEditorViewModel |
| D19 | ExistingPeriodicWorkPolicy.UPDATE replaces deprecated KEEP in SyncScheduler | 07-01 | WorkManager periodic sync |
| D20 | SettingsSyncRecord.id defaults to "" for pull-direction backward compatibility; push-direction always sets id = userId via mapper | 08-01 | Sync.kt, SyncMappers.kt |
| D21 | getPendingSettings filters by device_id to prevent echo-back (pulled settings must not be re-pushed) | 08-01 | SettingsDao, all push sites |
| D22 | GAP-B closed: settings flow bidirectionally; SyncPushPayload.settings was always null before this phase | 08-01 | SyncWorker, DocumentListVM, NodeEditorVM |
| D23 | Density scale direction: compact=1.0f (tightest), comfortable=1.10f, cozy=1.20f (widest) — corrects inverted original | 09-01 | MainActivity densityScale |
| D24 | getRefreshToken(): Flow<String?> added to AuthRepository interface + impl (reads REFRESH_TOKEN_KEY from DataStore) | 09-01 | SettingsViewModel.logout() |
| D25 | logout() is fail-open: postSideEffect(NavigateToLogin) on success, failure, and catch — user never stuck on SettingsScreen | 09-01 | SettingsViewModel, SettingsScreen |
| D26 | longPressDraggableHandle on Surface (full row), not NodeRow glyph — improves DnD discoverability | 09-01 | NodeEditorScreen |
| D27 | NodeContextMenuTest updated to remove showContextMenu/dismissContextMenu tests; context-menu functionality completely replaced by swipe gestures and NodeActionToolbar | 10-01 | NodeContextMenuTest |
| D28 | Use testDispatcher.scheduler.advanceUntilIdle() in orbit-test DSL to flush intent coroutines before asserting slot captures (advanceUntilIdle() top-level not in scope) | 10-01 | NodeEditorViewModelTest |
| D29 | viewModelScope.launch in init{} for infinite queryFlow collection — orbit-test joinIntents has 1s wall-clock timeout; repeatOnSubscription in onCreate causes OrbitTimeoutCancellationException | 11-04 | SearchViewModel |
| D30 | Shared TestCoroutineScheduler pattern: create scheduler explicitly, pass to StandardTestDispatcher(testScheduler) AND runTest(testDispatcher) — viewModelScope and orbit TestScope share one virtual clock | 11-04 | SearchViewModelTest |
| D31 | intent { reduce { newState } } from viewModelScope.launch — short-lived intents complete immediately; outer launch is non-orbit and not tracked by joinIntents | 11-04 | SearchViewModel |

## Blockers / Concerns

- BookmarkDaoTest.observeAllActive_excludesOtherUsers has a flaky Robolectric/Room race condition (pre-existing, passes on retry)
- All v0.4 audit gaps (GAP-A, GAP-B) are now closed

## Key Environment

- Java: Android Studio JBR at /c/Program Files/Android/Android Studio/jbr (JDK 21)
- ANDROID_HOME: /c/Users/gmain/AppData/Local/Android/Sdk
- Run tests: export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && export ANDROID_HOME="/c/Users/gmain/AppData/Local/Android/Sdk" && cd android && ./gradlew test
- Git remote: https://github.com/gmaingret/outlinergod.git (branch: master)

## Session Continuity

Last session: 2026-03-04T13:30:00Z
Stopped at: Completed Phase 11 (4/4 plans, 13/13 must-haves verified). FTS5 search, ZIP export, bookmarks screen, search screen all shipped. 289 tests, 0 failures.
Resume file: None
