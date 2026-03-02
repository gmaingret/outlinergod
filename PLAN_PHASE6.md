## Phase 6 — End-to-End Integration Testing & Release Prep

**Goal**: Phase 6 validates that every Phase 3–5 component works correctly together against a real running backend. It adds instrumented Android tests (Espresso / Compose UI Test), a full end-to-end sync round-trip test suite, performance baselines, crash reporting wiring, and release build configuration. By the end of this phase the app passes all automated tests and can be submitted to the Google Play internal track.

**Prerequisite**: All Phase 5 tasks must have passing tests before beginning Phase 6. Phase 5 exit criteria: `./gradlew test` exits 0 (zero failures), `./gradlew assembleDebug` exits 0.

Tasks run in task ID order. Each task is scoped to approximately 2–4 hours.

---

### P6-1: Instrumented test infrastructure

- **Task ID**: P6-1
- **Title**: Configure androidTest directory with Hilt test rules, TestAppModule, and ComposeTestRule helper
- **What to build**: Create `HiltTestRunner` in `com.gmaingret.outlinergod.app` (under `androidTest/`) extending `AndroidJUnitRunner` that returns `HiltTestApplication::class.java.name` from `newApplication()` — register it in `android/app/build.gradle.kts` as `testInstrumentationRunner = "com.gmaingret.outlinergod.app.HiltTestRunner"`. Create `TestAppModule` in `com.gmaingret.outlinergod.app.di` (under `androidTest/`) annotated `@Module @TestInstallIn(components = [SingletonComponent::class], replaces = [NetworkModule::class])` that provides a `HttpClient` with the OkHttp engine pointed at `http://127.0.0.1:3000` — this lets all instrumented tests talk to a local backend without touching production. Create `ComposeTestHelper.kt` in `com.gmaingret.outlinergod.app.util` (under `androidTest/`) with a top-level function `fun launchAppWithHilt(rule: HiltAndroidRule, composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>)` that calls `rule.inject()` then `composeRule.waitForIdle()` — this centralizes the two-line setup every test needs. Add the following `androidTestImplementation` dependencies to `android/app/build.gradle.kts`: `hilt-android-testing`, `androidx.test.ext:junit:1.2.1`, `androidx.test.espresso:espresso-core:3.6.1`, `androidx.compose.ui:ui-test-junit4` (via the Compose BOM), `androidx.work:work-testing:2.10.0`, `androidx.benchmark:benchmark-macro-junit4:1.3.3`.
- **Inputs required**: P3-1 (Gradle project), P3-11 (NetworkModule), P3-14 (MainActivity / AppNavHost)
- **Output artifact**:
  - `android/app/src/androidTest/java/com/outlinergod/app/HiltTestRunner.kt`
  - `android/app/src/androidTest/java/com/outlinergod/app/di/TestAppModule.kt`
  - `android/app/src/androidTest/java/com/outlinergod/app/util/ComposeTestHelper.kt`
  - `android/app/build.gradle.kts` (updated with androidTestImplementation entries and testInstrumentationRunner)
- **How to test**: Run `./gradlew assembleAndroidTest` — the command must exit 0 with no compilation errors, confirming that the Hilt test runner, `TestAppModule`, and all new dependencies resolve correctly. No test methods exist in this task; the artifact is the infrastructure scaffold consumed by P6-2 through P6-11.
- **Risk**: MEDIUM — `@TestInstallIn` replaces the production `NetworkModule`; if any other module depends on a binding from `NetworkModule` that `TestAppModule` omits, the Hilt component generation will fail at compile time; carefully mirror every `@Provides` from `NetworkModule` in `TestAppModule`.

---

### P6-2: Auth flow E2E

- **Task ID**: P6-2
- **Title**: Instrumented test — Google Sign-In mock, DocumentListScreen visibility, DataStore token persistence
- **What to build**: Create `TestAuthModule` in `com.gmaingret.outlinergod.app.di` (under `androidTest/`) annotated `@Module @TestInstallIn(components = [SingletonComponent::class], replaces = [AuthModule::class])`. It provides a `FakeCredentialManager` implementation of `CredentialManager` that immediately returns a hard-coded `GoogleIdTokenCredential` with `idToken = "fake-google-token"` and a `FakeAuthRepository` in `com.gmaingret.outlinergod.app.data.repository.fake` that bypasses real Google token exchange, stores `accessToken = "test-jwt"` and `email = "test@outlinergod.com"` into `EncryptedSharedPreferences` via `AuthRepository.saveTokens()`, and marks the user as authenticated. Create `AuthFlowE2ETest` in `com.gmaingret.outlinergod.app` (under `androidTest/`) annotated `@HiltAndroidTest` with `@get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)` and `@get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()`. In `@Test fun signInDisplaysDocumentListScreen()`, call `launchAppWithHilt(hiltRule, composeRule)`, simulate tapping `composeRule.onNodeWithContentDescription("Sign in with Google").performClick()`, then assert `composeRule.onNodeWithText("test@outlinergod.com").assertIsDisplayed()` confirming the TopAppBar shows the authenticated user's email. In `@Test fun tokenPersistsAcrossActivityRecreation()`, after sign-in recreate the Activity via `composeRule.activityRule.scenario.recreate()`, call `composeRule.waitForIdle()`, then assert `composeRule.onNodeWithText("test@outlinergod.com").assertIsDisplayed()` again — this verifies `AuthRepository` reloads the access token from `EncryptedSharedPreferences` on restart without re-prompting Google sign-in.
- **Inputs required**: P6-1 (test infrastructure), P3-12 (AuthRepository / AuthModule), P4-1 (AuthScreen / sign-in button), P4-3 (DocumentListScreen TopAppBar email display)
- **Output artifact**:
  - `android/app/src/androidTest/java/com/outlinergod/app/di/TestAuthModule.kt`
  - `android/app/src/androidTest/java/com/outlinergod/app/data/repository/fake/FakeAuthRepository.kt`
  - `android/app/src/androidTest/java/com/outlinergod/app/AuthFlowE2ETest.kt`
