---
phase: 08-settings-sync-push
plan: 01
subsystem: sync
tags: [android, kotlin, room, settings, sync, hlc, push, gap-closure]

# Dependency graph
requires:
  - phase: 07-settings-display-and-techdebt
    provides: GAP-A closed; SyncConstants singleton; settings display working in UI
  - phase: 05-hlc-format-fix
    provides: HLC decimal format; getUserId() convention for DAO queries
provides:
  - SettingsSyncRecord with id field (= userId) for backend SQLite upsert PK binding
  - SettingsDao.getPendingSettings(userId, sinceHlc, deviceId) checking all 4 HLC fields
  - SettingsEntity.toSettingsSyncRecord() mapper with id = userId
  - Settings wired into all 3 push sites (SyncWorker, DocumentListViewModel, NodeEditorViewModel)
  - 9 new tests covering pending/no-pending settings in all push paths
affects:
  - backend sync validation (must accept id field in settings push body)
  - future settings-related phases

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "getPendingSettings: filter by device_id so only locally-changed settings are pushed (avoids echo-back)"
    - "id = userId for settings rows: backend upsert uses id as PK, and in this app id == userId for settings"
    - "pendingSettings?.toSettingsSyncRecord(): null-safe mapper produces null payload field when no pending changes"

key-files:
  created: []
  modified:
    - android/app/src/main/java/com/gmaingret/outlinergod/network/model/Sync.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/db/dao/SettingsDao.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncMappers.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncWorker.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListViewModel.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModel.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/db/dao/SettingsDaoTest.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/sync/SyncWorkerTest.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListSyncTest.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorSyncTest.kt

key-decisions:
  - "D20: SettingsSyncRecord.id defaults to empty string so pull-direction deserialization is backward compatible; push-direction always sets id = userId via mapper"
  - "D21: getPendingSettings filters by device_id to avoid pushing settings received via pull (prevents echo-back loop)"
  - "D22: GAP-B closed - settings now flow bidirectionally; settings push was previously always null despite field existing in SyncPushPayload"

patterns-established:
  - "All push sites (SyncWorker, DocumentListVM, NodeEditorVM) follow identical pattern: query pending, build payload, include null-safe mapper result"

# Metrics
duration: 5min
completed: 2026-03-03
---

# Phase 08 Plan 01: Settings Sync Push Summary

**GAP-B closed: Android settings changes (theme, density, guide lines, backlink badge) now included in sync push payload with id=userId so backend SQLite upsert succeeds**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-03T19:50:34Z
- **Completed:** 2026-03-03T19:55:56Z
- **Tasks:** 3
- **Files modified:** 10

## Accomplishments
- Added `id` field to `SettingsSyncRecord` (backend requires `@id` parameter for upsert PK binding)
- New `SettingsDao.getPendingSettings()` query filters by all 4 HLC fields and device_id (only local changes pushed)
- New `SettingsEntity.toSettingsSyncRecord()` mapper sets `id = userId` for correct backend PK
- All 3 push sites (SyncWorker, DocumentListViewModel, NodeEditorViewModel) query and include pending settings
- 9 new tests verify: DAO filtering logic (3), SyncWorker push with/without settings (2), DocumentListVM push with/without settings (2), NodeEditorVM push with/without settings (2)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add id field, getPendingSettings query, toSettingsSyncRecord mapper** - `c825de1` (feat)
2. **Task 2: Wire settings into all 3 push sites** - `adba4c7` (feat)
3. **Task 3: Write 9 tests across 4 test files** - `5c7bba0` (test)

## Files Created/Modified
- `android/app/src/main/java/com/gmaingret/outlinergod/network/model/Sync.kt` - Added `@SerialName("id") val id: String = ""` as first field in SettingsSyncRecord
- `android/app/src/main/java/com/gmaingret/outlinergod/db/dao/SettingsDao.kt` - Added `getPendingSettings(userId, sinceHlc, deviceId): SettingsEntity?` checking all 4 HLC fields
- `android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncMappers.kt` - Added `SettingsEntity.toSettingsSyncRecord()` with id = userId
- `android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncWorker.kt` - Added pendingSettings query and settings field in SyncPushPayload
- `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListViewModel.kt` - Added pendingSettings query, settings in payload, toSettingsSyncRecord import
- `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModel.kt` - Added pendingSettings query, settings in payload, toSettingsSyncRecord import
- `android/app/src/test/java/com/gmaingret/outlinergod/db/dao/SettingsDaoTest.kt` - 3 new test cases + updated makeSettings helper with HLC/deviceId params
- `android/app/src/test/java/com/gmaingret/outlinergod/sync/SyncWorkerTest.kt` - 2 new test cases for settings push behavior
- `android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListSyncTest.kt` - 2 new test cases for settings in push payload
- `android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorSyncTest.kt` - 2 new test cases for settings in push payload

## Decisions Made
- D20: `SettingsSyncRecord.id` defaults to `""` so pull-direction deserialization remains backward-compatible; push-direction always sets `id = userId` via the mapper
- D21: `getPendingSettings` filters by `device_id` to prevent echo-back (settings received via pull should not be immediately re-pushed on the next sync)
- D22: GAP-B closed — settings now flow bidirectionally; the `SyncPushPayload.settings` field was previously always `null` despite the field existing

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- Pre-existing flaky test `BookmarkDaoTest.observeAllActive_excludesOtherUsers` failed on first run (known Robolectric/Room race condition). Passed on retry as expected.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- GAP-B is now closed. Both GAP-A (phase 07) and GAP-B (this phase) are resolved.
- All audit gaps from the v0.4 milestone audit are closed.
- Phase 08 is complete with all must-haves verified.
- 234 tests total (225 pre-existing + 9 new), all passing.

---
*Phase: 08-settings-sync-push*
*Completed: 2026-03-03*
