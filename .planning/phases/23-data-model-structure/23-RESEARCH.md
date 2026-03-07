# Phase 23: Data model & structure - Research

**Researched:** 2026-03-07
**Domain:** Android Room schema migration, Kotlin code reorganization, backend file cleanup
**Confidence:** HIGH

## Summary

Phase 23 is a pure tech-debt cleanup — five isolated changes with no new behaviour. All decisions are locked in CONTEXT.md with specific SQL, placement, and API shapes. The work spans three layers: Android data model (Room entity + migration), Android code structure (FractionalIndex relocation, UndoSnapshot unification), and backend cleanup (orphan file deletion).

The research verified the exact current state of every file that will change. The locked decisions in CONTEXT.md are precise enough that the planner can produce implementation tasks directly from them. The primary risk is the Room migration test: the existing `AppDatabaseMigrationTest.kt` uses `buildInMemory()` (Robolectric) which skips FTS gracefully, and the new MIGRATION_2_3 test must follow the same try/catch guard pattern. Schema export requires a `ksp { }` block in `build.gradle.kts` — verified that file uses Kotlin DSL (`.kts`), so Groovy-style `ksp { arg(...) }` syntax from CONTEXT.md must be translated to Kotlin DSL syntax.

**Primary recommendation:** Execute the five items as five sequential tasks (Migration, FractionalIndex move, UndoSnapshot, exportSchema, backend delete) in that order — migration first because it changes `NodeEntity` fields that subsequent tasks depend on.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Attachment columns — migration strategy**
- Parse-and-migrate: v2->v3 migration SQL splits the existing `ATTACH|mime|filename|url` content string into the new `attachment_url` and `attachment_mime` columns, then clears `content` to `''`
- DB version bumps to **3** (attachment columns = new schema)
- Migration SQL pattern:
  ```sql
  ALTER TABLE nodes ADD COLUMN attachment_url TEXT;
  ALTER TABLE nodes ADD COLUMN attachment_mime TEXT;
  UPDATE nodes SET
    attachment_mime = SUBSTR(content, 8, INSTR(SUBSTR(content, 8), '|') - 1),
    attachment_url  = SUBSTR(content, INSTR(content, '|', INSTR(content, '|', 8) + 1) + 1),
    content         = ''
  WHERE content LIKE 'ATTACH|%';
  ```
  (Exact SUBSTR indices are Claude's Discretion — planner may refine for the 4-part format `ATTACH|mime|filename|url`)

**Attachment columns — call site updates**
- `NodeEditorViewModel` (line 529): after upload success, write `attachmentUrl` + `attachmentMime` fields to the entity via `NodeDao.updateNode()` — same pattern as `persistContentChange`; `content` stays `""`
- `NodeEditorScreen` (line 447): replace `content.startsWith("ATTACH|")` branch with `flatNode.entity.attachmentUrl != null`
- `FlatNode.kt`: must expose `attachmentUrl` and `attachmentMime` from the underlying `NodeEntity` so the Screen can access them

**UndoSnapshot — type and placement**
- `data class` (not sealed class)
- Fields: `val nodes: List<FlatNode>`, `val deletedNodeIds: List<String> = emptyList()`
- Defined at the **top of NodeEditorViewModel.kt** — no separate file
- Replace both parallel ArrayDeques with one: `private val undoStack = ArrayDeque<UndoSnapshot>()`
- Push without deletes: `undoStack.addLast(UndoSnapshot(flatNodes))`
- Push with deletes (deleteNode): `undoStack.addLast(UndoSnapshot(flatNodes, idsToDelete))`
- Pop: `val snap = undoStack.removeLast()` → use `snap.nodes` and `snap.deletedNodeIds`

**FractionalIndex relocation**
- Move `prototype/FractionalIndex.kt` → `util/FractionalIndex.kt`
- Package declaration changes from `com.gmaingret.outlinergod.prototype` to `com.gmaingret.outlinergod.util`
- **Delete** `prototype/FractionalIndex.kt` — no stub, no re-export
- Update all import call sites (FlatNodeMapper — does NOT import it; NodeEditorViewModel, DocumentListScreen, DndPrototypeViewModel, tests)
- Rest of prototype/ package is left as-is

**exportSchema = true**
- Set `exportSchema = true` in `@Database` annotation in `AppDatabase.kt`
- Add ksp argument to `android/app/build.gradle.kts` ksp block for `room.schemaLocation`
- Schema JSON files committed under `android/app/schemas/com.gmaingret.outlinergod.db.AppDatabase/`
- `buildInMemory()` adds `MIGRATION_2_3` alongside existing `MIGRATION_1_2`

**Migration test**
- `AppDatabaseMigrationTest.kt` in `src/test/` (Robolectric, NOT instrumented androidTest — no MigrationTestHelper on JVM; pattern matches existing file)
- Covers `MIGRATION_2_3`: verifies `attachment_url` and `attachment_mime` columns exist
- Also verify existing `ATTACH|` row is correctly parsed on migration

**Backend orphan file**
- Delete `backend/src/hlc.ts` (old hex-format file from Phase 0)
- Only `backend/src/hlc/hlc.ts` remains — no imports to update

### Claude's Discretion
- Exact SUBSTR indices in the migration SQL (4-part format: `ATTACH|mime|filename|url`)
- Whether MIGRATION_2_3 is a named `val` on AppDatabase.Companion or defined inline in `addMigrations()`
- HLC/timestamp field assignments when writing attachment columns via DAO

### Deferred Ideas (OUT OF SCOPE)
- Deleting the rest of prototype/ (DndPrototypeActivity, WysiwygPrototypeActivity, prototype FlatNode, HlcClock, SyncRepository)
</user_constraints>

## Standard Stack

### Core (already in use — no new dependencies)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Room | 2.8.4 | Android SQLite ORM + migration framework | Project standard |
| KSP | Current (via plugin) | Room annotation processing + schema export | Project standard |
| JUnit 4 + Robolectric | 4.13 | Unit + quasi-integration tests on JVM | Project standard |

### No New Dependencies
Phase 23 adds no libraries. All changes use existing project infrastructure.

## Architecture Patterns

### Verified Current State of All Files to Change

**NodeEntity** (`db/entity/NodeEntity.kt`)
- Current version = 2, `exportSchema = false`
- No `attachmentUrl` or `attachmentMime` fields
- After phase: two new nullable String fields added, `@ColumnInfo(name = "attachment_url")` and `@ColumnInfo(name = "attachment_mime")`

**AppDatabase** (`db/AppDatabase.kt`)
- Currently: `version = 2`, `exportSchema = false`, one migration `MIGRATION_1_2` (adds FTS4)
- `buildInMemory()` already has try/catch-safe FTS4 pattern via `addMigrations(MIGRATION_1_2)` — no FTS_CALLBACK in test build
- After phase: `version = 3`, `exportSchema = true`, MIGRATION_2_3 added to both `build()` and `buildInMemory()`

**FractionalIndex** (`prototype/FractionalIndex.kt`)
- Current package: `com.gmaingret.outlinergod.prototype`
- Import sites confirmed:
  - `NodeEditorViewModel.kt` line 14: `import com.gmaingret.outlinergod.prototype.FractionalIndex`
  - `DocumentListScreen.kt` line 62: `import com.gmaingret.outlinergod.prototype.FractionalIndex`
  - `DndPrototypeViewModel.kt`: also uses it (stays in prototype — must update its import too since FractionalIndex moves)
  - `FlatNodeMapper.kt`: does NOT import FractionalIndex (confirmed by source inspection)
  - Test files: no `FractionalIndex` imports in test directories (confirmed by grep)
- After phase: `util/FractionalIndex.kt`, package = `com.gmaingret.outlinergod.util`

**Undo stack** (`NodeEditorViewModel.kt` lines 51-53 and 772-775)
- Current structure: three separate collections — `undoStack: ArrayDeque<List<FlatNode>>`, `redoStack: ArrayDeque<List<FlatNode>>`, `undoDeletedIds: ArrayDeque<List<String>>`
- `pushUndoSnapshot()` always pushes an aligned empty entry to `undoDeletedIds` (size alignment hack)
- `deleteNode()` has its own inline push at lines 772-775 that pushes real deletedIds
- `undo()` pops from both `undoStack` and `undoDeletedIds` (line 474-475)
- `redo()` uses `redoStack` only (no deleted IDs for redo — redo does not restore DB state)
- After phase: `data class UndoSnapshot(val nodes: List<FlatNode>, val deletedNodeIds: List<String> = emptyList())` at top of file; one `undoStack: ArrayDeque<UndoSnapshot>`; `redoStack` type stays `ArrayDeque<List<FlatNode>>` (redo stack only holds FlatNode lists — no deleted IDs needed for redo)

**NodeEditorScreen** (`ui/screen/nodeeditor/NodeEditorScreen.kt` line 447)
- Current: `val isAttachment = flatNode.entity.content.startsWith("ATTACH|")`
- FlatNode wraps NodeEntity directly: `data class FlatNode(val entity: NodeEntity, val depth: Int, val hasChildren: Boolean)`
- After phase: `val isAttachment = flatNode.entity.attachmentUrl != null`

**uploadAttachment** (`NodeEditorViewModel.kt` lines 502-545)
- Currently creates a new child NodeEntity with `content = "ATTACH|mime|filename|url"`
- After phase: creates a NodeEntity with `content = ""`, `attachmentUrl = uploaded.url`, `attachmentMime = uploaded.mimeType`; uses `nodeDao.insertNode(attachNode)` (unchanged — new entity, not update)

**backend/src/hlc.ts** (confirmed exists)
- Old hex-format HLC from Phase 0 prototype
- Not imported by anything in `backend/src/` — safe to delete
- `backend/src/hlc/hlc.ts` is the production decimal-format implementation that stays

**build.gradle.kts**
- Uses Kotlin DSL (`.kts` extension confirmed)
- No existing `ksp { }` block in the file (KSP settings are in `gradle.properties` via `ksp.incremental=false`)
- Schema export KSP arg must use Kotlin DSL syntax: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` — placed inside the `android { }` block or at top level alongside other ksp config

### Pattern: Room Migration (follow MIGRATION_1_2)
```kotlin
// Source: existing AppDatabase.kt companion object
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE nodes ADD COLUMN attachment_url TEXT")
        db.execSQL("ALTER TABLE nodes ADD COLUMN attachment_mime TEXT")
        db.execSQL("""
            UPDATE nodes SET
              attachment_mime = SUBSTR(content, 8, INSTR(SUBSTR(content, 8), '|') - 1),
              attachment_url  = SUBSTR(content, 8 + INSTR(SUBSTR(content, 8), '|') + INSTR(SUBSTR(content, 8 + INSTR(SUBSTR(content, 8), '|')), '|')),
              content         = ''
            WHERE content LIKE 'ATTACH|%'
        """)
    }
}
```

### Pattern: KSP schema export (Kotlin DSL, build.gradle.kts)
```kotlin
// Add inside android { } block or at top-level block
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

