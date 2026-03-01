## Phase 3 — Android Project Setup

**Goal**: A buildable Android project with complete infrastructure: Gradle Kotlin DSL project, Hilt DI wiring, Room database with all entities and DAOs, HLC clock and LWW merge logic, Ktor HTTP client with auth interceptor, AuthRepository and SyncRepository, navigation scaffold, and app entry point. No user-visible UI screens are built in this phase — only the infrastructure that Phase 4 builds on top of. `./gradlew test` exits 0 with zero failures across all test files.

**Prerequisite**: All Phase 0 prototypes must have passing tests before beginning Phase 3. Phase 1 and Phase 2 backend work may proceed in parallel with Phase 3.

Tasks run in task ID order. Each task is scoped to approximately 1–2 hours.

---

### P3-1: Android project scaffold

- **Task ID**: P3-1
- **Title**: Gradle Kotlin DSL project with version catalog and all dependency declarations
- **What to build**: Initialize the `android/` directory as an Android Gradle project using Kotlin DSL throughout (no Groovy). Create `android/settings.gradle.kts` defining `rootProject.name = "outlinegod"` and including the `:app` module. Create `android/gradle/libs.versions.toml` as the single version catalog with the following entries (all versions pinned): `kotlin = "2.1.0"`, `ksp = "2.1.0-1.0.29"`, `agp = "8.7.3"`, `compose-bom = "2025.03.00"`, `hilt = "2.52"`, `room = "2.8.4"`, `ktor = "3.4.0"`, `kotlinx-serialization = "1.8.0"`, `kotlinx-coroutines = "1.10.1"`, `datastore = "1.1.1"`, `navigation-compose = "2.8.5"`, `kotest = "6.0.0"`, `kotest-runner-junit4 = "6.0.0"`, `mockk = "1.14.0"`, `credentials = "1.3.0"`, `google-id = "1.1.1"`, `robolectric = "4.13"`. Create `android/app/build.gradle.kts` with: `applicationId = "com.gmaingret.outlinergod"`, `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`, Kotlin plugin, KSP plugin, Hilt plugin, and all library dependencies referencing the version catalog. All Compose dependencies must reference `platform(libs.compose.bom)`. Enable `buildFeatures { compose = true; buildConfig = true }`. Add `testOptions { unitTests { isIncludeAndroidResources = true } }` to support Robolectric for Room DAO tests. Create `android/build.gradle.kts` (root) and the initial `android/app/src/main/AndroidManifest.xml` with `<uses-permission android:name="android.permission.INTERNET" />`.
- **Inputs required**: None — this is the first Phase 3 task
- **Output artifact**:
  - `android/settings.gradle.kts`
  - `android/gradle/libs.versions.toml`
  - `android/build.gradle.kts`
  - `android/app/build.gradle.kts`
  - `android/app/src/main/AndroidManifest.xml`
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/SmokeTest.kt`
  - **Test cases**:
    1. `smoke_applicationId_isCorrect` — assert `BuildConfig.APPLICATION_ID == "com.gmaingret.outlinergod"`.
    2. `smoke_unitTestFramework_isAvailable` — assert `1 + 1 == 2` (confirms JUnit 4 is on the test classpath and `./gradlew test` can reach this file).
  - **Pass criteria**: `./gradlew assembleDebug` exits 0; `./gradlew test` exits 0, both cases green.
- **Risk**: LOW — standard Gradle Kotlin DSL bootstrap. The only common failure mode is a version catalog entry that references a non-existent Maven coordinate; verify each dependency resolves via `./gradlew dependencies` before proceeding.

---

### P3-2: Hilt application class and module definitions

- **Task ID**: P3-2
- **Title**: HiltAndroidApp, NetworkModule, DatabaseModule, RepositoryModule, and ClockModule
- **What to build**: Create `android/app/src/main/java/com/outlinegod/app/OutlineGodApp.kt` annotated `@HiltAndroidApp` extending `Application`. Define four Hilt modules under `android/app/src/main/java/com/outlinegod/app/di/`:
  - `NetworkModule.kt` (`@Module @InstallIn(SingletonComponent::class)`): declares a `@Provides @Singleton` binding for the Ktor `HttpClient` (the real implementation is wired in P3-11; for this task provide a placeholder `HttpClient(OkHttp) {}`) and a `@Provides @Named("baseUrl") String` reading `BuildConfig.BASE_URL` (add `BASE_URL` to `buildConfigField` in `build.gradle.kts`, defaulting to `"http://10.0.2.2:3000"` for emulator).
  - `DatabaseModule.kt` (`@Module @InstallIn(SingletonComponent::class)`): declares `@Provides @Singleton AppDatabase` (wired in P3-8; placeholder throws `NotImplementedError` for now) and four `@Provides` bindings for `NodeDao`, `DocumentDao`, `BookmarkDao`, `SettingsDao` delegating to `appDatabase.nodeDao()` etc.
  - `RepositoryModule.kt` (`@Module @InstallIn(SingletonComponent::class)`): declares `@Binds` stubs for all repository interface → implementation bindings (filled in P3-12 and P3-13). Add one concrete no-op `@Provides` to prevent Hilt from rejecting an otherwise abstract-only module.
  - `ClockModule.kt` (`@Module @InstallIn(SingletonComponent::class)`): declares `@Provides @Singleton HlcClock` (placeholder throws `NotImplementedError`; replaced when P3-9 is complete).
  - Create `@TestInstallIn` replacement modules in `android/app/src/test/java/com/outlinegod/app/di/`: `TestNetworkModule.kt` that provides a `MockEngine`-backed `HttpClient`, and `TestDatabaseModule.kt` that provides an in-memory `AppDatabase` (wired in P3-8).
- **Inputs required**: P3-1 (Gradle scaffold with Hilt dependency declared)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/OutlineGodApp.kt`
  - `android/app/src/main/java/com/outlinegod/app/di/NetworkModule.kt`
  - `android/app/src/main/java/com/outlinegod/app/di/DatabaseModule.kt`
  - `android/app/src/main/java/com/outlinegod/app/di/RepositoryModule.kt`
  - `android/app/src/main/java/com/outlinegod/app/di/ClockModule.kt`
  - `android/app/src/test/java/com/outlinegod/app/di/TestNetworkModule.kt`
  - `android/app/src/test/java/com/outlinegod/app/di/TestDatabaseModule.kt`
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/di/HiltModuleTest.kt`
  - **Test cases**:
    1. `outlineGodApp_isAnnotatedWithHiltAndroidApp` — use reflection: assert `OutlineGodApp::class.java.isAnnotationPresent(HiltAndroidApp::class.java)`.
    2. `networkModule_isAnnotatedWithModule` — assert `NetworkModule::class.java.isAnnotationPresent(Module::class.java)`.
    3. `databaseModule_isAnnotatedWithModule` — assert `DatabaseModule::class.java.isAnnotationPresent(Module::class.java)`.
    4. `allFourModules_haveInstallIn_annotation` — assert `NetworkModule`, `DatabaseModule`, `RepositoryModule`, `ClockModule` all carry `@InstallIn`.
  - **Pass criteria**: `./gradlew test` exits 0, all 4 cases green. `./gradlew kspDebugKotlin` exits 0 (Hilt component generation clean).
- **Risk**: LOW — module skeleton without real business logic. A missing `@Provides` for a required type produces a Hilt compile error (not a runtime crash), making failures immediately obvious.

---

### P3-3: Room entity definitions

- **Task ID**: P3-3
- **Title**: NodeEntity, DocumentEntity, BookmarkEntity, SettingsEntity with full HLC schema
- **What to build**: Create four Room `@Entity` data classes in `android/app/src/main/java/com/outlinegod/app/db/entity/`. Every syncable field must have a companion `_hlc` TEXT column. All column names use exact snake_case via `@ColumnInfo(name = "column_name")` matching the backend Drizzle schema column names precisely. Nullability mirrors the backend schema.
  - `NodeEntity.kt` (`@Entity(tableName = "nodes")`): `@PrimaryKey val id: String`, `@ColumnInfo(name = "document_id") val documentId: String`, `@ColumnInfo(name = "user_id") val userId: String`, `content: String = ""`, `content_hlc: String = ""`, `note: String = ""`, `note_hlc: String = ""`, `@ColumnInfo(name = "parent_id") val parentId: String? = null`, `parent_id_hlc: String = ""`, `@ColumnInfo(name = "sort_order") val sortOrder: String` (TEXT — never a numeric type), `sort_order_hlc: String = ""`, `completed: Int = 0`, `completed_hlc: String = ""`, `color: Int = 0`, `color_hlc: String = ""`, `collapsed: Int = 0`, `collapsed_hlc: String = ""`, `@ColumnInfo(name = "deleted_at") val deletedAt: Long? = null`, `deleted_hlc: String = ""`, `@ColumnInfo(name = "device_id") val deviceId: String = ""`, `@ColumnInfo(name = "created_at") val createdAt: Long`, `@ColumnInfo(name = "updated_at") val updatedAt: Long`, `val syncStatus: Int = 0  // 0 = SYNCED, 1 = PENDING`. No `children` field.
  - `DocumentEntity.kt` (`@Entity(tableName = "documents")`): `id`, `user_id`, `title`, `title_hlc`, `type` (String — "document" or "folder"), `parent_id` (String? — self-referencing), `parent_id_hlc`, `sort_order` (TEXT, never numeric), `sort_order_hlc`, `collapsed` (Int), `collapsed_hlc`, `deleted_at` (Long?), `deleted_hlc`, `device_id`, `created_at`, `updated_at`, `val syncStatus: Int = 0  // 0 = SYNCED, 1 = PENDING`. All column names match the backend documents table exactly.
  - `BookmarkEntity.kt` (`@Entity(tableName = "bookmarks")`): `id`, `user_id`, `title`, `title_hlc`, `target_type`, `target_type_hlc`, `target_document_id` (String?), `target_document_id_hlc`, `target_node_id` (String?), `target_node_id_hlc`, `query` (String?), `query_hlc`, `sort_order`, `sort_order_hlc`, `deleted_at` (Long?), `deleted_hlc`, `device_id`, `created_at`, `updated_at`, `val syncStatus: Int = 0  // 0 = SYNCED, 1 = PENDING`.
  - `SettingsEntity.kt` (`@Entity(tableName = "settings")`): `@PrimaryKey @ColumnInfo(name = "user_id") val userId: String`, `theme: String = "dark"`, `theme_hlc: String = ""`, `density: String = "cozy"`, `density_hlc: String = ""`, `show_guide_lines: Int = 1`, `show_guide_lines_hlc: String = ""`, `show_backlink_badge: Int = 1`, `show_backlink_badge_hlc: String = ""`, `device_id: String = ""`, `updated_at: Long`.
  Create `SyncStatus.kt` defining `enum class SyncStatus { SYNCED, PENDING }` in package `com.gmaingret.outlinergod.db.entity`.
