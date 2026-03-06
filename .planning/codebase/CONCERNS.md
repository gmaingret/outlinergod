# Codebase Concerns

**Analysis Date:** 2026-03-06

---

## Tech Debt

**Duplicate HLC implementations (three versions, two incompatible formats):**
- Issue: The prototype `HlcClock` at `android/app/src/main/java/com/gmaingret/outlinergod/prototype/HlcClock.kt` uses a hex format (`<16hex>-<4hex>-<deviceId>`). The production `HlcClock` at `android/app/src/main/java/com/gmaingret/outlinergod/sync/HlcClock.kt` uses decimal format (`<13dec>-<5dec>-<deviceId>`). The backend has *two* HLC files: `backend/src/hlc.ts` (the old hex-format class `HlcClock`) and `backend/src/hlc/hlc.ts` (the current decimal functional API). The old `backend/src/hlc.ts` is still on disk and contains a class that produces hex-format HLCs — diverging from the decimal format the rest of the system expects.
- Files: `android/.../prototype/HlcClock.kt`, `android/.../sync/HlcClock.kt`, `backend/src/hlc.ts`, `backend/src/hlc/hlc.ts`
- Impact: If any code accidentally imports `backend/src/hlc.ts` (the old file), HLC strings will be in hex format and will fail lexicographic comparison with decimal strings produced by the current system. Silent data-corruption risk during sync merge.
- Fix approach: Delete `backend/src/hlc.ts`. Archive or delete `android/.../prototype/HlcClock.kt`.

**Duplicate sync logic in ViewModels and SyncWorker:**
- Issue: The full pull-then-push sync sequence is copy-pasted into three places: `NodeEditorViewModel.triggerSync()`, `DocumentListViewModel.triggerSync()`, and `SyncWorker.doWork()`. Any sync bug or protocol change must be fixed in all three locations.
- Files: `android/.../ui/screen/nodeeditor/NodeEditorViewModel.kt` (lines 883–948), `android/.../ui/screen/documentlist/DocumentListViewModel.kt` (lines 67–155), `android/.../sync/SyncWorker.kt` (lines 39–107)
- Impact: Bug fixes and schema changes (e.g., adding a new entity type to sync) require three separate edits. High regression risk if one copy is missed.
- Fix approach: Extract a `SyncOrchestrator` class (or extend `SyncRepository`) that encapsulates the full pull/push/upsert/conflict-resolution/HLC-update cycle. ViewModels and SyncWorker delegate to it.

**Attachment metadata stored as pipe-delimited string in node content:**
- Issue: File attachments are stored by encoding `"ATTACH|<mimeType>|<filename>|<url>"` directly into the `content` field of a `NodeEntity`. This is detected in the UI by `content.startsWith("ATTACH|")`.
- Files: `android/.../ui/screen/nodeeditor/NodeEditorViewModel.kt` (line 529), `android/.../ui/screen/nodeeditor/NodeEditorScreen.kt` (line 447)
- Impact: Attachment nodes appear in FTS search results with raw pipe-delimited strings. Sync will replicate the literal string, not the file. No structured access to individual fields (mime type, URL) without reparsing. Content is not editable without corrupting the encoding.
- Fix approach: Add a dedicated `attachment_url` / `attachment_mime` column pair to `NodeEntity`, or use a separate `attachments` table. Until then, filter ATTACH nodes from FTS indexing.

**`recomputeFlatNodes` uses positional sort orders ("a0", "a1", ...) that overflow at 62 nodes:**
- Issue: `NodeEditorViewModel.recomputeFlatNodes()` assigns sort orders as `"a${DIGITS[rank]}"` where rank is clamped with `coerceAtMost(DIGITS.length - 1)`. At 62+ siblings, all nodes beyond position 61 receive `"az"` — the same sort order — breaking sort stability.
- Files: `android/.../ui/screen/nodeeditor/NodeEditorViewModel.kt` (lines 1001–1020)
- Impact: Any document with more than 62 siblings at the same depth will have undefined DnD sort order for nodes beyond rank 62. Can cause nodes to jump or cluster at the end after drag-and-drop.
- Fix approach: Use `FractionalIndex.generateNKeysBetween(null, null, n)` to generate stable, evenly-spaced keys for all `n` siblings instead of single-character lookups.