### Anti-Patterns to Avoid
- **Groovy DSL syntax in .kts file:** `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` in Groovy works differently — use Kotlin DSL `arg()` call directly
- **Instrumented test for migration:** CONTEXT.md mentioned `androidTest/` but existing migration tests all use Robolectric in `src/test/`. Use Robolectric pattern with try/catch guard, not `MigrationTestHelper` (which requires a device/emulator)
- **Forgetting DndPrototypeViewModel:** It imports FractionalIndex from prototype but is not a production call site. Since the file stays in the prototype package but imports a moved symbol, its import must be updated too
- **Redo stack with UndoSnapshot:** The `redoStack` only needs `List<FlatNode>` (no deleted IDs — redo does not trigger `restoreNodes`). Do not change `redoStack` type
- **FTS_CALLBACK in buildInMemory:** The in-memory builder deliberately excludes FTS_CALLBACK; MIGRATION_2_3 must be added to both `build()` and `buildInMemory()` without FTS changes

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SQL string parsing | Custom Kotlin parser for ATTACH| strings | SQLite SUBSTR + INSTR in migration SQL | One-time data migration; SQL is atomic |
| Schema versioning | Manual version tracking | Room's built-in `version` + `exportSchema` | Room validates schema integrity at runtime |

## Common Pitfalls

