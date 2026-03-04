# Phase 11: search-export-bookmarks - Research

**Researched:** 2026-03-04
**Domain:** Android Kotlin — Room FTS5, Orbit MVI, Export (ZIP/formats), Bookmarks CRUD
**Confidence:** HIGH

---

## Summary

Phase 11 delivers three user-facing features that were planned in the original PLAN_PHASE5.md but not yet implemented: FTS5 search (client-side, Room), data export (server-side ZIP via `GET /api/export`), and the bookmarks screen (CRUD on existing `BookmarkDao` + `BookmarkEntity`). All infrastructure — Room database, BookmarkDao, BookmarkEntity, BookmarkMerge, AppNavHost route — already exists. The FTS5 virtual table and triggers, however, are entirely absent from the codebase: the database is at version 1 with no migrations and no `nodes_fts` table.

**Primary recommendation:** Build in three tracks (Search, Export, Bookmarks) with Search requiring a Room DB migration as its first task. Export is the simplest (single server endpoint + Android ShareSheet). Bookmarks reuse the established DocumentList pattern. Keep Phase 11 Android-only; backend export endpoint is already defined in API.md and already implemented (Phase 02).

The old PLAN_PHASE5.md tasks (P5-1 through P5-15) contain correct architecture guidance but were written before the codebase settled its conventions. The package paths in that plan use `com.outlinergod.app` (wrong) instead of `com.gmaingret.outlinergod` (correct). All new code must use the correct package.

---

## Standard Stack

### Core (already in project — no new dependencies needed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Room KSP | 2.8.4 | Local DB + FTS5 queries via `@RawQuery` | Already wired; all DAOs established |
| Orbit MVI | 10.0.0 | ViewModel state management | Project-wide convention (D6) |
| Hilt | 2.56.1 | DI | Project-wide convention |
| Ktor Client | 3.1.3 | HTTP calls to backend | Project-wide convention |
| sh.calvin.reorderable | 2.5.1 | Bookmark drag-to-reorder | Already used in NodeEditorScreen |
| kotlinx.coroutines | 1.10.1 | Flow, debounce, flatMapLatest | Already wired |

### New dependencies required for Export

| Library | Purpose | Where to add |
|---------|---------|-------------|
| None | Export uses Android `Intent.ACTION_SEND` + Ktor GET response as String | Standard Android SDK |

The export in API.md is a full-account ZIP (`GET /api/export`). The original PLAN_PHASE5.md described per-document Markdown/OPML/text exports via `GET /api/documents/{id}/export?format=`. However, **API.md defines only `GET /api/export` (full ZIP)**. The planner must reconcile this: either implement only the ZIP export (matching the API contract) or add a per-document export endpoint. Recommend: implement the ZIP export as defined in API.md, wiring the Android side to download and share via ShareSheet.

**Installation:** No new `libs.versions.toml` entries needed.

---

## Architecture Patterns

### Recommended Package Structure for Phase 11

```
ui/screen/
├── search/
│   ├── SearchViewModel.kt        # Orbit MVI + FTS5 via SearchRepository
│   └── SearchScreen.kt           # TextField + grouped LazyColumn
├── BookmarksScreen.kt            # Replace stub (currently empty Box)
│
ui/viewmodel/
├── BookmarkListViewModel.kt      # Orbit MVI CRUD + BookmarkDao observation
├── ExportViewModel.kt            # Single GET + ShareContent side effect
│
data/repository/
├── SearchRepository.kt           # Interface: searchNodes(query, userId) -> List<SearchResult>
└── impl/SearchRepositoryImpl.kt  # Room @RawQuery FTS5 + structured filter parser
│
db/dao/
└── NodeDao.kt                    # Add: @RawQuery fun searchFts(query: SupportSQLiteQuery): List<NodeEntity>
│
db/
└── AppDatabase.kt                # Bump version to 2, add Migration 1→2 (FTS5 virtual table + triggers)
```

### Pattern 1: Room DB Migration for FTS5

**What:** Room database is at version 1. FTS5 virtual table must be created via a `Migration` object using raw SQL. Room cannot auto-generate FTS5 schemas.

**Critical constraint:** `@Fts5` annotation does not exist in Room. The table must be created in a migration, not via an annotation.

**When to use:** Required as the very first task. All search work depends on this.

