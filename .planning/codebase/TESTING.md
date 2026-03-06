# Testing Patterns

**Analysis Date:** 2026-03-06

## Test Framework

**Backend Runner:**
- Vitest `^2.0.0`
- Config: `backend/vitest.config.ts`
- Pool: `forks` (required for `better-sqlite3` native module — threads cause segfaults)
- Globals: enabled (`globals: true`)

**Android Runner:**
- JUnit 4 (via Robolectric for tests touching Android APIs)
- Mockk for mocking
- Kotest for property-based tests (commutativity, idempotency of HLC/LWW)
- Orbit MVI test DSL (`orbit-test:10.0.0`) for ViewModel tests

**Assertion Libraries:**
- Backend: Vitest's `expect` (Jest-compatible)
- Android: JUnit `Assert.*` (`assertEquals`, `assertTrue`, `assertNotNull`)

**Run Commands:**
```bash
# Backend
cd backend && pnpm test              # Run all tests
cd backend && pnpm test:coverage     # With V8 coverage report

# Android (requires JAVA_HOME + ANDROID_HOME)
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="/c/Users/gmain/AppData/Local/Android/Sdk"
cd android && ./gradlew test         # Run all JVM tests
```

## Test File Organization

**Backend:**
- Co-located: each `src/routes/auth.ts` has `src/routes/auth.test.ts` in the same directory
- Helper module: `src/test-helpers/createTestDb.ts` — shared factory for in-memory SQLite
- Pattern: every source file has a corresponding `.test.ts` (25 test files for 54 source files)

**Android:**
- Mirrored structure: `src/test/java/...` mirrors `src/main/java/...` package layout
- One test file per production class (e.g., `NodeEditorViewModel.kt` → split into `NodeEditorPersistenceTest.kt`, `NodeEditorScreenTest.kt`, `DragAndDropTest.kt`, `NodeContextMenuTest.kt`)
- Integration tests live alongside unit tests in the same `src/test/` tree
- 46 test files for 68 main source files

**Directory structure:**
```
android/app/src/test/java/com/gmaingret/outlinergod/
├── db/dao/          # DAO tests (Robolectric + Room inMemory)
├── db/entity/       # Entity schema tests (structural reflection)
├── di/              # Hilt module tests + TestDatabaseModule/TestNetworkModule
├── network/         # KtorClientFactory tests
├── prototype/       # HLC, LWW, Markdown, DnD prototype tests
├── repository/      # Auth, Sync, Search, Export repository tests
├── search/          # SearchQueryParser tests
├── sync/            # HlcClock, LwwMerge, SyncWorker, SyncWorkerIntegration
├── ui/mapper/       # FlatNodeMapper tests
├── ui/navigation/   # AppRoutes tests
└── ui/screen/       # ViewModel tests + lightweight screen tests
```

## Test Structure

**Backend suite pattern:**
```typescript
describe('Node routes', () => {
  let app: ReturnType<typeof buildApp>
  let sqlite: InstanceType<typeof Database>
  let tokenA: string

  beforeEach(async () => {
    process.env.JWT_SECRET = TEST_SECRET
    const testDb = createTestDb()
    sqlite = testDb.sqlite
    app = buildApp(sqlite)
    await app.ready()
    seedUser(sqlite, 'user-a')
    tokenA = await signTestJwt('user-a')
  })

  afterEach(async () => {
    await app.close()
    sqlite.close()
  })

  describe('GET /api/nodes', () => {
    it('returns200_withNodes_forValidDocument', async () => { ... })
    it('returns401_withNoAuthHeader', async () => { ... })
  })
})
```

**Android ViewModel test pattern (Orbit MVI):**
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentListViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        documentDao = mockk(relaxed = true)
        // ...
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `createDocument inserts document locally on success`() = runTest {
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.createDocument("Doc", "document", null, "V")
            expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Syncing))
            expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Error))
        }
        coVerify { documentDao.insertDocument(any()) }
    }
}
```

**Patterns:**
- `@Before`/`@After` for setup/teardown
- `createViewModel()` private factory method per test class (avoids constructor duplication)
- `fakeNode()` / `fakeDocument()` builder helpers with defaults and named overrides
- `setupNodeDao(nodes)` helper to configure Flow returns and suspend mocks at once

## Mocking

**Backend:** No mock library — tests run against a real in-memory SQLite via `createTestDb()`. Google OAuth verification is injected as a function parameter:
```typescript
// In test
app = buildApp(sqlite, { verifyGoogle: async () => ({ sub: 'g123', email: 'x@y.com', name: 'X', picture: '' }) })
```

**Android:** Mockk is the exclusive mock library.

**Standard mock setup:**
```kotlin
nodeDao = mockk(relaxed = true)           // relaxed = returns defaults for unmocked calls
authRepository = mockk()                  // strict = must mock everything called

