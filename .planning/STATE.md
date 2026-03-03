# OutlinerGod Project State

## Current Position

Phase: 08 of 08 (settings-sync-push) — COMPLETE
Plan: 01 of 1 (complete)
Status: v0.4 milestone — all audit gaps closed (GAP-A and GAP-B)
Last activity: 2026-03-03 - Completed 08-01-PLAN.md (GAP-B closed: settings now pushed bidirectionally)

Progress: ████████████████████ (completed: 01, 02, 03, 04, 05, 06, 07, 08 | planned: none remaining)

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

## Blockers / Concerns

- BookmarkDaoTest.observeAllActive_excludesOtherUsers has a flaky Robolectric/Room race condition (pre-existing, passes on retry)
- All v0.4 audit gaps (GAP-A, GAP-B) are now closed

## Key Environment

- Java: Android Studio JBR at /c/Program Files/Android/Android Studio/jbr (JDK 21)
- ANDROID_HOME: /c/Users/gmain/AppData/Local/Android/Sdk
- Run tests: export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && export ANDROID_HOME="/c/Users/gmain/AppData/Local/Android/Sdk" && cd android && ./gradlew test
- Git remote: https://github.com/gmaingret/outlinergod.git (branch: master)

## Session Continuity

Last session: 2026-03-03T19:55:56Z
Stopped at: Completed 08-01-PLAN.md — GAP-B closed, all v0.4 audit gaps resolved
Resume file: None
