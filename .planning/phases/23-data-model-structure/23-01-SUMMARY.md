---
phase: 23-data-model-structure
plan: 01
subsystem: database
tags: [room, sqlite, migration, android, kotlin, attachment]

# Dependency graph
requires:
  - phase: 11-search-export-bookmarks
    provides: FTS4 migration pattern (MIGRATION_1_2) used as template

provides:
  - NodeEntity with attachmentUrl and attachmentMime nullable String fields
  - AppDatabase version 3 with MIGRATION_2_3 that parses old ATTACH| rows
  - Three migration tests covering v2→v3 schema transition
  - Type-safe attachment access in NodeEditorScreen (attachmentUrl != null check)
affects:
  - 23-02 (NodeEditorViewModel uses attachmentUrl/attachmentMime)
  - 23-03 (sync serialization needs attachment fields)
  - 23-04 (schema export validation)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ALTER TABLE migration with data transformation via SQLite SUBSTR/INSTR"
    - "Nullable attachment columns (TEXT DEFAULT NULL) added to NodeEntity via Room migration"

key-files:
  created: []
  modified:
    - android/app/src/main/java/com/gmaingret/outlinergod/db/entity/NodeEntity.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/db/AppDatabase.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorScreen.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/db/AppDatabaseMigrationTest.kt

key-decisions:
  - "D23-01-A: exportSchema=false kept (exportSchema=true causes AbstractMethodError with Room 2.8.4 + kotlinx-serialization 1.7.3 version mismatch in KSP; schema export deferred to Plan 04)"
  - "D23-01-B: attachment_url and attachment_mime added at end of NodeEntity after updated_at (Room requires nullable columns with defaults at end for migrations)"
  - "D23-01-C: MIGRATION_2_3 uses SUBSTR/INSTR SQLite functions to parse ATTACH|mime|filename|url — no custom SQLite extension required"
  - "D23-01-D: filename not stored as separate column — derivable from attachmentUrl.substringAfterLast('/') for display"

patterns-established:
  - "Attachment columns: nullable TEXT with @ColumnInfo annotations; isAttachment check via attachmentUrl != null (not string parsing)"
  - "Migration data transformation: SQLite SUBSTR/INSTR for pipe-delimited content → typed columns"

requirements-completed: [SC-1]

# Metrics
duration: 90min
completed: 2026-03-07
---

# Phase 23 Plan 01: Data Model — Attachment Columns Summary

**NodeEntity v3 adds attachment_url and attachment_mime columns with MIGRATION_2_3 that parses existing ATTACH|mime|filename|url content rows into typed fields**

## Performance

- **Duration:** ~90 min (including build environment troubleshooting)
- **Started:** 2026-03-07T17:30:00Z
- **Completed:** 2026-03-07T19:00:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- Added `attachmentUrl` and `attachmentMime` nullable String fields to `NodeEntity` data class with `@ColumnInfo` annotations
- Bumped AppDatabase from version 2 to 3; added `MIGRATION_2_3` that ALTERs nodes table and runs a SUBSTR/INSTR UPDATE to migrate existing ATTACH| rows
- Updated `NodeEditorScreen` to detect attachments via `flatNode.entity.attachmentUrl != null` (no more regex parsing)
- Updated `NodeEditorViewModel.uploadAttachment()` to write `content = ""` and set the two new typed columns
- Added 3 new migration tests: `databaseVersion_is3`, `migration_2_3_addsAttachmentColumns`, `migration_2_3_parsesAttachContent`

## Task Commits

1. **Task 1: NodeEntity columns + MIGRATION_2_3 + migration tests** - `c65c45d` (feat)
2. **Task 2: NodeEditorScreen + AppDatabaseMigrationTest SQL fix** - `9b540ae` (fix, part of 23-02 commit)

## Files Created/Modified

