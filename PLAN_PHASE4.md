## Phase 4 — Android Core Features

**Goal**: Every user-facing screen and interaction built on top of the Phase 3 infrastructure. By the end of this phase the app is fully functional end-to-end: a user can sign in, create documents and nodes, edit content with inline Markdown formatting, reorder via drag-and-drop, and sync to the backend. No placeholder screens remain.

**Prerequisite**: All Phase 3 tasks must have passing tests before beginning Phase 4. Phase 3 exit criteria: `./gradlew test` exits 0 (zero failures), `./gradlew assembleDebug` exits 0, `./gradlew kspDebugKotlin` exits 0.

Tasks run in task ID order. Each task is scoped to approximately 1–2 hours.

---

### P4-0: Pre-phase dependency additions

- **Task ID**: P4-0
- **Title**: Add Orbit MVI and fix NODE_EDITOR route param to documentId
- **What to build**:
  1. In `android/gradle/libs.versions.toml` add:
     `orbit-core = "10.0.0"` and `orbit-viewmodel = "10.0.0"`
     In `android/app/build.gradle.kts` add:
     `implementation(libs.orbit.core)` and `implementation(libs.orbit.viewmodel)`
  2. In `AppRoutes.kt`, change:
     `const val NODE_EDITOR = "node_editor/{nodeId}"`
     to:
     `const val NODE_EDITOR = "node_editor/{documentId}"`
  3. In `NodeEditorScreen.kt` placeholder, change the parameter from
     `nodeId: String` to `documentId: String`.
