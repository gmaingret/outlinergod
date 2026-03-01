## Phase 5 — Android Advanced Features

**Goal**: Phase 5 builds every remaining user-facing feature on top of the Phase 4 foundation. By the end of this phase the app is feature-complete: search, bookmarks, backlinks, export (Markdown / OPML / Plain text), image upload, node-to-document conversion, offline indicator, full LWW conflict resolution in the WorkManager sync job, and a complete bottom-navigation AppNavHost. The app is ready for end-to-end integration testing in Phase 6.

**Prerequisite**: All Phase 4 tasks must have passing tests before beginning Phase 5. Phase 4 exit criteria: `./gradlew test` exits 0 (zero failures), `./gradlew assembleDebug` exits 0.

Tasks run in task ID order. Each task is scoped to approximately 1–2 hours.

---

### P5-1: SearchViewModel

- **Task ID**: P5-1
- **Title**: Add SearchRepository and SearchViewModel with debounced query
- **What to build**: Create `SearchRepository` interface in `com.gmaingret.outlinergod.data.repository` with `suspend fun searchNodes(query: String, documentId: String?): List<SearchResultGroup>`. Implement `SearchRepositoryImpl` in `com.gmaingret.outlinergod.data.repository.impl` that queries the local Room FTS5 virtual table (`nodes_fts`) using the `NodeDao` — this is a client-side search, NOT a server endpoint. The implementation parses the query string to separate FTS terms from structured filters (e.g., `is:completed`, `has:note`, `color:red`), builds a combined SQL query joining `nodes_fts` with `nodes`, and groups results into `SearchResult` data class (`nodeId: String, documentId: String, documentTitle: String, content: String, breadcrumb: String`) and `SearchResultGroup(documentId: String, documentTitle: String, nodes: List<SearchResult>)`. Register `SearchRepository → SearchRepositoryImpl` binding in `RepositoryModule` (P3-13). Create `@HiltViewModel SearchViewModel` in `com.gmaingret.outlinergod.ui.viewmodel` implementing `ContainerHost<SearchUiState, Nothing>` where `SearchUiState` is a sealed class with `Idle`, `Loading`, `Success(groups: List<SearchResultGroup>)`, and `Error(message: String)` states. Expose `fun onQueryChanged(query: String)` which feeds a `MutableStateFlow<String>`; in `init {}` launch an Orbit `intent {}` that applies `.debounce(400L).distinctUntilChanged().flatMapLatest { q -> if (q.isBlank()) flowOf(SearchUiState.Idle) else fetchResults(q) }` where `fetchResults` wraps the repository call in `try/catch`, reducing to `Loading` before the call and to `Success` or `Error` after.
- **Inputs required**: P3-11 (HttpClient / NetworkModule), P3-13 (RepositoryModule), P4-0 (Orbit MVI 10.0.0)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/data/model/SearchResult.kt`
  - `android/app/src/main/java/com/outlinegod/app/data/model/SearchResultGroup.kt`
  - `android/app/src/main/java/com/outlinegod/app/data/repository/SearchRepository.kt`
  - `android/app/src/main/java/com/outlinegod/app/data/repository/impl/SearchRepositoryImpl.kt`
  - `android/app/src/main/java/com/outlinegod/app/ui/viewmodel/SearchViewModel.kt`
  - `android/app/src/main/java/com/outlinegod/app/di/RepositoryModule.kt` (updated)
- **How to test**: `android/app/src/test/java/com/outlinegod/app/ui/viewmodel/SearchViewModelTest.kt` using `runTest` with `StandardTestDispatcher` and `MainDispatcherRule`; mock `SearchRepository` with MockK:
  1. `blank query emits Idle without calling repository` — call `onQueryChanged("")`; advance time 500ms; assert state is `SearchUiState.Idle`; `coVerify(exactly = 0) { repository.searchNodes(any(), any()) }`.
  2. `valid query after 400ms debounce emits Loading then Success` — `coEvery { repository.searchNodes("test", null) } returns listOf(fakeGroup())`; call `onQueryChanged("test")`; advance 400ms; assert state transitions through `Loading` to `Success`.
  3. `repository IOException emits Error state` — `coEvery { repository.searchNodes(any(), any()) } throws IOException("net")`; call `onQueryChanged("fail")`; advance 400ms; assert state is `SearchUiState.Error`.
  4. `rapid successive queries debounce to single repository call` — call `onQueryChanged("a")`, `onQueryChanged("ab")`, `onQueryChanged("abc")` with 50ms gaps; advance 400ms; `coVerify(exactly = 1) { repository.searchNodes("abc", null) }`.
  5. `Success state groups results by documentId with correct documentTitle` — return two results with same `documentId`; assert `Success.groups.size == 1` and `groups[0].nodes.size == 2`.
- **Risk**: LOW — standard Flow debounce + flatMapLatest pattern; Ktor GET with query params

---

### P5-2: SearchScreen

- **Task ID**: P5-2
- **Title**: Build SearchScreen composable with grouped results LazyColumn
- **What to build**: Create `SearchScreen` in `com.gmaingret.outlinergod.ui.screen` accepting `viewModel: SearchViewModel = hiltViewModel()` and `onNavigateToNode: (documentId: String, nodeId: String) -> Unit`. At the top render a search `TextField` (Material3 `OutlinedTextField`) wired to `viewModel.onQueryChanged(it)` with a leading search icon and a trailing clear icon when text is non-empty. Below the field, switch on `viewModel.collectAsState()`: `Idle` → `EmptySearchState` composable (centered search icon + "Start typing to search" hint); `Loading` → `LinearProgressIndicator` full-width; `Error` → `ErrorState` composable (error icon + message text); `Success` with non-empty groups → `LazyColumn` where each `SearchResultGroup` renders a sticky `stickyHeader {}` with the document title in `MaterialTheme.typography.titleSmall` followed by `SearchResultRow` items showing `content` and `breadcrumb` in smaller dimmed type — tapping any row calls `onNavigateToNode(group.documentId, result.nodeId)`; `Success` with empty groups → `NoResultsState` composable ("No results for this query"). The screen is a top-level destination registered in AppNavHost at `AppRoutes.SEARCH`.
- **Inputs required**: P5-1
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/SearchScreen.kt`
- **How to test**: Manual acceptance criteria (no Compose UI tests this phase):
  - Typing "test" shows a `LinearProgressIndicator`, then results grouped under document-title headers
  - Rapid typing produces only one network call per 400ms window (visible in Logcat)
  - Clearing the field shows the idle state with hint text
  - A query with no matches shows the no-results state
  - Tapping any result row navigates to `NodeEditorScreen` with the correct `documentId`