- **How to test**: File `android/app/src/androidTest/java/com/outlinergod/app/AuthFlowE2ETest.kt`, test methods `signInDisplaysDocumentListScreen` and `tokenPersistsAcrossActivityRecreation`, emulator API 34. Run with `./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gmaingret.outlinergod.app.AuthFlowE2ETest`.
- **Risk**: MEDIUM — `CredentialManager` is a concrete final class in the Credentials library; the fake must implement the interface rather than subclass the production object; verify the interface is accessible in the `credentials` artifact's public API before building.

---

### P6-3: Document CRUD E2E

- **Task ID**: P6-3
- **Title**: Instrumented test — create, rename, and swipe-delete a document; assert Room syncStatus = PENDING
- **What to build**: Create `DocumentCrudE2ETest` in `com.gmaingret.outlinergod.app` (under `androidTest/`) annotated `@HiltAndroidTest`. Inject `@Inject lateinit var documentDao: DocumentDao` (Room DAO available via Hilt in the test class body). In `@Before setUp()`, call `hiltRule.inject()` then sign-in via `FakeAuthRepository` (from P6-2) so the DocumentListScreen is the starting screen. In `@Test fun createDocumentAppearsInList()`: tap the FAB (`composeRule.onNodeWithContentDescription("Create document").performClick()`); wait for the rename dialog (`composeRule.onNodeWithTag("rename_dialog").assertIsDisplayed()`); type a title (`composeRule.onNodeWithTag("rename_input").performTextInput("Phase6Doc")`); confirm (`composeRule.onNodeWithText("Rename").performClick()`); assert `composeRule.onNodeWithText("Phase6Doc").assertIsDisplayed()`; then call `runBlocking { documentDao.getByTitle("Phase6Doc") }` and assert the returned entity is non-null with `syncStatus == SyncStatus.PENDING`. In `@Test fun softDeleteViaSwipeRemovesDocumentFromList()`: seed a document directly via `runBlocking { documentDao.upsert(fakeDocumentEntity("ToDelete")) }`; `composeRule.waitForIdle()`; perform a left swipe on the row (`composeRule.onNodeWithText("ToDelete").performTouchInput { swipeLeft() }`); assert `composeRule.onNodeWithText("ToDelete").assertDoesNotExist()`; verify `runBlocking { documentDao.getByTitle("ToDelete") }?.syncStatus == SyncStatus.PENDING`. In `@Test fun renameDocumentUpdatesListImmediately()`: create a document "OldName"; long-press its row to open the context menu; tap "Rename"; type "NewName"; confirm; assert `composeRule.onNodeWithText("NewName").assertIsDisplayed()` and `composeRule.onNodeWithText("OldName").assertDoesNotExist()`.
- **Inputs required**: P6-2 (TestAuthModule / FakeAuthRepository), P3-5 (DocumentDao), P4-3 (DocumentListScreen), P4-4 (DocumentListViewModel), P3-3 (NodeEntity, DocumentEntity, BookmarkEntity, SettingsEntity with full HLC schema)
- **Output artifact**:
  - `android/app/src/androidTest/java/com/outlinergod/app/DocumentCrudE2ETest.kt`
- **How to test**: File `android/app/src/androidTest/java/com/outlinergod/app/DocumentCrudE2ETest.kt`, test methods `createDocumentAppearsInList`, `softDeleteViaSwipeRemovesDocumentFromList`, `renameDocumentUpdatesListImmediately`, emulator API 34. Run with `./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gmaingret.outlinergod.app.DocumentCrudE2ETest`.
- **Risk**: LOW — all operations are local Room writes; no backend required; `SwipeToDismissBox` swipe gesture via `performTouchInput { swipeLeft() }` is standard Compose test API.

---

### P6-4: Node editor E2E

- **Task ID**: P6-4
- **Title**: Instrumented test — create three nodes with indentation; assert LazyColumn depth order and contentHlc update
- **What to build**: Create `NodeEditorE2ETest` in `com.gmaingret.outlinergod.app` (under `androidTest/`) annotated `@HiltAndroidTest`. Inject `@Inject lateinit var nodeDao: NodeDao` and `@Inject lateinit var documentDao: DocumentDao`. In `@Before setUp()`, call `hiltRule.inject()`, authenticate via `FakeAuthRepository`, seed one document via `runBlocking { documentDao.upsert(fakeDocumentEntity(id = "doc-e2e", title = "E2EDoc")) }`, and navigate to `NodeEditorScreen` by calling `navController.navigate(AppRoutes.nodeEditor("doc-e2e"))` (capture `navController` from `composeRule.activity` via `composeRule.activity.navController`). In `@Test fun threeNodesRenderInCorrectIndentOrder()`: create a root node by typing "Root" into the first empty node `TextField`; press Enter (via `composeRule.onNodeWithTag("node_content_0").performImeAction()`) to create a sibling; type "Child" and tap the indent toolbar button (`composeRule.onNodeWithContentDescription("Indent").performClick()`) to make it a child of Root; press Enter and type "Grandchild" then indent again; call `composeRule.waitForIdle()`; assert the `LazyColumn` contains three `NodeRow` items with test tags `"node_row_0"`, `"node_row_1"`, `"node_row_2"` and verify `"node_row_1"` has a `paddingStart` semantic property greater than `"node_row_0"` and `"node_row_2"` has a `paddingStart` greater than `"node_row_1"` (use `onNodeWithTag("node_row_1").fetchSemanticsNode().config[SemanticsProperties.TestTag]` combined with bounds comparison). In `@Test fun editingRootNodeUpdatesContentHlcInRoom()`: seed one node via `runBlocking { nodeDao.upsert(fakeNodeEntity(id = "n1", documentId = "doc-e2e", contentHlc = "0000000000000001-0000-aabbccdd")) }`; `composeRule.waitForIdle()`; clear and retype the node content (`composeRule.onNodeWithTag("node_content_0").performTextClearance(); composeRule.onNodeWithTag("node_content_0").performTextInput("Updated")`); `composeRule.waitForIdle()`; call `runBlocking { nodeDao.getById("n1") }`; assert `entity.contentHlc > "0000000000000001-0000-aabbccdd"` (lexicographic HLC comparison).
- **Inputs required**: P6-2 (FakeAuthRepository), P3-4 (NodeDao), P3-5 (DocumentDao), P4-5 (NodeEditorScreen), P4-6 (NodeEditorScreen mobile toolbar), P4-7 (indent/outdent in NodeEditorViewModel)
- **Output artifact**:
  - `android/app/src/androidTest/java/com/outlinergod/app/NodeEditorE2ETest.kt`