- **Inputs required**: P3-1 (Room 2.8.4 + KSP declared in version catalog)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/db/entity/NodeEntity.kt`
  - `android/app/src/main/java/com/outlinegod/app/db/entity/DocumentEntity.kt`
  - `android/app/src/main/java/com/outlinegod/app/db/entity/BookmarkEntity.kt`
  - `android/app/src/main/java/com/outlinegod/app/db/entity/SettingsEntity.kt`
  - `android/app/src/main/java/com/outlinegod/app/db/entity/SyncStatus.kt`
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/db/entity/EntitySchemaTest.kt`
  - **Test cases**:
    1. `nodeEntity_hasAllEightHlcColumns` — use Kotlin reflection on `NodeEntity::class.memberProperties`; assert `content_hlc`, `note_hlc`, `parent_id_hlc`, `sort_order_hlc`, `completed_hlc`, `color_hlc`, `collapsed_hlc`, `deleted_hlc` all exist as named properties.
    2. `nodeEntity_hasNoChildrenField` — assert `NodeEntity::class.memberProperties` contains no property named `children`.
    3. `nodeEntity_sortOrder_isStringType` — assert the `sortOrder` property's `returnType` is `KType` of `String`, not `Float`, `Double`, or `Int`.
    4. `documentEntity_hasAllFiveHlcColumns` — assert `title_hlc`, `parent_id_hlc`, `sort_order_hlc`, `collapsed_hlc`, `deleted_hlc` all exist in `DocumentEntity::class.memberProperties`.
    5. `bookmarkEntity_targetDocumentId_isNullable` — assert `BookmarkEntity::class.memberProperties.first { it.name == "targetDocumentId" }.returnType.isMarkedNullable`.
    6. `settingsEntity_userIdIsPrimaryKey` — assert `SettingsEntity::class.memberProperties.first { it.name == "userId" }` carries `@PrimaryKey` annotation.
    7. `nodeEntity_tableName_isNodes` — assert `NodeEntity::class.java.getAnnotation(Entity::class.java).tableName == "nodes"`.
    8. `allEntities_columnNames_areSnakeCase` — for each entity, collect all `@ColumnInfo(name = ...)` values; assert none contain uppercase letters (all snake_case — no camelCase slippage).
    9. `syncStatusDefaultsToZero` — insert each of the three entities (NodeEntity, DocumentEntity, BookmarkEntity) without specifying `syncStatus`; assert the retrieved value is `0` in each case.
  - **Pass criteria**: `./gradlew test` exits 0, all 9 cases green.
- **Risk**: LOW — pure data class definitions. The primary failure mode is a column name typo causing the Android and backend schemas to diverge silently; test 8 catches the camelCase slippage class of errors.

---

### P3-4: NodeDao

- **Task ID**: P3-4
- **Title**: NodeDao with Flow-returning reactive queries and soft-delete filtering
- **What to build**: Create `android/app/src/main/java/com/outlinegod/app/db/dao/NodeDao.kt` as a Room `@Dao` interface. Every method must have a real query annotation — no placeholder TODOs. Methods:
  - `@Query("SELECT * FROM nodes WHERE document_id = :documentId AND deleted_at IS NULL") fun getNodesByDocument(documentId: String): Flow<List<NodeEntity>>` — reactive; emits on any change.
  - `@Query("SELECT * FROM nodes WHERE id = :nodeId LIMIT 1") fun getNodeById(nodeId: String): Flow<NodeEntity?>` — reactive single-node observation (no `deleted_at` filter — used to observe tombstones too).
  - `@Query("SELECT * FROM nodes WHERE document_id = :documentId AND deleted_at IS NULL") suspend fun getNodesByDocumentSync(documentId: String): List<NodeEntity>` — one-shot for sync processing.
  - `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertNode(node: NodeEntity)`.
  - `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertNodes(nodes: List<NodeEntity>)` — batch upsert for receiving sync changes.
  - `@Update suspend fun updateNode(node: NodeEntity)`.
  - `@Query("UPDATE nodes SET deleted_at = :deletedAt, deleted_hlc = :deletedHlc, updated_at = :updatedAt WHERE id = :nodeId") suspend fun softDeleteNode(nodeId: String, deletedAt: Long, deletedHlc: String, updatedAt: Long)`.
  - `@Query("SELECT * FROM nodes WHERE device_id != :deviceId AND (content_hlc > :sinceHlc OR note_hlc > :sinceHlc OR parent_id_hlc > :sinceHlc OR sort_order_hlc > :sinceHlc OR completed_hlc > :sinceHlc OR color_hlc > :sinceHlc OR collapsed_hlc > :sinceHlc OR deleted_hlc > :sinceHlc)") suspend fun getPendingChanges(sinceHlc: String, deviceId: String): List<NodeEntity>`.