- **Risk**: LOW — standard grouped `LazyColumn` with sticky headers; no new state complexity

---

### P5-3: BookmarkListViewModel

- **Task ID**: P5-3
- **Title**: Add BookmarkListViewModel with CRUD operations driven by BookmarkDao
- **What to build**: Create `@HiltViewModel BookmarkListViewModel` in `com.gmaingret.outlinergod.ui.viewmodel` implementing `ContainerHost<BookmarkListUiState, BookmarkListSideEffect>`. Define `BookmarkListUiState` sealed class: `Loading`, `Success(bookmarks: List<BookmarkEntity>)`, `Error(message: String)`; `BookmarkListSideEffect` sealed class: `NavigateToDocument(documentId: String)`, `NavigateToNode(documentId: String, nodeId: String)`, `ExecuteSearch(query: String)`. In `init {}`, launch an Orbit `intent {}` that collects `bookmarkDao.observeAllActive(userId)` (a `Flow<List<BookmarkEntity>>` defined in P3-6) and reduces each emission into `BookmarkListUiState.Success`. Expose `fun onBookmarkTapped(bookmark: BookmarkEntity)` that posts the appropriate side effect based on `bookmark.targetType` ("document" → `NavigateToDocument`, "node" → `NavigateToNode`, "search" → `ExecuteSearch`). Expose `fun createBookmark(title: String, targetType: String, targetDocumentId: String?, targetNodeId: String?, query: String?, sortOrder: String)` that calls `POST /api/bookmarks` via `HttpClient`, then upserts the returned `BookmarkEntity` into Room via `bookmarkDao.upsert()` and marks it `syncStatus = SYNCED`. Expose `fun softDeleteBookmark(id: String)` that sets `deletedAt = System.currentTimeMillis()`, `deletedHlc = hlcClock.generate(deviceId)`, `syncStatus = PENDING`, and calls `bookmarkDao.upsert(entity)`. Expose `fun reorder(id: String, newSortOrder: String)` that updates `sortOrder`, sets `sortOrderHlc = hlcClock.generate(deviceId)`, `syncStatus = PENDING`, and calls `bookmarkDao.upsert()`.
- **Inputs required**: P3-6 (BookmarkDao), P3-9 (HlcClock), P3-11 (HttpClient), P3-12 (AuthRepository for userId/deviceId), P4-0 (Orbit MVI)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/viewmodel/BookmarkListViewModel.kt`
- **How to test**: `android/app/src/test/java/com/outlinegod/app/ui/viewmodel/BookmarkListViewModelTest.kt` using `runTest` + `StandardTestDispatcher`, mocked `BookmarkDao`, `HttpClient`, `HlcClock`, `AuthRepository`:
  1. `init collects dao flow and emits Success with full bookmark list` — stub `bookmarkDao.observeAllActive("u1")` to return `flowOf(listOf(fakeBookmark()))`; construct VM; advance; assert state is `Success` with 1 item.
  2. `onBookmarkTapped with type=document posts NavigateToDocument side effect` — call `onBookmarkTapped(documentBookmark)`; collect side effects; assert `NavigateToDocument("doc-1")` is emitted.
  3. `onBookmarkTapped with type=search posts ExecuteSearch side effect` — call `onBookmarkTapped(searchBookmark)`; assert `ExecuteSearch("#tag")` is emitted.
  4. `softDeleteBookmark sets deletedAt non-null and syncStatus PENDING` — call `softDeleteBookmark("b1")`; capture `bookmarkDao.upsert(slot)`; assert `slot.captured.deletedAt != null && slot.captured.syncStatus == SyncStatus.PENDING`.
  5. `reorder updates sortOrder and generates new HLC greater than empty string` — call `reorder("b1", "V0")`; capture upsert; assert `slot.captured.sortOrder == "V0"` and `slot.captured.sortOrderHlc.isNotBlank()`.
  6. `createBookmark upserts into Room on api success` — mock httpClient to return 201 with `BookmarkEntity` JSON; call `createBookmark(...)`; verify `bookmarkDao.upsert(any())` called once.
- **Risk**: LOW — same DAO-observation pattern as DocumentListViewModel (P4-3)

---

### P5-4: BookmarkListScreen

- **Task ID**: P5-4
- **Title**: Build BookmarkListScreen with drag-to-reorder and swipe-to-delete
- **What to build**: Create `BookmarkListScreen` in `com.gmaingret.outlinergod.ui.screen` accepting `viewModel: BookmarkListViewModel = hiltViewModel()` and `onNavigate: (BookmarkListSideEffect) -> Unit`. Collect `viewModel.collectAsState()` and wire side effects via `collectSideEffect { onNavigate(it) }`. When `Success`: render a `ReorderableLazyColumn` from `sh.calvin.reorderable:reorderable:3.0.0` where each `BookmarkItem` composable shows a leading icon (document icon for `"document"`, node/bullet icon for `"node"`, search icon for `"search"` targetType), the bookmark `title` in primary text, and a `Modifier.longPressDraggableHandle()` on the drag handle icon at the trailing end. On drop completion, compute `newSortOrder` via `generateKeyBetween(prevSortOrder, nextSortOrder)` from the fractional indexing Kotlin port and call `viewModel.reorder(id, newSortOrder)`. Each row uses `SwipeToDismissBox` (Material3): on dismiss confirm call `viewModel.softDeleteBookmark(id)` and show a `SnackBar` with an Undo action that calls `viewModel.undoDelete(id)` (restores `deletedAt = null`, `syncStatus = PENDING`). A `FloatingActionButton` at the bottom-right opens a `CreateBookmarkBottomSheet` (a `ModalBottomSheet` with a `TextField` for title, a `SegmentedButton` for type, and a Confirm button).
- **Inputs required**: P5-3, P4-7 (drag-and-drop pattern from NodeEditorScreen)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/BookmarkListScreen.kt`
- **How to test**: Manual acceptance criteria:
  - Bookmarks render sorted by `sort_order` with correct type icons
  - Long-press drag handle reorders items; drop updates persisted order (verified by restarting the screen)
  - Swipe-to-delete shows Undo snackbar; Undo restores the row immediately
  - FAB opens the create sheet; created bookmark appears at the bottom of the list
  - Tapping a document bookmark fires `NavigateToDocument` side effect and navigates correctly