**Example:**
```kotlin
// Source: ARCHITECTURE.md Section 3 (FTS5 external content pattern)
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE VIRTUAL TABLE IF NOT EXISTS nodes_fts USING fts5(
                content, note,
                content=nodes, content_rowid=_rowid,
                tokenize='porter unicode61'
            )
        """)
        // Populate from existing data
        db.execSQL("""
            INSERT INTO nodes_fts(rowid, content, note)
            SELECT _rowid, content, note FROM nodes WHERE deleted_at IS NULL
        """)
        // Update trigger (never use plain DELETE/UPDATE on external content FTS5)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS nodes_fts_update AFTER UPDATE ON nodes BEGIN
                INSERT INTO nodes_fts(nodes_fts, rowid, content, note)
                VALUES('delete', old._rowid, old.content, old.note);
                INSERT INTO nodes_fts(rowid, content, note)
                VALUES(new._rowid, new.content, new.note);
            END
        """)
        // Insert trigger
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS nodes_fts_insert AFTER INSERT ON nodes BEGIN
                INSERT INTO nodes_fts(rowid, content, note)
                VALUES(new._rowid, new.content, new.note);
            END
        """)
        // Delete trigger (soft delete: remove from FTS index when deleted_at set)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS nodes_fts_delete AFTER UPDATE OF deleted_at ON nodes
            WHEN new.deleted_at IS NOT NULL AND old.deleted_at IS NULL BEGIN
                INSERT INTO nodes_fts(nodes_fts, rowid, content, note)
                VALUES('delete', old._rowid, old.content, old.note);
            END
        """)
    }
}

// In AppDatabase.build():
Room.databaseBuilder(context, AppDatabase::class.java, "outlinergod.db")
    .addMigrations(MIGRATION_1_2)
    .build()
```

**AppDatabase version bump:** `@Database(version = 2, ...)`

### Pattern 2: FTS5 @RawQuery in NodeDao

**What:** Room does not support FTS5 queries through typed `@Query` annotations. Use `@RawQuery` with `SupportSQLiteQuery` (or `SimpleSQLiteQuery`).

**Example:**
```kotlin
// Source: ARCHITECTURE.md Section 3
@RawQuery
suspend fun searchFts(query: SupportSQLiteQuery): List<NodeEntity>

// Usage in SearchRepositoryImpl:
val sql = buildString {
    append("SELECT n.* FROM nodes n ")
    append("JOIN nodes_fts fts ON n._rowid = fts.rowid ")
    append("WHERE nodes_fts MATCH ? ")
    if (isCompleted != null) append("AND n.completed = $isCompleted ")
    if (color != null) append("AND n.color = $color ")
    append("AND n.deleted_at IS NULL ")
    append("ORDER BY bm25(nodes_fts)")
}
val query = SimpleSQLiteQuery(sql, arrayOf(ftsQuery))
nodeDao.searchFts(query)
```

**Critical:** `SimpleSQLiteQuery` is in `androidx.room:room-runtime`. No new dependency.

### Pattern 3: Orbit MVI ViewModel (project-wide convention)

Follow the established pattern from DocumentListViewModel and NodeEditorViewModel:

```kotlin
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val authRepository: AuthRepository,
) : ViewModel(), ContainerHost<SearchUiState, Nothing> {
    override val container = container<SearchUiState, Nothing>(SearchUiState.Idle)

    private val queryFlow = MutableStateFlow("")

    init {
        intent {
            queryFlow
                .debounce(400L)
                .distinctUntilChanged()
                .flatMapLatest { q ->
                    if (q.isBlank()) flowOf(SearchUiState.Idle)
                    else flow {
                        emit(SearchUiState.Loading)
                        try {
                            val userId = authRepository.getUserId().filterNotNull().first()
                            val results = searchRepository.searchNodes(q, userId)
                            emit(SearchUiState.Success(results))
                        } catch (e: Exception) {
                            emit(SearchUiState.Error(e.message ?: "Search failed"))
                        }
                    }
                }
                .collect { reduce { it } }
        }
    }

    fun onQueryChanged(query: String) {
        queryFlow.value = query
    }
}
```

### Pattern 4: BookmarkListViewModel (mirrors DocumentListViewModel)

The BookmarkDao already exposes `observeAllActive(userId): Flow<List<BookmarkEntity>>`. The ViewModel follows the identical pattern to `DocumentListViewModel`:

```kotlin
@HiltViewModel
class BookmarkListViewModel @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val authRepository: AuthRepository,
    private val hlcClock: HlcClock,
) : ViewModel(), ContainerHost<BookmarkListUiState, BookmarkListSideEffect> {
    // init: collect bookmarkDao.observeAllActive(userId)
    // fun onBookmarkTapped: post NavigateToDocument/NavigateToNode/ExecuteSearch
    // fun softDeleteBookmark: set deletedAt + deletedHlc, call bookmarkDao.softDeleteBookmark
    // fun createBookmark: insertBookmark with HLC-generated sort_order
}
```