- **Inputs required**: P3-3 (NodeEntity defined)
- **Output artifact**: `android/app/src/main/java/com/outlinegod/app/db/dao/NodeDao.kt`
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/db/dao/NodeDaoTest.kt`
  - Test setup: annotate the test class with `@RunWith(RobolectricTestRunner::class)`. Create a single-entity test database `@Database(entities = [NodeEntity::class], version = 1, exportSchema = false) abstract class TestNodeDb : RoomDatabase() { abstract fun nodeDao(): NodeDao }`. In `@Before`, build it via `Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TestNodeDb::class.java).allowMainThreadQueries().build()`.
  - **Test cases**:
    1. `getNodesByDocument_returnsActiveNodes` — insert 2 active nodes + 1 soft-deleted node (non-null `deleted_at`) for the same document; collect the first Flow emission via `runTest { dao.getNodesByDocument(docId).first() }`; assert list size is 2 and no returned node has `deleted_at` set.
    2. `getNodesByDocument_excludesDifferentDocument` — insert node for document A and node for document B; assert `getNodesByDocument(A)` returns exactly 1 node.
    3. `upsertNodes_insertsNewNodes` — call `upsertNodes` with 3 new nodes; assert `getNodesByDocumentSync` returns all 3.
    4. `upsertNodes_updatesExistingNode_byId` — insert node with `content = "old"`; call `upsertNodes` with same id but `content = "new"`; assert retrieved node has `content == "new"`.
    5. `softDeleteNode_setsTombstone_andExcludesFromActiveList` — insert node; call `softDeleteNode`; assert `getNodesByDocument` returns empty list but `getNodeById` emits the node with non-null `deleted_at`.
    6. `getPendingChanges_excludesEchoDevice` — insert node with `device_id = "deviceA"` and `content_hlc = "ZZZZ"`; call `getPendingChanges("0", "deviceA")`; assert result is empty.
    7. `getPendingChanges_includesNode_fromDifferentDevice` — insert node with `device_id = "deviceB"` and `content_hlc = "ZZZZ"`; call `getPendingChanges("0", "deviceA")`; assert result contains the node.
  - **Pass criteria**: `./gradlew test` exits 0, all 7 cases green.
- **Risk**: LOW — standard Room DAO. The `Flow` return type requires `runTest` + `kotlinx-coroutines-test` in unit tests; ensure this dependency is declared in `build.gradle.kts`.

---

### P3-5: DocumentDao

- **Task ID**: P3-5
- **Title**: DocumentDao with reactive queries, sort-order ordering, and soft-delete filtering
- **What to build**: Create `android/app/src/main/java/com/outlinegod/app/db/dao/DocumentDao.kt` as a Room `@Dao` interface. Methods:
  - `@Query("SELECT * FROM documents WHERE user_id = :userId AND deleted_at IS NULL ORDER BY sort_order ASC, id ASC") fun getAllDocuments(userId: String): Flow<List<DocumentEntity>>` — sorted by `sort_order` lexicographically, then `id` as tiebreaker (matching the backend's `ORDER BY sort_order ASC, id ASC` rule).
  - `@Query("SELECT * FROM documents WHERE id = :id LIMIT 1") fun getDocumentById(id: String): Flow<DocumentEntity?>`.
  - `@Query("SELECT * FROM documents WHERE id = :id LIMIT 1") suspend fun getDocumentByIdSync(id: String): DocumentEntity?`.
  - `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDocument(document: DocumentEntity)`.
  - `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDocuments(documents: List<DocumentEntity>)`.
  - `@Update suspend fun updateDocument(document: DocumentEntity)`.
  - `@Query("UPDATE documents SET deleted_at = :deletedAt, deleted_hlc = :deletedHlc, updated_at = :updatedAt WHERE id = :id") suspend fun softDeleteDocument(id: String, deletedAt: Long, deletedHlc: String, updatedAt: Long)`.
  - `@Query("SELECT * FROM documents WHERE user_id = :userId AND device_id != :deviceId AND (title_hlc > :sinceHlc OR parent_id_hlc > :sinceHlc OR sort_order_hlc > :sinceHlc OR collapsed_hlc > :sinceHlc OR deleted_hlc > :sinceHlc)") suspend fun getPendingChanges(userId: String, sinceHlc: String, deviceId: String): List<DocumentEntity>`.
- **Inputs required**: P3-3 (DocumentEntity defined)
- **Output artifact**: `android/app/src/main/java/com/outlinegod/app/db/dao/DocumentDao.kt`
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/db/dao/DocumentDaoTest.kt`
  - Test setup: `@RunWith(RobolectricTestRunner::class)`, single-entity `TestDocumentDb` in-memory database (same pattern as P3-4).
  - **Test cases**:
    1. `getAllDocuments_returnsActiveDocuments` — insert 3 active + 1 soft-deleted; assert Flow emission has 3 items.
    2. `getAllDocuments_sortsByFractionalIndex_lexicographically` — insert 3 documents with `sort_order = "V"`, `"a"`, `"Z"` (ASCII order: `V` < `Z` < `a`); assert returned order is `["V", "Z", "a"]`.
    3. `getAllDocuments_tiesInSortOrder_brokenById` — insert 2 documents with identical `sort_order`; assert the document with the lexicographically smaller `id` comes first.
    4. `getAllDocuments_excludesOtherUsers` — insert doc for user B; assert `getAllDocuments("userA")` is empty.
    5. `upsertDocuments_updatesExistingDocument` — insert document with `title = "old"`; call `upsertDocuments` with same id but `title = "new"`; assert retrieved title is `"new"`.
    6. `getDocumentByIdSync_returnsNull_forUnknownId` — call with non-existent UUID; assert result is null.
    7. `softDeleteDocument_excludesFromActiveList` — insert document; soft-delete; assert `getAllDocuments` excludes it.
  - **Pass criteria**: `./gradlew test` exits 0, all 7 cases green.
- **Risk**: LOW — test 2 validates that fractional index sorting is strictly lexicographic and never numeric, which is the most critical invariant for `sort_order` correctness.

---

### P3-6: BookmarkDao