- **Risk**: LOW — same `sh.calvin.reorderable` + `SwipeToDismissBox` pattern established in NodeEditorScreen (P4-7)

---

### P5-5: BookmarkDao sync wiring

- **Task ID**: P5-5
- **Title**: Wire BookmarkDao into SyncRepository pull/push cycle
- **What to build**: In `SyncRepositoryImpl` (from P3-13), extend the **pull handler**: after applying the `GET /api/sync/changes` response, iterate `response.bookmarks` and for each record call `bookmarkDao.getById(record.id)` — if null, upsert directly; if present, call `BookmarkMerge.merge(local, incoming)` (P3-10) and upsert only when the merged result differs from local. Apply tombstone propagation: if `incoming.deletedAt != null && incoming.deletedHlc > local.deletedHlc`, set `deletedAt` and `syncStatus = SYNCED`. Extend the **push handler**: call `bookmarkDao.getPending()` (entities with `syncStatus = PENDING`) and include them in the `POST /api/sync/changes` body as `BookmarkSyncRecord` objects (all HLC fields populated). On successful push response, call `bookmarkDao.markSynced(acceptedBookmarkIds)` for IDs present in `accepted_bookmark_ids`. For IDs present in `response.conflicts.bookmarks`, apply `BookmarkMerge.merge(local, conflicted)` and upsert the winner.
- **Inputs required**: P3-6 (BookmarkDao), P3-10 (BookmarkMerge), P3-13 (SyncRepositoryImpl)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/data/repository/impl/SyncRepositoryImpl.kt` (updated)
- **How to test**: `android/app/src/test/java/com/outlinegod/app/data/repository/SyncRepositoryBookmarkTest.kt` using `runTest`, in-memory Room DB (Robolectric), mocked Ktor client:
  1. `pull upserts new incoming bookmark into Room when no local counterpart` — return 1 bookmark in pull response with no local match; assert `bookmarkDao.getById(id) != null` after sync.
  2. `pull applies BookmarkMerge when local counterpart exists` — seed local bookmark with lower HLC on title; pull incoming with higher HLC on title; assert local title updated after sync.
  3. `pull propagates soft-delete when incoming deletedHlc is higher` — seed active local bookmark; pull tombstone with higher `deletedHlc`; assert `bookmarkDao.getById(id)?.deletedAt != null`.
  4. `push includes all PENDING bookmarks in request body` — seed 2 PENDING bookmarks; capture the POST /api/sync/changes body; assert both bookmark IDs appear in `bookmarks` array.
  5. `accepted_bookmark_ids are marked SYNCED` — mock push response with 2 accepted IDs; assert both entities have `syncStatus = SYNCED` after push.
  6. `conflicted bookmark applies BookmarkMerge and upserts winner` — mock push response with 1 conflicted bookmark (server wins on title); assert local entity title is updated.
- **Risk**: LOW — same pull/push wiring pattern as node and document sync

---

### P5-6: BacklinkViewModel

- **Task ID**: P5-6
- **Title**: Add BacklinkViewModel fetching remote backlinks for a given document
- **What to build**: Create `@HiltViewModel BacklinkViewModel` in `com.gmaingret.outlinergod.ui.viewmodel` implementing `ContainerHost<BacklinkUiState, Nothing>`. Define `BacklinkUiState` sealed class: `Hidden` (default), `Loading`, `Success(backlinks: List<BacklinkItem>)`, `Error(message: String)`. Define `BacklinkItem` data class in `com.gmaingret.outlinergod.data.model`: `sourceDocumentId: String, sourceDocumentTitle: String, referringNodeId: String, referringContent: String`. In `init {}`, read `settingsDao.get(userId)?.showBacklinkBadge` (P3-7); if `0`, reduce to `Hidden` immediately and return — the ViewModel does nothing further. Expose `fun loadBacklinks(documentId: String)` that reduces to `Loading`, then calls `GET /api/documents/{documentId}/backlinks` via `HttpClient` (P3-11), maps the JSON array to `List<BacklinkItem>`, and reduces to `Success`. On `IOException` or 4xx, reduces to `Error(message)`. Expose `fun setEnabled(enabled: Boolean)` that reduces to `Hidden` when `enabled == false` (called when the user toggles `show_backlink_badge` in settings at runtime).
- **Inputs required**: P3-7 (SettingsDao), P3-9 (HlcClock not needed here; SettingsDao is enough), P3-11 (HttpClient), P3-12 (AuthRepository for userId), P4-0 (Orbit MVI)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/data/model/BacklinkItem.kt`
  - `android/app/src/main/java/com/outlinegod/app/ui/viewmodel/BacklinkViewModel.kt`