- **How to test**: File `android/app/src/androidTest/java/com/outlinergod/app/NodeEditorE2ETest.kt`, test methods `threeNodesRenderInCorrectIndentOrder` and `editingRootNodeUpdatesContentHlcInRoom`, emulator API 34. Run with `./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gmaingret.outlinergod.app.NodeEditorE2ETest`.
- **Risk**: MEDIUM — asserting visual indentation depth via Compose semantics bounds requires that `NodeRow` composables expose their `depth` via a `Modifier.semantics { testTag = "node_row_$index" }` and that the test calculates position bounds from `fetchSemanticsNode().boundsInRoot`; if `NodeRow` uses padding derived from depth but doesn't expose it via semantics, bounds comparison is the fallback.

---

### P6-5: Sync round-trip E2E

- **Task ID**: P6-5
- **Title**: Instrumented test — SyncWorker pull populates Room from backend seed; push delivers local PENDING node to server
- **Prerequisite**: The local backend must be running at `http://127.0.0.1:3000` before executing this test. Start it with `docker compose up` (or `node dist/server.js`) from the `backend/` directory.
- **What to build**: Create `SyncRoundTripE2ETest` in `com.gmaingret.outlinergod.app` (under `androidTest/`) annotated `@HiltAndroidTest`. Inject `@Inject lateinit var nodeDao: NodeDao`, `@Inject lateinit var documentDao: DocumentDao`, and `@Inject lateinit var context: Context`. In `@Before setUp()`, call `hiltRule.inject()` and authenticate via `FakeAuthRepository` to obtain a valid JWT, then use `OkHttpClient` to POST a seed document and two seed nodes directly to `http://127.0.0.1:3000/api/documents` and `POST /api/documents/{id}/nodes/batch` (using the test JWT) so the backend has known data before the test runs. In `@Test fun pullPopulatesRoomFromBackendSeedData()`: build and run the worker via `TestListenableWorkerBuilder.from(context, SyncWorker::class.java).build()`; call `val result = worker.startWork().get(30, TimeUnit.SECONDS)`; assert `result is Result.success()`; assert `runBlocking { nodeDao.getById(seedNodeId1) } != null` and `runBlocking { nodeDao.getById(seedNodeId2) } != null` — confirming pull brought both seed nodes into Room. In `@Test fun pushDeliversPendingNodeToBackend()`: insert a `NodeEntity` with `syncStatus = SyncStatus.PENDING` and all HLC fields populated into Room via `nodeDao.upsert()`; run `SyncWorker` via `TestListenableWorkerBuilder`; assert `result is Result.success()`; then call `GET /api/documents/{documentId}/nodes` via `OkHttpClient` and assert the JSON response body contains the new node's ID — confirming the push phase sent the PENDING node to the backend.
- **Inputs required**: P6-1 (test infrastructure), P4-16 (SyncWorker), P5-13 (full LWW SyncWorker upgrade), P3-4 (NodeDao)
- **Output artifact**:
  - `android/app/src/androidTest/java/com/outlinergod/app/SyncRoundTripE2ETest.kt`
- **How to test**: File `android/app/src/androidTest/java/com/outlinergod/app/SyncRoundTripE2ETest.kt`, test methods `pullPopulatesRoomFromBackendSeedData` and `pushDeliversPendingNodeToBackend`, emulator API 34. **Backend must be running at 127.0.0.1:3000.** Run with `./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gmaingret.outlinergod.app.SyncRoundTripE2ETest`. If the backend is unreachable, tests will fail with `ConnectException` — this is expected and not a test infrastructure bug.
- **Risk**: HIGH — requires coordinating a live backend process, an Android emulator, and the WorkManager test builder in the same test run; clock skew between the host machine (backend) and the emulator's wall clock can cause HLC comparison failures if the emulator's clock drifts significantly; mitigate by seeding explicit HLC strings in test data rather than relying on `System.currentTimeMillis()`.

---

### P6-6: Conflict resolution E2E