- `android/app/src/main/java/com/gmaingret/outlinergod/db/entity/NodeEntity.kt` - Added `attachmentUrl` and `attachmentMime` nullable String fields at end of data class
- `android/app/src/main/java/com/gmaingret/outlinergod/db/AppDatabase.kt` - Version 2→3; added `MIGRATION_2_3` companion val; added to both `build()` and `buildInMemory()` migration lists
- `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorScreen.kt` - Removed `ATTACH_REGEX`; changed `isAttachment` to `attachmentUrl != null`; rewrote attachment display block to read `attachmentUrl`/`attachmentMime` directly
- `android/app/src/test/java/com/gmaingret/outlinergod/db/AppDatabaseMigrationTest.kt` - Updated `databaseVersion_is2` → `databaseVersion_is3`; added 2 new migration tests; updated `makeNode()` helper to accept attachment parameters

## Decisions Made

- `exportSchema = false` kept — `exportSchema = true` triggers `AbstractMethodError` in Room 2.8.4 KSP when it attempts to serialize `FieldBundle` using `kotlinx-serialization 1.7.3` which lacks the `typeParametersSerializers()` method added in 1.8.x. Schema export handled separately in Plan 04.
- `filename` not stored as a separate column — the display name is derivable from `attachmentUrl.substringAfterLast("/")`, keeping the schema minimal.
- Attachment columns placed at the end of NodeEntity after `updatedAt` so that `ALTER TABLE ... ADD COLUMN` migrations work correctly (SQLite only allows adding columns at the end).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] AppDatabaseMigrationTest SQL missing parent_id_hlc column**
- **Found during:** Task 1 verification run
- **Issue:** Raw INSERT SQL in `migration_2_3_parsesAttachContent` test omitted the `parent_id_hlc` column which has no DEFAULT — SQLite rejected the INSERT with "NOT NULL constraint failed"
- **Fix:** Added `parent_id_hlc` column with empty string value to the INSERT statement
- **Files modified:** `android/app/src/test/java/com/gmaingret/outlinergod/db/AppDatabaseMigrationTest.kt`
- **Verification:** All 7 AppDatabaseMigrationTest tests pass
- **Committed in:** `9b540ae` (23-02 fix commit)

**2. [Rule 3 - Blocking] KSP FileAlreadyExistsException on Windows**
- **Found during:** Task 1 (full suite run)
- **Issue:** KSP 2.1.0-1.0.29 with `ksp.incremental=false` on Windows creates `byRounds/` shadow output, then fails to copy it over existing top-level output on second and subsequent processing rounds — `java.nio.file.FileAlreadyExistsException` in `copyWithTimestamp()`
- **Fix:** Clear `build/generated/ksp/` and `build/kspCaches/` before each fresh build; added `org.gradle.caching=false` to `gradle.properties` to prevent Gradle from treating stale KSP output as cached-and-valid
- **Files modified:** `android/gradle.properties`
- **Verification:** Build succeeds after cache clear; `BUILD SUCCESSFUL` confirmed
- **Committed in:** `android/gradle.properties` change in this plan's commit

---

**Total deviations:** 2 auto-fixed (1 bug fix, 1 blocking environment issue)
**Impact on plan:** Both auto-fixes were necessary. No scope creep.

## Issues Encountered

- KSP incremental build state on Windows (FileAlreadyExistsException) caused repeated build failures — resolved by clearing KSP output directory and disabling Gradle build cache
- `exportSchema = true` combined with `room.schemaLocation` KSP arg caused `AbstractMethodError` in Room 2.8.4 KSP processor — revert to `exportSchema = false` fixed it

## Next Phase Readiness

- NodeEntity v3 is ready for sync serialization updates (Plan 23-03)
- All attachment display logic is now type-safe (no regex at render time)
- MIGRATION_2_3 handles existing installations with ATTACH| content rows

## Self-Check: PASSED

- `23-01-SUMMARY.md` — FOUND at `.planning/phases/23-data-model-structure/23-01-SUMMARY.md`
- `c65c45d` (Task 1 commit) — FOUND in git log
- `NodeEntity.kt` — FOUND; contains `attachment_url` column annotation
- `AppDatabase.kt` — FOUND; contains `MIGRATION_2_3` companion val
- `NodeEditorScreen.kt` — FOUND; contains `attachmentUrl != null` check
- `AppDatabaseMigrationTest.kt` — FOUND; contains `databaseVersion_is3` test

---
*Phase: 23-data-model-structure*
*Completed: 2026-03-07*