- **Task ID**: P3-6
- **Title**: BookmarkDao with reactive queries and soft-delete filtering
- **What to build**: Create `android/app/src/main/java/com/outlinegod/app/db/dao/BookmarkDao.kt` as a Room `@Dao` interface. Methods:
  - `@Query("SELECT * FROM bookmarks WHERE user_id = :userId AND deleted_at IS NULL ORDER BY sort_order ASC, id ASC") fun observeAllActive(userId: String): Flow<List<BookmarkEntity>>`.
  - `@Query("SELECT * FROM bookmarks WHERE id = :id LIMIT 1") suspend fun getBookmarkByIdSync(id: String): BookmarkEntity?`.
  - `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBookmark(bookmark: BookmarkEntity)`.
  - `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBookmarks(bookmarks: List<BookmarkEntity>)`.
  - `@Update suspend fun updateBookmark(bookmark: BookmarkEntity)`.
  - `@Query("UPDATE bookmarks SET deleted_at = :deletedAt, deleted_hlc = :deletedHlc, updated_at = :updatedAt WHERE id = :id") suspend fun softDeleteBookmark(id: String, deletedAt: Long, deletedHlc: String, updatedAt: Long)`.
  - `@Query("SELECT * FROM bookmarks WHERE user_id = :userId AND device_id != :deviceId AND (title_hlc > :sinceHlc OR target_type_hlc > :sinceHlc OR target_document_id_hlc > :sinceHlc OR target_node_id_hlc > :sinceHlc OR query_hlc > :sinceHlc OR sort_order_hlc > :sinceHlc OR deleted_hlc > :sinceHlc)") suspend fun getPendingChanges(userId: String, sinceHlc: String, deviceId: String): List<BookmarkEntity>`.
- **Inputs required**: P3-3 (BookmarkEntity defined)
- **Output artifact**: `android/app/src/main/java/com/outlinegod/app/db/dao/BookmarkDao.kt`
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/db/dao/BookmarkDaoTest.kt`
  - Test setup: `@RunWith(RobolectricTestRunner::class)`, single-entity `TestBookmarkDb` in-memory database.
  - **Test cases**:
    1. `observeAllActive_excludesSoftDeletedBookmarks` — insert 2 active + 1 soft-deleted; assert Flow emission has 2 items.
    2. `observeAllActive_excludesOtherUsers` — insert bookmark for user B; assert `observeAllActive("userA")` emits empty list.
    3. `upsertBookmarks_insertsBatch` — call with list of 3 bookmarks; assert all 3 appear in `observeAllActive`.
    4. `softDeleteBookmark_setsTombstone` — insert bookmark; soft-delete; assert `observeAllActive` excludes it.
    5. `searchBookmark_withNullTargetIds_roundtrip` — insert bookmark with `target_type = "search"`, both target IDs null, `query = "hello"`; assert `getBookmarkByIdSync` returns entity with `query == "hello"` and both target IDs null.
  - **Pass criteria**: `./gradlew test` exits 0, all 5 cases green.
- **Risk**: LOW — follows identical pattern to DocumentDao.

---

### P3-7: SettingsDao

- **Task ID**: P3-7
- **Title**: SettingsDao with reactive query and INSERT OR REPLACE upsert
- **What to build**: Create `android/app/src/main/java/com/outlinegod/app/db/dao/SettingsDao.kt` as a Room `@Dao` interface. Methods:
  - `@Query("SELECT * FROM settings WHERE user_id = :userId LIMIT 1") fun getSettings(userId: String): Flow<SettingsEntity?>` — emits null if no settings row exists yet; emits new value whenever the row changes.
  - `@Query("SELECT * FROM settings WHERE user_id = :userId LIMIT 1") suspend fun getSettingsSync(userId: String): SettingsEntity?` — one-shot for sync.
  - `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSettings(settings: SettingsEntity)` — handles both initial insert and full replacement.
- **Inputs required**: P3-3 (SettingsEntity defined)
- **Output artifact**: `android/app/src/main/java/com/outlinegod/app/db/dao/SettingsDao.kt`
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/db/dao/SettingsDaoTest.kt`
  - Test setup: `@RunWith(RobolectricTestRunner::class)`, single-entity `TestSettingsDb` in-memory database.
  - **Test cases**:
    1. `getSettings_emitsNull_whenNoRow` — call `getSettings("userId")` on empty DB; assert `runTest { dao.getSettings("userId").first() }` returns null.
    2. `upsertSettings_insertsNewRow` — call `upsertSettings` with a new `SettingsEntity`; assert `getSettingsSync` returns the same row with matching `theme` and `density`.
    3. `upsertSettings_replacesExistingRow` — insert settings with `theme = "dark"`; call `upsertSettings` with same `userId` but `theme = "light"`; assert `getSettingsSync` returns `theme == "light"`.
    4. `getSettings_emitsNewValue_afterUpsert` — use `runTest`; collect Flow asynchronously; call `upsertSettings`; assert the Flow emits a non-null `SettingsEntity`.
  - **Pass criteria**: `./gradlew test` exits 0, all 4 cases green.
- **Risk**: LOW — single-row upsert. Test 4 requires `kotlinx-coroutines-test`'s `runTest` + `UnconfinedTestDispatcher` to observe the reactive Flow update.

---

### P3-8: AppDatabase

