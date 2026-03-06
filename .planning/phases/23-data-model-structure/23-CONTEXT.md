# Phase 23: Data model & structure - Context

**Gathered:** 2026-03-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix 5 tech debt items in data model and code structure:
1. Attachment metadata in proper DB columns (not pipe-delimited content string)
2. FractionalIndex.kt moved from prototype/ to util/
3. Orphan backend/src/hlc.ts deleted
4. Room exportSchema = true with committed schema JSON
5. UndoSnapshot unified into a single data class

No new behaviour. Android tests must stay green throughout.

</domain>

<decisions>
## Implementation Decisions

### Attachment columns — migration strategy
- **Parse-and-migrate**: v2→v3 migration SQL splits the existing `ATTACH|mime|filename|url` content string into the new `attachment_url` and `attachment_mime` columns, then clears `content` to `''`
- DB version bumps to **3** (attachment columns = new schema)
- Migration SQL pattern:
  ```sql
  -- Add columns
  ALTER TABLE nodes ADD COLUMN attachment_url TEXT;
  ALTER TABLE nodes ADD COLUMN attachment_mime TEXT;
  -- Populate from existing ATTACH| content
  UPDATE nodes SET
    attachment_mime = SUBSTR(content, 8, INSTR(SUBSTR(content, 8), '|') - 1),
    attachment_url  = SUBSTR(content, INSTR(content, '|', INSTR(content, '|', 8) + 1) + 1),
    content         = ''
  WHERE content LIKE 'ATTACH|%';
  ```
  (Planner can refine the SQL if SUBSTR indices need adjustment for the 4-part format `ATTACH|mime|filename|url`)

### Attachment columns — call site updates
- `NodeEditorViewModel` (line 529): after upload success, write `attachmentUrl` + `attachmentMime` fields to the entity via `NodeDao.updateNode()` — same pattern as `persistContentChange`; `content` stays `""`
- `NodeEditorScreen` (line 447): replace `content.startsWith("ATTACH|")` branch with `flatNode.entity.attachmentUrl != null` — drives the attachment render path from the explicit column, not string parsing
- `FlatNode.kt` (in ui/mapper/): must expose `attachmentUrl` and `attachmentMime` from the underlying `NodeEntity` so the Screen can access them

### UndoSnapshot — type and placement
- **`data class`** (not sealed class): success criteria spec wins over the roadmap description text
- Fields: `val nodes: List<FlatNode>`, `val deletedNodeIds: List<String> = emptyList()`
- Defined at the **top of NodeEditorViewModel.kt** — it's only used by the ViewModel; no separate file
- Replace both parallel ArrayDeques with one: `private val undoStack = ArrayDeque<UndoSnapshot>()`
- Push without deletes: `undoStack.addLast(UndoSnapshot(flatNodes))`
- Push with deletes (deleteNode): `undoStack.addLast(UndoSnapshot(flatNodes, idsToDelete))`
- Pop: `val snap = undoStack.removeLast()` → use `snap.nodes` and `snap.deletedNodeIds`

### FractionalIndex relocation
- Move `prototype/FractionalIndex.kt` → `util/FractionalIndex.kt`
- Package declaration changes from `com.gmaingret.outlinergod.prototype` to `com.gmaingret.outlinergod.util`
- **Delete** `prototype/FractionalIndex.kt` — no stub, no re-export
- Update all import call sites (FlatNodeMapper, NodeEditorViewModel companion, tests)
- Rest of prototype/ package is left as-is (other prototype files are out of scope)

### exportSchema = true
- Set `exportSchema = true` in `@Database` annotation in `AppDatabase.kt`
- Add to `android/app/build.gradle` ksp block:
  ```groovy
  ksp {
      arg("room.schemaLocation", "$projectDir/schemas")
  }
  ```
- Schema JSON files committed under `android/app/schemas/com.gmaingret.outlinergod.db.AppDatabase/` (Room generates `3.json` on first build after change)
- `buildInMemory()` adds `MIGRATION_2_3` alongside existing `MIGRATION_1_2`

### Migration test
- `AppDatabaseMigrationTest.kt` in `androidTest/` (instrumented — required for `MigrationTestHelper`)
- Covers `MIGRATION_2_3`: creates v2 schema, runs migration, verifies `attachment_url` and `attachment_mime` columns exist with correct nullability
- Also verify existing `ATTACH|` row is correctly parsed: mime → `attachment_mime`, url → `attachment_url`, content → `""`

### Backend orphan file
- Delete `backend/src/hlc.ts` (old hex-format file from Phase 0)
- Only `backend/src/hlc/hlc.ts` remains — no imports to update (nothing should be importing the orphan)

### Claude's Discretion
- Exact SUBSTR indices in the migration SQL (4-part format: `ATTACH|mime|filename|url`)
- Whether MIGRATION_2_3 is a named `val` on AppDatabase.Companion or defined inline in `addMigrations()`
- HLC/timestamp field assignments when writing attachment columns via DAO

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `NodeDao.updateNode(node: NodeEntity)` — existing single-update; attachment column write uses this directly
- `AppDatabase.MIGRATION_1_2` — pattern to follow for MIGRATION_2_3 (object : Migration(2, 3))
- `purgeTombstones()` call in `startServer()` — establishes pattern; no changes needed for HLC file deletion

### Established Patterns
- `data class NodeEntity` with `@ColumnInfo(name = "...")` — add `attachment_url` and `attachment_mime` with `val attachmentUrl: String? = null, val attachmentMime: String? = null`
- `suspend fun` DAO methods with `@Query` — `updateNode` already exists, no new DAO method needed for attachment write
- `ArrayDeque<T>` for bounded stacks in ViewModel — keep the `if (undoStack.size >= 50) undoStack.removeFirst()` cap

### Integration Points
- `NodeEditorViewModel.kt` line 529: attachment upload success handler — rewrite to set entity fields instead of building ATTACH| string
- `NodeEditorScreen.kt` line 447: `isAttachment` check — rewrite to `flatNode.entity.attachmentUrl != null`
- `android/app/build.gradle` — add `ksp { arg(...) }` block alongside existing `ksp.incremental=false` in `gradle.properties`
- `FlatNodeMapper.kt` — if `FlatNode` wraps `NodeEntity`, the new columns are automatically available; verify `FlatNode` data class exposes `entity` directly (it does: `ui/mapper/FlatNode.kt`)

</code_context>

<specifics>
## Specific Ideas

- Migration SQL parses `ATTACH|mime|filename|url` — 4 parts separated by `|`; first part is literal "ATTACH", second is mime, third is filename (unused after migration), fourth is url
- `UndoSnapshot` preview shape confirmed:
  ```kotlin
  data class UndoSnapshot(
      val nodes: List<FlatNode>,
      val deletedNodeIds: List<String> = emptyList()
  )
  private val undoStack = ArrayDeque<UndoSnapshot>()
  ```

</specifics>

<deferred>
## Deferred Ideas

- Deleting the rest of prototype/ (DndPrototypeActivity, WysiwygPrototypeActivity, prototype FlatNode, HlcClock, SyncRepository) — out of scope for Phase 23, candidate for a future cleanup phase

</deferred>

---

*Phase: 23-data-model-structure*
*Context gathered: 2026-03-06*