- **How to test**: `android/app/src/test/java/com/outlinegod/app/ui/viewmodel/BacklinkViewModelTest.kt` using `runTest`, `StandardTestDispatcher`, mocked `HttpClient`, `SettingsDao`, `AuthRepository`:
  1. `loadBacklinks emits Loading then Success with mapped BacklinkItem list` — mock GET to return JSON array of 2 backlinks; call `loadBacklinks("doc-1")`; advance; assert state is `Success` with 2 items.
  2. `loadBacklinks with api 404 emits Error state` — mock GET to throw `ResponseException(404)`; assert state becomes `Error`.
  3. `setEnabled(false) transitions to Hidden regardless of current state` — set state to `Success(...)`; call `setEnabled(false)`; assert state is `Hidden`.
  4. `showBacklinkBadge=0 in settings causes Hidden state on init` — stub `settingsDao.get("u1")` with `showBacklinkBadge = 0`; construct VM; assert initial state is `Hidden` without any HTTP call.
  5. `Success with empty list remains Success(emptyList) not Hidden` — mock GET to return `[]`; call `loadBacklinks("doc-1")`; assert state is `Success(emptyList())`.
- **Risk**: LOW — simple single-endpoint fetch with four-state machine

---

### P5-7: BacklinkSection UI

- **Task ID**: P5-7
- **Title**: Add BacklinkSection composable to the bottom of NodeEditorScreen
- **What to build**: Create `BacklinkSection` composable in `com.gmaingret.outlinergod.ui.component` accepting `uiState: BacklinkUiState` and `onNavigateToDocument: (documentId: String) -> Unit`. Render nothing when `Hidden`; a centered 48dp-tall `CircularProgressIndicator` row when `Loading`; nothing when `Success(emptyList())`; when `Success` with items, render a collapsible section: a `ListItem` header with leading `Icon(Icons.Default.Link)`, headline "Linked from ${backlinks.size} document(s)", and a trailing `IconButton` toggling an `isExpanded: Boolean` remember state via a chevron icon (down/up). When `isExpanded`, render a `Column` of `BacklinkRow` items each showing `sourceDocumentTitle` in `MaterialTheme.typography.bodyMedium` bold + `referringContent` snippet in `MaterialTheme.typography.bodySmall` with dimmed color — tapping calls `onNavigateToDocument(sourceDocumentId)`. In `NodeEditorScreen` (P4-5), inject `backlinkViewModel: BacklinkViewModel = hiltViewModel()`, call `backlinkViewModel.loadBacklinks(documentId)` in a `LaunchedEffect(documentId)`, collect `backlinkViewModel.collectAsState()`, and add `BacklinkSection(uiState, onNavigateToDocument = { navController.navigate(AppRoutes.nodeEditor(it)) })` as the final item in the node `LazyColumn` wrapped in a `LazyColumn` `item {}` block.
- **Inputs required**: P5-6, P4-5 (NodeEditorScreen)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/component/BacklinkSection.kt`
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/NodeEditorScreen.kt` (updated)
- **How to test**: Manual acceptance criteria:
  - On opening a document that is referenced by two others, the "Linked from 2 document(s)" header appears below the last node
  - Tapping the header chevron expands to show two `BacklinkRow` items with document titles and snippets
  - Tapping a `BacklinkRow` navigates to the source document's NodeEditorScreen
  - When `show_backlink_badge` is disabled in settings the section is entirely absent
- **Risk**: LOW — collapsible composable with simple boolean remember state

---

### P5-8: Node-to-document conversion