- **Inputs required**: P3-1, P3-14
- **Output artifact**:
  - `android/gradle/libs.versions.toml` (updated)
  - `android/app/build.gradle.kts` (updated)
  - `android/app/src/main/java/com/outlinegod/app/ui/navigation/AppRoutes.kt` (updated)
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/NodeEditorScreen.kt` (updated)
- **How to test**: `./gradlew kspDebugKotlin` exits 0; existing P3-14 route tests still pass.
- **Risk**: LOW — additive dependency + rename only.

---

### P4-1: LoginViewModel

- **Task ID**: P4-1
- **Title**: LoginViewModel with Orbit MVI state and AuthRepository delegation
- **What to build**: Create `android/app/src/main/java/com/outlinegod/app/ui/screen/login/LoginViewModel.kt`. Annotate `@HiltViewModel` and extend `ViewModel()` implementing `ContainerHost<LoginUiState, LoginSideEffect>` from `org.orbit-mvi:orbit-core:10.0.0`. Define `override val container = container<LoginUiState, LoginSideEffect>(LoginUiState.Idle)`. In the same file define `sealed class LoginUiState { data object Idle : LoginUiState(); data object Loading : LoginUiState(); data object Success : LoginUiState(); data class Error(val message: String) : LoginUiState() }` and `sealed class LoginSideEffect { data object NavigateToDocumentList : LoginSideEffect() }`. Inject `private val authRepository: AuthRepository` via `@Inject constructor`. Implement `fun handleGoogleSignIn(idToken: String)` using Orbit's `intent { reduce { LoginUiState.Loading }; authRepository.googleSignIn(idToken).fold(onSuccess = { reduce { LoginUiState.Success }; postSideEffect(LoginSideEffect.NavigateToDocumentList) }, onFailure = { reduce { LoginUiState.Error(it.message ?: "Sign-in failed") } }) }`. Empty `idToken` is treated as a sign-in cancellation and produces `LoginUiState.Idle` (no error shown on cancel).
- **Inputs required**: P3-12 (AuthRepository interface); P3-1 (Orbit MVI 10.0.0 declared in `libs.versions.toml` as `orbit-core = "10.0.0"`)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/login/LoginViewModel.kt`
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/ui/screen/login/LoginViewModelTest.kt`
  - Test setup: `runTest` with `StandardTestDispatcher`; define `MainDispatcherRule` inner class using `Dispatchers.setMain(StandardTestDispatcher())` in `@Before` and `Dispatchers.resetMain()` in `@After`; use MockK `mockk<AuthRepository>()`.
  - **Test cases**:
    1. `initialState_isIdle` — construct `LoginViewModel(mockAuthRepository)`; assert `viewModel.container.stateFlow.value is LoginUiState.Idle`.
    2. `handleGoogleSignIn_setsLoadingState_immediately` — corotuine: call `handleGoogleSignIn("token")`; collect states via `container.stateFlow.take(2).toList()`; assert the first collected state after Idle is `LoginUiState.Loading`.
    3. `handleGoogleSignIn_onSuccess_transitionsToSuccess_andPostsSideEffect` — `coEvery { authRepository.googleSignIn("token") } returns Result.success(fakeAuthResponse())`; call `handleGoogleSignIn("token")`; advance coroutines; assert `container.stateFlow.value is LoginUiState.Success` and `container.sideEffectFlow` emits `NavigateToDocumentList`.
    4. `handleGoogleSignIn_onFailure_setsErrorState` — `coEvery { authRepository.googleSignIn(any()) } returns Result.failure(IOException("net error"))`; call `handleGoogleSignIn("token")`; advance coroutines; assert `container.stateFlow.value is LoginUiState.Error` and the message is non-blank.
    5. `handleGoogleSignIn_onFailure_doesNotPostNavigateSideEffect` — mock failure; collect all side effects via `container.sideEffectFlow.take(0).toList()` (use `timeout` via `withTimeoutOrNull`); assert `NavigateToDocumentList` is never emitted.
    6. `emptyIdToken_producesIdle_notError` — call `handleGoogleSignIn("")`; advance coroutines; assert state returns to `LoginUiState.Idle`.
  - **Pass criteria**: `./gradlew test` exits 0, all 6 cases green.
- **Risk**: LOW — thin delegation to AuthRepository. Orbit's `intent` block is a simple coroutine; the only failure mode is forgetting `Dispatchers.Main` replacement in tests, which the `MainDispatcherRule` eliminates.

---

### P4-2: LoginScreen UI

- **Task ID**: P4-2
- **Title**: LoginScreen composable with Credential Manager launch and navigation callback
- **What to build**: Replace the placeholder `android/app/src/main/java/com/outlinegod/app/ui/screen/LoginScreen.kt` by deleting it and creating `android/app/src/main/java/com/outlinegod/app/ui/screen/login/LoginScreen.kt`. Define `@Composable fun LoginScreen(viewModel: LoginViewModel = hiltViewModel(), onNavigateToDocumentList: () -> Unit)`. Inside, collect state via `viewModel.container.stateFlow.collectAsState()` and collect side effects via `LaunchedEffect(Unit) { viewModel.container.sideEffectFlow.collect { if (it is LoginSideEffect.NavigateToDocumentList) onNavigateToDocumentList() } }`. In a separate `LaunchedEffect(Unit)`, build `GetGoogleIdOption` with `serverClientId = BuildConfig.GOOGLE_CLIENT_ID`, wrap in `try { val result = credentialManager.getCredential(context, request); val idToken = GoogleIdTokenCredential.createFrom(result.credential).idToken; viewModel.handleGoogleSignIn(idToken) } catch (e: GetCredentialException) { viewModel.handleGoogleSignIn("") }`. When state is `Loading` render a centered `CircularProgressIndicator`; when state is `Error` show a `Snackbar` with `SnackbarHostState.showSnackbar(state.message)`. Add `buildConfigField("String", "GOOGLE_CLIENT_ID", "\"\"")` to `app/build.gradle.kts` `defaultConfig` block. **Google Cloud Console prerequisite**: the Android OAuth credential must be registered with package name `com.outlinegod.app` and SHA-1 fingerprint `D8:B0:6D:19:92:71:D4:DA:60:EF:A7:0A:93:A4:5C:29:B7:A3:D5:C4`. The resulting `GOOGLE_CLIENT_ID` (web client ID, not the Android client ID) must be set in `app/build.gradle.kts` `buildConfigField` and in the backend `.env` as `GOOGLE_CLIENT_ID`. Update `android/app/src/main/java/com/outlinegod/app/ui/navigation/AppNavHost.kt`: change the `login` composable entry to import `LoginScreen` from the new package and pass `onNavigateToDocumentList = { navController.navigate(AppRoutes.DOCUMENT_LIST) { popUpTo(AppRoutes.LOGIN) { inclusive = true } } }`.
- **Inputs required**: P4-1 (LoginViewModel, LoginUiState, LoginSideEffect); P3-1 (`credentials-play-services-auth:1.3.0` and `google-id:1.1.1` declared in version catalog); P3-14 (AppNavHost.kt to update)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/login/LoginScreen.kt`
  - `android/app/src/main/java/com/outlinegod/app/ui/navigation/AppNavHost.kt` (updated)
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/ui/screen/login/LoginScreenTest.kt`
  - No Compose UI tests in this phase. Tests verify structural correctness.
  - **Test cases**:
    1. `loginScreenKt_classExists_inLoginPackage` — `Class.forName("com.outlinegod.app.ui.screen.login.LoginScreenKt")`; assert non-null and no `ClassNotFoundException`.
    2. `buildConfig_googleClientId_fieldIsString` — assert `BuildConfig::class.java.getField("GOOGLE_CLIENT_ID").type == String::class.java`.
    3. `appNavHost_loginRoute_remainsStartDestination` — assert `AppRoutes.LOGIN == "login"` (regression guard after AppNavHost.kt is edited).
    4. `appRoutes_documentList_isCorrect` — assert `AppRoutes.DOCUMENT_LIST == "document_list"` (navigation target after sign-in).
  - **Pass criteria**: `./gradlew test` exits 0, all 4 cases green. `./gradlew assembleDebug` exits 0.
- **Risk**: LOW — Credential Manager is Android-framework code untestable without an emulator; all business logic lives in LoginViewModel (P4-1). The `LaunchedEffect` sign-in path is verified by manual acceptance: the Google sign-in bottom sheet appears on launch.

---

### P4-3: DocumentListViewModel

- **Task ID**: P4-3
- **Title**: DocumentListViewModel with Room observation, document CRUD, and folder toggle
- **What to build**: Create `android/app/src/main/java/com/outlinegod/app/ui/screen/documentlist/DocumentListViewModel.kt` annotated `@HiltViewModel` implementing `ContainerHost<DocumentListUiState, DocumentListSideEffect>`. Inject `private val documentDao: DocumentDao`, `private val authRepository: AuthRepository`, `private val hlcClock: HlcClock`, `@Named("baseUrl") private val baseUrl: String`, and `private val httpClient: HttpClient`. Define `sealed class DocumentListUiState { data object Loading : DocumentListUiState(); data class Success(val items: List<DocumentEntity>, val syncStatus: SyncStatus = SyncStatus.Idle) : DocumentListUiState(); data class Error(val message: String) : DocumentListUiState() }` and `enum class SyncStatus { Idle, Syncing, Error }` and `sealed class DocumentListSideEffect { data class ShowError(val message: String) : DocumentListSideEffect() }`. In `init`, launch: collect `authRepository.getAccessToken().filterNotNull().take(1).first()` to obtain `userId`; then `documentDao.getAllDocuments(userId).collect { reduce { DocumentListUiState.Success(it) } }`. Implement `fun createDocument(title: String, type: String, parentId: String?, sortOrder: String)` — use Orbit `intent { reduce to Loading; POST to /api/documents via httpClient; on success insertDocument locally; on failure postSideEffect ShowError }`. Implement `fun renameDocument(id: String, title: String)` — fetch entity via `documentDao.getDocumentByIdSync(id)`, update locally with `copy(title = title, updatedAt = now, titleHlc = hlcClock.generate(deviceId))`, call `documentDao.updateDocument()`, then `PATCH /api/documents/$id` in background. Implement `fun deleteDocument(id: String)` — call `documentDao.softDeleteDocument(id, now, hlcClock.generate(deviceId), now)` immediately, then `DELETE /api/documents/$id` in background. Implement `fun toggleFolderCollapse(id: String)` — fetch entity, `copy(collapsed = if (entity.collapsed == 0) 1 else 0)`, `documentDao.updateDocument()`.
- **Inputs required**: P3-5 (DocumentDao); P3-3 (DocumentEntity); P3-12 (AuthRepository for userId and deviceId); P3-9 (HlcClock); P3-11 (HttpClient and baseUrl); P3-2 (Hilt wiring)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/documentlist/DocumentListViewModel.kt`
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/ui/screen/documentlist/DocumentListViewModelTest.kt`
  - Test setup: `runTest` with `StandardTestDispatcher`, `MainDispatcherRule`; MockK mocks for `DocumentDao`, `AuthRepository`, `HlcClock`, `HttpClient`; stub `authRepository.getAccessToken()` to emit `"user-1"`.
  - **Test cases**:
    1. `initialLoad_emitsLoadingThenSuccess` — stub `documentDao.getAllDocuments("user-1")` to return `flowOf(listOf(fakeDocument()))`; construct VM; advance; assert state is `DocumentListUiState.Success` with 1 item.
    2. `createDocument_insertsDocumentLocally_onSuccess` — mock httpClient to return 201 with `DocumentEntity` JSON; call `createDocument("Doc", "document", null, "V")`; verify `documentDao.insertDocument(any())` was called.
    3. `createDocument_postsShowError_onFailure` — mock httpClient to throw `IOException`; call `createDocument(...)`; collect side effects; assert `ShowError` is emitted.
    4. `deleteDocument_softDeletesImmediately_beforeNetworkCall` — call `deleteDocument("doc-1")`; verify `documentDao.softDeleteDocument(eq("doc-1"), any(), any(), any())` was called before any HTTP interaction.
    5. `toggleFolderCollapse_togglesCollapsedBit` — stub `documentDao.getDocumentByIdSync("f1")` to return `fakeFolder(collapsed = 0)`; call `toggleFolderCollapse("f1")`; capture `documentDao.updateDocument(capture(slot))`; assert `slot.captured.collapsed == 1`.
    6. `renameDocument_updatesHlcTimestamp` — stub getDocumentByIdSync; call `renameDocument("doc-1", "New Name")`; capture updateDocument call; assert `slot.captured.titleHlc` is non-blank and matches `Regex("^[0-9a-f]{16}-[0-9a-f]{4}-.*")`.
  - **Pass criteria**: `./gradlew test` exits 0, all 6 cases green.
- **Risk**: LOW — the Room Flow observation pattern is well-established. Risk is `userId` arriving asynchronously before DAOs are queried; the `filterNotNull().take(1)` chain ensures the Flow only starts once the userId is available.

---

### P4-4: DocumentListViewModel sync trigger

- **Task ID**: P4-4
- **Title**: DocumentListViewModel pull/push on resume with SyncStatus indicator
- **What to build**: Extend `DocumentListViewModel` from P4-3. Inject `private val syncRepository: SyncRepository` and `private val nodeDao: NodeDao` and `private val bookmarkDao: BookmarkDao` and `private val settingsDao: SettingsDao`. Add `private val lastSyncHlcKey = stringPreferencesKey("last_sync_hlc")` backed by `DataStore<Preferences>` (inject via `private val dataStore: DataStore<Preferences>`). Implement `fun triggerSync()` as an Orbit `intent`: (1) if current state is `Success`, `reduce { state.copy(syncStatus = SyncStatus.Syncing) }`; (2) read `deviceId` from `authRepository.getDeviceId().first()`; (3) read `lastSyncHlc` from DataStore (default `"0"`); (4) call `syncRepository.pull(since = lastSyncHlc, deviceId = deviceId)` — on success apply pulled nodes/documents via `nodeDao.upsertNodes()` and `documentDao.upsertDocuments()` (LWW merge logic is deferred: in Phase 4, upsert overwrites; full LWW merge is WorkManager's responsibility in P4-16); (5) build push payload from `documentDao.getPendingChanges(userId, lastSyncHlc, deviceId)`; (6) call `syncRepository.push(payload)` — apply conflict documents via `documentDao.upsertDocuments(conflicts.documents)`; (7) update `lastSyncHlc` in DataStore to `response.serverHlc`; (8) `reduce { state.copy(syncStatus = SyncStatus.Idle) }`; on any exception `reduce { state.copy(syncStatus = SyncStatus.Error) }`. Add `fun onScreenResumed()` that calls `triggerSync()` once via a `viewModelScope.launch`.
- **Inputs required**: P4-3 (DocumentListViewModel base); P3-13 (SyncRepository); P3-12 (AuthRepository.getDeviceId()); P3-4 (NodeDao); P3-5 (DocumentDao)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/documentlist/DocumentListViewModel.kt` (updated)
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/ui/screen/documentlist/DocumentListSyncTest.kt`
  - Test setup: same MockK + `runTest` pattern as P4-3; additionally mock `SyncRepository` and use a `TestDataStore` backed by `PreferenceDataStoreFactory.create` with a temp directory.
  - **Test cases**:
    1. `triggerSync_setsSyncingStatus_whileRunning` — delay pull response via `coEvery { syncRepository.pull(any(), any()) } coAnswers { delay(1000); fakeChangesResponse() }`; call `triggerSync()`; advance partially; assert `syncStatus == SyncStatus.Syncing`.
    2. `triggerSync_setsIdleStatus_onSuccess` — mock pull and push to succeed; call `triggerSync()`; advance fully; assert `syncStatus == SyncStatus.Idle`.
    3. `triggerSync_setsErrorStatus_onNetworkFailure` — mock `syncRepository.pull` to throw `IOException`; assert `syncStatus == SyncStatus.Error` after advancing.
    4. `triggerSync_upsertsNodesFromPullResponse` — pull response contains 2 `NodeSyncRecord`s; verify `nodeDao.upsertNodes(any())` is called.
    5. `triggerSync_updatesLastSyncHlc_afterSuccessfulPush` — mock push returning `serverHlc = "BBBB"`; call `triggerSync()`; advance; read DataStore `last_sync_hlc`; assert value is `"BBBB"`.
    6. `onScreenResumed_callsTriggerSync` — spy on `triggerSync()` via a subclass; call `onScreenResumed()`; assert `triggerSync()` is invoked exactly once.
  - **Pass criteria**: `./gradlew test` exits 0, all 6 cases green.
- **Risk**: MEDIUM — DataStore in unit tests requires a real file; use `@get:Rule val tempDir = TemporaryFolder()` and `PreferenceDataStoreFactory.create(scope = testScope) { tempDir.newFile("prefs.preferences_pb") }` to avoid cross-test contamination.

---

### P4-5: DocumentListScreen UI

- **Task ID**: P4-5
- **Title**: DocumentListScreen composable with LazyColumn, create/rename/delete actions, and sync status bar
- **What to build**: Create `android/app/src/main/java/com/outlinegod/app/ui/screen/documentlist/DocumentListScreen.kt`. Define `@Composable fun DocumentListScreen(documentId: String? = null, viewModel: DocumentListViewModel = hiltViewModel(), onOpenDocument: (String) -> Unit, onNavigateToSettings: () -> Unit)`. Call `viewModel.onScreenResumed()` in `LaunchedEffect(Unit)`. Collect state via `viewModel.container.stateFlow.collectAsState()`. When `Loading`, render `CircularProgressIndicator`. When `Error`, render error text with a retry button. When `Success`: render a `Scaffold` with a `TopAppBar` (title "OutlineGod", settings icon calling `onNavigateToSettings`); a sync status `LinearProgressIndicator` below the top bar when `syncStatus == SyncStatus.Syncing`; a `FloatingActionButton` to create a new document (shows a simple `AlertDialog` with a title text field and type selector); the document list as a `LazyColumn` where each item is a `DocumentListItem` composable rendering the document title — tap calls `onOpenDocument(id)`, long-press shows a context menu `DropdownMenu` with Rename, Delete. Folders render a trailing `IconButton` toggling collapse via `viewModel.toggleFolderCollapse(id)`. Children of collapsed folders are filtered client-side (items whose `parentId` is a collapsed folder are excluded). Update `AppNavHost.kt` to replace the `document_list` placeholder with `DocumentListScreen(onOpenDocument = { id -> navController.navigate("node_editor/$id") }, onNavigateToSettings = { navController.navigate(AppRoutes.SETTINGS) })`.
- **Inputs required**: P4-3, P4-4 (DocumentListViewModel); P3-14 (AppNavHost to update)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/documentlist/DocumentListScreen.kt`
  - `android/app/src/main/java/com/outlinegod/app/ui/navigation/AppNavHost.kt` (updated)
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/ui/screen/documentlist/DocumentListScreenTest.kt`
  - No Compose UI tests. Structural and regression tests only.
  - **Test cases**:
    1. `documentListScreenKt_classExists` — `Class.forName("com.outlinegod.app.ui.screen.documentlist.DocumentListScreenKt")`; assert no exception.
    2. `appRoutes_nodeEditor_containsDocumentIdParam` — assert `AppRoutes.NODE_EDITOR.contains("{documentId}")` (or `"{nodeId}"` per P3-14 — verify the route matches the NavHost navigation call).
    3. `appRoutes_settings_isDefined` — assert `AppRoutes.SETTINGS == "settings"`.
    4. `syncStatus_enum_hasThreeValues` — assert `SyncStatus.values().size == 3`.
  - **Pass criteria**: `./gradlew test` exits 0, all 4 cases green. `./gradlew assembleDebug` exits 0.
- **Risk**: LOW — the client-side folder collapse filtering is a list filter operation, not a database query; correctness is verified manually.

---

### P4-6: FlatNodeMapper

- **Task ID**: P4-6
- **Title**: FlatNodeMapper pure function with depth computation and orphan handling
- **What to build**: Create `android/app/src/main/java/com/outlinegod/app/ui/mapper/FlatNodeMapper.kt`. Define `data class FlatNode(val entity: NodeEntity, val depth: Int, val hasChildren: Boolean)`. Implement `fun mapToFlatList(nodes: List<NodeEntity>, documentId: String): List<FlatNode>` as a pure top-level function. Algorithm: (1) filter out nodes with non-null `deleted_at`; (2) build a `childrenMap: Map<String?, List<NodeEntity>>` grouping active nodes by `parentId`, each group sorted by `ORDER BY sort_order ASC, id ASC` (lexicographic sort then ID tiebreaker matching the backend's `ORDER BY` rule); (3) define a recursive `fun dfs(parentId: String?, depth: Int): List<FlatNode>` that fetches `childrenMap[parentId] ?: emptyList()`, converts each child to `FlatNode(entity = child, depth = depth, hasChildren = childrenMap[child.id]?.isNotEmpty() == true)`, appends the child to the result, then — only if `child.collapsed == 0` — recursively calls `dfs(child.id, depth + 1)` and appends all descendant FlatNodes; (4) start traversal with `dfs(documentId, 0)` (top-level nodes have `parentId == documentId`); (5) orphan handling: after DFS, any active node whose `parentId` is not `null`, not `documentId`, and not found in `childrenMap` is appended at `depth = 0` with `hasChildren = false`. The function is stateless and free of side effects — it must be callable from any thread.
- **Inputs required**: P3-3 (NodeEntity with `parentId`, `sortOrder`, `collapsed`, `deletedAt` fields); P3-4 (NodeDao — FlatNodeMapper does not call DAOs, but its input type is `List<NodeEntity>` from P3-4)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/mapper/FlatNodeMapper.kt`
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/ui/mapper/FlatNodeMapperTest.kt`
  - Use plain JUnit 4 + Kotest `StringSpec` (`@RunWith(KotestTestRunner::class)`). Define `fun makeNode(id: String, parentId: String?, sortOrder: String, collapsed: Int = 0, deletedAt: Long? = null): NodeEntity` helper.
  - **Test cases**:
    1. `emptyList_returnsEmptyFlatList` — `mapToFlatList(emptyList(), "doc-1")`; assert result is empty.
    2. `singleTopLevelNode_depthZero` — one node with `parentId = "doc-1"`; assert `result[0].depth == 0`.
    3. `nestedNode_depthOne` — parent at depth 0 (parentId = "doc-1"), child at parentId = parent.id; assert `result[1].depth == 1`.
    4. `collapsedNode_childrenExcluded` — parent `collapsed = 1`, has one child; assert result contains only the parent (child is excluded).
    5. `expandedNode_childrenIncluded` — parent `collapsed = 0`, has one child; assert result has 2 items in DFS order.
    6. `hasChildren_true_whenNodeHasActiveChildren` — parent with 1 active child + 1 deleted child; assert `result.first { it.entity.id == parent.id }.hasChildren == true`.
    7. `hasChildren_false_afterAllChildrenDeleted` — parent whose only child has `deletedAt = 1000L`; assert `hasChildren == false`.
    8. `sortOrder_lexicographic_notNumeric` — three siblings with `sortOrder = "V"`, `"a"`, `"Z"`; assert order in result is `["V", "Z", "a"]` (ASCII/lexicographic, not alphanumeric).
    9. `orphanNode_appendedAtDepthZero` — node with `parentId = "ghost-uuid"` not present in the input list; assert orphan appears in result with `depth == 0`.
    10. `deletedNode_excludedFromResult` — node with `deletedAt = 1000L`; assert it does not appear in result at any position.
    11. `deepNesting_computesDepthCorrectly` (property-based) — `checkAll(Arb.int(1..10)) { depth -> build a chain of `depth` nodes; assert last node's depth == depth - 1 }`.
    12. `multipleRoots_allAtDepthZero` — three nodes with `parentId = "doc-1"`; assert all three have `depth == 0`.
  - **Pass criteria**: `./gradlew test` exits 0, all 12 cases green. Property-based tests run 200 iterations.
- **Risk**: MEDIUM — orphan handling and lexicographic sort-order edge cases (test 8 and 9) are the most common implementation mistakes. Test 8 validates the critical `sort_order` invariant: string lexicographic order is not alphabetical; `"Z" < "a"` in ASCII, so the ordering `["V", "Z", "a"]` is correct and distinct from alphabetical `["V", "a", "Z"]`.

---

### P4-7: NodeEditorViewModel — core

- **Task ID**: P4-7
- **Title**: NodeEditorViewModel with node load, create, delete, and focus navigation intents
- **What to build**: Create `android/app/src/main/java/com/outlinegod/app/ui/screen/nodeeditor/NodeEditorViewModel.kt` annotated `@HiltViewModel`. Inject `private val nodeDao: NodeDao`, `private val authRepository: AuthRepository`, `private val hlcClock: HlcClock`, `private val dataStore: DataStore<Preferences>`. Define `data class NodeEditorUiState(val status: NodeEditorStatus = NodeEditorStatus.Loading, val flatNodes: List<FlatNode> = emptyList(), val focusedNodeId: String? = null, val documentId: String = "")` and `sealed class NodeEditorStatus { data object Loading; data object Success; data class Error(val message: String) }`. In `init { }` do nothing; expose `fun loadDocument(documentId: String)` that collects `nodeDao.getNodesByDocument(documentId)` as a Flow, maps via `FlatNodeMapper.mapToFlatList(nodes, documentId)`, and reduces to `NodeEditorUiState(status = Success, flatNodes = result, documentId = documentId)`. Implement `fun onEnterPressed(nodeId: String, cursorPosition: Int)` — locate the node in the flat list; split content at `cursorPosition`; update local node with left half via `nodeDao.updateNode()`; generate a new UUID + fractional sort_order between current and next sibling; insert a new `NodeEntity` for the right half via `nodeDao.insertNode()`; set `focusedNodeId` to the new node's ID. Implement `fun onBackspaceOnEmptyNode(nodeId: String)` — if node content and note are both empty, soft-delete via `nodeDao.softDeleteNode()`; set `focusedNodeId` to the preceding node in the flat list. Implement `fun moveFocus(direction: FocusDirection)` where `FocusDirection` is `enum { Up, Down }` — compute next/previous index in `flatNodes`, set `focusedNodeId`. The file also contains `enum class FocusDirection { Up, Down }` in the same package.
- **Inputs required**: P4-6 (FlatNodeMapper, FlatNode); P3-4 (NodeDao with all CRUD methods); P3-9 (HlcClock); P3-3 (NodeEntity); P3-1 (fractional-indexing Kotlin port declared in `libs.versions.toml` as a local utility or inlined)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/nodeeditor/NodeEditorViewModel.kt`
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/ui/screen/nodeeditor/NodeEditorViewModelTest.kt`
  - Test setup: `runTest`, `MainDispatcherRule`, MockK for `NodeDao`, `AuthRepository`, `HlcClock`; stub `nodeDao.getNodesByDocument(any())` to return `flowOf(listOf(...))`.
  - **Test cases**:
    1. `loadDocument_setsSuccessStatus_withFlatNodes` — stub DAO with 2 nodes; call `loadDocument("doc-1")`; advance; assert `state.status == Success` and `state.flatNodes.size == 2`.
    2. `onEnterPressed_splitContent_atCursorPosition` — node content = `"HelloWorld"`, cursorPosition = 5; call `onEnterPressed("n1", 5)`; verify `nodeDao.updateNode` called with `content = "Hello"` and `nodeDao.insertNode` called with `content = "World"`.
    3. `onEnterPressed_atStart_insertsBlankNodeAbove` — cursorPosition = 0, content = `"ABC"`; verify `insertNode` called with `content = ""` and `updateNode` called with `content = "ABC"`.
    4. `onEnterPressed_setsFocusedNodeId_toNewNode` — call `onEnterPressed`; assert `state.focusedNodeId` equals the UUID of the newly inserted node.
    5. `onBackspaceOnEmptyNode_softDeletesNode` — stub empty-content node; call `onBackspaceOnEmptyNode("n1")`; verify `nodeDao.softDeleteNode(eq("n1"), any(), any(), any())` called.
    6. `onBackspaceOnEmptyNode_setsFocusToPrecedingNode` — flat list has nodes `[n0, n1_empty]`; call `onBackspaceOnEmptyNode("n1")`; assert `state.focusedNodeId == "n0"`.
    7. `onBackspaceOnNonEmptyNode_doesNotDelete` — node has non-empty content; call `onBackspaceOnEmptyNode`; verify `nodeDao.softDeleteNode` is never called.
    8. `moveFocus_Down_advancesFocusIndex` — flat list has 3 nodes, `focusedNodeId = "n0"`; call `moveFocus(FocusDirection.Down)`; assert `state.focusedNodeId == "n1"`.
    9. `moveFocus_Up_atFirstNode_doesNothing` — `focusedNodeId = "n0"` (first node); call `moveFocus(Up)`; assert `state.focusedNodeId` remains `"n0"`.
  - **Pass criteria**: `./gradlew test` exits 0, all 9 cases green.
- **Risk**: LOW — ViewModel logic is pure state manipulation using stubs. The fractional sort_order generation for new nodes must use the same algorithm as FlatNodeMapper's sort — use `generateKeyBetween(currentSortOrder, nextSiblingsSortOrder)` from a ported `fractional-indexing` utility class.

---

### P4-8: NodeEditorViewModel persistence

- **Task ID**: P4-8
- **Title**: NodeEditorViewModel debounced 300 ms Room upsert with HLC timestamp on every content change
- **What to build**: Extend `NodeEditorViewModel` from P4-7. Implement `fun onContentChanged(nodeId: String, newContent: String)` — updates the in-memory `flatNodes` list optimistically (via `reduce { copy(flatNodes = flatNodes.map { if (it.entity.id == nodeId) it.copy(entity = it.entity.copy(content = newContent)) else it }) }`), then triggers a debounced Room write: `private var debounceJobs = mutableMapOf<String, Job>()`; `debounceJobs[nodeId]?.cancel(); debounceJobs[nodeId] = viewModelScope.launch { delay(300); persistContentChange(nodeId, newContent) }`. Implement `private suspend fun persistContentChange(nodeId: String, content: String)` — fetch entity via `nodeDao.getNodesByDocumentSync(documentId).firstOrNull { it.id == nodeId }` (if null, return silently); obtain `deviceId` from `authRepository.getDeviceId().first()`; generate `val hlc = hlcClock.generate(deviceId)`; call `nodeDao.updateNode(entity.copy(content = content, contentHlc = hlc, updatedAt = System.currentTimeMillis(), deviceId = deviceId))`. Similarly implement `fun onNoteChanged(nodeId: String, newNote: String)` with the same debounce + HLC pattern targeting `note` and `noteHlc` columns. Expose `fun onNodeFocusLost(nodeId: String)` that cancels any pending debounce job and calls `persistContentChange` immediately (flush on blur).
- **Inputs required**: P4-7 (NodeEditorViewModel with `viewModelScope`); P3-4 (NodeDao.updateNode + getNodesByDocumentSync); P3-9 (HlcClock); P3-12 (AuthRepository.getDeviceId())
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/nodeeditor/NodeEditorViewModel.kt` (updated)
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/ui/screen/nodeeditor/NodeEditorPersistenceTest.kt`
  - Test setup: `runTest` with `TestCoroutineScheduler` for time-control; `advanceTimeBy(ms)` to simulate debounce.
  - **Test cases**:
    1. `onContentChanged_updatesInMemoryStateImmediately` — call `onContentChanged("n1", "new content")`; without advancing time, assert `state.flatNodes.first { it.entity.id == "n1" }.entity.content == "new content"`.
    2. `onContentChanged_doesNotWriteToRoom_before300ms` — call `onContentChanged`; `advanceTimeBy(299)`; verify `nodeDao.updateNode` is never called.
    3. `onContentChanged_writesToRoom_after300ms` — call `onContentChanged`; `advanceTimeBy(300)`; verify `nodeDao.updateNode(any())` called exactly once.
    4. `onContentChanged_rapidSuccession_coalesces_toSingleWrite` — call `onContentChanged("n1", "a")`, `onContentChanged("n1", "ab")`, `onContentChanged("n1", "abc")` within 300ms window; advance 300ms; verify `nodeDao.updateNode` called exactly once with `content == "abc"`.
    5. `onContentChanged_stampsHlcTimestamp_onWrite` — capture `nodeDao.updateNode` argument; assert `it.contentHlc` matches HLC format regex `^[0-9a-f]{16}-[0-9a-f]{4}-`.
    6. `onNodeFocusLost_flushesDebounce_immediately` — call `onContentChanged`; immediately call `onNodeFocusLost`; without advancing time, verify `nodeDao.updateNode` called.
    7. `onNoteChanged_writesToNoteHlc_column` — call `onNoteChanged("n1", "my note")`; advance 300ms; capture updateNode; assert `it.noteHlc` is non-blank and `it.note == "my note"`.
  - **Pass criteria**: `./gradlew test` exits 0, all 7 cases green.
- **Risk**: LOW — the debounce `Job` map pattern is standard coroutine idiom. The only tricky part is `TestCoroutineScheduler.advanceTimeBy` which requires `runTest { }` to use the same scheduler; ensure `viewModelScope` uses `UnconfinedTestDispatcher` in tests via `Dispatchers.setMain`.

---

### P4-9: NodeEditorScreen UI

- **Task ID**: P4-9
- **Title**: NodeEditorScreen composable with LazyColumn flat node list and BasicTextField editing
- **What to build**: Create `android/app/src/main/java/com/outlinegod/app/ui/screen/nodeeditor/NodeEditorScreen.kt`. Define `@Composable fun NodeEditorScreen(documentId: String, viewModel: NodeEditorViewModel = hiltViewModel())`. In `LaunchedEffect(documentId)`, call `viewModel.loadDocument(documentId)`. Collect state via `viewModel.container.stateFlow.collectAsState()`. When `Loading`, show `CircularProgressIndicator`. When `Success`, render a `Scaffold` with a `TopAppBar` displaying the document title (derived from `documentId` — fetch the document name from `DocumentDao` via a separate `@Composable fun rememberDocumentTitle(documentId: String): State<String>` helper). Render flat nodes as `LazyColumn { items(state.flatNodes, key = { it.entity.id }) { flatNode -> NodeRow(flatNode = flatNode, isFocused = state.focusedNodeId == flatNode.entity.id, onContentChanged = { viewModel.onContentChanged(flatNode.entity.id, it) }, onEnterPressed = { cursor -> viewModel.onEnterPressed(flatNode.entity.id, cursor) }, onBackspaceOnEmpty = { viewModel.onBackspaceOnEmptyNode(flatNode.entity.id) }, onMoveFocus = { viewModel.moveFocus(it) }, onFocusLost = { viewModel.onNodeFocusLost(flatNode.entity.id) }) } }`. Implement `@Composable fun NodeRow(...)` in the same file: uses `Modifier.padding(start = (flatNode.depth * 24).dp)` for indentation; a filled dot `Canvas` glyph on the left; a `BasicTextField(state = rememberTextFieldState(flatNode.entity.content), inputTransformation = EnterKeyInterceptor { cursor -> onEnterPressed(cursor) }, modifier = Modifier.fillMaxWidth())` using `TextFieldState`; apply `MarkdownVisualTransformation` from P0-2 prototype (`com.outlinegod.app.prototype.MarkdownVisualTransformation`) as the `textStyle` source; a directional arrow `IconButton` on the right when `flatNode.hasChildren`. Update `AppNavHost.kt` to replace the `node_editor` placeholder with `NodeEditorScreen(documentId = backStackEntry.arguments?.getString("documentId") ?: "")`.
- **Inputs required**: P4-7, P4-8 (NodeEditorViewModel); P4-6 (FlatNode); P3-14 (AppNavHost); Phase 0 prototype `MarkdownVisualTransformation` from `com.outlinegod.app.prototype` package
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/nodeeditor/NodeEditorScreen.kt`
  - `android/app/src/main/java/com/outlinegod/app/ui/navigation/AppNavHost.kt` (updated)
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/ui/screen/nodeeditor/NodeEditorScreenTest.kt`
  - No Compose UI tests. Structural tests only.
  - **Test cases**:
    1. `nodeEditorScreenKt_classExists` — `Class.forName("com.outlinegod.app.ui.screen.nodeeditor.NodeEditorScreenKt")`; assert no exception.
    2. `nodeEditorStatus_hasLoadingSuccessError` — assert `NodeEditorStatus::class.sealedSubclasses.map { it.simpleName }.containsAll(listOf("Loading", "Success", "Error"))`.
    3. `flatNode_depthField_isInt` — assert `FlatNode::class.memberProperties.first { it.name == "depth" }.returnType.classifier == Int::class`.
    4. `appNavHost_nodeEditorRoute_containsDocumentId` — assert `AppRoutes.NODE_EDITOR.contains("documentId") || AppRoutes.NODE_EDITOR.contains("nodeId")`.
  - **Pass criteria**: `./gradlew test` exits 0, all 4 cases green. `./gradlew assembleDebug` exits 0.
- **Risk**: LOW — `BasicTextField` with `TextFieldState` is stable as of Compose Foundation 1.9.x. The `MarkdownVisualTransformation` (visible markers, identity offset) is cursor-safe by design. The `EnterKeyInterceptor` (`InputTransformation`) from the P0-3 risk prototype is already tested and production-ready.

---

### P4-10: Drag-and-drop integration

- **Task ID**: P4-10
- **Title**: Wire P0-1 DnD gesture into NodeEditorScreen for vertical reorder and horizontal reparent
- **What to build**: Extend `NodeEditorScreen` from P4-9. Wrap the `LazyColumn` in `ReorderableColumn` from `sh.calvin.reorderable:reorderable:3.0.0`. Replace `NodeRow` `Modifier` with `Modifier.longPressDraggableHandle(state = reorderableLazyListState)` from the reorderable library. On drop completion (`onMove` callback), call `viewModel.onNodeDropped(fromIndex, toIndex, horizontalDragOffsetDp)`. Implement `fun onNodeDropped(fromIndex: Int, toIndex: Int, xOffsetDp: Float)` in `NodeEditorViewModel`: (1) determine target depth from `xOffsetDp` — every 24dp left = outdent one level, right = indent under preceding sibling (port horizontal-drag depth logic from P0-1 prototype in `com.outlinegod.prototype.DndPrototype`); (2) determine new `parentId` from target depth and the node at `toIndex - 1` in the flat list; (3) compute new `sort_order` via `generateKeyBetween(prevSiblingSortOrder, nextSiblingSortOrder)` using the fractional indexing utility; (4) obtain `deviceId` and `hlc` from `hlcClock.generate(deviceId)`; (5) call `nodeDao.updateNode(moved.copy(parentId = newParentId, sortOrder = newSortOrder, parentIdHlc = hlc, sortOrderHlc = hlc, deviceId = deviceId, updatedAt = now))`; (6) reduce in-memory `flatNodes` optimistically by recomputing `FlatNodeMapper.mapToFlatList` from the updated entity list. Add haptic feedback via `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)` at drag start.
- **Inputs required**: P4-9 (NodeEditorScreen, NodeEditorViewModel); P4-6 (FlatNodeMapper for recomputation); P3-9 (HlcClock); Phase 0 P0-1 prototype (horizontal-drag depth logic)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/nodeeditor/NodeEditorScreen.kt` (updated)
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/nodeeditor/NodeEditorViewModel.kt` (updated with `onNodeDropped`)
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/ui/screen/nodeeditor/DragAndDropTest.kt`
  - No Compose UI tests. Test the ViewModel logic only.
  - **Test cases**:
    1. `onNodeDropped_verticalReorder_sameParent_updatesSortOrder` — flat list `[A, B, C]` all siblings; drop B below C (fromIndex=1, toIndex=2, xOffset=0dp); verify `nodeDao.updateNode` called with B having a `sortOrder > C.sortOrder` (lexicographically).
    2. `onNodeDropped_horizontalRightDrag_reparentsUnderPreceding` — xOffset = 24dp (one indent level); preceding node is A; verify updated B has `parentId == A.id`.
    3. `onNodeDropped_horizontalLeftDrag_outdents` — B is child of A (depth 1); xOffset = -24dp; verify updated B has `parentId == A.parentId` (one level up).
    4. `onNodeDropped_stampsParentIdHlc_andSortOrderHlc` — capture `nodeDao.updateNode` arg; assert `it.parentIdHlc` and `it.sortOrderHlc` are non-blank HLC strings.
    5. `onNodeDropped_optimisticallyUpdatesInMemoryFlatNodes` — call `onNodeDropped`; without awaiting Room; assert `state.flatNodes` order changes reflect the drop.
  - **Pass criteria**: `./gradlew test` exits 0, all 5 cases green.
- **Risk**: HIGH — gesture conflict between `longPressDraggableHandle` (vertical reorder) and the horizontal `pointerInput` offset detection. The P0-1 prototype must have resolved this conflict before P4-10 begins. If the gestures still conflict, the fallback is separate Indent/Outdent toolbar buttons (from PRD Section 12 Mobile Toolbar) instead of horizontal drag detection, deferring horizontal drag to a future phase.

---

### P4-11: Node context menu

- **Task ID**: P4-11
- **Title**: Node context menu bottom sheet with five actions
- **What to build**: Add `private var contextMenuNodeId by mutableStateOf<String?>(null)` to `NodeEditorScreen`. In `NodeRow`, detect long-press on the glyph `Canvas` via `Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { if (!isDragging) contextMenuNodeId = flatNode.entity.id }) }` — only trigger the context menu when the user long-presses and releases without moving (the `isDragging` flag comes from the reorderable library's drag state). When `contextMenuNodeId != null`, show `ModalBottomSheet(onDismissRequest = { contextMenuNodeId = null })` containing: (1) **Add note** — calls `viewModel.showNoteEditor(nodeId)` (sets a flag in UiState causing the note field to expand in `NodeRow`); (2) **Set color** — a `Row` of 6 colored `Box` composables (red, orange, yellow, green, blue, purple) + a clear button, each calling `viewModel.onColorChanged(nodeId, colorInt)`; (3) **Toggle completed** — calls `viewModel.onCompletedToggled(nodeId)`; (4) **Convert to document** — calls `viewModel.convertNodeToDocument(nodeId)` which calls `POST /api/nodes/$nodeId/convert-to-document` via `httpClient`, then navigates back to DocumentList on success; (5) **Delete node** — calls `viewModel.onBackspaceOnEmptyNode(nodeId)` (reuses soft-delete logic; works for non-empty nodes too — update the method to not check for empty content). Add `@Serializable` actions to `NodeEditorViewModel`: `fun onColorChanged(nodeId: String, color: Int)` (debounced write via same pattern as `onContentChanged`), `fun onCompletedToggled(nodeId: String)` (fetch + toggle + write with HLC), `fun convertNodeToDocument(nodeId: String)` (HTTP POST + navigate side effect).
- **Inputs required**: P4-9 (NodeEditorScreen); P4-7, P4-8 (NodeEditorViewModel); P3-13 (SyncRepository model types); P3-11 (HttpClient)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/nodeeditor/NodeEditorScreen.kt` (updated)
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/nodeeditor/NodeEditorViewModel.kt` (updated)
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/ui/screen/nodeeditor/NodeContextMenuTest.kt`
  - **Test cases**:
    1. `onColorChanged_writesColorAndColorHlc_toRoom` — call `onColorChanged("n1", 3)`; advance 300ms; capture `nodeDao.updateNode`; assert `it.color == 3` and `it.colorHlc` is a valid HLC.
    2. `onCompletedToggled_flipsCompletedBit` — stub node with `completed = 0`; call `onCompletedToggled("n1")`; capture `nodeDao.updateNode`; assert `it.completed == 1`.
    3. `onCompletedToggled_again_flipsBackToZero` — stub node with `completed = 1`; call `onCompletedToggled`; assert `it.completed == 0`.
    4. `convertNodeToDocument_callsCorrectEndpoint` — mock httpClient; call `convertNodeToDocument("n1")`; verify HTTP `POST` to path containing `/api/nodes/n1/convert-to-document`.
    5. `convertNodeToDocument_onSuccess_emitsNavigateSideEffect` — mock success response; call `convertNodeToDocument`; assert a navigation side effect is emitted.
    6. `convertNodeToDocument_onFailure_emitsShowError` — mock 404 response; assert `ShowError` side effect emitted.
  - **Pass criteria**: `./gradlew test` exits 0, all 6 cases green.