- **Task ID**: P3-8
- **Title**: AppDatabase class wiring all four entities and DAOs, with in-memory test builder
- **What to build**: Create `android/app/src/main/java/com/outlinegod/app/db/AppDatabase.kt`. Annotate with `@Database(entities = [NodeEntity::class, DocumentEntity::class, BookmarkEntity::class, SettingsEntity::class], version = 1, exportSchema = false)`. Declare abstract DAO accessors: `abstract fun nodeDao(): NodeDao`, `abstract fun documentDao(): DocumentDao`, `abstract fun bookmarkDao(): BookmarkDao`, `abstract fun settingsDao(): SettingsDao`. Add a `companion object` with two factory methods: `fun build(context: Context): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "outlinegod.db").build()` for production, and `fun buildInMemory(context: Context): AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()` for tests. Update `DatabaseModule.kt` (from P3-2) to call `AppDatabase.build(context)` via `@ApplicationContext context: Context` injection, replacing the placeholder. Update `TestDatabaseModule.kt` to call `AppDatabase.buildInMemory(context)`.
- **Inputs required**: P3-4, P3-5, P3-6, P3-7 (all four DAOs defined); P3-2 (DatabaseModule to be updated)
- **Output artifact**: `android/app/src/main/java/com/outlinegod/app/db/AppDatabase.kt`
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/db/AppDatabaseTest.kt`
  - Test setup: `@RunWith(RobolectricTestRunner::class)`; call `AppDatabase.buildInMemory(ApplicationProvider.getApplicationContext())`.
  - **Test cases**:
    1. `buildInMemory_returnsOpenDatabase` — assert `db.isOpen == true`.
    2. `buildInMemory_exposesNodeDao` — assert `db.nodeDao()` is non-null and its class implements `NodeDao`.
    3. `buildInMemory_exposesDocumentDao` — assert `db.documentDao()` is non-null.
    4. `buildInMemory_exposesBookmarkDao` — assert `db.bookmarkDao()` is non-null.
    5. `buildInMemory_exposesSettingsDao` — assert `db.settingsDao()` is non-null.
    6. `twoInMemoryInstances_areIndependent` — create two in-memory databases; insert a node via the first; call `getNodesByDocumentSync` on the second; assert empty list.
  - **Pass criteria**: `./gradlew test` exits 0, all 6 cases green. All DAO tests from P3-4 through P3-7 must also pass after P3-8 is complete (they all use the same `buildInMemory` pattern).
- **Risk**: LOW — standard Room `@Database` class. `exportSchema = false` avoids schema file management. Verify KSP generates the `AppDatabase_Impl` class cleanly via `./gradlew kspDebugKotlin`.

---

### P3-9: HlcClock

- **Task ID**: P3-9
- **Title**: Hybrid Logical Clock generate, receive, and compare in Kotlin
- **What to build**: **If P0-3 is complete and `android/app/src/main/java/com/outlinegod/prototype/HlcClock.kt` passes all P0-3 tests**: move the file to `android/app/src/main/java/com/outlinegod/app/sync/HlcClock.kt`, update the package declaration to `com.gmaingret.outlinergod.sync`, and adapt any imports. **Otherwise, implement from scratch**: Create `com.gmaingret.outlinergod.sync.HlcClock` as a class (not object) that accepts a `clock: () -> Long = { System.currentTimeMillis() }` parameter for testability. It maintains `private val localHlc = AtomicReference("")`. Methods: `fun generate(deviceId: String): String` — reads `clock()`, compares wall to `localHlc`'s wall component, increments a 16-bit counter on ties (max `0xFFFF`), formats as `<wall_ms_16hex>-<counter_4hex>-<deviceId>` using `padStart(16, '0')` for wall and `padStart(4, '0')` for counter, updates `localHlc` via `compareAndSet`, returns the new string. `fun receive(incoming: String, deviceId: String): String` — parses the incoming HLC, advances wall to `maxOf(localWall, incomingWall)`, resets counter to 0 if wall advanced or increments if equal, updates `localHlc`, returns the new HLC. `fun compare(a: String, b: String): Int = a.compareTo(b)` — pure lexicographic; a higher HLC is always lexicographically greater. Update `ClockModule.kt` (P3-2) to provide a real `HlcClock` singleton.
- **Inputs required**: P3-1 (project scaffold); P0-3 artifact if passing
- **Output artifact**: `android/app/src/main/java/com/outlinegod/app/sync/HlcClock.kt`
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/sync/HlcClockTest.kt`
  - Tests use Kotest `StringSpec` with `@RunWith(KotestTestRunner::class)` (from `io.kotest:kotest-runner-junit4`). Property-based tests use `io.kotest:kotest-property` `checkAll` with `Arb`.
  - **Test cases**:
    1. `generate_matchesFormat` — call `generate("d1")`; assert result matches `Regex("^[0-9a-f]{16}-[0-9a-f]{4}-d1$")`.
    2. `generate_isMonotonicallyIncreasing` — call `generate("d")` 100 times; assert each result is lexicographically `>` the previous via `String.compareTo`.
    3. `generate_incrementsCounter_onSameMillisecond` — create `HlcClock(clock = { 1000L })`; call `generate("d")` twice; assert first has counter `"0000"`, second has counter `"0001"`.
    4. `receive_advancesWallPastIncoming` — create clock returning `T`; build incoming HLC string with wall `T + 5000`; call `receive(incoming, "d")`; assert returned HLC wall hex ≥ hex of `T + 5000`.
    5. `receive_resetsCounter_whenWallAdvances` — generate an HLC with non-zero counter; call `receive` with strictly higher wall; assert returned counter is `"0000"`.
    6. `compare_higherTimestamp_returnsPositive` — build two HLC strings with walls T1 and T2 where T2 > T1; assert `compare(hlc_T2, hlc_T1) > 0`.
    7. `compare_higherCounter_returnsPositive` — two HLCs with identical wall, counters `"0000"` vs `"0001"`; assert `compare(counter_0001, counter_0000) > 0`.
    8. `wallHex_isExactlySixteenChars` (property-based) — `checkAll(Arb.long(0L..Long.MAX_VALUE)) { wallMs -> create HlcClock(clock = { wallMs }); val hlc = generate("d"); val wallPart = hlc.split("-")[0]; assert wallPart.length == 16 }`.
    9. `sortLexicographic_matchesGenerationOrder` (property-based) — `checkAll(Arb.int(2..50)) { n -> generate "d" n times into a list; sort by String.compareTo; assert sorted list == original list }`.
  - **Pass criteria**: `./gradlew test` exits 0, all 9 cases green. Property-based tests run Kotest's default 1000 iterations.
- **Risk**: HIGH — HLC correctness is foundational to the entire sync system. A one-character padding error in the 16-char wall-time hex makes all HLC comparisons silently wrong. The `clock` constructor parameter is essential for deterministic tests; without it, the monotonicity test is non-deterministic. `AtomicReference` is required because `SyncRepository` runs from a background coroutine dispatcher.

---

### P3-10: LWW merge functions

- **Task ID**: P3-10
- **Title**: NodeMerge, DocumentMerge, and BookmarkMerge per-field Last-Write-Wins functions
- **What to build**: **If P0-3 is complete and LWW Kotlin merge code exists in the prototype package**: move and update package declarations. **Otherwise, implement from scratch** in `android/app/src/main/java/com/outlinegod/app/sync/`:
  - `NodeMerge.kt`: top-level function `fun merge(local: NodeEntity, incoming: NodeEntity): NodeEntity`. For each independently syncable field pair — (`content`/`content_hlc`), (`note`/`note_hlc`), (`parent_id`/`parent_id_hlc`), (`sort_order`/`sort_order_hlc`), (`completed`/`completed_hlc`), (`color`/`color_hlc`), (`collapsed`/`collapsed_hlc`), (`deleted_at`/`deleted_hlc`) — keep the value from whichever side has the lexicographically greater `_hlc` via `HlcClock.compare()`. On equal HLCs, keep local (idempotency). Immutable fields (`id`, `user_id`, `document_id`, `created_at`) always come from `local`. `device_id` and `updated_at` carry over from whichever side won the most fields (local on tie). Do not mutate either input.
  - `DocumentMerge.kt`: `fun merge(local: DocumentEntity, incoming: DocumentEntity): DocumentEntity`. Per-field LWW for (`title`/`title_hlc`), (`parent_id`/`parent_id_hlc`), (`sort_order`/`sort_order_hlc`), (`collapsed`/`collapsed_hlc`), (`deleted_at`/`deleted_hlc`). Immutable: `id`, `user_id`, `type`, `created_at`.
  - `BookmarkMerge.kt`: `fun merge(local: BookmarkEntity, incoming: BookmarkEntity): BookmarkEntity`. Per-field LWW for (`title`/`title_hlc`), (`target_type`/`target_type_hlc`), (`target_document_id`/`target_document_id_hlc`), (`target_node_id`/`target_node_id_hlc`), (`query`/`query_hlc`), (`sort_order`/`sort_order_hlc`), (`deleted_at`/`deleted_hlc`). Immutable: `id`, `user_id`, `created_at`.