**`FractionalIndex` is in the `prototype` package but used in production:**
- Issue: `FractionalIndex` lives at `android/.../prototype/FractionalIndex.kt` alongside `DndPrototypeActivity` and `WysiwygPrototypeActivity`. Production ViewModels (`NodeEditorViewModel`) import directly from `prototype.*`.
- Files: `android/.../prototype/FractionalIndex.kt`, `android/.../ui/screen/nodeeditor/NodeEditorViewModel.kt` (line 16)
- Impact: The `prototype` package signals "disposable code". New developers may archive the prototype directory without realising `FractionalIndex` is load-bearing. Build-time confusion.
- Fix approach: Move `FractionalIndex.kt` to `android/.../util/` or `android/.../sync/`.

**KSP incremental builds disabled globally:**
- Issue: `android/gradle.properties` sets `ksp.incremental=false` and `ksp.incremental.intermodule=false`. This is documented as a workaround for KSP incremental build corruption.
- Files: `android/gradle.properties`
- Impact: Every build reprocesses all KSP annotations (Room, Hilt). This meaningfully increases clean-build times and negates incremental-build benefits for a codebase with heavy annotation use.
- Fix approach: Upgrade to the latest KSP version (may have fixed the corruption), or re-enable incrementally and confirm the bug is gone.

**`SettingsSyncRecord` defined inline in `sync.ts` rather than in `merge.ts`:**
- Issue: `NodeSyncRecord`, `DocumentSyncRecord`, and `BookmarkSyncRecord` are defined in `backend/src/merge.ts` and have corresponding `mergeX()` functions there. `SettingsSyncRecord` and `mergeSettings()` are defined inline in `backend/src/routes/sync.ts`.
- Files: `backend/src/routes/sync.ts` (lines 26–222), `backend/src/merge.ts`
- Impact: The settings merge logic has no dedicated test coverage (tests for `merge.ts` cover the other three entity types). The `sync.ts` route file is already 534 lines; the inline merge functions make it harder to navigate.
- Fix approach: Move `SettingsSyncRecord` and `mergeSettings()` into `backend/src/merge.ts` with corresponding tests.

**`exportSchema = false` in Room `@Database`:**
- Issue: `AppDatabase` sets `exportSchema = false`, disabling Room's schema export. This means Room will not write schema JSON files that can be committed and used to verify migration correctness.
- Files: `android/.../db/AppDatabase.kt` (line 26)
- Impact: Future migrations cannot be validated against a known baseline. If the schema diverges from production devices, there is no committed artifact to compare against.
- Fix approach: Set `exportSchema = true`, add a `schemaLocation` to `build.gradle.kts`, and commit the generated schema JSON files.

---

## Known Bugs

**`onEnterPressed` at cursor position 0 creates blank node in wrong position:**
- Symptoms: When Enter is pressed with cursor at the start of a node, the intent computes `newSortOrder` as `generateKeyBetween(currentNode.sortOrder, nextSibling)` (a key *after* the current node), then immediately recomputes `beforeSortOrder` as `generateKeyBetween(prevSibling, currentNode)` — discarding `newSortOrder`. The variable `blankNode` is created with `sortOrder = newSortOrder` and then immediately `.copy(sortOrder = beforeSortOrder)`. The intermediate `newSortOrder` allocation is wasted computation but the final result is correct. However, the split-at-zero path does **not** update the current node's `contentHlc` to reflect that content was left unchanged — meaning the current node won't be treated as pending for the next sync even though its sibling relationship changed.
- Files: `android/.../ui/screen/nodeeditor/NodeEditorViewModel.kt` (lines 164–193)
- Trigger: Press Enter with cursor at position 0.
- Workaround: None. The node still appears correctly; the sync gap is minor in practice.

**`restoreNode` matches by `deletedAt` timestamp, not by explicit ID set:**
- Symptoms: `NodeEditorViewModel.restoreNode()` fetches all nodes in the document with the same `deletedAt` timestamp and restores all of them. If two unrelated delete operations happen at the same millisecond, both subtrees are restored when only one was intended.
- Files: `android/.../ui/screen/nodeeditor/NodeEditorViewModel.kt` (lines 794–806)
- Trigger: Delete two nodes at the same millisecond (unlikely in practice but possible under load).