- **Task ID**: P5-8
- **Title**: Wire "Convert to document" action in NodeEditorViewModel and context menu
- **What to build**: In `NodeEditorViewModel` (P4-5), add `fun convertNodeToDocument(nodeId: String)` that: (1) calls `POST /api/nodes/{nodeId}/convert-to-document` via `HttpClient`; (2) on success, deserializes the returned `DocumentEntity`, upserts it via `documentDao.upsert()` (P3-12); (3) soft-deletes the source node locally via `nodeDao.softDelete(nodeId, deletedAt = now, deletedHlc = hlcClock.generate(deviceId))` and removes it from the flat list in `UiState`; (4) posts `NodeEditorSideEffect.NavigateToDocument(newDocument.id)` and `NodeEditorSideEffect.ShowSnackbar(message = "Converted to document", actionLabel = "Undo", actionNodeId = newDocument.id)`. The Undo action calls `POST /api/documents/{docId}/convert-to-node` with the original document's `target_document_id`, `target_parent_id`, and a freshly generated `sort_order` — on success remove the temporary document and re-insert the node locally. In `NodeContextMenu` (P4-11), wire the "Convert to document" `DropdownMenuItem` to call `viewModel.convertNodeToDocument(selectedNodeId)`. In `NodeEditorScreen`, handle `NavigateToDocument` side effect by calling `navController.navigate(AppRoutes.nodeEditor(newDocumentId))`.
- **Inputs required**: P4-5 (NodeEditorViewModel), P4-11 (NodeContextMenu), P3-5 (NodeDao), P3-12 (DocumentDao / AuthRepository)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/viewmodel/NodeEditorViewModel.kt` (updated)
  - `android/app/src/main/java/com/outlinegod/app/ui/component/NodeContextMenu.kt` (updated)
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/NodeEditorScreen.kt` (updated)
- **How to test**: `android/app/src/test/java/com/outlinegod/app/ui/viewmodel/NodeEditorViewModelConvertTest.kt` using `runTest`, mocked `HttpClient`, `NodeDao`, `DocumentDao`, `HlcClock`:
  1. `convertNodeToDocument calls POST /api/nodes/{nodeId}/convert-to-document` — capture the Ktor request URL; assert it ends with `/convert-to-document`.
  2. `on success: node is removed from flat list in UiState.Success` — seed flat list with node "n1"; call convert; assert `UiState.Success.flatNodes` does not contain "n1".
  3. `on success: documentDao.upsert called with returned document` — mock API to return `{ document: { id: "new-doc" } }`; verify `documentDao.upsert(any())` called once.
  4. `on success: NavigateToDocument side effect emitted with new document id` — collect side effects; assert `NavigateToDocument("new-doc")` emitted.
  5. `on success: ShowSnackbar side effect emitted with Undo action` — assert `ShowSnackbar` emitted with non-null `actionLabel == "Undo"`.
  6. `api 409 error posts ShowSnackbar error side effect, flat list unchanged` — mock API to return 409; assert flat list still contains "n1" and `NavigateToDocument` not emitted.
- **Risk**: LOW — single POST + local state removal; undo path is straightforward (a second API call)

---

### P5-9: ExportViewModel

