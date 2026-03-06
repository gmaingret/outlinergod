# Phase 21: Sync Architecture - Research

**Researched:** 2026-03-06
**Domain:** Android Kotlin — refactoring, Hilt DI, coroutines, Orbit MVI, WorkManager
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Orchestrator shape:**
- New standalone class — `SyncOrchestrator` is a new interface + `SyncOrchestratorImpl` in the `sync/` package (alongside `SyncWorker`, `SyncConstants`, `SyncMappers`)
- `SyncRepository` stays thin — only `pull()` and `push()`; it does not get a `fullSync()` method
- `SyncOrchestrator` is a new Hilt binding in `RepositoryModule` (or a new `SyncModule`)
- The `sync/` package becomes the single place to edit for any sync logic change

**Caller interface:**
- `suspend fun fullSync(): Result<Unit>` — simple, idiomatic with the codebase's `runCatching` pattern
- Orchestrator is fully self-contained: it reads DataStore for `lastSyncHlc`, runs pull/upsert/push/conflict resolution, and writes the new `serverHlc` back to DataStore
- Callers just do: `val result = orchestrator.fullSync()` and map to `SyncStatus` themselves
- ViewModels still own `SyncStatus` state updates (Syncing → Idle/Error) based on the Result

**Token refresh ownership:**
- Orchestrator handles refresh: `fullSync()` calls `authRepository.refreshToken()` as its first step
- If `refreshToken()` fails → `fullSync()` returns `Result.failure()` immediately (no pull or push attempted)
- This matches `SyncWorker`'s current behaviour (returns `Result.failure()` on auth failure)
- Callers no longer call `refreshToken()` themselves — it's absorbed into `fullSync()`

**What callers become after refactor:**
- `DocumentListViewModel.triggerSync()`: `reduce { Syncing }` → `orchestrator.fullSync()` → `reduce { Idle/Error }`
- `NodeEditorViewModel.triggerSync()`: same 3-line pattern
- `SyncWorker.doWork()`: remove manual refresh + entire pull/push block → delegate to `orchestrator.fullSync()`; map `Result.failure()` → `Result.failure()` (WorkManager), `Result.success()` → `Result.success()`

**hasLocalData guard:**
- Orchestrator always includes the `hasLocalData` guard: `val lastSyncHlc = if (documentDao.countDocuments(userId) > 0) storedHlc else "0"`
- Uses `documentDao.countDocuments(userId)` — confirmed present at `DocumentDao.kt:31`
- Makes all 3 callers behave identically on reinstall; NodeEditorViewModel was previously missing this guard

**DAOs in orchestrator:**
- `SyncOrchestratorImpl` constructor injects all 4 DAOs directly: `NodeDao`, `DocumentDao`, `BookmarkDao`, `SettingsDao`
- `SyncRepository` stays thin — only `pull()` and `push()` — no upsert methods added
- SyncWorker removes ALL DAO injections after refactor — only injects `SyncOrchestrator` and `AuthRepository`
- ViewModels keep their own DAO injections for local mutations — only the sync block is delegated

**Test strategy for SyncOrchestrator:**
- Unit tests with MockK — mock `SyncRepository`, `AuthRepository`, all 4 DAOs, `DataStore`
- Required test cases: happy path, auth failure (no pull attempted), pull failure (no push attempted), push failure
- Existing `SyncWorkerIntegrationTest` (Phase 12) updated to use `SyncOrchestrator` — tests run through the same delegation path

### Claude's Discretion
- Exact Hilt module placement for `SyncOrchestrator` binding (new module vs. extension of RepositoryModule)
- Whether `SyncOrchestrator` interface lives in `sync/` or `repository/` (impl always in `sync/`)
- Whether `SyncWorker.doWork()` needs a retry on orchestrator failure or delegates retry to WorkManager (keep current `Result.retry()` behaviour)

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

## Summary