- **Risk**: LOW — long-press vs drag disambiguation uses the reorderable library's existing drag-state boolean. The `ModalBottomSheet` is a standard MD3 component.

---

### P4-12: Node note editor

- **Task ID**: P4-12
- **Title**: Inline note BasicTextField expanding below node content with Enter-as-newline behavior
- **What to build**: Extend `NodeRow` in `NodeEditorScreen`. Add a parameter `isNoteExpanded: Boolean` and `onNoteChanged: (String) -> Unit`. When `isNoteExpanded == true` OR `flatNode.entity.note.isNotBlank()`, render a second `BasicTextField` below the content field: `BasicTextField(state = rememberTextFieldState(flatNode.entity.note), inputTransformation = NoteEnterTransformation(), modifier = Modifier.fillMaxWidth().padding(start = 20.dp), textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)))`. Create `class NoteEnterTransformation : InputTransformation` — its `transformInput()` must NOT intercept `'\n'` (Enter in the note field inserts a newline, not a new node; this is the inverse of `EnterKeyInterceptor` from NodeEditorScreen content fields). Call `onNoteChanged(newText)` via a `snapshotFlow { state.text.toString() }.collect` inside a `LaunchedEffect`. In `NodeEditorViewModel`, track which nodes have note editors expanded via `private val expandedNoteNodeIds = mutableStateSetOf<String>()` and expose `fun showNoteEditor(nodeId: String)` (adds to set) and `fun hideNoteEditor(nodeId: String)` (removes from set). Add `val isNoteExpanded: Boolean` to `FlatNode` (computed from the ViewModel's expanded set during state mapping). Apply the same debounced `onNoteChanged` → `persistNoteChange` pipeline from P4-8.
- **Inputs required**: P4-8 (NodeEditorViewModel persistence for notes); P4-9 (NodeEditorScreen, NodeRow); P4-11 (showNoteEditor trigger from context menu)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/nodeeditor/NodeEditorScreen.kt` (updated)
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/nodeeditor/NodeEditorViewModel.kt` (updated)
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/ui/screen/nodeeditor/NoteEditorTest.kt`
  - **Test cases**:
    1. `showNoteEditor_addsNodeIdToExpandedSet` — call `viewModel.showNoteEditor("n1")`; assert the resulting `flatNodes` list has the node with `isNoteExpanded == true`.
    2. `hideNoteEditor_removesNodeIdFromExpandedSet` — call `showNoteEditor` then `hideNoteEditor`; assert `isNoteExpanded == false`.
    3. `noteWithContent_isAlwaysExpanded_regardlessOfExpandedSet` — node has `note = "existing note"`; `isNoteExpanded` must be `true` even if not in the expanded set.
    4. `noteEnterTransformation_doesNotInterceptNewline` — instantiate `NoteEnterTransformation`; build a `TextFieldBuffer` with a `'\n'` character; call `transformInput()`; assert the buffer still contains `'\n'` (newline was preserved).
    5. `noteChange_writes_toNoteField_notContentField` — call `onNoteChanged("n1", "my note")`; advance 300ms; capture `nodeDao.updateNode`; assert `it.note == "my note"` and `it.content` is unchanged.
  - **Pass criteria**: `./gradlew test` exits 0, all 5 cases green.
- **Risk**: LOW — `NoteEnterTransformation` is an intentional no-op for newline characters; the test ensures it does not accidentally suppress Enter. The key invariant (Enter in note = newline, Enter in content = new node) is enforced by using two distinct `InputTransformation` instances.

---

### P4-13: NodeEditorViewModel sync trigger

- **Task ID**: P4-13
- **Title**: NodeEditorViewModel pull/push on resume and 30-second inactivity timer
- **What to build**: Extend `NodeEditorViewModel` from P4-12. Inject `private val syncRepository: SyncRepository`. Implement `private suspend fun triggerSync()` following the same pull → push → update-lastSyncHlc pattern as P4-4. Implement `fun onScreenResumed()` — cancels any running inactivity timer; calls `triggerSync()` once; then starts a 30-second inactivity timer: `private var inactivityTimerJob: Job? = null` with `inactivityTimerJob = viewModelScope.launch { delay(30_000); triggerSync() }`. Call `resetInactivityTimer()` from every user action that mutates nodes: `onContentChanged`, `onNoteChanged`, `onNodeDropped`, `onColorChanged`, `onCompletedToggled`. Implement `fun resetInactivityTimer()` — cancels the current `inactivityTimerJob` and launches a new 30-second job. Implement `fun onScreenPaused()` — cancels `inactivityTimerJob` to prevent sync after the user leaves the screen. Add `syncStatus: SyncStatus` to `NodeEditorUiState` (same enum as `DocumentListUiState.SyncStatus` — move to a shared `com.outlinegod.app.ui.common.SyncStatus` file).
- **Inputs required**: P4-12 (NodeEditorViewModel base with all node mutations); P4-4 (pull/push sync pattern, lastSyncHlc DataStore); P3-13 (SyncRepository)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/nodeeditor/NodeEditorViewModel.kt` (updated)
  - `android/app/src/main/java/com/outlinegod/app/ui/common/SyncStatus.kt` (new — shared enum)
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/ui/screen/nodeeditor/NodeEditorSyncTest.kt`
  - Test setup: `runTest` with `TestCoroutineScheduler`; MockK for `SyncRepository`.
  - **Test cases**:
    1. `onScreenResumed_triggersSyncImmediately` — call `onScreenResumed()`; advance all; verify `syncRepository.pull(any(), any())` called once.
    2. `onScreenResumed_startsInactivityTimer_30s` — call `onScreenResumed()`; advance 29_999ms; verify pull called once (initial); advance 1ms more; verify pull called twice.
    3. `onContentChanged_resetsInactivityTimer` — call `onScreenResumed()`; advance 15_000ms; call `onContentChanged("n1", "x")`; advance 29_999ms; verify pull called exactly once (timer was reset at 15s, so 15+29 = 44s total, no second trigger yet); advance 1ms; verify twice.
    4. `onScreenPaused_cancelsInactivityTimer` — call `onScreenResumed()`; call `onScreenPaused()`; advance 60_000ms; verify pull called exactly once (no timer trigger after pause).
    5. `triggerSync_setsSyncingThenIdle_inUiState` — mock pull/push success; call `onScreenResumed()`; collect states; assert `NodeEditorStatus` transitions include `syncStatus == SyncStatus.Syncing` then `SyncStatus.Idle`.
  - **Pass criteria**: `./gradlew test` exits 0, all 5 cases green.
- **Risk**: LOW — `TestCoroutineScheduler.advanceTimeBy` makes timer logic deterministic. The inactivity reset pattern (`cancel + relaunch`) is a standard coroutine idiom with no concurrency hazard.

---

### P4-14: SettingsViewModel

- **Task ID**: P4-14
- **Title**: SettingsViewModel loading settings from Room and persisting changes with HLC timestamps
- **What to build**: Create `android/app/src/main/java/com/outlinegod/app/ui/screen/settings/SettingsViewModel.kt` annotated `@HiltViewModel`. Inject `private val settingsDao: SettingsDao`, `private val authRepository: AuthRepository`, `private val hlcClock: HlcClock`. Define `sealed class SettingsUiState { data object Loading : SettingsUiState(); data class Success(val settings: SettingsEntity) : SettingsUiState(); data class Error(val message: String) : SettingsUiState() }`. In `init`, collect `authRepository.getAccessToken().filterNotNull().take(1).first()` for `userId`, then collect `settingsDao.getSettings(userId)` Flow: when null emit a default `SettingsEntity` then insert it via `settingsDao.upsertSettings(defaultEntity)`; otherwise reduce to `Success(settings)`. Implement `fun updateTheme(theme: String)` — validate `theme in listOf("dark", "light")`; fetch entity via `settingsDao.getSettingsSync(userId)`; upsert `entity.copy(theme = theme, themeHlc = hlcClock.generate(deviceId), updatedAt = now)`. Implement `fun updateDensity(density: String)` — same pattern; valid values: `"cozy"`, `"comfortable"`, `"compact"`. Implement `fun toggleGuideLines()` — flip `showGuideLines` bit (0 ↔ 1) with HLC stamp. Implement `fun toggleBacklinkBadge()` — flip `showBacklinkBadge` bit with HLC stamp. All mutation methods call `settingsDao.upsertSettings(updated)` and the Room Flow subscription causes `Success` state to update automatically.
- **Inputs required**: P3-7 (SettingsDao); P3-3 (SettingsEntity); P3-9 (HlcClock); P3-12 (AuthRepository)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/settings/SettingsViewModel.kt`
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/ui/screen/settings/SettingsViewModelTest.kt`
  - Test setup: `runTest`, `MainDispatcherRule`, MockK for `SettingsDao` and `AuthRepository`, `HlcClock`.
  - **Test cases**:
    1. `initialLoad_withExistingSettings_emitsSuccess` — stub `settingsDao.getSettings("u1")` to emit `flowOf(fakeSettings())`; construct VM; advance; assert state is `SettingsUiState.Success`.
    2. `initialLoad_withNoSettings_insertsDefaults` — stub `getSettings` to emit `flowOf(null)`; advance; verify `settingsDao.upsertSettings(any())` called with `theme == "dark"` and `density == "cozy"`.
    3. `updateTheme_dark_to_light_upsetsEntity` — stub `getSettingsSync` returning dark settings; call `updateTheme("light")`; capture upsert arg; assert `it.theme == "light"` and `it.themeHlc` is non-blank.
    4. `updateTheme_invalidValue_doesNotUpsert` — call `updateTheme("sepia")`; verify `settingsDao.upsertSettings` never called.
    5. `updateDensity_validValues_accepted` — call `updateDensity("compact")`; verify upsert called; call `updateDensity("ultracompact")`; verify second call does not upsert.
    6. `toggleGuideLines_flipsZeroToOne` — stub settings with `showGuideLines = 0`; call `toggleGuideLines()`; assert upserted `showGuideLines == 1`.
    7. `toggleBacklinkBadge_flipsOneToZero` — stub settings with `showBacklinkBadge = 1`; call `toggleBacklinkBadge()`; assert upserted `showBacklinkBadge == 0`.
    8. `allMutations_stampHlcOnUpdatedField` — call each of the four mutation methods; capture all upsert calls; assert each has a non-blank HLC on its respective HLC column.
  - **Pass criteria**: `./gradlew test` exits 0, all 8 cases green.
- **Risk**: LOW — pure DAO delegation with HLC stamping. The validation in `updateTheme` and `updateDensity` prevents invalid state from reaching the database or the sync system.

---

### P4-15: SettingsScreen UI

- **Task ID**: P4-15
- **Title**: SettingsScreen composable with theme toggle, density selector, and guide lines / backlink badge switches
- **What to build**: Replace the placeholder at `android/app/src/main/java/com/outlinegod/app/ui/screen/SettingsScreen.kt` by creating `android/app/src/main/java/com/outlinegod/app/ui/screen/settings/SettingsScreen.kt`. Define `@Composable fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel(), onNavigateBack: () -> Unit)`. Collect state via `viewModel.container.stateFlow.collectAsState()`. Render a `Scaffold` with `TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, ...) } })`. When `Success(settings)`, render a `LazyColumn` of settings rows: (1) **Theme** — a `Row` with two `FilterChip` composables (`Dark` checked when `settings.theme == "dark"`, `Light` when `"light"`); call `viewModel.updateTheme("dark")` or `updateTheme("light")` on selection. (2) **Density** — three `FilterChip` composables for `Cozy`/`Comfortable`/`Compact`; call `viewModel.updateDensity(...)`. (3) **Show guide lines** — `ListItem` with a trailing `Switch(checked = settings.showGuideLines == 1, onCheckedChange = { viewModel.toggleGuideLines() })`. (4) **Show backlink badge** — same pattern with `toggleBacklinkBadge()`. Update `AppNavHost.kt` to replace the `settings` placeholder with `SettingsScreen(onNavigateBack = { navController.popBackStack() })`.
- **Inputs required**: P4-14 (SettingsViewModel); P3-14 (AppNavHost to update)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/settings/SettingsScreen.kt`
  - `android/app/src/main/java/com/outlinegod/app/ui/navigation/AppNavHost.kt` (updated)
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/ui/screen/settings/SettingsScreenTest.kt`
  - No Compose UI tests. Structural tests only.
  - **Test cases**:
    1. `settingsScreenKt_classExists` — `Class.forName("com.outlinegod.app.ui.screen.settings.SettingsScreenKt")`; assert no exception.
    2. `settingsUiState_hasLoadingSuccessError` — assert `SettingsUiState::class.sealedSubclasses.map { it.simpleName }.containsAll(listOf("Loading", "Success", "Error"))`.
    3. `settingsEntity_density_validValues` — assert `listOf("cozy", "comfortable", "compact")` — these match the values the SettingsViewModel accepts (regression guard if density string values change).
    4. `appNavHost_settingsRoute_isCorrect` — assert `AppRoutes.SETTINGS == "settings"`.
  - **Pass criteria**: `./gradlew test` exits 0, all 4 cases green. `./gradlew assembleDebug` exits 0.
- **Risk**: LOW — all logic is in SettingsViewModel (P4-14); the screen is a thin render layer.

---

### P4-16: WorkManager background sync

- **Task ID**: P4-16
- **Title**: SyncWorker with token refresh, LWW merge application, and 15-minute periodic scheduling
- **What to build**: Create `android/app/src/main/java/com/outlinegod/app/sync/SyncWorker.kt` as a `CoroutineWorker` using Hilt's `@HiltWorker` + `@AssistedInject constructor(@Assisted appContext: Context, @Assisted params: WorkerParameters, private val syncRepository: SyncRepository, private val authRepository: AuthRepository, private val nodeDao: NodeDao, private val documentDao: DocumentDao, private val bookmarkDao: BookmarkDao, private val settingsDao: SettingsDao, private val hlcClock: HlcClock, private val dataStore: DataStore<Preferences>)`. In `doWork()`: (1) attempt `authRepository.refreshToken()` — if it returns `Result.failure`, return `Result.failure()` (token expired, reschedule is not appropriate without a valid token); (2) read `userId` from DataStore; (3) read `lastSyncHlc` from DataStore (key `"last_sync_hlc"`, default `"0"`); (4) read `deviceId` from `authRepository.getDeviceId().first()`; (5) **PULL**: call `syncRepository.pull(lastSyncHlc, deviceId)` — on failure return `Result.retry()`; apply pulled nodes using full LWW merge: for each pulled `NodeSyncRecord`, check if a local `NodeEntity` exists via `nodeDao.getNodesByDocumentSync(documentId)`; if it exists call `NodeMerge.merge(local, incomingEntity)` and `nodeDao.upsertNodes(listOf(merged))`; if it does not exist insert directly; repeat for documents using `DocumentMerge`, bookmarks using `BookmarkMerge`; (6) **PUSH**: collect `documentDao.getPendingChanges(userId, lastSyncHlc, deviceId)` and `nodeDao.getPendingChanges(lastSyncHlc, deviceId)` and `bookmarkDao.getPendingChanges(userId, lastSyncHlc, deviceId)` and `settingsDao.getSettingsSync(userId)` (include if `updatedAt > lastSyncHlcWallMs`); build `SyncPushPayload`; call `syncRepository.push(payload)` — on failure return `Result.retry()`; apply conflicts using merge functions; (7) update DataStore `last_sync_hlc = response.serverHlc`; (8) return `Result.success()`. Create `android/app/src/main/java/com/outlinegod/app/sync/SyncScheduler.kt` as a `@Singleton class` injecting `WorkManager`. Expose `fun schedulePeriodicSync()` — `WorkManager.enqueueUniquePeriodicWork("outlinegod_sync", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED)).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())`. Call `SyncScheduler.schedulePeriodicSync()` from `OutlineGodApp.onCreate()` (inject `SyncScheduler` into `OutlineGodApp` via field injection with `@Inject lateinit var syncScheduler: SyncScheduler`). Add `HiltWorkerFactory` wiring: in `OutlineGodApp.onCreate()`, override `getWorkManagerConfiguration()` returning `Configuration.Builder().setWorkerFactory(hiltWorkerFactory).build()` and remove `WorkManager` from `AndroidManifest.xml` automatic initialization via `<provider android:name="androidx.startup.InitializationProvider" ... tools:node="remove" />` (or use `WorkManagerInitializer` removal approach for Hilt).
- **Inputs required**: P3-10 (NodeMerge, DocumentMerge, BookmarkMerge for LWW application); P3-13 (SyncRepository, SyncPushPayload); P3-12 (AuthRepository.refreshToken, getDeviceId); P3-4, P3-5, P3-6, P3-7 (all DAOs); P3-9 (HlcClock); P4-13 (lastSyncHlc DataStore pattern to reuse same key)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/sync/SyncWorker.kt`
  - `android/app/src/main/java/com/outlinegod/app/sync/SyncScheduler.kt`
  - `android/app/src/main/java/com/outlinegod/app/OutlineGodApp.kt` (updated with HiltWorkerFactory and SyncScheduler)
  - `android/app/src/main/AndroidManifest.xml` (updated to disable automatic WorkManager init)
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/sync/SyncWorkerTest.kt`
  - Test setup: `@RunWith(RobolectricTestRunner::class)`; build `SyncWorker` directly via constructor injection with MockK mocks (not via WorkManager test framework to avoid Hilt complexity in unit tests); use `TestListenableWorkerBuilder` from `work-testing` if Hilt wiring is needed.
  - **Test cases**:
    1. `doWork_refreshesToken_beforeSync` — verify `authRepository.refreshToken()` is called before `syncRepository.pull` in execution order.
    2. `doWork_returnsFailure_whenTokenRefreshFails` — mock `authRepository.refreshToken()` to return `Result.failure(...)`; assert `doWork()` returns `Result.failure()` and `syncRepository.pull` is never called.
    3. `doWork_returnsRetry_onNetworkPullFailure` — mock `syncRepository.pull` to throw `IOException`; assert `doWork()` returns `Result.retry()`.
    4. `doWork_appliesLwwMerge_forConflictingNode` — prepare local node with `contentHlc = "AAAA"` and pulled node with same id and `contentHlc = "BBBB"` (higher); assert `nodeDao.upsertNodes` called with the pulled node's content winning.
    5. `doWork_updatesLastSyncHlc_afterSuccess` — mock pull and push returning `serverHlc = "CCCC"`; run `doWork()`; read DataStore `last_sync_hlc`; assert `"CCCC"`.
    6. `doWork_returnsSuccess_onCleanRun` — all mocks succeed; assert `doWork()` returns `Result.success()`.
    7. `syncScheduler_enqueuesUniquePeriodicWork_withCorrectConstraints` — mock `WorkManager`; call `schedulePeriodicSync()`; capture `WorkRequest`; assert it is `PeriodicWorkRequest` with `NetworkType.CONNECTED` and interval of 15 minutes.
  - **Pass criteria**: `./gradlew test` exits 0, all 7 cases green.
- **Risk**: MEDIUM — token refresh on a background thread: if the access token expires mid-sync, the refresh call itself may race with another concurrent refresh triggered by the 401 interceptor in `KtorClientFactory` (P3-11). Mitigation: `AuthRepository.refreshToken()` is `Mutex`-guarded (P3-12), so at most one refresh runs at a time. The WorkManager refresh (step 1 in `doWork`) pre-empts any 401 retry by ensuring a fresh token before network calls begin. Document this ordering contract with an `// IMPORTANT` comment at the top of `SyncWorker.doWork()`.

---

**Phase 4 exit criteria**: All 16 tasks complete. `./gradlew test` exits 0 (zero failures, zero skipped tests, all Phase 4 test files P4-1 through P4-16 passing). `./gradlew assembleDebug` exits 0. Manual acceptance: the app launches on an API 26+ emulator, Google sign-in completes, the document list screen loads and shows a sync status indicator, opening a document renders nodes in a flat list with inline Markdown formatting, Enter creates a new sibling node, drag-and-drop reorders nodes, the context menu appears on long-press, the settings screen toggles persist across app restarts, and the WorkManager periodic sync job is visible in Android Studio's Background Task Inspector scheduled to run every 15 minutes.
