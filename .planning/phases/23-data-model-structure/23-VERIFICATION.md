---
phase: 23-data-model-structure
verified: 2026-03-07T23:30:00Z
status: passed
score: 6/6 must-haves verified
re_verification:
  previous_status: gaps_found
  previous_score: 13/16
  gaps_closed:
    - "android/app/schemas/com.gmaingret.outlinergod.db.AppDatabase/3.json exists and is committed to git HEAD"
    - "AppDatabase.buildInMemory() tests unblocked — destructive doFirst hook removed so KSP stays UP-TO-DATE"
    - "build.gradle.kts no longer contains deleteRecursively — schema file is safe across all future KSP runs"
  gaps_remaining: []
  regressions: []
---

# Phase 23: Data Model Structure Verification Report

**Phase Goal:** Attachment metadata lives in proper DB columns, FractionalIndex is in a production package, the orphan backend HLC file is deleted, Room schema export is enabled, and the undo stack uses a single cohesive type.
**Verified:** 2026-03-07T23:30:00Z
**Status:** passed
**Re-verification:** Yes — after gap closure (Plan 05)

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | NodeEntity has `attachment_url` and `attachment_mime` nullable String fields | VERIFIED | `NodeEntity.kt` lines 31-32: `@ColumnInfo(name = "attachment_url") val attachmentUrl: String? = null` and `@ColumnInfo(name = "attachment_mime") val attachmentMime: String? = null` |
| 2 | MIGRATION_2_3 converts ATTACH\|mime\|filename\|url rows to typed columns | VERIFIED | `AppDatabase.kt` lines 96-110: MIGRATION_2_3 with ALTER TABLE + SUBSTR/INSTR UPDATE SQL |
| 3 | NodeEditorViewModel.uploadAttachment() writes attachmentUrl and attachmentMime | VERIFIED | `NodeEditorViewModel.kt` lines 531-532: `attachmentUrl = uploaded.url`, `attachmentMime = uploaded.mimeType`, `content = ""` |
| 4 | NodeEditorScreen detects attachments via `flatNode.entity.attachmentUrl != null` | VERIFIED | `NodeEditorScreen.kt` line 446: `val isAttachment = flatNode.entity.attachmentUrl != null`; ATTACH_REGEX removed |
| 5 | AppDatabaseMigrationTest has databaseVersion_is3, migration_2_3_addsAttachmentColumns, and migration_2_3_parsesAttachContent tests | VERIFIED | All three tests present in `AppDatabaseMigrationTest.kt` at lines 70, 76, 92 |
| 6 | FractionalIndex.kt lives at `util/FractionalIndex.kt` with package `com.gmaingret.outlinergod.util` | VERIFIED | File exists at util/; package declaration confirmed |
| 7 | prototype/FractionalIndex.kt is deleted | VERIFIED | File absent from filesystem |
| 8 | All production import sites import FractionalIndex from util package | VERIFIED | `NodeEditorViewModel.kt:14` and `DocumentListScreen.kt:62` both import from `com.gmaingret.outlinergod.util.FractionalIndex`; `DndPrototypeViewModel.kt:3` also updated |
| 9 | `backend/src/hlc.ts` (orphan hex-format file) is deleted | VERIFIED | File absent from filesystem |
| 10 | `backend/src/hlc/hlc.ts` still exists | VERIFIED | File present |
| 11 | `data class UndoSnapshot(val nodes: List<FlatNode>, val deletedNodeIds: List<String> = emptyList())` defined at top of NodeEditorViewModel.kt | VERIFIED | Lines 34-37 confirmed |
| 12 | `undoStack` is `ArrayDeque<UndoSnapshot>` | VERIFIED | Line 56: `private val undoStack = ArrayDeque<UndoSnapshot>()` |
| 13 | `undoDeletedIds` ArrayDeque does not exist | VERIFIED | grep returns NOT FOUND |
| 14 | `pushUndoSnapshot()` pushes `UndoSnapshot(flatNodes)` | VERIFIED | Line 61: `undoStack.addLast(UndoSnapshot(current))` |
| 15 | `deleteNode()` pushes `UndoSnapshot(flatNodes, idsToDelete)` | VERIFIED | Line 774: `undoStack.addLast(UndoSnapshot(flatNodes, idsToDelete))` |
| 16 | `undo()` pops a single UndoSnapshot and uses `snap.deletedNodeIds` | VERIFIED | Lines 476-477: `val snap = undoStack.removeLast()` / `val deletedIds = snap.deletedNodeIds` |
| 17 | AppDatabase `exportSchema = true` | VERIFIED | `AppDatabase.kt` line 26: `exportSchema = true` |
| 18 | `android/app/build.gradle.kts` has `ksp { arg("room.schemaLocation", ...) }` block | VERIFIED | Lines 157-159: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` |
| 19 | `build.gradle.kts` has no destructive `doFirst` / `deleteRecursively` hook | VERIFIED | grep returns NOT FOUND — hook removed in commit 2ccbcc3 |
| 20 | `android/app/schemas/com.gmaingret.outlinergod.db.AppDatabase/3.json` exists on filesystem | VERIFIED | File present; contains `attachment_url` and `attachment_mime` fields; `"version": 3`; `"identityHash": "8e578df2b7b9d8eb578d8af4a5551e16"` |
| 21 | `3.json` is tracked by git at HEAD | VERIFIED | `git ls-files android/app/schemas/` returns exactly one line: `android/app/schemas/com.gmaingret.outlinergod.db.AppDatabase/3.json` |
| 22 | `3.json` content includes `attachment_url` and `attachment_mime` columns | VERIFIED | Schema JSON `createSql` for `nodes` table ends with `` `attachment_url` TEXT, `attachment_mime` TEXT ``; both fields appear in the `fields` array |

**Score: 22/22 truths verified**

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `android/app/src/main/java/com/gmaingret/outlinergod/db/entity/NodeEntity.kt` | NodeEntity with attachment_url and attachment_mime fields | VERIFIED | Both fields present with @ColumnInfo annotations |
| `android/app/src/main/java/com/gmaingret/outlinergod/db/AppDatabase.kt` | Room DB version 3 with MIGRATION_2_3 and exportSchema=true | VERIFIED | version=3, exportSchema=true, MIGRATION_2_3 present |
| `android/app/src/test/java/com/gmaingret/outlinergod/db/AppDatabaseMigrationTest.kt` | Migration tests including databaseVersion_is3 | VERIFIED | All three new tests present and substantive |
| `android/app/src/main/java/com/gmaingret/outlinergod/util/FractionalIndex.kt` | FractionalIndex object in util package | VERIFIED | Exists with correct package declaration |
| `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModel.kt` | UndoSnapshot data class, unified undoStack | VERIFIED | UndoSnapshot defined at top; undoStack typed correctly; undoDeletedIds absent |
| `android/app/schemas/com.gmaingret.outlinergod.db.AppDatabase/3.json` | Room-generated schema snapshot for version 3 | VERIFIED | Present on filesystem and tracked in git HEAD; contains correct attachment column definitions |
| `android/app/build.gradle.kts` | KSP schemaLocation arg; no destructive doFirst hook | VERIFIED | `ksp { arg("room.schemaLocation", ...) }` present; `deleteRecursively` absent |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| NodeEditorScreen.kt | NodeEntity.attachmentUrl | `flatNode.entity.attachmentUrl != null` | WIRED | Line 446 confirmed |
| NodeEditorViewModel.uploadAttachment() | NodeEntity | `attachmentUrl = uploaded.url`, `attachmentMime = uploaded.mimeType` | WIRED | Lines 531-532 confirmed |
| NodeEditorViewModel.kt | util/FractionalIndex.kt | `import com.gmaingret.outlinergod.util.FractionalIndex` | WIRED | Line 14 confirmed |
| DocumentListScreen.kt | util/FractionalIndex.kt | `import com.gmaingret.outlinergod.util.FractionalIndex` | WIRED | Line 62 confirmed |
| build.gradle.kts ksp block | android/app/schemas/ | `arg("room.schemaLocation", "$projectDir/schemas")` | WIRED | KSP arg present; no deleteRecursively hook; 3.json committed and UP-TO-DATE on second kspDebugKotlin run |
| pushUndoSnapshot() | undoStack | `undoStack.addLast(UndoSnapshot(flatNodes))` | WIRED | Line 61 confirmed |
| undo() | snap.deletedNodeIds | `val snap = undoStack.removeLast()` | WIRED | Lines 476-477 confirmed |

### Requirements Coverage (Success Criteria)

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| SC-1 | 23-01 | Attachment fields as typed DB columns; no ATTACH\| prefix in content | SATISFIED | NodeEntity has attachmentUrl/attachmentMime; MIGRATION_2_3 parses old rows; NodeEditorScreen uses `attachmentUrl != null` guard |
| SC-2 | 23-02 | FractionalIndex in util/ package; no production import from prototype.* | SATISFIED | util/FractionalIndex.kt with correct package; all 3 import sites updated |
| SC-3 | 23-02 | backend/src/hlc.ts deleted; backend/src/hlc/hlc.ts intact | SATISFIED | Orphan file deleted; canonical file present |
| SC-4 | 23-04 / 23-05 | exportSchema = true; 3.json committed under android/app/schemas/ | SATISFIED | exportSchema=true; ksp schemaLocation arg; 3.json present on filesystem and in git HEAD; identityHash matches; attachment columns in schema |
| SC-5 | 23-03 | UndoSnapshot data class; undoStack typed as ArrayDeque<UndoSnapshot>; undoDeletedIds removed | SATISFIED | All three conditions verified directly in NodeEditorViewModel.kt |
| SC-6 | 23-01 / 23-03 | All existing tests pass; Room migration test covers attachment columns | SATISFIED | AppDatabaseMigrationTest has databaseVersion_is3, migration_2_3_addsAttachmentColumns, migration_2_3_parsesAttachContent; NodeEditorViewModelTest 28/28 green per Plan 03 summary |

### Anti-Patterns Found

None. The blocker from the previous verification (destructive `doFirst { schemasDir.deleteRecursively() }` hook) was removed in commit 2ccbcc3.

### Human Verification Required

None — all required items were verifiable programmatically.

### Gap Closure Summary

Three gaps from the initial verification were all rooted in a single incident: commit 6b9398f (Plan 03) triggered a Gradle task that fired the `doFirst` hook added by Plan 03 itself, deleting schemas/ immediately before committing. Plan 05 resolved this with two atomic commits:

- **2ccbcc3** — Removed the `tasks.matching { it.name.startsWith("ksp") }.configureEach { doFirst { schemasDir.deleteRecursively() } }` block from build.gradle.kts entirely.
- **5ba4ebe** — Manually deleted the stale 3.json, ran `kspDebugKotlin` to regenerate it fresh, confirmed UP-TO-DATE on second run, and committed the result.

All six success criteria from the ROADMAP.md phase definition are now fully satisfied. Phase 24 MigrationTestHelper can locate `schemas/3.json` at its expected path.

---

_Verified: 2026-03-07T23:30:00Z_
_Verifier: Claude (gsd-verifier)_