Phase 21 is a pure consolidation refactor. The full pull-then-push sync cycle currently exists in three places — `DocumentListViewModel.triggerSync()`, `NodeEditorViewModel.triggerSync()`, and `SyncWorker.doWork()` — with near-identical code that is copy-pasted verbatim (140+ lines duplicated). The goal is to extract this logic into a single `SyncOrchestrator` class so that any future sync change (a new entity type, a bug fix, a behavioural tweak) requires editing exactly one file.

The refactor introduces no new behaviour. The only bug being fixed is that `NodeEditorViewModel.triggerSync()` currently lacks the `hasLocalData` guard that `DocumentListViewModel` and `SyncWorker` both have; the orchestrator restores consistent behaviour across all callers. All existing tests must remain green (222+ Android tests), and new unit tests for `SyncOrchestrator` must cover the four key scenarios: happy path, auth failure, pull failure, and push failure.

The codebase already has all the infrastructure needed: MockK for unit tests, Orbit MVI's `test {}` DSL for ViewModel tests, `TestListenableWorkerBuilder` for WorkManager tests, and a real in-memory Room `AppDatabase.buildInMemory()` for integration tests. The implementation is a mechanical extraction with well-understood boundaries.

**Primary recommendation:** Extract the sync cycle into `SyncOrchestratorImpl` in `sync/`, bind it in `RepositoryModule`, then gut the three caller `triggerSync()` bodies down to 3 lines each.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Hilt | 2.56.1 | Dependency injection | Project-standard; all other repos use it |
| Kotlin coroutines | (via Kotlin stdlib) | `suspend fun`, `runCatching`, `Flow.first()` | Project-standard for all async |
| DataStore Preferences | (via existing dep) | Persist `lastSyncHlc` per user | Already used in all 3 callers |
| MockK | (existing test dep) | Mock DAOs, repos, DataStore in unit tests | Project-standard for Android unit tests |
| Orbit MVI test | 10.0.0 | `test {}` DSL for ViewModel state assertions | Established in Phase 4 |
| WorkManager testing | 2.10.0 | `TestListenableWorkerBuilder` for SyncWorker | Used in existing SyncWorkerTest |

### No New Dependencies
This phase adds zero new libraries. Everything needed already exists in the project.

## Architecture Patterns

### Recommended Project Structure (sync/ package after refactor)
```
android/app/src/main/java/com/gmaingret/outlinergod/
└── sync/
    ├── SyncOrchestrator.kt        # NEW — interface
    ├── SyncOrchestratorImpl.kt    # NEW — implementation (all the extracted logic)
    ├── SyncWorker.kt              # MODIFIED — gutted to delegate
    ├── SyncConstants.kt           # UNCHANGED
    ├── SyncMappers.kt             # UNCHANGED
    ├── SyncScheduler.kt           # UNCHANGED
    ├── HlcClock.kt                # UNCHANGED
    ├── NodeMerge.kt               # UNCHANGED
    ├── DocumentMerge.kt           # UNCHANGED
    └── BookmarkMerge.kt           # UNCHANGED
```

### Pattern 1: SyncOrchestrator Interface
**What:** A single suspend function that owns the entire sync cycle.
**When to use:** Any caller that needs to sync — ViewModel or WorkManager.

```kotlin
// SyncOrchestrator.kt (in sync/ or repository/ — Claude's discretion)
interface SyncOrchestrator {
    suspend fun fullSync(): Result<Unit>
}
```

### Pattern 2: SyncOrchestratorImpl — Extracted Logic
**What:** The body of `fullSync()` is the union of the three existing `triggerSync()` bodies, with the `hasLocalData` guard always applied.