- **Inputs required**: P3-9 (HlcClock.compare()); P3-3 (entity classes); P0-3 artifact if passing
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/sync/NodeMerge.kt`
  - `android/app/src/main/java/com/outlinegod/app/sync/DocumentMerge.kt`
  - `android/app/src/main/java/com/outlinegod/app/sync/BookmarkMerge.kt`
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/sync/LwwMergeTest.kt`
  - Tests use Kotest `StringSpec` with `@RunWith(KotestTestRunner::class)`. Property-based tests use `Arb`.
  - **Test cases**:
    1. `nodeMerge_higherContentHlcWins` — two `NodeEntity` objects differing only in `content`/`content_hlc`; assert `merge()` returns the `content` from the entity with the higher `content_hlc`.
    2. `nodeMerge_localWins_onEqualHlc` — identical `content_hlc` on both sides; assert `merge(local, incoming).content == local.content`.
    3. `nodeMerge_isIdempotent` (property-based) — `checkAll(nodeEntityArb, nodeEntityArb) { a, b -> assert merge(a, merge(a, b)) == merge(a, b) }`. Define `nodeEntityArb` as `Arb.bind(Arb.string(), Arb.string()) { hlc1, hlc2 -> makeNode(contentHlc = hlc1) }`.
    4. `nodeMerge_isCommutative_perField` (property-based) — `checkAll(nodeEntityArb, nodeEntityArb) { a, b -> val ab = merge(a, b); val ba = merge(b, a); assert ab.content == ba.content && ab.note == ba.note && ab.sortOrder == ba.sortOrder }` (fields independently commute; `device_id` may differ).
    5. `nodeMerge_deleteWins_whenDeletedHlcIsHighest` — incoming has non-null `deleted_at` and `deleted_hlc = "ZZZZ"` while all local field HLCs are `"AAAA"`; assert merged `deleted_at` is non-null.
    6. `nodeMerge_contentFieldIndependent_ofDeletion` — local: `content_hlc = "ZZZZ"`, `deleted_at = null`; incoming: `deleted_hlc = "ZZZZZ"` (higher), `content_hlc = "AAAA"`; assert merged `deleted_at` is non-null (incoming wins) AND `content == local.content` (local wins content independently).
    7. `documentMerge_titleField_higherHlcWins` — same pattern as test 1, applied to `DocumentEntity`.
    8. `bookmarkMerge_sortOrderField_higherHlcWins` — same pattern as test 1, applied to `BookmarkEntity`.
    9. `nodeMerge_immutableFields_alwaysFromLocal` — assert merged `id == local.id`, `userId == local.userId`, `documentId == local.documentId` regardless of which side's HLCs are higher.
  - **Pass criteria**: `./gradlew test` exits 0, all 9 cases green. Property-based tests run 500 iterations.
- **Risk**: HIGH — same fundamental complexity as backend `merge.ts`. Mispairing a field with its wrong HLC column causes silent data loss. Test 4 (commutativity) and test 6 (field independence from deletion) are the invariants that can only be reliably caught by property-based testing.

---

### P3-11: Ktor HTTP client and AuthInterceptor

- **Task ID**: P3-11
- **Title**: Ktor HttpClient with OkHttp engine, 401 auth-retry interceptor, and retry plugin
- **What to build**: Create `android/app/src/main/java/com/outlinegod/app/network/KtorClientFactory.kt`. The `fun create(tokenProvider: suspend () -> String?, tokenRefresher: suspend () -> String?): HttpClient` factory builds an `HttpClient(OkHttp)` with: (1) `install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }` using `ktor-client-content-negotiation` and `ktor-serialization-kotlinx-json`. (2) `install(HttpTimeout) { requestTimeoutMillis = 30_000; connectTimeoutMillis = 10_000 }`. (3) A custom 401 interceptor via `install(HttpSend)`: on receiving a 401 response, call `tokenRefresher()` (the `AuthRepository.refreshToken()` lambda); if it returns a non-null new token, clone the request with `Authorization: Bearer <newToken>` and execute once; if it returns null, return the 401 as-is to avoid an infinite refresh loop. (4) A pre-request `BearerTokens`-free header attachment: use a `requestPipeline.intercept(HttpRequestPipeline.State)` or the `HttpSend` plugin to read `tokenProvider()` and set `headers["Authorization"] = "Bearer $token"` on every request before sending. (5) `install(HttpRequestRetry) { retryOnServerErrors(maxRetries = 3); exponentialDelay(base = 2.0, maxDelayMs = 30_000L) }`. Update `NetworkModule.kt` (P3-2) to call `KtorClientFactory.create(tokenProvider = { authRepository.getAccessToken().first() }, tokenRefresher = { authRepository.refreshToken().getOrNull()?.accessToken })`.
- **Inputs required**: P3-2 (NetworkModule to be updated); P3-1 (Ktor dependencies declared)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/network/KtorClientFactory.kt`
  - `android/app/src/main/java/com/outlinegod/app/di/NetworkModule.kt` (updated)
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/network/KtorClientFactoryTest.kt`
  - Use `io.ktor:ktor-client-mock` `MockEngine`; do not make real network calls.
  - **Test cases**:
    1. `client_attachesBearerToken_onEveryRequest` — create client with `MockEngine { respondOk() }` and `tokenProvider = { "mytoken" }`; make any request; assert the engine received `Authorization: Bearer mytoken`.
    2. `client_intercepts401_andRetries_withRefreshedToken` — `MockEngine` returns 401 on first call, 200 on second; `tokenRefresher` returns `"newtoken"`; assert the second engine call had `Authorization: Bearer newtoken` and the client returns 200.
    3. `client_doesNotInfiniteRetry_whenRefreshFails` — `MockEngine` always returns 401; `tokenRefresher` returns null; assert client does not retry and propagates 401 (verify MockEngine was called exactly once after the initial attempt).
    4. `client_retries_onServerError_upToThreeTimes` — `MockEngine` returns 500 three times then 200; assert the final response received by the caller is 200.
    5. `client_doesNotRetry_on4xxClientError` — `MockEngine` returns 400; assert response is 400 and MockEngine was called exactly once (no retry on 4xx).
    6. `client_deserializesJsonResponse` — `MockEngine` returns `{"server_hlc":"AAAA","nodes":[],"documents":[],"settings":null,"bookmarks":[]}`; deserialize as `SyncChangesResponse` (defined in P3-13); assert no exception and `server_hlc == "AAAA"`.
  - **Pass criteria**: `./gradlew test` exits 0, all 6 cases green.
- **Risk**: MEDIUM — the 401 intercept has a race condition: two concurrent requests receiving 401 simultaneously may both invoke `tokenRefresher` in parallel, with the second refresh failing on an already-rotated token. Mitigation: the `tokenRefresher` lambda delegates to `AuthRepository.refreshToken()` which is `Mutex`-guarded (P3-12); the second coroutine waits for the first refresh to complete and receives the updated token. Document this contract in a `// IMPORTANT` comment in `KtorClientFactory.kt`.

---

### P3-12: AuthRepository