**`DELETE /files/:filename` does not verify ownership before deletion:**
- Symptoms: The route checks that the file exists on disk, then deletes it. Ownership is checked only in the `GET` route (line 192). An authenticated user can delete another user's file if they know the UUID filename.
- Files: `backend/src/routes/files.ts` (lines 218–240)
- Trigger: Authenticated user calls `DELETE /api/files/<uuid>.<ext>` with another user's file UUID.
- Fix approach: Add `WHERE user_id = ?` to the ownership check before deletion.

---

## Security Considerations

**`JWT_SECRET` is accessed via `process.env.JWT_SECRET!` with a non-null assertion, no runtime check:**
- Risk: If `JWT_SECRET` is unset, `new TextEncoder().encode(undefined)` produces an empty byte sequence. JWTs signed with an empty secret are trivially forgeable.
- Files: `backend/src/middleware/auth.ts` (line 38), `backend/src/routes/auth.ts` (line 41)
- Current mitigation: None. Docker Compose is expected to supply the variable, but there is no startup assertion.
- Recommendations: Add a startup guard in `backend/src/index.ts` that throws if `JWT_SECRET` is missing or shorter than 32 characters.

**File serve endpoint uses filesystem `stat` + `readFile` with user-controlled filename:**
- Risk: `FILENAME_PATTERN` (`/^[0-9a-f-]{36}\.[a-z0-9]+$/`) prevents path traversal. However, the check runs before the ownership DB lookup (lines 182–192). A file that passes the pattern check but has no DB record still triggers a filesystem `stat` call, leaking file existence information to unauthenticated requesters.
- Files: `backend/src/routes/files.ts` (lines 179–213)
- Current mitigation: `requireAuth` preHandler blocks unauthenticated access. Risk is low.
- Recommendations: Move the DB lookup before the filesystem `stat` to return 404 on missing DB record without touching the filesystem.

**Refresh token stored as hex in SQLite with no index on `token` column:**
- Risk: The `refresh_tokens` table primary key is `token` (a 64-char hex string). Lookups are fast. However, revoked tokens are never purged — the table grows unboundedly. A large `refresh_tokens` table is a minor data-exposure risk if the database file is compromised.
- Files: `backend/src/routes/auth.ts` (lines 149–177), `backend/src/db/schema.ts` (lines 21–30)
- Current mitigation: Tokens are revoked on rotation. Expired tokens are checked at use time.
- Recommendations: Add a background cleanup job (similar to `purgeTombstones`) to hard-delete revoked or expired refresh tokens older than 90 days.

**`GOOGLE_CLIENT_ID` must be the web client ID, not the Android client ID:**
- Risk: If the wrong client ID is set in `.env`, Google token verification will reject all sign-ins silently with a 401. There is no startup validation of this value.
- Files: `backend/src/routes/auth.ts` (line 22), `.env` (not readable)
- Current mitigation: Documented in `MEMORY.md`.
- Recommendations: Add a startup log message that prints the first 8 chars of `GOOGLE_CLIENT_ID` so operators can verify configuration without exposing the full value.

---

## Performance Bottlenecks

**`persistReorderedNodes` issues one `updateNode` call per changed node with no batching:**
- Problem: After every drag-and-drop or undo/redo, `persistReorderedNodes` iterates all flat nodes and issues individual `updateNode` (Room `@Update`) calls for each node whose `sortOrder` or `parentId` changed. A subtree move can affect dozens of nodes.
- Files: `android/.../ui/screen/nodeeditor/NodeEditorViewModel.kt` (lines 578–603)
- Cause: Room `@Update` does not have a batch variant; `upsertNodes` uses `REPLACE` which resets all fields including HLCs.
- Improvement path: Add a `@Query`-based batch update to `NodeDao` that accepts a list of (id, sortOrder, sortOrderHlc, parentId, parentIdHlc, updatedAt, deviceId) tuples, or wrap the loop in a single Room transaction.