- **Task ID**: P5-9
- **Title**: Add ExportViewModel calling the per-document export endpoint
- **What to build**: Create `@HiltViewModel ExportViewModel` in `com.gmaingret.outlinergod.ui.viewmodel` implementing `ContainerHost<ExportUiState, ExportSideEffect>`. Define `ExportUiState` sealed class: `Idle`, `Loading(format: ExportFormat)`, `Error(message: String)`; define `enum class ExportFormat(val apiValue: String, val mimeType: String, val fileExtension: String)` with entries `MARKDOWN("markdown", "text/markdown", "md")`, `OPML("opml", "text/xml", "opml")`, `TEXT("text", "text/plain", "txt")`. Define `ExportSideEffect` sealed class: `ShareContent(content: String, mimeType: String, filename: String)`. Expose `fun export(documentId: String, format: ExportFormat)` that: reduces to `Loading(format)`; calls `GET /api/documents/{documentId}/export?format={format.apiValue}` via `HttpClient` receiving the response as a `String` body; on success posts `ShareContent(body, format.mimeType, "export-{documentId}.{format.fileExtension}")` and reduces to `Idle`; on error reduces to `Error(e.message ?: "Export failed")`.
- **Inputs required**: P3-11 (HttpClient), P4-0 (Orbit MVI)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/viewmodel/ExportViewModel.kt`
- **How to test**: `android/app/src/test/java/com/outlinegod/app/ui/viewmodel/ExportViewModelTest.kt` using `runTest`, `StandardTestDispatcher`, mocked `HttpClient`:
  1. `export(MARKDOWN) calls GET /api/documents/{id}/export?format=markdown` — capture request URL; assert query param `format=markdown` is present.
  2. `on success: ShareContent side effect emitted with text/markdown MIME type` — mock GET to return "# title"; collect side effects; assert `ShareContent(mimeType = "text/markdown")`.
  3. `on success: Loading state transitions back to Idle after side effect` — assert state is `Idle` after export completes.
  4. `api IOException emits Error state` — mock GET to throw `IOException`; assert state is `Error`.
  5. `export(OPML) uses text/xml MIME type and .opml extension` — assert `ShareContent.mimeType == "text/xml"` and `filename.endsWith(".opml")`.
  6. `export(TEXT) uses text/plain MIME type` — assert `ShareContent.mimeType == "text/plain"`.
- **Risk**: LOW — single GET with string body; three format variants differ only in query param and MIME type

---

### P5-10: ExportBottomSheet UI

- **Task ID**: P5-10
- **Title**: Build ExportBottomSheet composable and wire Android ShareSheet intent
- **What to build**: Create `ExportBottomSheet` in `com.gmaingret.outlinergod.ui.component` accepting `documentId: String`, `viewModel: ExportViewModel = hiltViewModel()`, and `onDismiss: () -> Unit`. Render a `ModalBottomSheet(onDismissRequest = onDismiss)` containing a `Column`: a drag handle, a title `Text("Export document")` in `titleMedium`, and three `ListItem` rows — "Export as Markdown" (file icon), "Export as OPML" (code icon), "Export as Plain Text" (text icon). When state is `Loading`, overlay a `LinearProgressIndicator` at the top of the sheet and disable the three rows. Collect `ExportSideEffect.ShareContent` via `viewModel.collectSideEffect {}` and inside that block create `Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_TEXT, content); type = mimeType }`, then call `context.startActivity(Intent.createChooser(intent, "Share via"))`. In `NodeEditorScreen` (P4-5), add an "Export" `DropdownMenuItem` to the `TopAppBar` overflow `DropdownMenu`; tapping it sets `var showExportSheet by remember { mutableStateOf(false) }`; when `showExportSheet` is true, render `ExportBottomSheet(documentId = documentId, onDismiss = { showExportSheet = false })`.
- **Inputs required**: P5-9, P4-5 (NodeEditorScreen TopAppBar overflow menu)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/component/ExportBottomSheet.kt`
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/NodeEditorScreen.kt` (updated)
- **How to test**: Manual acceptance criteria:
  - TopAppBar overflow menu in NodeEditorScreen contains an "Export" option
  - Tapping Export opens the `ModalBottomSheet` with three format options
  - Tapping "Export as Markdown" shows `LinearProgressIndicator` then opens the Android ShareSheet with the document's content
  - Dismissing the sheet (swipe down or back press) closes it without errors
  - OPML export opens ShareSheet with MIME type `text/xml`
- **Risk**: LOW — standard `ModalBottomSheet` + `ACTION_SEND` intent; no custom View integration

---

### P5-11: Image upload

- **Task ID**: P5-11
- **Title**: Implement photo picker, Compressor, multipart upload, and cursor insertion
- **What to build**: In `NodeEditorViewModel` (P4-5), add `fun uploadImage(context: Context, uri: Uri)` that: (1) resolves the file size via `context.contentResolver.openFileDescriptor(uri, "r")?.statSize`; if `> 10_000_000L`, post `NodeEditorSideEffect.ShowError("File exceeds 10 MB limit")` and return; (2) convert `Uri` to a temp `File` by reading bytes from `contentResolver.openInputStream(uri)`; (3) if file size `> 1_000_000L`, call `Compressor.compress(context, file) { size(1_000_000); format(Bitmap.CompressFormat.WEBP_LOSSY); quality(80) }` — otherwise use the original file; (4) build a `MultiPartFormDataContent` body with part `"file"` (the compressed bytes with filename) and optional part `"node_id"` (the currently focused node's ID); (5) call `POST /api/files` via `HttpClient`; (6) on success, read `response.url`, then call `textFieldState.edit { insert(selection.start, "![]($url)") }` on the currently focused node's `TextFieldState`; (7) on error post `ShowError("Upload failed: ${e.message}")`. In `NodeEditorScreen`, add an attachment `IconButton` (paperclip icon) to the mobile toolbar row (P4-6); wire it to `rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { viewModel.uploadImage(context, it) } }`; call `launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))` on tap.
- **Inputs required**: P4-5 (NodeEditorViewModel + TextFieldState), P4-6 (NodeEditorScreen mobile toolbar), P3-11 (HttpClient)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/viewmodel/NodeEditorViewModel.kt` (updated)
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/NodeEditorScreen.kt` (updated)
- **How to test**: `android/app/src/test/java/com/outlinegod/app/ui/viewmodel/NodeEditorImageUploadTest.kt` using `runTest`, mocked `Compressor` (via Mockk `mockkStatic`), mocked `HttpClient`, mocked `ContentResolver`:
  1. `uploadImage with file > 10MB posts ShowError without calling api` — stub statSize to `11_000_000L`; assert `ShowError` emitted and `coVerify(exactly = 0) { httpClient.post(any()) }`.
  2. `uploadImage with file <= 1MB skips Compressor and calls POST /api/files directly` — stub statSize to `800_000L`; verify `Compressor.compress` never called; verify `httpClient.post(any())` called once.
  3. `uploadImage with 3MB file calls Compressor before POST` — stub statSize to `3_000_000L`; verify `Compressor.compress(any(), any(), any())` called before httpClient.
  4. `on upload success inserts markdown image syntax at cursor position` — mock POST to return `{ url: "/api/files/uuid.jpg" }`; assert `TextFieldState` value contains `"![]( /api/files/uuid.jpg)"` at cursor position.
  5. `api 413 response posts ShowError side effect` — mock httpClient to return 413; assert `ShowError("Upload failed:...")` emitted.
- **Risk**: MEDIUM — multipart Ktor body construction combined with `TextFieldState.edit {}` cursor mutation; Compressor static mocking in tests requires `mockkStatic`

---

### P5-12: SettingsViewModel HLC sync

- **Task ID**: P5-12
- **Title**: Wire SettingsDao into SettingsViewModel with per-field HLC-aware push/pull
- **What to build**: In `SettingsViewModel` (P4-14), inject `settingsDao: SettingsDao` (P3-7) and `hlcClock: HlcClock` (P3-9). Replace direct `PUT /api/settings` API calls for user preference changes with local-first writes: on each change (theme, density, `showGuideLines`, `showBacklinkBadge`), generate a new HLC via `hlcClock.generate(deviceId)`, update the companion `_hlc` field on `SettingsEntity`, set `syncStatus = PENDING`, and call `settingsDao.upsert(entity)`. On cold start, load initial state from `settingsDao.get(userId)` (falling back to defaults if null) instead of calling `GET /api/settings`. In `SyncRepositoryImpl` (P3-13), extend the **pull handler**: for each field in the incoming `SettingsSyncRecord`, compare `incoming.{field}_hlc` vs `local.{field}_hlc` lexicographically; update `SettingsEntity.{field}` only when `incoming_hlc > local_hlc`. Extend the **push handler**: if `settingsDao.getPending(userId) != null`, include it as `settings` in `POST /api/sync/changes`; on accepted response call `settingsDao.markSynced(userId)`.
- **Inputs required**: P3-7 (SettingsDao), P3-9 (HlcClock), P3-13 (SyncRepositoryImpl), P4-14 (SettingsViewModel)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/viewmodel/SettingsViewModel.kt` (updated)
  - `android/app/src/main/java/com/outlinegod/app/data/repository/impl/SyncRepositoryImpl.kt` (updated)