- **Task ID**: P6-6
- **Title**: Instrumented test — conflicting concurrent edits resolved by NodeMerge via HLC comparison
- **Prerequisite**: The local backend must be running at `http://127.0.0.1:3000` before executing this test.
- **What to build**: Create `ConflictResolutionE2ETest` in `com.gmaingret.outlinergod.app` (under `androidTest/`) annotated `@HiltAndroidTest`. Inject `@Inject lateinit var nodeDao: NodeDao`. In `@Before setUp()`, authenticate via `FakeAuthRepository` and seed a document on the backend via `OkHttpClient`. In `@Test fun deviceAEditsWhenHlcIsHigher()`: simulate "device A" (Room, higher HLC) by upserting a `NodeEntity` with `content = "Device A wins"` and `contentHlc = "0000017b00000005-0002-device-a"` into Room via `nodeDao.upsert()`; simulate "device B" (lower HLC) by posting a `NodeSyncRecord` directly to `POST /api/sync/changes` via `OkHttpClient` with `content = "Device B loses"` and `contentHlc = "0000017b00000005-0001-device-b"` (lexicographically lower); run `SyncWorker` via `TestListenableWorkerBuilder` to execute the full pull→push cycle; call `runBlocking { nodeDao.getById(nodeId) }`; assert `entity.content == "Device A wins"` — confirming `NodeMerge.merge()` picked the higher HLC. In `@Test fun deletionWinsOverConcurrentEdit()`: seed a node on the backend; simulate a local soft-delete in Room by setting `deletedAt = System.currentTimeMillis()` and `deletedHlc = "0000017b00000009-0000-device-a"` (high HLC); simulate a concurrent edit from the backend with `contentHlc = "0000017b00000005-0000-device-b"` (lower HLC than `deletedHlc`); run `SyncWorker`; assert `runBlocking { nodeDao.getById(nodeId) }?.deletedAt != null` — the node remains deleted, confirming deletion-wins semantics from ARCHITECTURE.md §4.
- **Inputs required**: P6-5 (SyncRoundTripE2ETest infrastructure pattern), P3-10 (NodeMerge), P5-13 (SyncWorker LWW upgrade), P3-4 (NodeDao)
- **Output artifact**:
  - `android/app/src/androidTest/java/com/outlinergod/app/ConflictResolutionE2ETest.kt`
- **How to test**: File `android/app/src/androidTest/java/com/outlinergod/app/ConflictResolutionE2ETest.kt`, test methods `deviceAEditsWhenHlcIsHigher` and `deletionWinsOverConcurrentEdit`, emulator API 34. **Backend must be running at 127.0.0.1:3000.** Run with `./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gmaingret.outlinergod.app.ConflictResolutionE2ETest`.
- **Risk**: HIGH — the HLC race window is narrow; if `TestListenableWorkerBuilder` runs the worker synchronously before the backend has processed the conflicting POST, the test may observe an intermediate state rather than the merged state; mitigate by inserting a `Thread.sleep(500)` between posting the conflicting backend record and running the worker, and document this as an acceptable test stabilization technique.

---

### P6-7: Offline / reconnect E2E

- **Task ID**: P6-7
- **Title**: Instrumented test — create nodes while offline, re-enable network, assert SYNCED within 30 seconds
- **Prerequisite**: The local backend must be running at `http://127.0.0.1:3000` before executing this test. The emulator must have a rooted/debug build that allows `svc wifi disable` via `UiDevice.executeShellCommand`.
- **What to build**: Create `OfflineReconnectE2ETest` in `com.gmaingret.outlinergod.app` (under `androidTest/`) annotated `@HiltAndroidTest`. Inject `@Inject lateinit var nodeDao: NodeDao` and `@Inject lateinit var context: Context`. Declare `private val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())` at the class level. In `@After tearDown()`, always call `uiDevice.executeShellCommand("svc wifi enable")` to restore connectivity regardless of test outcome. In `@Test fun nodesCreatedOfflineSyncAfterReconnect()`: authenticate via `FakeAuthRepository`; disable network via `uiDevice.executeShellCommand("svc wifi disable")`; insert two `NodeEntity` objects with `syncStatus = SyncStatus.PENDING` into Room via `runBlocking { nodeDao.upsert(fakeNode1); nodeDao.upsert(fakeNode2) }`; call `composeRule.waitForIdle()`; assert `composeRule.onAllNodesWithContentDescription("Offline banner").onFirst().assertIsDisplayed()` — verifying the `OfflineBanner` (P5-14) is visible; re-enable network via `uiDevice.executeShellCommand("svc wifi enable")`; wait for the `WorkManager` `SyncWorker` to complete by polling `nodeDao.getById(fakeNode1.id)?.syncStatus` with a 30-second timeout: `val startMs = SystemClock.elapsedRealtime(); while (SystemClock.elapsedRealtime() - startMs < 30_000L) { if (nodeDao.getById(fakeNode1.id)?.syncStatus == SyncStatus.SYNCED && nodeDao.getById(fakeNode2.id)?.syncStatus == SyncStatus.SYNCED) break; Thread.sleep(500) }`; after the loop, assert both nodes have `syncStatus == SyncStatus.SYNCED`.
- **Inputs required**: P6-2 (FakeAuthRepository), P5-14 (OfflineBanner / OfflineRepository), P4-16 (SyncWorker WorkManager registration with CONNECTED constraint), P3-4 (NodeDao)
- **Output artifact**:
  - `android/app/src/androidTest/java/com/outlinergod/app/OfflineReconnectE2ETest.kt`
- **How to test**: File `android/app/src/androidTest/java/com/outlinergod/app/OfflineReconnectE2ETest.kt`, test method `nodesCreatedOfflineSyncAfterReconnect`, emulator API 34. **Backend must be running at 127.0.0.1:3000.** Run with `./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gmaingret.outlinergod.app.OfflineReconnectE2ETest`. The 30-second polling loop is the primary reliability concern — if the backend is slow or the emulator's `WorkManager` constraint detection is delayed, increase the timeout to 60 seconds.
- **Risk**: MEDIUM — `svc wifi disable` / `svc wifi enable` shell commands are timing-sensitive on emulators; the emulator's `ConnectivityManager` may report `onAvailable` before actual TCP connectivity is established, causing the first sync attempt to fail and fall back to exponential backoff; the 30-second timeout accommodates one failed retry at the base 30-second backoff interval.