every { authRepository.getUserId() } returns flowOf("user-1")
every { hlcClock.generate(any()) } returns "1636300202430-00000-device-1"
coEvery { nodeDao.updateNode(any()) } just Runs
coEvery { documentDao.insertDocument(any()) } throws RuntimeException("DB error")
```

**Slot capture for argument verification:**
```kotlin
val slot = slot<NodeEntity>()
coEvery { nodeDao.updateNode(capture(slot)) } just Runs
// ...after action...
assertEquals("new content", slot.captured.content)
```

**What to Mock:**
- All DAOs in ViewModel tests (use `mockk(relaxed = true)` for DAOs not under test)
- `AuthRepository` (always mocked in ViewModel tests)
- `HlcClock` (always mocked; `every { hlcClock.generate(any()) } returns "1636..."`)
- `SyncRepository` (relaxed mock; default to `Result.failure(RuntimeException(...))` to fast-fail sync)
- `DataStore<Preferences>` — use real `PreferenceDataStoreFactory` with `TemporaryFolder`, NOT `mockk` (DataStore has complex internal state)

**What NOT to Mock:**
- SQLite in backend tests (always use real in-memory DB via `createTestDb()`)
- Room database in DAO tests and integration tests (use `Room.inMemoryDatabaseBuilder`)
- Fastify framework internals
- `FlatNodeMapper` (pure function — test directly)

## Fixtures and Factories

**Backend seed helpers (per test file — not shared):**
```typescript
function seedUser(sqlite, id: string): void { /* INSERT INTO users */ }
function seedDocument(sqlite, overrides: Partial<{...}> = {}): string { /* returns id */ }
function seedNode(sqlite, overrides: Partial<{...}>): string { /* returns id */ }
function makeValidNode(overrides = {}): Record<string, unknown> { /* builds valid node body */ }
```
Note: `seedDocument` and `seedNode` helpers are duplicated across `nodes.test.ts`, `sync.test.ts`, and `documents.test.ts`. There is no shared test fixture module beyond `createTestDb.ts`.

**Android entity factories (per test file):**
```kotlin
private fun fakeNode(
    id: String,
    content: String = "",
    parentId: String? = testDocumentId,
    sortOrder: String = "aP",
) = NodeEntity(id = id, documentId = testDocumentId, userId = "user-1", ...)