### Pitfall 1: SUBSTR index arithmetic for 4-part ATTACH| format
**What goes wrong:** The format is `ATTACH|mime|filename|url` (4 parts). Naive SUBSTR for the URL field misses the filename segment.
**Why it happens:** The UPDATE SQL must skip past `ATTACH|`, then past `mime|`, then past `filename|` to reach the URL. Three `|` separators to skip.
**How to avoid:** The planner should compute the exact SUBSTR call: position of first `|` after "ATTACH" = 7 (1-based: A=1, T=2, T=3, A=4, C=5, H=6, |=7). Mime starts at 8. Second `|` starts the filename. Third `|` starts the URL. Use nested INSTR calls.
**Warning signs:** Migration test inserts a known `ATTACH|image/png|photo.jpg|https://host/file.jpg` row and checks that `attachment_url = 'https://host/file.jpg'` after migration.

### Pitfall 2: Schema export directory not created
**What goes wrong:** Room KSP generates the schema JSON but Gradle doesn't create `android/app/schemas/` directory.
**Why it happens:** The `schemas/` directory must exist or be created by the build. Room KSP creates subdirectories automatically on build.
**How to avoid:** After adding the ksp arg, run `./gradlew assembleDebug` or `./gradlew kspDebugKotlin` once to generate `schemas/com.gmaingret.outlinergod.db.AppDatabase/3.json`. Commit this file.
**Warning signs:** `exportSchema = true` without a committed JSON file will cause CI failures if `room.schemaLocation` path doesn't exist.