---

### P6-8: Search E2E

- **Task ID**: P6-8
- **Title**: Instrumented test — seed backend documents, search for keyword, tap result, assert NodeEditorScreen opens
- **Prerequisite**: The local backend must be running at `http://127.0.0.1:3000` before executing this test.
- **What to build**: Create `SearchE2ETest` in `com.gmaingret.outlinergod.app` (under `androidTest/`) annotated `@HiltAndroidTest`. In `@Before setUp()`, authenticate via `FakeAuthRepository`; then use `OkHttpClient` to create three documents on the backend (`POST /api/documents`) and batch-upsert nodes for each: Document 1 with node content `"outlinergod-unique-keyword-alpha"`, Document 2 with `"ordinary content"`, Document 3 with `"ordinary content too"`; record the `documentId` and `nodeId` of Document 1's matching node for later assertion. In `@Test fun searchKeywordReturnsCorrectDocumentGroupWithin2Seconds()`: navigate to `SearchScreen` by tapping the Search tab in the `NavigationBar` (`composeRule.onNodeWithContentDescription("Search").performClick()`); type the unique keyword into the search field (`composeRule.onNodeWithTag("search_field").performTextInput("outlinergod-unique-keyword-alpha")`); wait up to 2 seconds for the results using `composeRule.waitUntil(timeoutMillis = 2_000L) { composeRule.onAllNodesWithTag("search_result_group").fetchSemanticsNodes().isNotEmpty() }`; assert `composeRule.onAllNodesWithTag("search_result_group").assertCountEquals(1)` — only one document group matches; assert `composeRule.onNodeWithText("outlinergod-unique-keyword-alpha").assertIsDisplayed()` — the matching node content appears in the result row. In `@Test fun tappingSearchResultNavigatesToNodeEditorScreen()`: perform the same search as above; tap the result row (`composeRule.onNodeWithTag("search_result_row_0").performClick()`); assert `composeRule.onNodeWithTag("node_editor_screen").assertIsDisplayed()` — the `NodeEditorScreen` is visible; assert the document title displayed in the TopAppBar matches the seeded document title (use `composeRule.onNodeWithTag("top_app_bar_title").assertTextEquals("Document 1 Title")`).
- **Inputs required**: P6-2 (FakeAuthRepository), P5-1 (SearchViewModel), P5-2 (SearchScreen), P5-15 (AppNavHost bottom NavigationBar)
- **Output artifact**:
  - `android/app/src/androidTest/java/com/outlinergod/app/SearchE2ETest.kt`
- **How to test**: File `android/app/src/androidTest/java/com/outlinergod/app/SearchE2ETest.kt`, test methods `searchKeywordReturnsCorrectDocumentGroupWithin2Seconds` and `tappingSearchResultNavigatesToNodeEditorScreen`, emulator API 34. **Backend must be running at 127.0.0.1:3000.** Run with `./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gmaingret.outlinergod.app.SearchE2ETest`.
- **Risk**: LOW — the search endpoint is a straightforward GET; the 2-second `waitUntil` window accommodates the `SearchViewModel`'s 400ms debounce plus network round-trip; the unique keyword eliminates false-positive result matches.

---

### P6-9: Bookmark E2E

- **Task ID**: P6-9
- **Title**: Instrumented test — create bookmark via FAB, drag to new position, assert sort_order changed, soft-delete with Undo
- **What to build**: Create `BookmarkE2ETest` in `com.gmaingret.outlinergod.app` (under `androidTest/`) annotated `@HiltAndroidTest`. Inject `@Inject lateinit var bookmarkDao: BookmarkDao`. In `@Before setUp()`, authenticate via `FakeAuthRepository` and navigate to `BookmarkListScreen` by tapping the Bookmarks tab (`composeRule.onNodeWithContentDescription("Bookmarks").performClick()`). In `@Test fun createBookmarkAppearsInListWithPendingSyncStatus()`: tap the FAB (`composeRule.onNodeWithContentDescription("Create bookmark").performClick()`); type title "E2EBookmark" into the `CreateBookmarkBottomSheet` title field; select "document" target type via the `SegmentedButton`; tap "Confirm"; assert `composeRule.onNodeWithText("E2EBookmark").assertIsDisplayed()`; call `runBlocking { bookmarkDao.getAll().first { it.title == "E2EBookmark" } }` and assert `entity.syncStatus == SyncStatus.PENDING`. In `@Test fun dragBookmarkToNewPositionChangeSortOrderAndPersists()`: seed two `BookmarkEntity` objects via `runBlocking { bookmarkDao.upsert(bm1); bookmarkDao.upsert(bm2) }` with `sortOrder = "V"` and `sortOrder = "a"` respectively so bm1 appears first; perform a long-press drag on bm1's drag handle to below bm2's position using `composeRule.onNodeWithTag("bookmark_drag_handle_0").performTouchInput { longClick(); moveBy(Offset(0f, 200f)); up() }`; `composeRule.waitForIdle()`; call `runBlocking { bookmarkDao.getById(bm1.id)!! }` and assert `entity.sortOrder != "V"` (sort order was regenerated after the reorder). In `@Test fun softDeleteBookmarkShowsUndoThenRestores()`: seed one `BookmarkEntity` "ToDeleteBm"; perform swipe-left dismiss on its row; assert `composeRule.onNodeWithText("ToDeleteBm").assertDoesNotExist()`; assert the Undo snackbar is visible (`composeRule.onNodeWithText("Undo").assertIsDisplayed()`); tap Undo; assert `composeRule.onNodeWithText("ToDeleteBm").assertIsDisplayed()` and `runBlocking { bookmarkDao.getById("ToDeleteBm-id")!! }.deletedAt == null`.
- **Inputs required**: P6-2 (FakeAuthRepository), P3-6 (BookmarkDao), P5-3 (BookmarkListViewModel), P5-4 (BookmarkListScreen), P5-15 (AppNavHost NavigationBar)
- **Output artifact**:
  - `android/app/src/androidTest/java/com/outlinergod/app/BookmarkE2ETest.kt`