The canonical sequence (extracted from `SyncWorker.doWork()` which is the most complete version):
1. `authRepository.refreshToken()` — if failure, return `Result.failure()` immediately
2. Read `deviceId`, `userId`, `storedHlc` from DataStore
3. Apply `hasLocalData` guard: if `documentDao.countDocuments(userId) == 0`, set `lastSyncHlc = "0"`
4. `syncRepository.pull(since = lastSyncHlc, deviceId = deviceId)` — if failure, return `Result.failure()` (callers decide retry)
5. Upsert pulled data: documents → nodes → bookmarks → settings (documents before nodes — application-level referential integrity)
6. Gather pending changes from all 4 DAOs
7. Build `SyncPushPayload` and call `syncRepository.push(payload)` — if failure, return `Result.failure()`
8. Apply conflict resolutions from push response (nodes, documents, bookmarks, settings)
9. Write `pushResponse.serverHlc` to DataStore
10. Return `Result.success(Unit)`

**Key difference from existing callers:** `SyncWorker.doWork()` returns `Result.retry()` on pull/push failure. `SyncOrchestratorImpl.fullSync()` must return `Result.failure()` on any network failure — `SyncWorker.doWork()` then maps `Result.failure()` to `Result.retry()` itself. This keeps the orchestrator agnostic to WorkManager concepts.

```kotlin
// SyncOrchestratorImpl.kt (sketch — planner will produce the full implementation)
@Singleton
class SyncOrchestratorImpl @Inject constructor(
    private val syncRepository: SyncRepository,
    private val authRepository: AuthRepository,
    private val nodeDao: NodeDao,
    private val documentDao: DocumentDao,
    private val bookmarkDao: BookmarkDao,
    private val settingsDao: SettingsDao,
    private val dataStore: DataStore<Preferences>
) : SyncOrchestrator {

    override suspend fun fullSync(): Result<Unit> = runCatching {
        // Step 1: Refresh token
        authRepository.refreshToken().getOrThrow()

        // Steps 2-3: Auth + HLC cursor
        val deviceId = authRepository.getDeviceId().first()
        val userId = authRepository.getUserId().filterNotNull().first()
        val storedHlc = dataStore.data.map { prefs ->
            prefs[SyncConstants.lastSyncHlcKey(userId)] ?: "0"
        }.first()
        val hasLocalData = documentDao.countDocuments(userId) > 0
        val lastSyncHlc = if (hasLocalData) storedHlc else "0"

        // Step 4: Pull
        val pullResponse = syncRepository.pull(since = lastSyncHlc, deviceId = deviceId).getOrThrow()

        // Step 5: Upsert pulled data (documents before nodes)
        if (pullResponse.documents.isNotEmpty()) documentDao.upsertDocuments(pullResponse.documents.map { it.toDocumentEntity() })
        if (pullResponse.nodes.isNotEmpty()) nodeDao.upsertNodes(pullResponse.nodes.map { it.toNodeEntity() })
        if (pullResponse.bookmarks.isNotEmpty()) bookmarkDao.upsertBookmarks(pullResponse.bookmarks.map { it.toBookmarkEntity() })
        pullResponse.settings?.let { settingsDao.upsertSettings(it.toSettingsEntity()) }

        // Steps 6-7: Gather pending + push
        val pushPayload = SyncPushPayload(
            deviceId = deviceId,
            nodes = nodeDao.getPendingChanges(userId, lastSyncHlc, deviceId).map { it.toNodeSyncRecord() }.ifEmpty { null },
            documents = documentDao.getPendingChanges(userId, lastSyncHlc, deviceId).map { it.toDocumentSyncRecord() }.ifEmpty { null },
            bookmarks = bookmarkDao.getPendingChanges(userId, lastSyncHlc, deviceId).map { it.toBookmarkSyncRecord() }.ifEmpty { null },
            settings = settingsDao.getPendingSettings(userId, lastSyncHlc, deviceId)?.toSettingsSyncRecord()
        )
        val pushResponse = syncRepository.push(pushPayload).getOrThrow()

        // Step 8: Apply conflicts
        if (pushResponse.conflicts.nodes.isNotEmpty()) nodeDao.upsertNodes(pushResponse.conflicts.nodes.map { it.toNodeEntity() })
        if (pushResponse.conflicts.documents.isNotEmpty()) documentDao.upsertDocuments(pushResponse.conflicts.documents.map { it.toDocumentEntity() })
        if (pushResponse.conflicts.bookmarks.isNotEmpty()) bookmarkDao.upsertBookmarks(pushResponse.conflicts.bookmarks.map { it.toBookmarkEntity() })
        pushResponse.conflicts.settings?.let { settingsDao.upsertSettings(it.toSettingsEntity()) }

        // Step 9: Update HLC cursor
        dataStore.edit { prefs -> prefs[SyncConstants.lastSyncHlcKey(userId)] = pushResponse.serverHlc }
    }
}
```