**Key:** Phase 11 bookmarks are local-first (write to Room, sync picks them up via existing BookmarkDao.getPendingChanges). No need to call `POST /api/bookmarks` directly from the ViewModel — let sync handle it. This is simpler and consistent with how nodes/documents work.

### Pattern 5: Export (Single GET + ShareSheet)

The API defines `GET /api/export` returning a ZIP binary stream. Android downloads the ZIP using Ktor, writes to a temp file in `context.cacheDir`, then fires `Intent.ACTION_SEND` or `Intent.ACTION_VIEW`.

```kotlin
// ExportViewModel
fun exportAll() = intent {
    reduce { ExportUiState.Loading }
    try {
        val bytes = httpClient.get("$baseUrl/api/export").readBytes()
        val file = File(context.cacheDir, "outlinergod-export.zip")
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        postSideEffect(ExportSideEffect.ShareFile(uri, "application/zip"))
        reduce { ExportUiState.Idle }
    } catch (e: Exception) {
        reduce { ExportUiState.Error(e.message ?: "Export failed") }
    }
}
```

**FileProvider requirement:** Must add `<provider>` to AndroidManifest.xml and `res/xml/file_paths.xml`. This is an Android requirement for sharing files via Intent on API 24+.

### Anti-Patterns to Avoid

- **Direct DELETE/UPDATE on FTS5 external content tables**: causes index corruption. Always use the `'delete'` special INSERT command pattern in triggers.
- **Querying FTS5 with typed Room `@Query`**: Room cannot generate FTS5 SQL. Always use `@RawQuery` with `SimpleSQLiteQuery`.
- **Calling backend `POST /api/bookmarks` directly from BookmarkListViewModel**: the project uses local-first writes. Write to Room; let SyncWorker/SyncRepository push on the next sync cycle.
- **Using `com.outlinergod.app` package**: the correct package is `com.gmaingret.outlinergod` (all existing files use this).
- **Using `reorderable:3.0.0`**: the project is on `2.5.1`. The `3.0.0` API differs. Do not upgrade.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| FTS5 query escaping | Custom escaper | `SimpleSQLiteQuery` parameterized queries | Prevents injection; correct for FTS5 MATCH syntax |
| Fractional index for bookmark sort_order | Custom sort algorithm | `FractionalIndex.generateKeyBetween()` (already in `prototype/FractionalIndex.kt`) | Already battle-tested in project |
| File sharing URI | `file://` URIs directly | `FileProvider.getUriForFile()` | Required on API 24+; direct file URIs cause `FileUriExposedException` |
| Search debounce | Manual `delay()` + coroutine cancel | `Flow.debounce(400L).distinctUntilChanged().flatMapLatest {}` | Correctly handles rapid typing + cancellation |
| ZIP creation on Android | Custom ZIP code | Backend `GET /api/export` already generates the ZIP | The ZIP is server-side; Android only downloads |

**Key insight:** The FTS5 index is maintained automatically by database triggers (created in the migration). No application code needs to manage index updates after the triggers are in place.

---

## Common Pitfalls

### Pitfall 1: FTS5 MATCH Syntax Differs from Regular SQL LIKE

**What goes wrong:** Using `%keyword%` SQL LIKE syntax inside an FTS5 MATCH clause. FTS5 uses its own query syntax: `keyword*` for prefix, `"phrase query"` for phrases, `AND`/`OR`/`NOT` operators.

**Why it happens:** Developers mix up the two search models.

**How to avoid:** Always pass the user's raw query string to FTS5 MATCH. Sanitize by escaping double-quotes if the query contains them. For simple word queries, FTS5 handles them naturally.

**Warning signs:** SQL error `fts5: syntax error near...` in Logcat during search.

### Pitfall 2: Room `@RawQuery` Requires `observedEntities` for Live Queries

**What goes wrong:** A `@RawQuery` `Flow`-returning function does not get invalidated when the underlying table changes if `observedEntities` is not specified.

**Why it happens:** Room's invalidation tracker doesn't know which tables the raw query touches.

**How to avoid:** For suspend functions (one-shot search, not live), `@RawQuery` works fine without `observedEntities`. For this phase, search is one-shot (triggered by debounced input), so no `observedEntities` needed.

```kotlin
// Correct for one-shot search:
@RawQuery
suspend fun searchFts(query: SupportSQLiteQuery): List<NodeEntity>
```

### Pitfall 3: FTS5 External Content Table Population on Migration

**What goes wrong:** Creating the FTS5 virtual table but forgetting to populate it from existing rows. Existing nodes are invisible to search.