- **How to test**: File `android/app/src/androidTest/java/com/outlinergod/app/BookmarkE2ETest.kt`, test methods `createBookmarkAppearsInListWithPendingSyncStatus`, `dragBookmarkToNewPositionChangeSortOrderAndPersists`, `softDeleteBookmarkShowsUndoThenRestores`, emulator API 34. Run with `./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gmaingret.outlinergod.app.BookmarkE2ETest`.
- **Risk**: LOW — bookmark operations are fully local (Room); drag gesture in Compose tests via `performTouchInput` is well-supported by `sh.calvin.reorderable`'s test integration; Undo action is a deterministic UI state transition.

---

### P6-10: Export E2E

- **Task ID**: P6-10
- **Title**: Instrumented test — trigger Markdown export via TopAppBar overflow, intercept ACTION_SEND intent, assert EXTRA_TEXT content
- **What to build**: Create `ExportE2ETest` in `com.gmaingret.outlinergod.app` (under `androidTest/`) annotated `@HiltAndroidTest`. The test uses `androidx.test.espresso.intent.Intents` for intent interception. In `@Before setUp()`, call `Intents.init()` then authenticate via `FakeAuthRepository`; seed a document via `runBlocking { documentDao.upsert(fakeDocumentEntity(id = "export-doc", title = "ExportTitle")) }` and seed one node with `content = "First node content"` via `nodeDao.upsert()`; navigate to `NodeEditorScreen` for "export-doc". In `@After tearDown()`, call `Intents.release()`. In `@Test fun markdownExportFiresActionSendIntentWithTitleAsHeading()`: tap the TopAppBar overflow menu icon (`composeRule.onNodeWithContentDescription("More options").performClick()`); tap "Export" in the dropdown (`composeRule.onNodeWithText("Export").performClick()`); assert the `ExportBottomSheet` `ModalBottomSheet` is displayed; stub the intent result so the share chooser resolves immediately (`intending(hasAction(Intent.ACTION_SEND)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))`); tap "Export as Markdown" (`composeRule.onNodeWithText("Export as Markdown").performClick()`); call `composeRule.waitUntil(timeoutMillis = 5_000L) { Intents.getIntents().any { it.action == Intent.ACTION_SEND } }`; assert `intended(hasAction(Intent.ACTION_SEND))`; capture the intercepted intent via `Intents.getIntents().last()` and assert `intent.getStringExtra(Intent.EXTRA_TEXT)!!.contains("# ExportTitle")` — confirming the document title is formatted as a Markdown `h1` heading in the exported body.
- **Inputs required**: P6-2 (FakeAuthRepository), P5-9 (ExportViewModel), P5-10 (ExportBottomSheet), P4-5 (NodeEditorScreen TopAppBar overflow menu)
- **Output artifact**:
  - `android/app/src/androidTest/java/com/outlinergod/app/ExportE2ETest.kt`
- **How to test**: File `android/app/src/androidTest/java/com/outlinergod/app/ExportE2ETest.kt`, test method `markdownExportFiresActionSendIntentWithTitleAsHeading`, emulator API 34. Run with `./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gmaingret.outlinergod.app.ExportE2ETest`. Note: this test validates the intent emission only; it does not verify the full Markdown formatting of all nodes (covered by `ExportViewModelTest` unit tests in Phase 5).
- **Risk**: LOW — `Intents.intended(hasAction(Intent.ACTION_SEND))` is the standard Espresso Intents pattern; the only complication is ensuring the `ExportViewModel` uses the `TestAppModule`'s `HttpClient` (pointed at 127.0.0.1:3000) so the export GET succeeds; if the backend is not running, stub the export HTTP call in a `TestExportModule` that overrides the `ExportViewModel`'s Ktor client to return a fixed Markdown string.

---

### P6-11: Image upload E2E