- **How to test**: `android/app/src/test/java/com/outlinegod/app/ui/viewmodel/SettingsViewModelSyncTest.kt` using `runTest`, in-memory Room (Robolectric), mocked `HlcClock`, mocked `SyncRepositoryImpl`:
  1. `changing theme updates SettingsEntity themeHlc to a value greater than the previous hlc` — seed entity with `themeHlc = "low"`; call `setTheme("light")`; assert `settingsDao.get(userId)!!.themeHlc > "low"`.
  2. `cold start loads settings from Room not from API` — seed Room with `theme = "light"`; construct SettingsViewModel; assert `uiState.theme == "light"` without any HTTP call.
  3. `pull with higher incoming themeHlc overwrites local theme` — seed local with `theme = "dark", themeHlc = "0001"`; call pull with `theme = "light", themeHlc = "0002"`; assert `settingsDao.get(userId)!!.theme == "light"`.
  4. `pull with lower incoming themeHlc keeps local theme` — seed local with `themeHlc = "0005"`; pull with `themeHlc = "0003"`; assert local theme unchanged.
  5. `pending settings are included in sync push payload` — call `setTheme("light")` (marks PENDING); trigger push; capture POST body; assert `settings.theme == "light"` in payload.
- **Risk**: LOW — same per-field HLC comparison logic already validated in P3-10 merge tests; settings has only four fields

---

### P5-13: Full LWW conflict resolution in SyncWorker

- **Task ID**: P5-13
- **Title**: Upgrade SyncWorker from overwrite strategy to per-field LWW merge via NodeMerge / DocumentMerge / BookmarkMerge
- **What to build**: In `SyncWorker` (P4-16), replace the Phase 4 simple-overwrite strategy for each record type with full LWW merge. For **nodes**: for each `NodeSyncRecord` in the pull response, call `nodeDao.getById(record.id)` — if null, map to `NodeEntity` and upsert directly; if present, call `NodeMerge.merge(local, incoming)` (P3-10) to produce a merged `NodeEntity`; call `nodeDao.upsert(merged)` only when `merged != local` (skip no-op writes). Apply deletion cascades: if `incoming.deletedAt != null && incoming.deletedHlc > (local.deletedHlc ?: "")`, call `nodeDao.softDeleteSubtree(record.id)` which recursively soft-deletes all descendants. For **documents**: same pattern with `DocumentMerge.merge()` and `documentDao.upsert()`; folder soft-deletes cascade via `documentDao.softDeleteSubtree(id)`. For **bookmarks**: same pattern with `BookmarkMerge.merge()` and `bookmarkDao.upsert()`. After pull, run the push phase (unchanged from P4-16). Log `"SyncWorker merged: ${mergedCount} updated / ${skippedCount} skipped / ${deletedCount} deleted"` via `Log.d("SyncWorker", ...)`.
- **Inputs required**: P3-10 (NodeMerge, DocumentMerge, BookmarkMerge), P3-4 (NodeDao), P3-5 (DocumentDao), P3-6 (BookmarkDao), P4-16 (SyncWorker)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/sync/SyncWorker.kt` (updated)
- **How to test**: `android/app/src/test/java/com/outlinegod/app/sync/SyncWorkerLwwTest.kt` using `runTest`, in-memory Room (Robolectric), mocked Ktor client configured to return specific payloads:
  1. `incoming node with no local counterpart is upserted directly` — return 1 new node in pull; assert `nodeDao.getById(id) != null` after sync.
  2. `incoming node with lower HLC on all fields: local wins, nodeDao.upsert not called` — seed local node with `contentHlc = "high"`; pull incoming with `contentHlc = "low"`; assert `nodeDao.getById(id)!!.content` unchanged and upsert not called (verify via spy).
  3. `incoming node with higher HLC on content only: merged result has incoming content, local values for all other fields` — seed local; pull incoming with only `contentHlc` higher; assert merged entity's content = incoming, all other fields = local.
  4. `incoming node with deletedAt set and higher deletedHlc: subtree soft-deleted` — seed local node with 2 children; pull tombstone with `deletedHlc` higher than local; assert all 3 nodes have `deletedAt != null` after sync.
  5. `node already soft-deleted locally: incoming edit with lower deletedHlc does not resurrect it` — seed deleted local node; pull incoming with `deletedHlc` lower than local; assert node remains deleted.
  6. `merged entity equal to local: upsert not called (no redundant write)` — pull incoming identical to local in all fields and HLCs; assert `nodeDao.upsert` not called.
  7. `document merge follows same LWW rules as node merge` — seed local document with high titleHlc; pull with lower titleHlc; assert document title unchanged.
- **Risk**: HIGH — merge correctness across all field/HLC combinations is critical; incorrect lexicographic HLC comparison or field-mapping bug causes silent data loss or phantom resurrections on every sync

---

### P5-14: OfflineRepository and offline indicator

- **Task ID**: P5-14
- **Title**: Implement OfflineRepository with NetworkCallback Flow and persistent offline Banner
- **What to build**: Create `OfflineRepository` interface in `com.gmaingret.outlinergod.data.repository` with a single property `val isOffline: Flow<Boolean>`. Implement `OfflineRepositoryImpl` in `com.gmaingret.outlinergod.data.repository.impl`: inject `@ApplicationContext context: Context`; in `init {}` create a `MutableStateFlow<Boolean>` initialized to `connectivityManager.activeNetwork == null`; register a `ConnectivityManager.NetworkCallback` via `connectivityManager.registerDefaultNetworkCallback(callback)` where `override fun onAvailable` emits `false` and `override fun onLost` emits `true`; expose the `StateFlow` as `isOffline`. Register the binding in `NetworkModule` (P3-11) as `@Provides @Singleton`. Create `OfflineBanner` composable in `com.gmaingret.outlinergod.ui.component`: a full-width `Surface` with `MaterialTheme.colorScheme.errorContainer` background, height 40dp, centered `Text("You're offline — changes will sync when reconnected", style = MaterialTheme.typography.labelMedium)`. In `DocumentListScreen` (P4-3/P4-4) and `NodeEditorScreen` (P4-5), inject `offlineRepository: OfflineRepository`; collect `offlineRepository.isOffline.collectAsStateWithLifecycle()`; render `AnimatedVisibility(visible = isOffline) { OfflineBanner() }` pinned immediately below the `TopAppBar` (above the main content `Scaffold` body).
- **Inputs required**: P3-11 (NetworkModule), P4-3 (DocumentListScreen), P4-5 (NodeEditorScreen)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/data/repository/OfflineRepository.kt`
  - `android/app/src/main/java/com/outlinegod/app/data/repository/impl/OfflineRepositoryImpl.kt`
  - `android/app/src/main/java/com/outlinegod/app/ui/component/OfflineBanner.kt`
  - `android/app/src/main/java/com/outlinegod/app/di/NetworkModule.kt` (updated)
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/DocumentListScreen.kt` (updated)
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/NodeEditorScreen.kt` (updated)
- **How to test**: `android/app/src/test/java/com/outlinegod/app/data/repository/OfflineRepositoryTest.kt` using `runTest`, mocked `ConnectivityManager` and `NetworkCallback` captured via `argumentCaptor`:
  1. `isOffline emits true when NetworkCallback.onLost fires` — capture callback; invoke `callback.onLost(fakeNetwork)`; collect `isOffline`; assert emitted `true`.
  2. `isOffline emits false when NetworkCallback.onAvailable fires` — start offline; invoke `callback.onAvailable(fakeNetwork)`; assert `false` emitted.
  3. `initial value is true when connectivityManager.activeNetwork is null` — stub `activeNetwork = null`; assert `isOffline.value == true` at construction.
  4. `initial value is false when activeNetwork is non-null` — stub `activeNetwork = fakeNetwork`; assert `isOffline.value == false`.
