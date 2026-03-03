---
phase: 07-settings-display-and-techdebt
plan: 01
subsystem: ui
tags: [android, kotlin, settings, datastore, workmanager, room, orbit-mvi]

# Dependency graph
requires:
  - phase: 04-android-ui
    provides: SettingsViewModel, MainActivity, DocumentListViewModel, NodeEditorViewModel, SyncWorker, SyncScheduler
  - phase: 05-android-integration
    provides: getUserId() pattern established for DAO queries
  - phase: 06-tech-debt-cleanup
    provides: v0.4 milestone audit gaps identified (GAP-A, GAP-B)
provides:
  - GAP-A closed: MainActivity now queries settings by userId (UUID), not accessToken (JWT string)
  - Single LAST_SYNC_HLC_KEY constant in SyncConstants.kt (no more duplication)
  - SyncScheduler uses ExistingPeriodicWorkPolicy.UPDATE (not deprecated KEEP)
  - Dead code removed: orphaned db/entity/SyncStatus.kt deleted
affects:
  - 08-backend-gap-closure (follows this phase in gap-closure roadmap)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Shared sync constants in SyncConstants object (sync package) — single source of truth"
    - "getUserId() for all settings/DAO lookups; getAccessToken() only for JWT/Ktor"

key-files:
  created:
    - android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncConstants.kt
  modified:
    - android/app/src/main/java/com/gmaingret/outlinergod/MainActivity.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncScheduler.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncWorker.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListViewModel.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModel.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/sync/SyncWorkerTest.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListSyncTest.kt
  deleted:
    - android/app/src/main/java/com/gmaingret/outlinergod/db/entity/SyncStatus.kt

key-decisions:
  - "D17: GAP-A fix — MainActivity must call getUserId() (UUID) not getAccessToken() (JWT) for settingsDao.getSettings(); JWT string never matches UUID-keyed settings rows"
  - "D18: SyncConstants object in sync package holds LAST_SYNC_HLC_KEY; all callers (SyncWorker, DocumentListViewModel, NodeEditorViewModel, tests) import from SyncConstants"
  - "D19: ExistingPeriodicWorkPolicy.UPDATE replaces deprecated KEEP; UPDATE re-evaluates constraints on policy change rather than ignoring the new request"

patterns-established:
  - "SyncConstants: shared constants for sync subsystem live in sync/SyncConstants.kt"
  - "getUserId() for DB/DAO userId queries; getAccessToken() only for JWT token transmission"

# Metrics
duration: 15min
completed: 2026-03-03
---

# Phase 07 Plan 01: Settings Display and Tech Debt Summary

**GAP-A closed: MainActivity now uses getUserId() so theme/density settings actually apply; plus SyncConstants deduplication and WorkManager policy fix**

## Performance

- **Duration:** 15 min
- **Started:** 2026-03-03T19:11:23Z
- **Completed:** 2026-03-03T19:26:35Z
- **Tasks:** 2 of 2
- **Files modified:** 7 modified, 1 created, 1 deleted

## Accomplishments
- Closed GAP-A: MainActivity was passing a JWT access token string to `settingsDao.getSettings()`, which uses a UUID `userId` as key — the lookup always returned null, so theme/density/guideline settings changes were never applied visually. One-line fix to call `getUserId()` instead.
- Extracted `LAST_SYNC_HLC_KEY` from 3 separate companion objects (SyncWorker, DocumentListViewModel, NodeEditorViewModel) into a single `SyncConstants.LAST_SYNC_HLC_KEY`; all callers and tests updated.
- Fixed `SyncScheduler` to use `ExistingPeriodicWorkPolicy.UPDATE` (modern replacement for deprecated `KEEP`).
- Deleted orphaned `db/entity/SyncStatus.kt` (never imported; active `SyncStatus` lives at `ui/common/SyncStatus.kt`).

## Task Commits

Each task was committed atomically:

1. **Task 1: Fix MainActivity getUserId() and delete orphaned SyncStatus enum** - `0188b12` (fix)
2. **Task 2: Fix SyncScheduler policy and extract LAST_SYNC_HLC_KEY to shared constant** - `e71eb32` (refactor)

**Plan metadata:** (pending — docs commit)

## Files Created/Modified
- `android/app/src/main/java/com/gmaingret/outlinergod/MainActivity.kt` - Changed `getAccessToken()` to `getUserId()` in settingsFlow flatMapLatest
- `android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncConstants.kt` - New file: `object SyncConstants` with single `LAST_SYNC_HLC_KEY` definition
- `android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncScheduler.kt` - `ExistingPeriodicWorkPolicy.KEEP` -> `UPDATE`
- `android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncWorker.kt` - Removed companion object (was only holding LAST_SYNC_HLC_KEY), now uses `SyncConstants.LAST_SYNC_HLC_KEY`
- `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListViewModel.kt` - Removed companion object, uses `SyncConstants.LAST_SYNC_HLC_KEY`
- `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModel.kt` - Removed `LAST_SYNC_HLC_KEY` from companion (kept `recomputeFlatNodes`), uses `SyncConstants.LAST_SYNC_HLC_KEY`
- `android/app/src/test/java/com/gmaingret/outlinergod/sync/SyncWorkerTest.kt` - `SyncWorker.LAST_SYNC_HLC_KEY` -> `SyncConstants.LAST_SYNC_HLC_KEY`
- `android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListSyncTest.kt` - `DocumentListViewModel.LAST_SYNC_HLC_KEY` -> `SyncConstants.LAST_SYNC_HLC_KEY`
- `android/app/src/main/java/com/gmaingret/outlinergod/db/entity/SyncStatus.kt` - DELETED (orphaned enum, zero imports)

## Decisions Made
- **D17 (GAP-A):** MainActivity must call `getUserId()` (returns UUID) not `getAccessToken()` (returns JWT string) when querying `settingsDao.getSettings(userId)`. The settings rows are keyed by UUID user ID — passing a JWT token string always yielded null settings, so SettingsScreen writes (using getUserId) were invisible to MainActivity.
- **D18 (SyncConstants):** All sync-related DataStore key constants live in `sync/SyncConstants.kt`. Single source of truth — no duplication across 3 companion objects.
- **D19 (WorkManager policy):** `ExistingPeriodicWorkPolicy.UPDATE` is the correct modern policy; `KEEP` is deprecated since WorkManager 2.8. `UPDATE` keeps existing work but updates constraints/interval if they differ.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

- **Known flaky test:** `BookmarkDaoTest.observeAllActive_excludesOtherUsers` failed with `UncaughtExceptionsBeforeTest` (Robolectric/Room Invalidation Tracker race condition). This is the pre-existing flaky test documented in STATE.md since Phase 05. All 225 other tests pass. The specific sync-related tests (`SyncWorkerTest`, `DocumentListSyncTest`) were run in isolation and both passed cleanly.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness
- GAP-A is now closed — settings changes from SettingsScreen will visually apply in MainActivity
- GAP-B (backend gap) is the remaining audit gap; phase 08 addresses it
- All Android tests green (225 tests, 1 known pre-existing flaky excluded)

---
*Phase: 07-settings-display-and-techdebt*
*Completed: 2026-03-03*