### Pitfall 3: AppDatabaseMigrationTest using buildInMemory() — version check
**What goes wrong:** After bumping to version 3, `databaseVersion_is2()` test fails.
**Why it happens:** The test asserts `assertEquals(2, db.openHelper.readableDatabase.version)` — hardcoded to version 2.
**How to avoid:** Update that assertion to `assertEquals(3, ...)` when bumping the DB version. Check for other hardcoded version assertions in the test file.

### Pitfall 4: FractionalIndex import in DndPrototypeViewModel
**What goes wrong:** DndPrototypeViewModel.kt also imports `com.gmaingret.outlinergod.prototype.FractionalIndex`. After the move, the build fails.
**Why it happens:** The prototype file is in the same package, so it previously used an unqualified reference. After move, it must import from `util`.
**How to avoid:** Update `DndPrototypeViewModel.kt` import to `com.gmaingret.outlinergod.util.FractionalIndex` even though the rest of prototype/ is out of scope.

### Pitfall 5: redoStack type change
**What goes wrong:** Changing `redoStack` from `ArrayDeque<List<FlatNode>>` to `ArrayDeque<UndoSnapshot>` breaks redo logic (redo does not call `restoreNodes`).
**Why it happens:** The UndoSnapshot unification only applies to `undoStack`. The `redo()` function only needs the `flatNodes` list, not the deleted IDs.
**How to avoid:** Keep `redoStack: ArrayDeque<List<FlatNode>>`. Only `undoStack` changes type to `ArrayDeque<UndoSnapshot>`.

## Code Examples

### UndoSnapshot unified type
```kotlin
// At top of NodeEditorViewModel.kt, before the class declaration
data class UndoSnapshot(
    val nodes: List<FlatNode>,
    val deletedNodeIds: List<String> = emptyList()
)

// Inside NodeEditorViewModel:
private val undoStack = ArrayDeque<UndoSnapshot>()
private val redoStack = ArrayDeque<List<FlatNode>>()  // unchanged type

// Push without deletes (replaces pushUndoSnapshot):
if (undoStack.size >= 50) undoStack.removeFirst()
undoStack.addLast(UndoSnapshot(flatNodes))
redoStack.clear()

// Push with deletes (replaces inline push in deleteNode):
if (undoStack.size >= 50) undoStack.removeFirst()
undoStack.addLast(UndoSnapshot(flatNodes, idsToDelete))
redoStack.clear()

// Pop in undo():
val snap = undoStack.removeLast()
val deletedIds = snap.deletedNodeIds
```

### NodeEntity attachment columns
```kotlin
// In NodeEntity data class — add after existing fields:
@ColumnInfo(name = "attachment_url") val attachmentUrl: String? = null,
@ColumnInfo(name = "attachment_mime") val attachmentMime: String? = null,
```

### Attachment upload rewrite (NodeEditorViewModel ~line 518)
```kotlin
// Replace ATTACH| string building with proper field assignment:
val attachNode = NodeEntity(
    id = UUID.randomUUID().toString(),
    documentId = parentNode.entity.documentId,
    userId = parentNode.entity.userId,
    content = "",           // empty — attachment data is in dedicated columns
    contentHlc = hlc,
    attachmentUrl = uploaded.url,
    attachmentMime = uploaded.mimeType,
    // ... other fields unchanged
)
nodeDao.insertNode(attachNode)
```