- **Risk**: LOW — standard `ConnectivityManager.NetworkCallback` pattern; `collectAsStateWithLifecycle` is well-established in Compose

---

### P5-15: AppNavHost final wiring

- **Task ID**: P5-15
- **Title**: Add bottom NavigationBar with three tabs, settings nav, and deep-link support
- **What to build**: In `AppNavHost` (P3-14), wrap the `NavHost` in a `Scaffold` whose `bottomBar` slot contains a `NavigationBar` composable with three `NavigationBarItem` entries: Documents (`Icons.Default.Home`, route `AppRoutes.DOCUMENT_LIST`), Search (`Icons.Default.Search`, route `AppRoutes.SEARCH`), Bookmarks (`Icons.Default.Bookmark`, route `AppRoutes.BOOKMARK_LIST`). Derive `selectedRoute` from `navController.currentBackStackEntryAsState().value?.destination?.route`. Each tab item calls `navController.navigate(route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }` to preserve independent back stacks per tab. Add `AppRoutes.SEARCH` and `AppRoutes.BOOKMARK_LIST` as new constants in `AppRoutes.kt` and register `SearchScreen` and `BookmarkListScreen` as `composable` destinations in the `NavHost`. Add a settings `IconButton` (`Icons.Default.Settings`) to the `TopAppBar` `actions` block of `DocumentListScreen`, `SearchScreen`, and `BookmarkListScreen`, navigating to `AppRoutes.SETTINGS`. Register a deep-link on the `NodeEditorScreen` destination: `deepLinks = listOf(navDeepLink { uriPattern = "outlinegod://document/{documentId}" })` and add the corresponding `<intent-filter>` with `android:scheme="outlinegod"` to `AndroidManifest.xml`.
- **Inputs required**: P3-14 (AppNavHost), P5-2 (SearchScreen), P5-4 (BookmarkListScreen), P4-14 (SettingsScreen), P4-5 (NodeEditorScreen)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/navigation/AppNavHost.kt` (updated)
  - `android/app/src/main/java/com/outlinegod/app/ui/navigation/AppRoutes.kt` (updated)
  - `android/app/src/main/AndroidManifest.xml` (updated with deep-link intent-filter)
- **How to test**: `android/app/src/test/java/com/outlinegod/app/ui/navigation/AppNavHostRoutesTest.kt` using plain JUnit4 + `TestNavHostController` (no Compose UI framework required):
  1. `AppRoutes_SEARCH_equals_search` — assert `AppRoutes.SEARCH == "search"`.
  2. `AppRoutes_BOOKMARK_LIST_equals_bookmark_list` — assert `AppRoutes.BOOKMARK_LIST == "bookmark_list"`.
  3. `deep link pattern contains outlinegod scheme` — assert the `uriPattern` string registered for NODE_EDITOR contains `"outlinegod://document/"`.
  4. `tab navigation NavOptions has saveState=true` — instantiate `NavOptions.Builder().setPopUpTo(0, false, true).setLaunchSingleTop(true).setRestoreState(true).build()`; assert `restoreState == true` (validates the pattern used in AppNavHost is correct).
- **Risk**: MEDIUM — Compose Navigation multi-tab back-stack state restoration requires precise `NavOptions` (`saveState = true` + `restoreState = true`); omitting either causes tab state loss on re-selection

---

## Phase 5 Exit Criteria

- `./gradlew test` exits 0 with zero failures across all 15 `*Test.kt` files produced in this phase
- `./gradlew assembleDebug` exits 0
- All 15 task test files exist with all named test cases green
- Manual acceptance criteria verified for UI tasks P5-2, P5-4, P5-7, P5-10
- No `@Ignore`-annotated test without a written inline comment explaining why
- SyncWorker upgrade (P5-13) verified by running all 7 LwwTest cases passing before marking complete