- **Task ID**: P6-11
- **Title**: Instrumented test — mock photo picker URI, assert POST /api/files multipart request, assert markdown image in TextFieldState
- **What to build**: Create `ImageUploadE2ETest` in `com.gmaingret.outlinergod.app` (under `androidTest/`) annotated `@HiltAndroidTest`. Create a companion `TestNetworkModuleWithMockEngine` in `com.gmaingret.outlinergod.app.di` (under `androidTest/`) annotated `@Module @TestInstallIn(components = [SingletonComponent::class], replaces = [NetworkModule::class])` that provides a `HttpClient` configured with `MockEngine` instead of OkHttp — the `MockEngine` is exposed as a `@Inject lateinit var mockEngine: MockEngine` in the test class so tests can inspect requests. In `@Before setUp()`, authenticate via `FakeAuthRepository` and navigate to `NodeEditorScreen` with a seeded document and one node already in Room. In `@Test fun photoPickerUriResultsInMultipartPostAndMarkdownInsertion()`: configure `mockEngine` to respond to `POST /api/files` with `{ "url": "/api/files/test-uuid.png", "uuid": "test-uuid", "filename": "test-uuid.png", "size": 50000, "mime_type": "image/png" }`; register a custom `ActivityResultRegistry` test double that immediately delivers `Uri.parse("android.resource://com.gmaingret.outlinergod.app/raw/test_image_asset")` for any `PickVisualMedia` launch: create an `ActivityResultRegistry` override inside the test and provide it to the `MainActivity` via Hilt's `@BindValue` on a `ActivityResultRegistry` binding; tap the attachment toolbar button (`composeRule.onNodeWithContentDescription("Add attachment").performClick()`); `composeRule.waitUntil(timeoutMillis = 5_000L) { mockEngine.requestHistory.any { it.url.encodedPath == "/api/files" } }`; assert `mockEngine.requestHistory.last().url.encodedPath == "/api/files"` and `mockEngine.requestHistory.last().method == HttpMethod.Post`; assert the request body is a `MultiPartFormDataContent` by checking the `Content-Type` header contains `"multipart/form-data"`; assert `composeRule.onNodeWithTag("node_content_0").fetchSemanticsNode().config[SemanticsProperties.EditableText].text.contains("![](/api/files/test-uuid.png)")`.
- **Inputs required**: P6-1 (test infrastructure), P6-2 (FakeAuthRepository), P5-11 (image upload in NodeEditorViewModel), P4-5 (NodeEditorScreen mobile toolbar)
- **Output artifact**:
  - `android/app/src/androidTest/java/com/outlinergod/app/di/TestNetworkModuleWithMockEngine.kt`
  - `android/app/src/androidTest/java/com/outlinergod/app/ImageUploadE2ETest.kt`
  - `android/app/src/androidTest/res/raw/test_image_asset.png` (a bundled < 1 MB PNG for the test)
- **How to test**: File `android/app/src/androidTest/java/com/outlinergod/app/ImageUploadE2ETest.kt`, test method `photoPickerUriResultsInMultipartPostAndMarkdownInsertion`, emulator API 34. Run with `./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gmaingret.outlinergod.app.ImageUploadE2ETest`.
- **Risk**: MEDIUM — intercepting `ActivityResultRegistry` in an instrumented test requires the `MainActivity` to accept the registry from the outside; this is standard in `ComponentActivity` via constructor injection of `ActivityResultRegistry`; verify that the Hilt `@BindValue` approach for overriding the registry is compatible with the `MainActivity` constructor signature used in Phase 3.

---

### P6-12: Performance baselines

- **Task ID**: P6-12
- **Title**: Macrobenchmark — cold startup, scroll jank (200 nodes), SyncWorker round-trip (500 nodes)
- **What to build**: Create a new Gradle module `android/benchmark/` as a `com.android.test` module with dependency `implementation(libs.benchmark.macro.junit4)`. In `android/benchmark/src/main/java/com/outlinergod/app/benchmark/`, create three benchmark classes. `StartupBenchmark`: annotated `@RunWith(AndroidJUnit4::class)`, contains `@get:Rule val benchmarkRule = MacrobenchmarkRule()` and `@Test fun coldStartup()` which calls `benchmarkRule.measureRepeated(packageName = "com.gmaingret.outlinergod.app", metrics = listOf(StartupTimingMetric()), compilationMode = CompilationMode.None(), startupMode = StartupMode.COLD, iterations = 5) { pressHome(); startActivityAndWait() }` — target threshold is `< 600ms` timeToInitialDisplay on a Pixel 6 emulator; document in the output file that this is a guideline, not a CI gate. `ScrollJankBenchmark`: `@Test fun scrollNodeListWith200Nodes()` seeds 200 nodes directly in the target process via `device.executeShellCommand(...)` then measures `JankMetric()` during a fling scroll on the `NodeEditorScreen` `LazyColumn`; target threshold is `< 3` janky frames per fling; document as guideline. `SyncWorkerBenchmark`: `@Test fun syncRoundTripWith500Nodes()` measures `TraceSectionMetric("SyncWorker.doWork")` over 3 iterations with 500 nodes seeded on the local backend; target threshold is `< 2s` per full round-trip; document as guideline. After each benchmark run, write results to `android/benchmark-results/` using `BenchmarkState`'s JSON output via `benchmarkRule.getMetricResult()` serialization.
- **Inputs required**: P6-1 (androidTestImplementation benchmark-macro-junit4 dependency already declared), P4-16 / P5-13 (SyncWorker), P4-5 (NodeEditorScreen LazyColumn)
- **Output artifact**:
  - `android/benchmark/build.gradle.kts`
  - `android/benchmark/src/main/java/com/outlinergod/app/benchmark/StartupBenchmark.kt`
  - `android/benchmark/src/main/java/com/outlinergod/app/benchmark/ScrollJankBenchmark.kt`
  - `android/benchmark/src/main/java/com/outlinergod/app/benchmark/SyncWorkerBenchmark.kt`
  - `android/benchmark-results/` (directory, created at runtime)
- **How to test**: Run `./gradlew :benchmark:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.profiling.mode=None` — benchmark results are written to `android/benchmark-results/` as JSON files. Review the `timeToInitialDisplayMs`, `jankCount`, and `traceSectionDurationMs` values against the documented thresholds. **Thresholds are guidelines, not CI gates** — emulator performance varies by host machine CPU; do not fail the build based on benchmark numbers alone.
- **Risk**: MEDIUM — macrobenchmark results on emulators are inherently noisy (±30% variance between runs); the `ScrollJankBenchmark` in particular is sensitive to the emulator's GPU emulation quality; if the emulator does not support hardware GPU acceleration, jank results will be meaningless; document this limitation in `android/benchmark-results/README.md`.