- **Task ID**: P3-12
- **Title**: AuthRepository backed by DataStore for access token, refresh token, and device ID
- **What to build**: Create `android/app/src/main/java/com/outlinegod/app/repository/AuthRepository.kt` as a Kotlin interface with: `suspend fun googleSignIn(idToken: String): Result<AuthResponse>`, `suspend fun refreshToken(): Result<TokenPair>`, `suspend fun getMe(): Result<UserProfile>`, `suspend fun logout(refreshToken: String): Result<Unit>`, `fun getAccessToken(): Flow<String?>`, `fun getDeviceId(): Flow<String>`. Create network model data classes (all `@Serializable`) in `android/app/src/main/java/com/outlinegod/app/network/model/Auth.kt`: `AuthResponse(token: String, refresh_token: String, user: UserProfile, is_new_user: Boolean)`, `TokenPair(token: String, refresh_token: String)`, `UserProfile(id: String, google_sub: String, email: String, name: String, picture: String)`. Create `android/app/src/main/java/com/outlinegod/app/repository/impl/AuthRepositoryImpl.kt` backed by `DataStore<Preferences>` (injected via `@Inject`). DataStore keys defined in a private companion: `ACCESS_TOKEN = stringPreferencesKey("access_token")`, `REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")`, `DEVICE_ID_KEY = stringPreferencesKey("device_id")`. `getDeviceId()` generates `UUID.randomUUID().toString()` on first call and persists it — subsequent calls return the persisted value. `refreshToken()` is guarded by `private val refreshMutex = Mutex()` via `refreshMutex.withLock { ... }` to prevent concurrent refresh races. `logout()` clears `ACCESS_TOKEN` and `REFRESH_TOKEN_KEY` from DataStore. Create `android/app/src/main/java/com/outlinegod/app/di/AuthModule.kt` (`@Module @InstallIn(SingletonComponent::class)`) providing `@Provides @Singleton DataStore<Preferences>` using `context.dataStore` (from `androidx.datastore:datastore-preferences`). Bind `AuthRepository → AuthRepositoryImpl` in `RepositoryModule.kt`.
- **Inputs required**: P3-11 (Ktor HttpClient for network calls); P3-2 (RepositoryModule for binding)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/repository/AuthRepository.kt`
  - `android/app/src/main/java/com/outlinegod/app/repository/impl/AuthRepositoryImpl.kt`
  - `android/app/src/main/java/com/outlinegod/app/di/AuthModule.kt`
  - `android/app/src/main/java/com/outlinegod/app/network/model/Auth.kt`
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/repository/AuthRepositoryTest.kt`
  - Use MockK to mock the Ktor `HttpClient`; use an in-memory `DataStore<Preferences>` via `PreferenceDataStoreFactory.create` with a temp file or `TestDataStore` pattern.
  - **Test cases**:
    1. `googleSignIn_storesAccessToken` — mock HttpClient to return a valid `AuthResponse`; call `googleSignIn("id-token")`; assert `getAccessToken().first()` equals the returned `token`.
    2. `googleSignIn_storesRefreshToken` — same setup; assert DataStore `REFRESH_TOKEN_KEY` equals the returned `refresh_token`.
    3. `googleSignIn_returnsFailure_onNetworkError` — mock HttpClient to throw `IOException`; assert `Result.isFailure`.
    4. `refreshToken_isMutexGuarded` — using `runTest` with `TestCoroutineScheduler`, launch two concurrent `refreshToken()` coroutines; use a counting mock that delays on the first call; assert the HTTP client was called sequentially (not overlapping).
    5. `deviceId_isStableAcrossMultipleCalls` — call `getDeviceId().first()` twice; assert both values are equal and match `Regex("[0-9a-f-]{36}")`.
    6. `logout_clearsAccessToken` — call `googleSignIn(...)` then `logout("rt")`; assert `getAccessToken().first()` is null after logout.
    7. `getMe_returnsUserProfile_onSuccess` — mock HttpClient to return `UserProfile`; call `getMe()`; assert `Result.isSuccess` and fields match.
  - **Pass criteria**: `./gradlew test` exits 0, all 7 cases green.
- **Risk**: MEDIUM — the `Mutex` guard in `refreshToken()` is critical for correctness (see P3-11 Risk). DataStore in unit tests requires a real file or `TestScope`-backed in-memory store; verify the test DataStore tears down between test cases via `@After`.

---

### P3-13: SyncRepository

- **Task ID**: P3-13
- **Title**: SyncRepository pull and push network calls, plus all sync record data classes
- **What to build**: Define sync network model data classes (all `@Serializable`) in `android/app/src/main/java/com/outlinegod/app/network/model/Sync.kt`. Field names use `@SerialName("snake_case")` to match the backend JSON exactly. Classes: `NodeSyncRecord` (all fields + HLC companions from API.md §4 HLC Sync Record — Node), `DocumentSyncRecord` (all fields from API.md §4 HLC Sync Record — Document), `BookmarkSyncRecord` (API.md §4 HLC Sync Record — Bookmark), `SettingsSyncRecord` (API.md §4 HLC Sync Record — Settings), `SyncChangesResponse(server_hlc: String, nodes: List<NodeSyncRecord>, documents: List<DocumentSyncRecord>, settings: SettingsSyncRecord?, bookmarks: List<BookmarkSyncRecord>)`, `SyncPushPayload(device_id: String, nodes: List<NodeSyncRecord>? = null, documents: List<DocumentSyncRecord>? = null, settings: SettingsSyncRecord? = null, bookmarks: List<BookmarkSyncRecord>? = null)`, `SyncPushResponse(server_hlc: String, accepted_node_ids: List<String>, accepted_document_ids: List<String>, accepted_bookmark_ids: List<String>, conflicts: SyncConflicts)`, `SyncConflicts(nodes: List<NodeSyncRecord>, documents: List<DocumentSyncRecord>, bookmarks: List<BookmarkSyncRecord>, settings: SettingsSyncRecord?)`. Create interface `android/app/src/main/java/com/outlinegod/app/repository/SyncRepository.kt` with: `suspend fun pull(since: String, deviceId: String): Result<SyncChangesResponse>`, `suspend fun push(payload: SyncPushPayload): Result<SyncPushResponse>`. Create implementation `android/app/src/main/java/com/outlinegod/app/repository/impl/SyncRepositoryImpl.kt`: `pull()` calls `httpClient.get("$baseUrl/api/sync/changes") { parameter("since", since); parameter("device_id", deviceId) }.body<SyncChangesResponse>()`; `push()` calls `httpClient.post("$baseUrl/api/sync/changes") { contentType(ContentType.Application.Json); setBody(payload) }.body<SyncPushResponse>()`. Both wrap the call in `runCatching { ... }`. No WorkManager scheduling — that is Phase 4. Bind `SyncRepository → SyncRepositoryImpl` in `RepositoryModule.kt`.
- **Inputs required**: P3-11 (Ktor HttpClient); P3-10 (entity types used by sync records); P3-2 (RepositoryModule)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/network/model/Sync.kt`
  - `android/app/src/main/java/com/outlinegod/app/repository/SyncRepository.kt`
  - `android/app/src/main/java/com/outlinegod/app/repository/impl/SyncRepositoryImpl.kt`
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/repository/SyncRepositoryTest.kt`
  - Use `io.ktor:ktor-client-mock` `MockEngine`; do not make real network calls.
  - **Test cases**:
    1. `pull_callsCorrectEndpoint_withQueryParams` — call `pull("0", "device1")`; assert `MockEngine` received a `GET` request to a path containing `/api/sync/changes` with query params `since=0` and `device_id=device1`.
    2. `pull_parsesResponse_correctly` — `MockEngine` returns valid JSON matching `SyncChangesResponse` with `server_hlc = "AAAA"` and empty arrays; assert deserialized response has `serverHlc == "AAAA"` and `nodes` is an empty list.
    3. `pull_since_sentinelZero_isPassedAsLiteralString` — call `pull("0", "d")`; assert the `since` query parameter value is exactly the string `"0"` (not `0` as a number, which would fail HLC string comparison on the backend).
    4. `pull_returnsFailure_onNetworkError` — `MockEngine` throws `IOException`; assert `Result.isFailure`.
    5. `push_callsCorrectEndpoint_withJsonBody` — call `push(SyncPushPayload(device_id = "d1"))`; assert `MockEngine` received a `POST` to `/api/sync/changes` with `Content-Type: application/json` and body containing `"device_id":"d1"`.
    6. `push_parsesConflictsInResponse` — `MockEngine` returns `SyncPushResponse` JSON with a non-empty `conflicts.nodes` array; assert deserialized `conflicts.nodes` is non-empty.
    7. `push_returnsFailure_onTimeout` — `MockEngine` delays response past `requestTimeoutMillis`; assert `Result.isFailure`.
  - **Pass criteria**: `./gradlew test` exits 0, all 7 cases green.