**FTS4 `ORDER BY n.updated_at DESC` does full-table scan on large corpora:**
- Problem: The FTS JOIN query in `SearchRepositoryImpl` sorts by `n.updated_at DESC`. SQLite cannot use the FTS index to satisfy this ORDER BY; it sorts the matched rows after filtering. On a large note corpus this is an O(n log n) post-filter.
- Files: `android/.../repository/impl/SearchRepositoryImpl.kt` (lines 37–54)
- Cause: FTS4 (unlike FTS5) does not expose `bm25()` ranking; falling back to `updated_at` sort requires the full result set to be materialized and sorted.
- Improvement path: Limit the result set size with `LIMIT 200` before sorting, or migrate to FTS5 (available on Android 10+ devices).

**`getNodesByDocumentSync` called on every keystroke via `persistContentChange`:**
- Problem: `persistContentChange` and `persistNoteChange` each call `nodeDao.getNodesByDocumentSync(documentId)` — a full document query — on every debounce flush (300ms after each keystroke). They do this to retrieve the current DB entity before updating it.
- Files: `android/.../ui/screen/nodeeditor/NodeEditorViewModel.kt` (lines 688–718)
- Cause: The ViewModel holds the in-memory `FlatNode` but needs the full `NodeEntity` to call `@Update`. Rather than getting a single node by ID, it fetches the full document list.
- Improvement path: Add `NodeDao.getNodeByIdSync(nodeId: String): NodeEntity?` and use it instead.

**Backend sync pull query uses 8-9 unindexed HLC comparisons per table:**
- Problem: `GET /sync/changes` runs queries like `WHERE (content_hlc > ? OR note_hlc > ? OR ... OR deleted_hlc > ?)` — eight OR conditions on separate TEXT columns. Without a composite index or a dedicated `max_hlc` column, SQLite performs a full table scan per sync pull.
- Files: `backend/src/routes/sync.ts` (lines 466–507)
- Cause: Per-field HLC means the "most recently changed" value is spread across many columns.
- Improvement path: Add a `max_hlc TEXT GENERATED ALWAYS AS (MAX(content_hlc, note_hlc, ...))` computed column with an index, or maintain a denormalized `updated_hlc` column updated on every write.

---

## Fragile Areas

**`NodeEditorViewModel` is 1,053 lines and handles persistence, sync, DnD, undo, attachments, and UI state:**
- Files: `android/.../ui/screen/nodeeditor/NodeEditorViewModel.kt`
- Why fragile: The class has 10 injected dependencies, three debounce job maps (`contentDebounceJobs`, `noteDebounceJobs`, `colorDebounceJobs`), an undo/redo stack that is not persisted across process death, and inline sync orchestration. Any change to one concern risks unintentional side effects on another.
- Safe modification: Changes to persistence (debounce, `onNodeFocusLost`) should be tested against sync timing. Changes to `reorderNodes` must verify undo stack alignment (`undoStack` and `undoDeletedIds` must remain the same length).
- Test coverage: ViewModel tests exist but are unit tests that mock all DAO calls. No integration test covering the debounce-flush-then-sync cycle.

**`undoStack` and `undoDeletedIds` are parallel `ArrayDeque` instances that must stay in sync:**
- Files: `android/.../ui/screen/nodeeditor/NodeEditorViewModel.kt` (lines 62–71, 783–787)
- Why fragile: Two separate deques are maintained in parallel. `pushUndoSnapshot` always appends an `emptyList()` to `undoDeletedIds` to stay aligned. `deleteNode` directly manipulates `undoStack` and `undoDeletedIds` without going through `pushUndoSnapshot`, bypassing the alignment guarantee. If the two deques ever differ in length, `undo()` will pop mismatched entries.
- Safe modification: All undo snapshot pushes must go through `pushUndoSnapshot`. `deleteNode` must be refactored to use `pushUndoSnapshot` before overwriting `undoDeletedIds`.

**`SyncLogger` is a process-wide `object` with no privacy controls:**
- Files: `android/.../util/SyncLogger.kt`
- Why fragile: `SyncLogger` is always enabled. Every HTTP request, token value presence/absence, and node operation is logged to both Logcat and an in-memory ring buffer. The logs include token presence indicators and base URL strings. In a release build, `Log.d` calls are typically stripped by ProGuard, but the in-memory ring buffer is not guarded.
- Safe modification: Wrap `SyncLogger.log()` calls with a `BuildConfig.DEBUG` check, or replace with a proper logging framework that respects build variants.