---

### P6-13: Release build configuration

- **Task ID**: P6-13
- **Title**: Configure release buildType with minification, ProGuard rules, and env-var signing
- **What to build**: In `android/app/build.gradle.kts`, add a `release` buildType inside `buildTypes {}` with: `isMinifyEnabled = true`, `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`, `isShrinkResources = true`. Add a `signingConfigs` block above `buildTypes {}` defining `release` signing config that reads `storeFile = file(System.getenv("KEYSTORE_PATH") ?: "debug.keystore")`, `storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"`, `keyAlias = System.getenv("KEY_ALIAS") ?: "androiddebugkey"`, `keyPassword = System.getenv("KEY_PASSWORD") ?: "android"` — the fallback values point to the debug keystore so local builds continue to work without env vars set. Wire the signing config to the release buildType via `signingConfig = signingConfigs.getByName("release")`. Create `android/app/proguard-rules.pro` with four keep rules: (1) Hilt generated components — `-keep class dagger.hilt.** { *; }` and `-keep class hilt_aggregated_deps.** { *; }` and `-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel`; (2) Room entities — `-keep class com.gmaingret.outlinergod.app.db.entity.** { *; }`; (3) kotlinx.serialization — `-keepattributes *Annotation*, InnerClasses` and `-dontnote kotlinx.serialization.**` and `-keep class kotlinx.serialization.** { *; }` and `-keep @kotlinx.serialization.Serializable class com.gmaingret.outlinergod.app.** { *; }`; (4) Orbit MVI — `-keep class org.orbitmvi.** { *; }`. Add `./gradlew bundleRelease` as a CI verification step by creating `android/.github/workflows/ci.yml` (or noting in comments that this command must be added to the existing CI pipeline) that runs `./gradlew bundleRelease` after `./gradlew test`.
- **Inputs required**: P3-1 (Gradle project), all prior phases (all classes that must survive R8 minification)
- **Output artifact**:
  - `android/app/build.gradle.kts` (updated with release buildType and signingConfigs)
  - `android/app/proguard-rules.pro`
- **How to test**: Set env vars `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` to point to a test keystore (or omit them to use the debug keystore fallback), then run `./gradlew bundleRelease` — the command must exit 0 and produce `android/app/build/outputs/bundle/release/app-release.aab`. Verify R8 did not strip Hilt components by inspecting the AAB's `classes.dex` for `com.gmaingret.outlinergod.app.HiltComponents` using `apkanalyzer`.
- **Risk**: LOW — env-var pattern for signing is standard CI practice (GitHub Actions, CircleCI, Bitrise all support it); the ProGuard rules for Hilt and kotlinx.serialization are documented by each library's maintainers and have no novel risk.

---

### P6-14: Final exit criteria verification

- **Task ID**: P6-14
- **Title**: Run full three-suite test pipeline and document results in TEST_RESULTS.md
- **What to build**: This task produces no new code — it runs all three test suites in sequence and documents the results. Execute the following commands in order: (1) `./gradlew test` — runs all unit tests from Phases 3–5 across all `*Test.kt` files in `android/app/src/test/`; (2) `./gradlew connectedAndroidTest` — runs all instrumented tests from P6-2 through P6-11 in `android/app/src/androidTest/`; the backend must be running at `127.0.0.1:3000` before this command executes; (3) `./gradlew bundleRelease` — produces the release AAB; all three commands must exit 0. Create `android/TEST_RESULTS.md` documenting: total unit test count and pass count from step (1); total instrumented test count and pass count from step (2); release AAB file size in MB from step (3); benchmark results from P6-12 (startup time ms, jank frames, sync duration ms) copied from `android/benchmark-results/`; any tests skipped with `@Ignore` listed with their justification comments. If any test fails, the phase is NOT complete — fix the underlying test or implementation before marking P6-15 done.
- **Inputs required**: All P6-1 through P6-13 tasks completed
- **Output artifact**:
  - `android/TEST_RESULTS.md`
  - `android/app/build/outputs/bundle/release/app-release.aab` (produced by step 3)
- **How to test**: `android/TEST_RESULTS.md` must exist with non-zero pass counts for both unit and instrumented test suites, and must record a successful `bundleRelease` output. The presence of `app-release.aab` confirms step (3) succeeded.
- **Risk**: LOW — this task aggregates prior work; any failures discovered here are root-caused to earlier tasks rather than to P6-15 itself; the task is complete only when all three commands exit 0 and `TEST_RESULTS.md` is written.

---

## Phase 6 Exit Criteria

- `./gradlew test` exits 0 with zero unit test failures across all Phase 3–5 test files
- `./gradlew connectedAndroidTest` exits 0 with zero instrumented test failures across all P6-2 through P6-11 test files (emulator API 34, backend running at 127.0.0.1:3000)
- `./gradlew bundleRelease` exits 0 and produces `android/app/build/outputs/bundle/release/app-release.aab`
- `android/TEST_RESULTS.md` exists documenting pass counts and benchmark results
- All instrumented tests use `@HiltAndroidTest` and `HiltAndroidRule` — no test bypasses Hilt DI
- No real Google credentials appear in any committed file — `GOOGLE_CLIENT_ID` is injected via `BuildConfig` from `build.gradle.kts` (not hardcoded)
- ProGuard rules preserve all Room entities, Hilt components, kotlinx.serialization annotations, and Orbit MVI classes — verified by running the release AAB through `apkanalyzer`
- No `@Ignore`-annotated test without a written inline comment explaining why
- Benchmark thresholds documented as guidelines in `android/benchmark-results/README.md` — not enforced as CI gates
