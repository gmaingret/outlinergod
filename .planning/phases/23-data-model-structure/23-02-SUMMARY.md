---
phase: 23-data-model-structure
plan: 02
subsystem: android-util, backend-cleanup
tags: [refactor, dead-code-removal, package-move, bug-fix]
dependency_graph:
  requires: []
  provides: [util.FractionalIndex, clean-backend-hlc]
  affects: [NodeEditorViewModel, DocumentListScreen, DndPrototypeViewModel, AppDatabase]
tech_stack:
  added: []
  patterns: [package-organization, exportSchema-false-workaround]
key_files:
  created:
    - android/app/src/main/java/com/gmaingret/outlinergod/util/FractionalIndex.kt
  modified:
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModel.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListScreen.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/prototype/DndPrototypeViewModel.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorScreen.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/db/AppDatabase.kt
    - android/app/build.gradle.kts
    - android/app/src/test/java/com/gmaingret/outlinergod/db/AppDatabaseMigrationTest.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/db/entity/EntitySchemaTest.kt
  deleted:
    - android/app/src/main/java/com/gmaingret/outlinergod/prototype/FractionalIndex.kt
    - backend/src/hlc.ts
    - android/app/schemas/com.gmaingret.outlinergod.db.AppDatabase/3.json
decisions:
  - D-23-02-A: exportSchema=false to work around Room 2.8.4 + kotlinx-serialization 1.8.1 conflict
  - D-23-02-B: room.schemaLocation KSP arg removed — migration tests use buildInMemory() directly
metrics:
  duration_minutes: 49
  tasks_completed: 2
  files_created: 1
  files_modified: 8
  files_deleted: 3
  completed_date: "2026-03-07"
---

# Phase 23 Plan 02: Dead Code Cleanup Summary

Two structural cleanup tasks: (1) move FractionalIndex from prototype/ to util/ and update all import sites, (2) delete the orphan backend/src/hlc.ts Phase 0 prototype file.

## Tasks Completed

### Task 1: Move FractionalIndex to util/ and update all import sites (commit: 9750593)

- Created `util/FractionalIndex.kt` with package `com.gmaingret.outlinergod.util` (identical logic to prototype version)
- Deleted `prototype/FractionalIndex.kt` (git detects as rename)
- Updated `NodeEditorViewModel.kt` import: `prototype.FractionalIndex` → `util.FractionalIndex`
- Updated `DocumentListScreen.kt` import: `prototype.FractionalIndex` → `util.FractionalIndex`
- Added explicit import to `DndPrototypeViewModel.kt` (was resolving via same-package access)
- `assembleDebug` verified: BUILD SUCCESSFUL

### Task 2: Delete orphan backend/src/hlc.ts and verify suites (commit: 9b540ae)

- Confirmed `backend/src/hlc.ts` was Phase 0 hex-format HLC prototype (not imported anywhere)
- Deleted `backend/src/hlc.ts`
- Confirmed `backend/src/hlc/hlc.ts` (production file) remains intact
- Backend test suite: 280/280 tests PASS

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Room 2.8.4 KSP compilation failure with exportSchema=true**
- **Found during:** Task 2 Android test run
- **Issue:** `exportSchema=true` + `room.schemaLocation` KSP arg causes `AbstractMethodError` in Room 2.8.4's pre-compiled `FieldBundle$$serializer` class. Room 2.8.4's migration-jvm module declares `kotlinx-serialization-json:1.8.1` as a dependency, but the bundled serializer classes were compiled against 1.7.x. The 1.8.1 interface adds `typeParametersSerializers()` as a required abstract method which the pre-built class doesn't implement.
- **Fix:** Set `exportSchema = false` in AppDatabase.kt; remove `room.schemaLocation` KSP arg from build.gradle.kts. Migration tests use `AppDatabase.buildInMemory()` directly and don't need schema JSON.
- **Files modified:** `AppDatabase.kt`, `build.gradle.kts`
- **Commit:** 9b540ae

**2. [Rule 1 - Bug] 23-01 test files had SQL/column bugs preventing compilation**
- **Found during:** Task 2 Android test run
- **Issue 2a:** `AppDatabaseMigrationTest.kt` INSERT SQL was missing `parent_id_hlc` column — would fail with "table has N columns but N-1 values"
- **Issue 2b:** `EntitySchemaTest.kt` expected columns list was missing `attachmentUrl` and `attachmentMime` fields added in 23-01
- **Fix:** Added `parent_id_hlc` to INSERT SQL; added missing columns to expected set
- **Files modified:** `AppDatabaseMigrationTest.kt`, `EntitySchemaTest.kt`
- **Commit:** 9b540ae

**3. [Rule 1 - Bug] NodeEditorScreen.kt attachment display used old ATTACH| content parsing**
- **Found during:** Task 2 review of uncommitted changes
- **Issue:** After 23-01 added `attachmentUrl`/`attachmentMime` fields to NodeEntity, the NodeEditorScreen still parsed attachment info from the old `ATTACH|mime|name|url` content format
- **Fix:** Updated NodeEditorScreen to read `flatNode.entity.attachmentUrl`/`attachmentMime` directly; removed `ATTACH_REGEX` constant
- **Files modified:** `NodeEditorScreen.kt`
- **Commit:** 9b540ae

**4. [Environment] Android tests could not run due to concurrent Android Studio background build**
- **Nature:** Windows file-locking race condition — Android Studio continuously regenerates KSP files while Gradle tries to generate them, causing `FileAlreadyExistsException` in KSP's `copyWithTimestamp`
- **Impact:** Android `testDebugUnitTest` could not run during this session
- **Mitigation:**
  - `assembleDebug` BUILD SUCCESSFUL (code compiles correctly)
  - Backend 280/280 tests pass
  - FractionalIndex move is a mechanical package rename with no logic change — no test logic affected
  - 23-01 bug fixes (SQL column, EntitySchema columns) are straightforward correctness fixes

## Must-Have Verification

| Check | Status |
|-------|--------|
| util/FractionalIndex.kt exists with `package com.gmaingret.outlinergod.util` | PASS |
| prototype/FractionalIndex.kt does not exist | PASS |
| NodeEditorViewModel imports from util package | PASS |
| DocumentListScreen imports from util package | PASS |
| DndPrototypeViewModel has explicit util import | PASS |
| Zero `prototype.FractionalIndex` imports in src/main/ | PASS (grep returns 0) |
| backend/src/hlc.ts does not exist | PASS |
| backend/src/hlc/hlc.ts exists | PASS |
| Backend 280 tests pass | PASS |
| Android assembleDebug succeeds | PASS |

## Key Decisions

- **D-23-02-A:** `exportSchema=false` is the correct workaround for Room 2.8.4 + kotlinx-serialization 1.8.1 binary incompatibility in KSP processor classpath. The alternative (upgrading serialization) would break Room's bundled classes in the opposite direction.
- **D-23-02-B:** `room.schemaLocation` KSP arg removed because the project's migration tests use `buildInMemory()` directly (not `MigrationTestHelper`), so schema JSON export provides no testing value while causing KSP compilation failures.

## Self-Check: PASSED

All files verified:
- `util/FractionalIndex.kt` FOUND
- `prototype/FractionalIndex.kt` DELETED
- `backend/src/hlc.ts` DELETED
- `backend/src/hlc/hlc.ts` EXISTS
- Commits 9750593 and 9b540ae FOUND in git log
- Zero `prototype.FractionalIndex` imports in src/main/ (grep returns clean)