**Why it happens:** `content=nodes` tells FTS5 where to read on query, but the index itself starts empty.

**How to avoid:** After `CREATE VIRTUAL TABLE nodes_fts`, immediately run `INSERT INTO nodes_fts(rowid, content, note) SELECT _rowid, content, note FROM nodes WHERE deleted_at IS NULL`.

### Pitfall 4: FileProvider Not Declared = FileUriExposedException

**What goes wrong:** `Intent.ACTION_SEND` with a `file://` URI crashes on API 24+ with `FileUriExposedException`.

**Why it happens:** Android blocked direct file URI sharing for security.

**How to avoid:** Add `<provider>` to AndroidManifest.xml with `android:authorities="${applicationId}.fileprovider"` and create `res/xml/file_paths.xml` specifying `<cache-path>` to allow sharing from `cacheDir`.

**Warning signs:** App crashes with `FileUriExposedException` when tapping Export.

### Pitfall 5: Reorderable 2.5.1 API vs 3.0.0 API

**What goes wrong:** Copying drag-and-drop code from ARCHITECTURE.md or online examples that use `reorderable:3.0.0` API. The 3.0.0 API differs from 2.5.1 in function signatures.

**Why it happens:** ARCHITECTURE.md recommends 3.0.0 but the project locked in at 2.5.1 (the latest confirmed version at project start per MEMORY.md).

**How to avoid:** Use `rememberReorderableLazyListState(lazyListState) { from, to -> ... }` — the 2.5.1 API requires explicit `lazyListState` parameter. Reference the existing `NodeEditorScreen.kt` drag-and-drop implementation as the authoritative example.

### Pitfall 6: Orbit MVI `intent {}` inside `init {}` for Flow collection

**What goes wrong:** Starting Flow collection inside `init {}` directly (not inside `intent {}`). The Flow is not managed by the Orbit container and can leak.

**Why it happens:** Developers confuse `init {}` with `intent {}`.

**How to avoid:** Always wrap long-running Flow collections in `intent {}`:

```kotlin
init {
    intent {
        bookmarkDao.observeAllActive(userId).collect { ... }
    }
}
```

---

## Code Examples

### FTS5 Search with Structured Filter Parsing

```kotlin
// Source: ARCHITECTURE.md Section 3
// SearchRepositoryImpl.kt
suspend fun searchNodes(rawQuery: String, userId: String): List<SearchResult> {
    val parser = SearchQueryParser(rawQuery)
    val ftsQuery = parser.ftsTerms  // e.g. "apple pie"
    val isCompleted = parser.isCompleted  // Boolean? from "is:completed"
    val colorFilter = parser.color    // Int? from "color:red" → 1

    val sql = buildString {
        append("SELECT n.id, n.document_id, n.content, n.note, n.parent_id ")
        append("FROM nodes n ")
        append("JOIN nodes_fts fts ON n._rowid = fts.rowid ")
        append("WHERE nodes_fts MATCH ? ")
        append("AND n.user_id = ? ")
        append("AND n.deleted_at IS NULL ")
        if (isCompleted != null) append("AND n.completed = ${if (isCompleted) 1 else 0} ")
        if (colorFilter != null) append("AND n.color = $colorFilter ")
        append("ORDER BY bm25(nodes_fts)")
    }
    val query = SimpleSQLiteQuery(sql, arrayOf(ftsQuery, userId))
    return nodeDao.searchFts(query).map { it.toSearchResult() }
}
```

### FileProvider Setup for Export

```xml
<!-- res/xml/file_paths.xml -->
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="exports" path="." />
</paths>
```