### exportSchema ksp arg (build.gradle.kts Kotlin DSL)
```kotlin
// Add at top level in build.gradle.kts (outside android block, alongside other top-level blocks):
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `exportSchema = false` | `exportSchema = true` + committed JSON | Phase 23 | Enables Room schema diffing and migration validation |
| `ATTACH|mime|filename|url` in `content` | Dedicated `attachment_url` + `attachment_mime` columns | Phase 23 (v2→v3 migration) | Clean data model; no string parsing at render time |
| Three parallel ArrayDeques for undo | Single `ArrayDeque<UndoSnapshot>` | Phase 23 | Eliminates off-by-one risk in `undoDeletedIds` alignment |
| FractionalIndex in prototype/ package | FractionalIndex in util/ package | Phase 23 | No production import from prototype namespace |

## Open Questions

1. **SUBSTR exact indices for 3rd pipe (filename→url boundary)**
   - What we know: format is `ATTACH|mime|filename|url`; need to skip 3 delimiters in SQL
   - What's unclear: the nested INSTR(SUBSTR(...), '|') depth for the third delimiter
   - Recommendation: Planner should write the SQL as nested INSTR calls and validate with a migration test row `ATTACH|image/png|photo.jpg|https://host/file.jpg`. The CONTEXT.md SQL is a starting approximation — refine using: `SUBSTR(content, 8 + INSTR(SUBSTR(content,8),'|') + INSTR(SUBSTR(content, 8 + INSTR(SUBSTR(content,8),'|') + 1), '|') + 1)`

2. **Schema JSON file timing**
   - What we know: Room KSP generates `3.json` on first build after `exportSchema = true` + ksp arg
   - What's unclear: whether `./gradlew test` alone triggers schema generation or if `assembleDebug` is needed
   - Recommendation: Run `./gradlew kspDebugKotlin` explicitly in the task instructions to ensure `3.json` is generated before committing

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + Robolectric 4.13 (Android unit tests on JVM) |
| Config file | `android/app/build.gradle.kts` (testOptions block) |
| Quick run command | `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && export ANDROID_HOME="/c/Users/gmain/AppData/Local/Android/Sdk" && cd android && ./gradlew testDebugUnitTest` |
| Full suite command | Same as quick run (all JVM tests) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SC-1 | attachment_url/attachment_mime columns on NodeEntity; no ATTACH\| in content | unit | `./gradlew testDebugUnitTest --tests "*.AppDatabaseMigrationTest"` | ✅ (extend existing) |
| SC-2 | FractionalIndex in util/ package, no prototype import in production | build | `./gradlew assembleDebug` (compile error = fail) | N/A |
| SC-3 | backend/src/hlc.ts deleted | manual verify | `ls backend/src/hlc.ts` returns error | N/A |
| SC-4 | exportSchema = true + schema JSON committed | build+file | `./gradlew kspDebugKotlin` + `ls android/app/schemas/` | ❌ Wave 0 |
| SC-5 | UndoSnapshot data class; single undoStack | unit | `./gradlew testDebugUnitTest --tests "*.NodeEditorViewModelTest"` | ✅ (existing undo tests cover behavior) |
| SC-6 | All existing tests pass | full suite | `./gradlew testDebugUnitTest` | ✅ |

### Sampling Rate
- **Per task commit:** `./gradlew testDebugUnitTest --tests "*.AppDatabaseMigrationTest" --tests "*.NodeEditorViewModelTest"` (quick targeted run)
- **Per wave merge:** `./gradlew testDebugUnitTest` (full suite, ~225 tests)
- **Phase gate:** Full suite green before marking phase complete

### Wave 0 Gaps
- [ ] `android/app/schemas/com.gmaingret.outlinergod.db.AppDatabase/3.json` — generated by build, committed to git
- [ ] New tests in `AppDatabaseMigrationTest.kt`: `databaseVersion_is3`, `migration_2_3_addsAttachmentColumns`, `migration_2_3_parsesAttachContent`

*(Existing `NodeEditorViewModelTest.kt` undo tests cover SC-5 behaviorally — refactoring UndoSnapshot must keep those tests green without modification)*

## Sources

### Primary (HIGH confidence)
- Direct source file inspection: `NodeEntity.kt`, `AppDatabase.kt`, `NodeEditorViewModel.kt`, `NodeEditorScreen.kt`, `FlatNode.kt`, `FlatNodeMapper.kt`, `prototype/FractionalIndex.kt`, `build.gradle.kts`, `backend/src/hlc.ts`, `AppDatabaseMigrationTest.kt`, `NodeEditorViewModelTest.kt`
- CONTEXT.md locked decisions (user decisions from /gsd:discuss-phase)

### Secondary (MEDIUM confidence)
- Room KSP schema export: standard Room documentation pattern — `exportSchema = true` + `room.schemaLocation` ksp arg generates JSON under the specified directory

### Tertiary (LOW confidence)
- None — all claims are directly verifiable from source files

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; all existing
- Architecture: HIGH — verified from actual source files
- Pitfalls: HIGH — derived from actual code inspection (hardcoded version assertion, DndPrototypeViewModel import, redoStack type)

**Research date:** 2026-03-07
**Valid until:** 2026-04-07 (stable domain; no fast-moving dependencies)