**`AppDatabase.buildInMemory` skips `FTS_CALLBACK`, leaving FTS4 triggers absent in test databases:**
- Files: `android/.../db/AppDatabase.kt` (lines 109–113)
- Why fragile: The production `build()` path adds `FTS_CALLBACK` which creates FTS4 virtual tables and triggers on fresh install. `buildInMemory()` omits this callback intentionally (Robolectric lacks FTS support). As a result, any test that calls `buildInMemory()` operates on a database without FTS4 triggers — any `INSERT`/`UPDATE`/soft-delete to `nodes` will not update `nodes_fts`. Search tests are forced to mock `NodeDao` instead of exercising real FTS.
- Safe modification: When adding FTS-dependent features, add Android instrumented tests (`androidTest/`) to cover FTS correctness on a real device or emulator.

**`filterSubtree` in `NodeEditorViewModel.Companion` uses BFS starting from children, not from the root node itself:**
- Files: `android/.../ui/screen/nodeeditor/NodeEditorViewModel.kt` (lines 977–998)
- Why fragile: `filterSubtree(nodes, rootNodeId)` returns the *children* of `rootNodeId` but not `rootNodeId` itself. `mapToFlatList` is then called with the filtered list and `rootNodeId` as the documentId root. This means the node the user tapped to zoom in on is not in the list passed to the mapper; the mapper reconstructs children using `dfs(rootNodeId, 0)` treating it as a virtual root. This works but is non-obvious: the caller must understand that `filterSubtree` excludes the root and that `mapToFlatList` treats the second argument as a virtual anchor, not a rendered node.
- Safe modification: Add a clarifying comment to both `filterSubtree` and its call site in `loadDocument`.

---

## Scaling Limits

**SQLite single-writer bottleneck on the backend:**
- Current capacity: Single Docker container, WAL mode enabled. Concurrent reads are fine; concurrent writes serialize.
- Limit: The sync push endpoint processes all incoming records sequentially in a single request handler. Under high concurrency (multiple devices pushing simultaneously for the same user), SQLite write-lock contention will produce 503-level delays.
- Scaling path: The architecture is inherently single-user-per-server (self-hosted), so this is acceptable for now. If multi-user SaaS mode is ever wanted, a PostgreSQL migration is required.

**Undo stack is in-memory only, lost on process death:**
- Current capacity: 50-entry ring buffer per `NodeEditorViewModel` instance.
- Limit: The stack is cleared on process death (e.g., app backgrounded, system pressure, screen rotation with `ViewModel` recreation). Users lose their undo history on every cold open.
- Scaling path: Persist undo snapshots to Room or `SavedStateHandle` if deep undo history is a UX requirement.

**Tombstone purge runs only at server startup:**
- Current capacity: `purgeTombstones()` at `backend/src/tombstone.ts` is called once when the server starts.
- Limit: If the server runs continuously for months, no tombstone cleanup occurs until the next restart. The `nodes`, `documents`, and `bookmarks` tables grow with soft-deleted rows.
- Scaling path: Schedule `purgeTombstones()` via a periodic timer (e.g., every 24 hours using `setInterval`) in addition to startup execution.

---

## Dependencies at Risk

**`sh.calvin.reorderable:reorderable:2.5.1` is pinned to a pre-3.x version:**
- Risk: The 3.x API surface is different enough that upgrading requires changing `rememberReorderableLazyListState` call sites. The 2.x branch may stop receiving bug fixes.
- Impact: DnD reordering (`NodeEditorScreen`) and any future list reordering features are coupled to the 2.x API.
- Migration plan: When upgrading, update `rememberReorderableLazyListState` to the 3.x signature across `NodeEditorScreen.kt`.

**Ktor Client pinned to `3.1.3` due to Kotlin 2.1.0 constraint:**
- Risk: Ktor 3.2+ likely resolves bugs and adds features, but is blocked by Kotlin version compatibility.
- Impact: HTTP client features and OkHttp engine updates are unavailable.
- Migration plan: Upgrade Kotlin to 2.2.x when stable, then lift Ktor to the latest 3.x release.