```xml
<!-- AndroidManifest.xml — inside <application> -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

### Bookmark Soft-Delete (follows existing NodeDao pattern)

```kotlin
// Uses existing BookmarkDao.softDeleteBookmark():
fun softDeleteBookmark(id: String) = intent {
    val deviceId = authRepository.getDeviceId().first()
    val now = System.currentTimeMillis()
    val hlc = hlcClock.generate(deviceId)
    bookmarkDao.softDeleteBookmark(
        id = id,
        deletedAt = now,
        deletedHlc = hlc,
        updatedAt = now
    )
}
```

### AppDatabase Migration Wiring

```kotlin
// AppDatabase.kt — version 2
@Database(
    entities = [NodeEntity::class, DocumentEntity::class, BookmarkEntity::class, SettingsEntity::class],
    version = 2,   // bumped from 1
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // ... same as before ...
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) { ... }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "outlinergod.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        fun buildInMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .addMigrations(MIGRATION_1_2)
                .allowMainThreadQueries()
                .build()
    }
}
```

**Critical:** `buildInMemory()` must also include the migration for FTS5 tests to work. In-memory databases start at version 1 and need the migration applied.

---

## Key Gaps to Address in Planning

### Gap 1: Export endpoint mismatch

**API.md** defines `GET /api/export` (full account ZIP). **PLAN_PHASE5.md** (P5-9, P5-10) described per-document `GET /api/documents/{id}/export?format=markdown|opml|text`. These do not match.

**Resolution:** Implement to the API.md contract. The planner should scope export as: one ExportViewModel with a single `fun exportAll()` action calling `GET /api/export`, downloading the ZIP to `cacheDir`, and sharing via FileProvider + `Intent.ACTION_SEND`. Per-document format export is out of scope unless backend adds the endpoint.

### Gap 2: SEARCH route not yet in AppNavHost/AppRoutes

`AppRoutes` only defines: LOGIN, DOCUMENT_LIST, DOCUMENT_DETAIL, NODE_EDITOR, SETTINGS, BOOKMARKS. A `SEARCH` route constant and composable registration must be added. The `BOOKMARKS` route already exists but its screen is an empty `Box`.

### Gap 3: FTS5 virtual table has never been created

The ARCHITECTURE.md and CLAUDE.md both reference the FTS5 table as if it exists ("FTS5 virtual table and triggers created via raw SQL in a Room migration"), but the codebase has no migrations at all. Task 1 of Phase 11 must create `MIGRATION_1_2`.

### Gap 4: SearchRepository not yet in RepositoryModule

`SearchRepository` and `SearchRepositoryImpl` do not exist. They must be created and registered in `RepositoryModule.kt`.

### Gap 5: Backlinks (P5-6, P5-7 from original plan) — excluded

The CLAUDE.md says: "Backlinks are computed client-side only, at document load time. The server never stores or computes backlinks." The original PLAN_PHASE5.md referenced a `GET /api/documents/{documentId}/backlinks` endpoint that does not exist in API.md. Backlink computation is out of scope for Phase 11. Do not implement BacklinkViewModel or BacklinkSection.

### Gap 6: Image upload (P5-11) — excluded

Image upload involves Compressor and multipart POST to `POST /api/files`. This is backend-coupled and not part of "search-export-bookmarks". Out of scope for Phase 11.

### Gap 7: OfflineRepository + offline indicator (P5-14) — excluded

Out of scope for Phase 11. The PRD names "search, export, bookmarks" as this phase's scope.

### Gap 8: Node-to-document conversion (P5-8) — excluded

Out of scope for Phase 11. Backend endpoint required.

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| `@Fts3`/`@Fts4` Room annotations | Raw SQL migration + `@RawQuery` for FTS5 | bm25() ranking, prefix search, boolean operators |
| Per-document format export | Full account ZIP via single endpoint | Simpler; matches API.md |
| `GoogleSignInClient` | Credential Manager (irrelevant to this phase) | N/A |

---

## Sources

### Primary (HIGH confidence)
- Codebase inspection — `AppDatabase.kt` (version=1, no migrations), `BookmarkDao.kt`, `NodeDao.kt`, `BookmarkEntity.kt`, `AppRoutes.kt`, `AppNavHost.kt` — confirmed current state
- `ARCHITECTURE.md` Section 3 — FTS5 external content table pattern, trigger pattern, `@RawQuery` usage
- `CLAUDE.md` hard rules — FTS5 queries client-side only, `sort_order` as TEXT, `getUserId()` for DAO queries
- `API.md` Sections 7 (Bookmarks) and 8 (Export) — endpoint contracts
- `PLAN_PHASE5.md` — original task descriptions (package names wrong but logic correct)
- `libs.versions.toml` — all actual dependency versions confirmed

### Secondary (MEDIUM confidence)
- `MEMORY.md` — confirmed `reorderable:2.5.1` (not 3.0.0), established test patterns
- Android `FileProvider` documentation — required for file sharing on API 24+

---

## Metadata

**Confidence breakdown:**
- FTS5 migration pattern: HIGH — from ARCHITECTURE.md with direct SQLite confirmation
- Bookmark ViewModel pattern: HIGH — mirrors DocumentListViewModel exactly
- Export approach: HIGH — single endpoint in API.md, standard Android Intent pattern
- Search query parser: MEDIUM — structure is clear but implementation complexity of operators (`has:note`, `color:red`, `is:completed`) must be scoped per task
- Reorderable DnD for bookmarks: HIGH — same library/version used in NodeEditorScreen

**Research date:** 2026-03-04
**Valid until:** 2026-04-04 (stable libraries, no fast-moving dependencies)