- **Risk**: LOW — thin network wrapper. The primary risk is `@SerialName` mismatches on the data classes causing silent JSON deserialization errors; tests 2 and 6 catch field-name mismatches for the most critical fields.

---

### P3-14: Navigation scaffold

- **Task ID**: P3-14
- **Title**: NavHost with six named routes and empty placeholder Composables
- **What to build**: Create `android/app/src/main/java/com/outlinegod/app/ui/navigation/AppRoutes.kt` as a Kotlin `object` with six route string constants: `const val LOGIN = "login"`, `const val DOCUMENT_LIST = "document_list"`, `const val DOCUMENT_DETAIL = "document_detail/{documentId}"`, `const val NODE_EDITOR = "node_editor/{nodeId}"`, `const val SETTINGS = "settings"`, `const val BOOKMARKS = "bookmarks"`. Create `android/app/src/main/java/com/outlinegod/app/ui/navigation/AppNavHost.kt` as `@Composable fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier)` that creates a `NavHost(navController = navController, startDestination = AppRoutes.LOGIN, modifier = modifier)` with six `composable(route)` entries, each containing only `Box(modifier = Modifier.fillMaxSize())` — no ViewModels, no state, no real content. Create six placeholder screen files in `android/app/src/main/java/com/outlinegod/app/ui/screen/`: `LoginScreen.kt`, `DocumentListScreen.kt`, `DocumentDetailScreen.kt` (accepts `documentId: String` parameter), `NodeEditorScreen.kt` (accepts `nodeId: String` parameter), `SettingsScreen.kt`, `BookmarksScreen.kt` — each is a `@Composable fun XScreen(...)` containing `Box(modifier = Modifier.fillMaxSize())` and nothing else.
- **Inputs required**: P3-1 (navigation-compose dependency declared); P3-2 (Hilt wiring for @AndroidEntryPoint used in P3-15)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/navigation/AppRoutes.kt`
  - `android/app/src/main/java/com/outlinegod/app/ui/navigation/AppNavHost.kt`
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/LoginScreen.kt`
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/DocumentListScreen.kt`
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/DocumentDetailScreen.kt`
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/NodeEditorScreen.kt`
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/SettingsScreen.kt`
  - `android/app/src/main/java/com/outlinegod/app/ui/screen/BookmarksScreen.kt`
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/ui/navigation/AppRoutesTest.kt`
  - **Test cases**:
    1. `login_route_isCorrectString` — assert `AppRoutes.LOGIN == "login"`.
    2. `documentList_route_isCorrectString` — assert `AppRoutes.DOCUMENT_LIST == "document_list"`.
    3. `documentDetail_route_containsDocumentIdParam` — assert `AppRoutes.DOCUMENT_DETAIL.contains("{documentId}")`.
    4. `nodeEditor_route_containsNodeIdParam` — assert `AppRoutes.NODE_EDITOR.contains("{nodeId}")`.
    5. `allSixRoutes_areDefined_andNonBlank` — collect `listOf(AppRoutes.LOGIN, AppRoutes.DOCUMENT_LIST, AppRoutes.DOCUMENT_DETAIL, AppRoutes.NODE_EDITOR, AppRoutes.SETTINGS, AppRoutes.BOOKMARKS)`; assert `size == 6` and `all { it.isNotBlank() }`.
    6. `noRouteDuplicated` — assert the set of all six route values has size 6 (no duplicates).
  - **Pass criteria**: `./gradlew test` exits 0, all 6 cases green.
- **Risk**: LOW — pure constant definitions and empty Composables. Route string correctness is the only failure mode and is fully covered by tests. No ViewModel wiring to misconfigure in this task.

---

### P3-15: Main activity, OutlineGodTheme, and app entry point

- **Task ID**: P3-15
- **Title**: MainActivity, OutlineGodTheme with Material3 dark/light support, and manifest wiring
- **What to build**: Create `android/app/src/main/java/com/outlinegod/app/ui/theme/OutlineGodTheme.kt`. Define `@Composable fun OutlineGodTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)` that wraps `content` in `MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(), typography = Typography(), content = content)`. Use Material3's default `darkColorScheme()` and `lightColorScheme()` for Phase 3 — custom color token definitions are deferred to Phase 4. Create `android/app/src/main/java/com/outlinegod/app/MainActivity.kt` annotated `@AndroidEntryPoint`. In `onCreate`, call `enableEdgeToEdge()` then `setContent { OutlineGodTheme { val navController = rememberNavController(); AppNavHost(navController = navController) } }`. Update `android/app/src/main/AndroidManifest.xml`: set `android:name=".OutlineGodApp"` on the `<application>` tag; declare `MainActivity` with `android:exported="true"` and an `<intent-filter>` containing `<action android:name="android.intent.action.MAIN" />` and `<category android:name="android.intent.category.LAUNCHER" />`. Verify `./gradlew kspDebugKotlin` exits 0 confirming the complete Hilt component hierarchy — `OutlineGodApp` (@HiltAndroidApp) → `MainActivity` (@AndroidEntryPoint) → all four modules — generates without errors.
- **Inputs required**: P3-14 (AppNavHost); P3-2 (OutlineGodApp); P3-1 (Compose BOM with Material3)
- **Output artifact**:
  - `android/app/src/main/java/com/outlinegod/app/ui/theme/OutlineGodTheme.kt`
  - `android/app/src/main/java/com/outlinegod/app/MainActivity.kt`
  - `android/app/src/main/AndroidManifest.xml` (updated)
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/app/MainActivityTest.kt`
  - **Test cases**:
    1. `mainActivity_isAnnotatedWithAndroidEntryPoint` — assert `MainActivity::class.java.isAnnotationPresent(AndroidEntryPoint::class.java)`.
    2. `outlineGodApp_isAnnotatedWithHiltAndroidApp` — assert `OutlineGodApp::class.java.isAnnotationPresent(HiltAndroidApp::class.java)`.
    3. `manifest_declaresMainActivity_withLauncherIntent` — read `AndroidManifest.xml` via `javaClass.classLoader.getResourceAsStream("AndroidManifest.xml")`; assert the content contains `"android.intent.action.MAIN"` and `"android.intent.category.LAUNCHER"`.
    4. `outlinerGodTheme_darkAndLight_areDistinct` — verify `darkColorScheme()` and `lightColorScheme()` produce different `ColorScheme` objects (assert `darkColorScheme().background != lightColorScheme().background`).
  - **Pass criteria**: `./gradlew test` exits 0, all 4 cases green. `./gradlew assembleDebug` exits 0 producing a valid APK. `./gradlew kspDebugKotlin` exits 0 with complete Hilt component generation.
- **Risk**: LOW — standard Android entry point wiring. Missing `@HiltAndroidApp` or `@AndroidEntryPoint` produces a Hilt compile error immediately. The only runtime risk is `enableEdgeToEdge()` requiring API 29+ — `minSdk = 26` means this call must be guarded with `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)` or replaced with the AppCompat compatibility shim.

---

**Phase 3 exit criteria**: All 15 tasks complete. `./gradlew test` exits 0 (zero failures, zero skipped tests, all Phase 3 test files passing — P3-1 through P3-15). `./gradlew assembleDebug` exits 0 with a buildable APK. `./gradlew kspDebugKotlin` exits 0 (Hilt and Room KSP code generation clean). The app launches on an API 26+ emulator showing the empty placeholder Login screen with no crashes.