**`orbit-mvi:10.0.0` — `expectInitialState()` deprecated:**
- Risk: Tests use `expectState()` from the new orbit-test DSL, but if the library makes further breaking changes the DSL may shift again.
- Impact: All ViewModel tests (`NodeEditorViewModelTest`, `DocumentListViewModelTest`, etc.) use orbit-test assertions directly.
- Migration plan: Pin to 10.x and watch for 11.x changelog before upgrading.

---

## Missing Critical Features

**No client-side tombstone purge:**
- Problem: The Android Room database retains soft-deleted nodes (`deleted_at IS NOT NULL`) indefinitely. There is no equivalent of the server's `purgeTombstones()` on the Android side.
- Blocks: The local database will grow with tombstones on heavily-used devices, degrading Room query performance over time.

**No file orphan cleanup on document deletion:**
- Problem: `DELETE /documents/:id` soft-deletes nodes but explicitly skips file cleanup (`// TODO: file cleanup`). Uploaded file attachments remain on disk and in the `files` table when their parent document is deleted.
- Files: `backend/src/routes/documents.ts` (line 362)
- Blocks: Storage space on the Docker host grows unboundedly if users delete documents with attachments.

**No file sync across devices:**
- Problem: File attachments are uploaded directly to the backend (`/api/files/upload`). The file URL is embedded in the node `content` field as `"ATTACH|..."`. The URL is a relative path (`/api/files/<uuid>.<ext>`) that only resolves against the configured `BASE_URL`. If a user opens the document on a different device that has never synced the attachment node, the URL will 200 but the image display depends on Coil + OkHttp resolving it at render time. Offline viewing of attachments is not possible.
- Blocks: True offline-first behavior for documents containing attachments.

---

## Test Coverage Gaps

**`NodeEditorViewModel` has no end-to-end debounce test:**
- What's not tested: The interaction between `onContentChanged` (debounce 300ms), `onNodeFocusLost` (flush), and `triggerSync` (30s timer). No test verifies that a fast-typing sequence followed by focus-loss correctly persists to Room and does not double-write.
- Files: `android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModelTest.kt`
- Risk: Silent data loss if debounce and flush interact unexpectedly under rapid input.
- Priority: High

**`DELETE /files/:filename` missing ownership test:**
- What's not tested: No test asserts that a user cannot delete another user's file. The `GET /files/:filename` ownership check is tested, but `DELETE` is not.
- Files: `backend/src/routes/files.test.ts`
- Risk: Cross-user file deletion is possible.
- Priority: High

**`FractionalIndex.generateNKeysBetween` is untested for large n:**
- What's not tested: Edge cases where `n >= DIGITS.length` (62), or calling `generateNKeysBetween` with both bounds non-null and very close keys.
- Files: `android/app/src/test/java/com/gmaingret/outlinergod/prototype/FractionalIndexTest.kt`
- Risk: Key collision at high sibling counts.
- Priority: Medium

**Search `in:note` / `in:title` post-filter bypasses FTS entirely:**
- What's not tested: The post-filter in `SearchRepositoryImpl` does a simple `String.contains()` on the match term after stripping the trailing `*`. There is no test verifying that FTS prefix terms (e.g., `"foo*"`) are correctly stripped before the post-filter, or that multi-word FTS terms are handled correctly.
- Files: `android/.../repository/impl/SearchRepositoryImpl.kt` (lines 60–66)
- Risk: Post-filter may silently miss matches when the query has prefix `*` or multiple words.
- Priority: Medium

**`SyncWorker` integration test uses a mocked `SyncRepository`:**
- What's not tested: The actual pull-upsert-push-conflict-resolve cycle against a real Room database in a worker context. `SyncWorkerIntegrationTest` uses a real Room DB but still mocks `SyncRepository`, meaning network errors, partial pushes, and conflict resolution are not tested end-to-end.
- Files: `android/app/src/test/java/com/gmaingret/outlinergod/sync/SyncWorkerIntegrationTest.kt`
- Risk: The conflict resolution path (applying `pushResponse.conflicts`) is exercised only via unit mocks, not against real Room upsert behavior.
- Priority: Medium

---

*Concerns audit: 2026-03-06*