private fun fakeDocument(
    id: String = "doc-1",
    title: String = "Test Document",
    sortOrder: String = "a0",
) = DocumentEntity(...)
```
These helpers are redefined in each test class — no shared test factory object.

**Location:** All test fixtures are local to their test file. No `test-fixtures/` directory or shared factory classes exist in Android.

## Coverage

**Backend:**
- Coverage configured: `vitest.config.ts` enables V8 provider over `src/**/*.ts`, excluding test files and `test-helpers/`.
- No minimum threshold enforced in config.
- View: `cd backend && pnpm test:coverage`

**Android:**
- No coverage configuration found (`jacoco` not in gradle files).
- Coverage is implicitly enforced by the "task not done until tests pass" rule in `CLAUDE.md`.

## Test Types

**Backend Unit Tests:**
- `hlc/hlc.test.ts`: pure function tests for HLC generate/receive/parse/compare
- `merge.test.ts`: LWW merge commutativity and field-win scenarios
- `tombstone.test.ts`: `purgeTombstones()` logic

**Backend Integration Tests (via @fastify/inject — no real HTTP port):**
- All `routes/*.test.ts` files: full request/response cycle against real SQLite
- Each test file creates a fresh `buildApp(sqlite)` with in-memory DB in `beforeEach`
- Auth flow tested end-to-end with real JWT signing via `jose`

**Android Unit Tests (JVM, Robolectric for Android APIs):**
- `sync/HlcClockTest.kt`: monotonicity, counter increment, format validation
- `sync/LwwMergeTest.kt`: property-based commutativity and idempotency via Kotest
- `ui/mapper/FlatNodeMapperTest.kt`: DFS traversal, collapse behavior, orphan handling
- `ui/screen/*/ViewModelTest.kt`: Orbit MVI state transitions via `viewModel.test { ... }`
- `db/dao/*DaoTest.kt`: real Room inMemory DB, Robolectric runner

**Android Integration Tests:**
- `sync/SyncWorkerIntegrationTest.kt`: real Room inMemory DB + real DataStore + mockk SyncRepository — verifies wiring between worker, DAOs, and DataStore
- `db/AppDatabaseMigrationTest.kt`: verifies Room schema migrations

**E2E Tests:** Not present.

## Common Patterns

**Async Testing (Android):**
```kotlin
@Test
fun `onContentChanged writesToRoom after300ms`() = runTest {
    val viewModel = createViewModel()
    viewModel.test(this) {
        containerHost.onContentChanged("n1", "new content")
        awaitState()                                    // consume optimistic state
        testDispatcher.scheduler.advanceTimeBy(301)     // advance virtual clock
        testDispatcher.scheduler.runCurrent()
    }
    coVerify(exactly = 1) { nodeDao.updateNode(any()) }
}
```
Key: `advanceTimeBy(N)` + `runCurrent()` is the pattern for testing debounced writes. `advanceUntilIdle()` can be used when you want all pending work to complete.

**Async Testing (Backend):**
```typescript
it('returns200_withNodes_forValidDocument', async () => {
  const res = await app.inject({
    method: 'GET',
    url: `/api/nodes?document_id=${docId}`,
    headers: { authorization: `Bearer ${tokenA}` },
  })
  expect(res.statusCode).toBe(200)
  const body = res.json()
  expect(body.nodes).toHaveLength(3)
})
```

**Error Testing (Backend):**
```typescript
it('returns404_whenDocumentNotFound', async () => {
  const res = await app.inject({ method: 'GET', url: `/api/nodes?document_id=${randomUUID()}`, headers: { authorization: `Bearer ${tokenA}` } })
  expect(res.statusCode).toBe(404)
  expect(res.json()).toEqual({ error: 'Document not found' })
})
```

**Property-Based Testing (Android):**
```kotlin
@Test
fun nodeMerge_isCommutative_perField() {
    runBlocking {  // block body required — expression body breaks JUnit4
        checkAll(500, Arb.string(0..8), Arb.string(0..8)) { hlc1, hlc2 ->
            val a = makeNode(contentHlc = hlc1, sortOrderHlc = hlc2)
            val b = makeNode(contentHlc = hlc2, sortOrderHlc = hlc1)
            assertEquals(mergeNode(a, b).content, mergeNode(b, a).content)
        }
    }
}
```

**HLC Reset Between Tests (Backend):**
```typescript
beforeEach(() => {
  resetHlcForTesting()  // resets module-level mutable state in hlc/hlc.ts
})
```
This is required because `hlc/hlc.ts` uses module-level mutable state (`lastMs`, `lastCounter`). Tests that do not call `resetHlcForTesting()` will have order-dependent behavior.

## What Could Have Been Done Better

**1. Screen tests are mostly structural, not behavioral**
- `LoginScreenTest.kt`, `DocumentListScreenTest.kt`, `NodeEditorScreenTest.kt` use `Class.forName()` to verify the composable file exists and check constants/routes. They test no actual behavior.
- Better approach: extract ViewModel logic into testable pure functions (already done for `FlatNodeMapper`), and write behavior tests against those functions instead of reflection-based class existence checks.

**2. Backend seed helpers are duplicated across test files**
- `seedUser()`, `seedDocument()`, `seedNode()` are copy-pasted into `nodes.test.ts`, `sync.test.ts`, `documents.test.ts`, and others. Only the DB connection helper (`createTestDb.ts`) is shared.
- Better approach: a `src/test-helpers/seedHelpers.ts` barrel with shared seed functions.

**3. Robolectric constraint limits FTS testing**
- `AppDatabase.buildInMemory()` excludes FTS virtual table creation because Robolectric's SQLite lacks FTS support. `SearchRepositoryTest.kt` consequently mocks the DAO rather than testing real FTS queries.
- Better approach: use an instrumented test (`androidTest/`) for FTS5 integration, or use a real SQLite JDBC driver in JVM tests (e.g., `xerial/sqlite-jdbc`) instead of Robolectric.

**4. `SyncWorkerTest` vs `SyncWorkerIntegrationTest` — coverage gap**
- `SyncWorkerTest` mocks all DAOs and verifies that `pull()` + `push()` are called with correct args. `SyncWorkerIntegrationTest` uses real Room but mocks `SyncRepository`.
- Neither test exercises the full end-to-end path (real Room + real HTTP). This means that serialization format mismatches between `SyncChangesResponse` and `NodeEntity` would not be caught.

**5. HLC module-level state requires manual reset**
- `hlc/hlc.ts` stores `lastMs` and `lastCounter` as module-level mutable variables, requiring `resetHlcForTesting()` to be called in `beforeEach`. Tests that forget this call will inherit state from previous tests.
- Better approach: make HLC a class instance (like the prototype `HlcClock` class in `src/hlc.ts`), so each test creates a fresh instance with no shared state.

**6. NodeEditorViewModel is untestable without large mock trees**
- Every `NodeEditorViewModel` test must construct 9 dependencies (nodeDao, authRepository, hlcClock, syncRepository, documentDao, bookmarkDao, settingsDao, dataStore, fileRepository). A `createViewModel()` factory is present in each test class but still requires all 9 mocks to be set up.
- Better approach: split `NodeEditorViewModel` into focused sub-ViewModels or use use-case classes to reduce fan-out.

---

*Testing analysis: 2026-03-06*