### Pattern 3: Caller Reduction — ViewModel
**What:** Each ViewModel's `triggerSync()` body collapses to 3 lines.

```kotlin
// DocumentListViewModel.triggerSync() after refactor
private fun triggerSync() = intent {
    reduce { /* ... copy(syncStatus = SyncStatus.Syncing) */ }
    val result = syncOrchestrator.fullSync()
    reduce { /* ... copy(syncStatus = if (result.isSuccess) SyncStatus.Idle else SyncStatus.Error) */ }
}
```

Note: `DocumentListViewModel.triggerSync()` is currently `fun triggerSync()` (public). After refactor it becomes `private fun triggerSync()` only if no external caller uses it. Check: `createDocument`, `renameDocument`, `deleteDocument` all call `triggerSync()` internally; `onScreenResumed` starts a loop calling it. It should stay internal — but its visibility modifier does not need to change unless tests call it directly (the `DocumentListSyncTest` calls `containerHost.triggerSync()` which uses Orbit MVI's test DSL, not direct invocation).

### Pattern 4: Caller Reduction — SyncWorker
**What:** SyncWorker becomes a thin adapter between WorkManager and the orchestrator.

```kotlin
// SyncWorker.doWork() after refactor
override suspend fun doWork(): Result {
    return when (syncOrchestrator.fullSync().isSuccess) {
        true -> Result.success()
        false -> Result.retry()
    }
}
```

Note: The current SyncWorker distinguishes between `Result.failure()` (auth failure) and `Result.retry()` (network failure). After the refactor, `fullSync()` returns a single `Result<Unit>` without this distinction. The CONTEXT.md decisions say to keep current `Result.retry()` behaviour — meaning SyncWorker maps any `fullSync()` failure to `Result.retry()`. Auth failure distinction is lost, but CONTEXT.md accepts this ("keep current `Result.retry()` behaviour"). Verify: the current `doWork()` does return `Result.failure()` on auth failure vs `Result.retry()` on network. The CONTEXT says: "map `Result.failure()` → `Result.failure()` (WorkManager), `Result.success()` → `Result.success()`". This means SyncWorker should map orchestrator failure to WorkManager `Result.failure()`. But Claude's Discretion says "keep current `Result.retry()` behaviour". The planner should use WorkManager `Result.failure()` for the doWork return to match the CONTEXT.md caller section, and note the auth/network distinction cannot be preserved without adding an error type.

### Pattern 5: Hilt Binding
**What:** Add `SyncOrchestrator` to `RepositoryModule` (or a new `SyncModule` — Claude's discretion).

```kotlin
// In RepositoryModule.kt (simplest — no new file)
@Provides
@Singleton
fun provideSyncOrchestrator(impl: SyncOrchestratorImpl): SyncOrchestrator = impl
```

The `SyncOrchestratorImpl` constructor uses `@Inject` so Hilt can construct it. All constructor parameters (DAOs, repos, DataStore) are already singleton-bound.

### Anti-Patterns to Avoid
- **Merging into SyncRepository:** `SyncRepository` interface stays thin (`pull` + `push` only). A `fullSync()` on `SyncRepository` would make it responsible for DAO access, violating its role as a network-only layer.
- **Returning rich error types from fullSync():** The existing codebase uses `Result<T>` with `runCatching`. Adding a sealed class for error types would be inconsistent and out of scope.
- **Removing DataStore injection from ViewModels if they still need it:** Both ViewModels inject `DataStore` for purposes other than HLC; do not remove it from their constructors unless verified it has no other use.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Coroutine exception wrapping | Custom exception hierarchy | `runCatching {}` + `Result<T>` | Established pattern in entire codebase |
| Hilt constructor injection boilerplate | Manual factory | `@Inject constructor` + `@Singleton` | Already works for every other class |
| WorkManager test wiring | Custom test runner | `TestListenableWorkerBuilder` + custom `WorkerFactory` | Established in SyncWorkerTest/SyncWorkerIntegrationTest |
| Orbit MVI ViewModel test assertions | Manual state collection | `viewModel.test(this) { expectState(...) }` | Established in DocumentListSyncTest |

## Common Pitfalls

### Pitfall 1: SyncWorker Factory Still References Old Constructor
**What goes wrong:** After removing DAO parameters from `SyncWorker`'s constructor, the `createWorkerFactory()` lambda in both `SyncWorkerTest` and `SyncWorkerIntegrationTest` will fail to compile because it passes the old parameters.
**Why it happens:** The `WorkerFactory` lambda directly calls the `SyncWorker` constructor — it must be updated in sync with the constructor change.
**How to avoid:** Update both test files' `createWorkerFactory()` to pass `syncOrchestrator` instead of the 4 DAOs + `dataStore`.
**Warning signs:** Compile error "Too many arguments" in SyncWorkerTest.kt or SyncWorkerIntegrationTest.kt.

### Pitfall 2: DocumentListSyncTest Tests Internals That No Longer Exist
**What goes wrong:** `DocumentListSyncTest` verifies `nodeDao.upsertNodes(...)` was called (via `coVerify`) and that `dataStore` contains the updated HLC. After refactor, these calls happen inside `SyncOrchestratorImpl`, not in the ViewModel — so the mocks in the ViewModel test are never triggered.
**Why it happens:** The ViewModel test mocks DAOs directly; after the ViewModel delegates to the orchestrator, the DAO mocks are unreachable.
**How to avoid:** `DocumentListSyncTest` must be rewritten to mock `SyncOrchestrator` instead of `SyncRepository` + DAOs. The tests verifying `upsertNodes` and DataStore HLC move to `SyncOrchestratorTest`. The ViewModel tests only verify state transitions (Syncing → Idle/Error).
**Warning signs:** Tests pass but coverage of orchestrator logic is zero; or tests fail with "no call recorded" on `nodeDao`.

### Pitfall 3: NodeEditorViewModel Keeps Importing SyncMappers
**What goes wrong:** After removing the sync block from `NodeEditorViewModel.triggerSync()`, the mapper imports (`toNodeEntity`, `toDocumentEntity`, etc.) become unused. Kotlin compiler will warn; the build may succeed but it's dead code.
**Why it happens:** The imports were only used in the now-deleted sync block.
**How to avoid:** Remove all `import com.gmaingret.outlinergod.sync.to*` imports from NodeEditorViewModel (and from DocumentListViewModel) after gutting their sync blocks. Keep only what local mutations still use.
**Warning signs:** IDE shows "Unused import" warnings on all `toXxx` extension function imports.

### Pitfall 4: `triggerSync()` Visibility in DocumentListSyncTest
**What goes wrong:** The `DocumentListSyncTest` calls `containerHost.triggerSync()`. If `triggerSync()` is marked `private` in the ViewModel, the Orbit MVI test DSL still accesses it via the ContainerHost interface — but only if Orbit's test framework can invoke it. Verify this works.
**Why it happens:** Orbit MVI's `test {}` DSL uses the `ContainerHost` interface; it invokes intents via the public containerHost reference. Private functions are accessible from within the class but the test DSL calls them on the outer ContainerHost.
**How to avoid:** Do not change the visibility of `triggerSync()`. Leave it as-is (`fun triggerSync()` in DocumentListViewModel, `private fun triggerSync()` in NodeEditorViewModel). The current test pattern works as-is for both.
**Warning signs:** Compile error in test: "Cannot access 'triggerSync': it is private in 'NodeEditorViewModel'".

### Pitfall 5: Circular Dependency if SyncOrchestrator Depends on AuthRepository which Depends on HttpClient which Depends on SyncOrchestrator
**What goes wrong:** Hilt graph fails at compile time with "cycle detected".
**Why it happens:** `NetworkModule` uses `dagger.Lazy<AuthRepository>` specifically to break this cycle (see MEMORY.md: "NetworkModule uses `dagger.Lazy<AuthRepository>` to break circular dep with HttpClient"). `SyncOrchestratorImpl` injecting `AuthRepository` directly is fine because it does not go through the HttpClient cycle.
**How to avoid:** `SyncOrchestratorImpl` injects `AuthRepository` directly (not `Lazy<AuthRepository>`). This is safe — the circular dep is HttpClient ↔ AuthRepository, not SyncOrchestrator ↔ AuthRepository.
**Warning signs:** Hilt compilation error mentioning "Found a dependency cycle".

### Pitfall 6: SyncWorkerIntegrationTest Regression
**What goes wrong:** The integration test creates `SyncWorker` directly with a real `AppDatabase`. After the refactor, `SyncWorker` no longer holds DAOs — but the integration test existed to verify that pulled nodes actually land in Room. If we mock `SyncOrchestrator` in the updated integration test, we lose the integration coverage.
**Why it happens:** The integration test's value was verifying the DAO wiring. After moving DAO calls to `SyncOrchestratorImpl`, a real integration test should test `SyncOrchestratorImpl` directly with a real database — not `SyncWorker`.
**How to avoid:** Create `SyncOrchestratorIntegrationTest` with a real in-memory Room DB (using `AppDatabase.buildInMemory(context)`), mirroring the pattern from `SyncWorkerIntegrationTest`. Update `SyncWorkerIntegrationTest` to mock `SyncOrchestrator` (it becomes a thin test verifying WorkManager result mapping only).
**Warning signs:** After refactor, `SyncWorkerIntegrationTest` still references `nodeDao` and `documentDao` in the worker factory — compile error.

## Code Examples

Verified patterns from existing codebase:

### runCatching Pattern (from SyncRepositoryImpl)
```kotlin
// Source: android/app/src/main/java/com/gmaingret/outlinergod/repository/impl/SyncRepositoryImpl.kt
return runCatching {
    val response = httpClient.get("/api/sync/pull") { ... }
    response.body<SyncChangesResponse>()
}
```

### Auth value reading in coroutines (from SyncWorker.doWork())
```kotlin
// Source: android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncWorker.kt
val deviceId = authRepository.getDeviceId().first()
val userId = authRepository.getUserId().filterNotNull().first()
val storedHlc = dataStore.data.map { prefs ->
    prefs[SyncConstants.lastSyncHlcKey(userId)] ?: "0"
}.first()
```

### DataStore HLC write (from DocumentListViewModel.triggerSync())
```kotlin
// Source: android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListViewModel.kt
dataStore.edit { prefs ->
    prefs[SyncConstants.lastSyncHlcKey(userId)] = response.serverHlc
}
```

### Hilt Singleton binding in RepositoryModule (from existing module)
```kotlin
// Source: android/app/src/main/java/com/gmaingret/outlinergod/di/RepositoryModule.kt
@Provides
@Singleton
fun provideSyncRepository(impl: SyncRepositoryImpl): SyncRepository = impl
```

### WorkManager custom factory in test (from SyncWorkerTest)
```kotlin
// Source: android/app/src/test/java/com/gmaingret/outlinergod/sync/SyncWorkerTest.kt
private fun createWorkerFactory(): WorkerFactory {
    return object : WorkerFactory() {
        override fun createWorker(
            appContext: Context, workerClassName: String, workerParameters: WorkerParameters
        ): ListenableWorker = SyncWorker(appContext, workerParameters, syncRepository, authRepository, ...)
    }
}
```

### Orbit MVI state assertion in test (from DocumentListSyncTest)
```kotlin
// Source: android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListSyncTest.kt
viewModel.test(this) {
    containerHost.triggerSync()
    expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Syncing))
    expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Idle))
}
```

### In-memory Room for integration tests (from SyncWorkerIntegrationTest)
```kotlin
// Source: android/app/src/test/java/com/gmaingret/outlinergod/sync/SyncWorkerIntegrationTest.kt
database = AppDatabase.buildInMemory(context)
nodeDao = database.nodeDao()
// NOTE: no FTS virtual table in Robolectric — buildInMemory() omits FTS_CALLBACK
```

## Exact Divergence Between the Three Callers

Understanding what differs is essential for planning the extraction correctly.

| Aspect | DocumentListViewModel | NodeEditorViewModel | SyncWorker |
|--------|----------------------|---------------------|------------|
| `hasLocalData` guard | YES | NO (bug) | YES |
| `refreshToken()` call | NO | NO | YES (step 1) |
| Upsert order | documents → nodes → bookmarks → settings | nodes → documents → bookmarks → settings | documents → nodes → bookmarks → settings |
| Pull failure handling | `getOrThrow()` → caught by try/catch | `getOrThrow()` → caught by try/catch | `getOrElse { return Result.retry() }` |
| Push failure handling | `getOrThrow()` → caught by try/catch | `getOrThrow()` → caught by try/catch | `getOrElse { return Result.retry() }` |
| Return value | Unit (updates SyncStatus) | Unit (updates SyncStatus) | WorkManager Result |

The orchestrator canonical version: add `refreshToken()`, add `hasLocalData` guard, fix upsert order to documents-before-nodes, use `runCatching {}` / `getOrThrow()` for network calls, return `Result<Unit>`.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + MockK + Kotest (Android unit) |
| Config file | android/app/build.gradle.kts (testImplementation deps) |
| Quick run command | `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && export ANDROID_HOME="/c/Users/gmain/AppData/Local/Android/Sdk" && cd android && ./gradlew test --tests "*.SyncOrchestratorTest" --tests "*.SyncOrchestratorIntegrationTest"` |
| Full suite command | `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && export ANDROID_HOME="/c/Users/gmain/AppData/Local/Android/Sdk" && cd android && ./gradlew test` |

### Phase Requirements → Test Map

| Behavior | Test Type | Automated Command | File Exists? |
|----------|-----------|-------------------|-------------|
| `SyncOrchestrator.fullSync()` happy path | unit | `./gradlew test --tests "*.SyncOrchestratorTest"` | ❌ Wave 0 |
| Auth failure → Result.failure(), no pull | unit | `./gradlew test --tests "*.SyncOrchestratorTest"` | ❌ Wave 0 |
| Pull failure → Result.failure(), no push | unit | `./gradlew test --tests "*.SyncOrchestratorTest"` | ❌ Wave 0 |
| Push failure → Result.failure() | unit | `./gradlew test --tests "*.SyncOrchestratorTest"` | ❌ Wave 0 |
| Pulled nodes land in real Room DB | integration | `./gradlew test --tests "*.SyncOrchestratorIntegrationTest"` | ❌ Wave 0 |
| HLC written to real DataStore | integration | `./gradlew test --tests "*.SyncOrchestratorIntegrationTest"` | ❌ Wave 0 |
| DocumentListViewModel: Syncing → Idle on success | unit | `./gradlew test --tests "*.DocumentListSyncTest"` | ✅ (must update) |
| DocumentListViewModel: Syncing → Error on failure | unit | `./gradlew test --tests "*.DocumentListSyncTest"` | ✅ (must update) |
| SyncWorker: success → Result.success() | unit | `./gradlew test --tests "*.SyncWorkerTest"` | ✅ (must update) |
| SyncWorker: failure → Result.failure()/retry() | unit | `./gradlew test --tests "*.SyncWorkerTest"` | ✅ (must update) |
| All 222+ existing tests still pass | regression | `./gradlew test` | ✅ |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "*.SyncOrchestratorTest" --tests "*.SyncWorkerTest" --tests "*.DocumentListSyncTest"`
- **Per wave merge:** `./gradlew test` (full suite)
- **Phase gate:** Full suite green (222+ tests) before marking phase complete

### Wave 0 Gaps
- [ ] `android/app/src/test/java/com/gmaingret/outlinergod/sync/SyncOrchestratorTest.kt` — 4 unit tests (happy path, auth failure, pull failure, push failure)
- [ ] `android/app/src/test/java/com/gmaingret/outlinergod/sync/SyncOrchestratorIntegrationTest.kt` — integration tests with real in-memory Room (mirrors SyncWorkerIntegrationTest pattern)

*(All existing test infrastructure is present — no new framework installs required)*

## Open Questions

1. **SyncWorker failure mapping: `Result.failure()` vs `Result.retry()`**
   - What we know: CONTEXT.md says SyncWorker maps `Result.failure()` → WorkManager `Result.failure()` and `Result.success()` → `Result.success()`. But CONTEXT.md Claude's Discretion says "keep current `Result.retry()` behaviour".
   - What's unclear: The current SyncWorker uses `Result.retry()` for network failures and `Result.failure()` only for auth failures. After the refactor, `fullSync()` returns a single `Result<Unit>` — the distinction is lost.
   - Recommendation: Map `fullSync()` failure to WorkManager `Result.failure()` (per the CONTEXT.md decision table). Document that the auth-vs-network distinction is intentionally collapsed. WorkManager will not retry on `Result.failure()`, which is more conservative but acceptable for a tech-debt phase.

2. **`triggerSync()` visibility in DocumentListViewModel**
   - What we know: `DocumentListViewModel.triggerSync()` is currently public. `DocumentListSyncTest` calls it via Orbit's `containerHost.triggerSync()`.
   - What's unclear: After the refactor, `triggerSync()` is still called internally by `createDocument`, `renameDocument`, `deleteDocument`. There's no strong reason to change its visibility.
   - Recommendation: Leave visibility unchanged. Making it `private` would break the Orbit test DSL call; making it `internal` is unnecessary churn.

## Sources

### Primary (HIGH confidence)
- Direct code reading of `SyncWorker.kt`, `DocumentListViewModel.kt`, `NodeEditorViewModel.kt` — exact implementation verified
- Direct code reading of `SyncWorkerTest.kt`, `SyncWorkerIntegrationTest.kt`, `DocumentListSyncTest.kt` — exact test patterns verified
- Direct code reading of `RepositoryModule.kt`, `AuthRepository.kt`, `SyncRepository.kt`, `SyncConstants.kt` — exact interfaces verified

### Secondary (MEDIUM confidence)
- MEMORY.md project memory — verified cross-checked with actual source files
- CONTEXT.md decisions — authoritative (user-locked decisions)

### Tertiary (LOW confidence)
- None — all findings verified directly from source code

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in project, no new dependencies
- Architecture: HIGH — extraction pattern derived directly from existing code; no speculation
- Pitfalls: HIGH — identified from structural analysis of exact code being changed and existing tests that will break

**Research date:** 2026-03-06
**Valid until:** Stable — this research covers a refactor of existing code; no external library changes needed
