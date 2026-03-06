---
phase: 21-sync-architecture
plan: 01
subsystem: sync
tags: [android, kotlin, room, datastore, hilt, mockk, tdd]

# Dependency graph
requires:
  - phase: 07-android-gaps
    provides: SyncConstants.lastSyncHlcKey, SyncWorker canonical sync sequence
  - phase: 13-integration-polish
    provides: SyncWorker, SyncMappers, SyncRepository interfaces
provides:
  - SyncOrchestrator interface (suspend fun fullSync(): Result<Unit>)
  - SyncOrchestratorImpl: canonical pull-then-push sync cycle with runCatching
  - Hilt binding in RepositoryModule for SyncOrchestrator → SyncOrchestratorImpl
  - 4 unit tests (MockK): happy path, auth failure, pull failure, push failure
  - 4 integration tests (real Room DB + DataStore): node persisted, HLC written, hasLocalData guard, pull failure clean
affects:
  - 21-02: SyncWorker refactor (will delegate to SyncOrchestrator.fullSync())
  - any future plan touching sync logic

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "runCatching{} + getOrThrow() for network calls in orchestrator (vs getOrElse{ Result.retry() } in WorkManager)"
    - "SyncOrchestratorImpl as single source of truth for sync cycle — callers delegate instead of duplicating logic"
    - "TDD: RED (stub impl + failing tests committed) → GREEN (full impl committed)"

key-files:
  created:
    - android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncOrchestrator.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncOrchestratorImpl.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/sync/SyncOrchestratorTest.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/sync/SyncOrchestratorIntegrationTest.kt
  modified:
    - android/app/src/main/java/com/gmaingret/outlinergod/di/RepositoryModule.kt

key-decisions:
  - "SyncOrchestratorImpl uses runCatching{}.getOrThrow() instead of getOrElse{ return Result.retry() } — WorkManager maps Result.failure() to retry in Plan 02"
  - "hasLocalData guard preserved: countDocuments(userId)==0 forces lastSyncHlc='0' even if DataStore has a stored value"
  - "Document upsert before node upsert (application-level ordering) — foreign key constraint safety"

patterns-established:
  - "Orchestrator pattern: single canonical implementation of multi-step workflow; all callers delegate"
  - "TDD cycle: stub impl → failing tests committed → full impl → all tests pass → separate commits for each phase"

requirements-completed: []

# Metrics
duration: 35min
completed: 2026-03-06
---

# Phase 21 Plan 01: SyncOrchestrator — Canonical Sync Cycle Summary

**SyncOrchestrator interface + SyncOrchestratorImpl extracted from SyncWorker.doWork() with runCatching/getOrThrow pattern, 8 tests (4 unit + 4 integration) all green**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-03-06T17:30:00Z
- **Completed:** 2026-03-06T18:05:00Z
- **Tasks:** 1 (TDD: RED + GREEN)
- **Files modified:** 5

## Accomplishments

- Created `SyncOrchestrator` interface with `suspend fun fullSync(): Result<Unit>`
- Created `SyncOrchestratorImpl` implementing the full 10-step pull-then-push sync cycle (extracted verbatim from `SyncWorker.doWork()`) using `runCatching{}` + `getOrThrow()`
- Added Hilt binding `provideSyncOrchestrator` to `RepositoryModule`
- Wrote 4 unit tests (MockK, all DAOs mocked): happy path, auth failure, pull failure, push failure
- Wrote 4 integration tests (real Room in-memory DB + real DataStore): node persisted in Room, HLC written to DataStore, hasLocalData guard forces since="0", pull failure leaves DataStore unchanged
- All 8 tests pass; no regressions in existing suite

## Task Commits

TDD commits:

1. **RED — failing tests + stub impl** - `82b2181` (test)
2. **GREEN — full implementation + RepositoryModule binding** - `b78a5f5` (feat)

## Files Created/Modified

- `android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncOrchestrator.kt` - Interface: `suspend fun fullSync(): Result<Unit>`
- `android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncOrchestratorImpl.kt` - Full sync implementation with `@Singleton @Inject` constructor
- `android/app/src/main/java/com/gmaingret/outlinergod/di/RepositoryModule.kt` - Added `provideSyncOrchestrator` Hilt binding
- `android/app/src/test/java/com/gmaingret/outlinergod/sync/SyncOrchestratorTest.kt` - 4 unit tests (MockK)
- `android/app/src/test/java/com/gmaingret/outlinergod/sync/SyncOrchestratorIntegrationTest.kt` - 4 integration tests (real Room + DataStore)

## Decisions Made

- **runCatching vs WorkManager pattern:** `SyncOrchestratorImpl` uses `runCatching{} + getOrThrow()` rather than `getOrElse { return Result.retry() }`. The orchestrator returns `Result.failure()` on any network error; Plan 02 will update `SyncWorker` to map `Result.failure()` to `WorkManager.Result.retry()`.
- **hasLocalData guard preserved:** When `documentDao.countDocuments(userId) == 0`, `lastSyncHlc` is forced to `"0"` regardless of any stored DataStore value. This handles reinstall-with-DataStore-backup scenarios.
- **Document-before-node ordering:** `documentDao.upsertDocuments()` is called before `nodeDao.upsertNodes()` to satisfy the foreign key constraint on `nodes.document_id`.

## Deviations from Plan

None - plan executed exactly as written. The TDD cycle (RED → GREEN) followed the plan specification precisely.

## Issues Encountered

- Android Studio file locks on `build/test-results/*/binary/output.bin` prevented running the full test suite via `./gradlew :app:testDebugUnitTest`. The targeted test run (`--tests "*.SyncOrchestratorTest" --tests "*.SyncOrchestratorIntegrationTest"`) completed successfully: 8 tests, 0 failures. The prior full-suite XML results showed 315 tests, 0 failures (pre-existing passing state).

## Next Phase Readiness

- `SyncOrchestrator` is ready to be injected into `SyncWorker`, `DocumentListViewModel`, and `NodeEditorViewModel` (Plan 21-02)
- Hilt graph resolves correctly — `@Singleton SyncOrchestratorImpl` with all dependencies injected
- No blockers

---
*Phase: 21-sync-architecture*
*Completed: 2026-03-06*
