# Phase 12: integration-e2e - Research

**Researched:** 2026-03-04
**Domain:** Android integration testing, sync correctness verification, backend hardening, UX polish
**Confidence:** MEDIUM (architecture patterns HIGH; specific tool versions MEDIUM; library APIs vary by source)

---

## Summary

Phase 12 is the final phase of OutlinerGod. It closes the remaining known tech debt items from the v0.4 milestone audit, wires the glyph tap zoom-in (currently a no-op), integrates Phase 11's search/export/bookmarks screens into navigation, and delivers a suite of integration verification tests that exercise the full Android-to-backend sync cycle without a real device.

The project has no unit testing gaps at the ViewModel/DAO level — 236 tests pass as of Phase 10. The remaining work is (1) structured integration testing of the sync protocol, (2) fixing the four named tech debt items from the v0.4 audit, (3) backend hardening for self-hosted deployment, and (4) final UX polish. There is no Espresso or Compose UI instrumented test infrastructure in place; the project uses JVM-only tests via Robolectric. Phase 12 should stay within that boundary: instrumented E2E tests would require device/emulator infrastructure that is not set up.

**Primary recommendation:** Scope Phase 12 as "final integration, tech debt closure, and backend hardening" — not full end-to-end device testing. The realistic E2E verification is a manual checklist (as used in all prior VERIFICATION.md files) plus a new SyncWorkerTest that exercises the full pull-then-push cycle against an in-memory DB using TestListenableWorkerBuilder.

---

## Standard Stack

The existing stack is locked. No new major libraries are needed. The additions below are narrowly targeted.

### Core (existing — confirmed versions)

| Library | Version | Purpose | Status |
|---------|---------|---------|--------|
| Orbit MVI | 10.0.0 | ViewModel state | All ViewModels |
| Room | 2.8.4 | Local database | All DAOs |
| WorkManager | 2.10.0 | Background sync | SyncWorker, SyncScheduler |
| Hilt | 2.56.1 | DI | All modules |
| Ktor Client | 3.1.3 | HTTP | KtorClientFactory |
| Fastify | 5.7.4 | Backend | All routes |
| Drizzle ORM | 0.45.1 | Backend ORM | All routes |
| Vitest | ^2.0.0 | Backend tests | All route tests |

### New for Phase 12

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| `work-testing` | 2.10.0 | WorkManager test helpers | Already in testImplementation; needed for SyncWorkerTest |
| `@fastify/rate-limit` | ^10.3.0 | Auth route rate limiting | Prevents brute-force on /api/auth/google |

### Not Needed

- No Espresso. No Compose UI test instrumentation beyond what already exists.
- No Firebase Test Lab or device farms.
- No new navigation libraries (Navigation Compose already in use).

**Installation (backend only):**
```bash
cd backend && pnpm add @fastify/rate-limit
```

---

## Architecture Patterns

### Pattern 1: WorkManager Integration Test with TestListenableWorkerBuilder

**What:** Test SyncWorker.doWork() in isolation using a real in-memory Room database and a Ktor mock engine — no real HTTP, no real WorkManager scheduler.

**When to use:** Testing the full pull-then-push sync cycle including HLC update, conflict resolution, and last_sync_hlc persistence in DataStore.

**Source:** https://developer.android.com/develop/background-work/background-tasks/testing/persistent/worker-impl

```kotlin
// Source: Android Developers - Testing Worker implementation
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SyncWorkerTest {
    // Use TestListenableWorkerBuilder for CoroutineWorker (NOT TestWorkerBuilder)
    // TestWorkerBuilder is for synchronous Worker only

    @Test
    fun doWork_pullsAndPushes_updatesLastSyncHlc() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Build worker with injected fakes (WorkerFactory required for Hilt workers)
        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(TestSyncWorkerFactory(
                syncRepository = fakeSyncRepository,
                authRepository = fakeAuthRepository,
                nodeDao = inMemoryNodeDao,
                // ...
            ))
            .build()

        val result = worker.doWork()
        assertThat(result, `is`(Result.success()))
    }
}
```

**Critical note:** Hilt workers use `@AssistedInject` — `TestListenableWorkerBuilder` cannot instantiate them via reflection. A custom `WorkerFactory` that constructs `SyncWorker` with test fakes is mandatory.

### Pattern 2: Backend Rate Limiting on Auth Routes

**What:** Register `@fastify/rate-limit` with narrow scope on `/api/auth/google` and `/api/auth/refresh` only (not globally, since self-hosted single user traffic is low everywhere else).

**Source:** https://github.com/fastify/fastify-rate-limit (v10.3.0, Fastify ^5)

```typescript
// Source: fastify-rate-limit GitHub - scoped plugin registration
import rateLimit from '@fastify/rate-limit'

// Inside createAuthRoutes plugin, NOT global registration in buildApp():
fastify.register(rateLimit, {
    max: 10,
    timeWindow: '1 minute',
    // keyGenerator uses IP by default — correct for self-hosted
})

// Then register the protected route:
fastify.post('/auth/google', async (req, reply) => { ... })
```

**Why scoped:** Global rate limit would interfere with sync push which sends large batches. Auth routes are the only brute-force risk.

### Pattern 3: Tombstone Purge on Server Startup

**What:** Delete soft-deleted records older than 90 days on container startup, after migrations run.

**When to use:** Called once in `startServer()` after `runMigrations()`.

```typescript
// Source: ARCHITECTURE.md Section 3 - Soft deletes with 90-day tombstone retention
function purgeTombstones(sqlite: InstanceType<typeof Database>): void {
    const ninetyDaysAgo = Date.now() - 90 * 24 * 60 * 60 * 1000
    sqlite.prepare('DELETE FROM nodes WHERE deleted_at IS NOT NULL AND deleted_at < ?').run(ninetyDaysAgo)
    sqlite.prepare('DELETE FROM documents WHERE deleted_at IS NOT NULL AND deleted_at < ?').run(ninetyDaysAgo)
    sqlite.prepare('DELETE FROM bookmarks WHERE deleted_at IS NOT NULL AND deleted_at < ?').run(ninetyDaysAgo)
}
```

### Pattern 4: NodeDao userId Filter Fix (Tech Debt TD-4)

**What:** Add `user_id = :userId` filter to `NodeDao.getPendingChanges` to match `DocumentDao` and `BookmarkDao` pattern.

**Why:** Currently `NodeDao.getPendingChanges` filters by `device_id` only. In a multi-user scenario (even if currently single-user self-hosted), a different user's nodes on the same device would be pushed under the wrong user ID.

```kotlin
// Current (inconsistent):
@Query("SELECT * FROM nodes WHERE device_id = :deviceId AND (...hlc conditions...)")
suspend fun getPendingChanges(sinceHlc: String, deviceId: String): List<NodeEntity>

// Fixed (consistent with DocumentDao and BookmarkDao):
@Query("SELECT * FROM nodes WHERE user_id = :userId AND device_id = :deviceId AND (...hlc conditions...)")
suspend fun getPendingChanges(userId: String, sinceHlc: String, deviceId: String): List<NodeEntity>
```

**Call site updates required:** NodeEditorViewModel.triggerSync(), SyncWorker.doWork(), DocumentListViewModel (if it calls getPendingChanges) — all must pass userId.

### Pattern 5: Zoom-In Navigation (Glyph Tap)

**What:** Tap on a node glyph = zoom in (hoist to that node as root). Per CLAUDE.md: "Tap glyph = zoom in. Never collapse."

**Current state:** `onGlyphTap = { /* zoom in -- wired in future task */ }` at NodeEditorScreen.kt line 272.

**Implementation approach:** Navigate to NODE_EDITOR route with a modified `rootNodeId` argument. The NodeEditorViewModel already has `loadDocument(documentId)` — the zoom pattern needs a `zoomNode(nodeId)` intent that sets a `rootNodeId` in state, causing `mapToFlatList` to treat that node as root.

```kotlin
// In NodeEditorUiState:
data class NodeEditorUiState(
    // ... existing fields ...
    val rootNodeId: String? = null  // null = document root, non-null = zoomed node
)

// In NodeEditorViewModel:
fun zoomIn(nodeId: String) = intent {
    reduce { state.copy(rootNodeId = nodeId) }
    // mapToFlatList already accepts rootNodeId parameter — wire it
}
```

**Navigation back stack:** Each zoom push is a Compose navigation back stack entry. System Back naturally "zooms out."

### Anti-Patterns to Avoid

- **Global rate limiting:** Don't apply `@fastify/rate-limit` globally — the sync push route sends hundreds of records in one request and would hit limits immediately.
- **Instrumented E2E tests without device infrastructure:** Don't add `androidTest` instrumented tests that require an emulator — the CI environment does not have one. Stay with JVM + Robolectric.
- **Testing WorkManager via `TestWorkerBuilder`:** `TestWorkerBuilder` is for synchronous `Worker` only. `SyncWorker` is `CoroutineWorker` — use `TestListenableWorkerBuilder`.
- **Skipping WorkerFactory for Hilt workers:** `TestListenableWorkerBuilder.build()` uses reflection and cannot handle `@AssistedInject`. Always provide a custom `WorkerFactory`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Rate limiting | Custom request counter in middleware | `@fastify/rate-limit` v10.3.0 | Time-windowed LRU with distributed storage support, handles header emission (Retry-After, X-RateLimit-*) |
| WorkManager integration testing | Manual coroutine runner | `TestListenableWorkerBuilder` | Handles CoroutineWorker lifecycle, avoids thread timing issues |
| Tombstone purge scheduler | Custom cron-like timer | One-shot call in `startServer()` | Self-hosted Docker; startup-time purge is sufficient for 90-day retention |
| FractionalIndex key generation | Custom string algorithm | Existing `FractionalIndex.kt` Kotlin port | Already implemented and tested in Phase 6; reuse |

**Key insight:** Phase 12 is a finishing phase. The "don't hand-roll" principle is strongest here — the existing infrastructure covers almost everything. The only new library is `@fastify/rate-limit`.

---

## Common Pitfalls

### Pitfall 1: NodeDao.getPendingChanges Missing userId — Silent Data Leak

**What goes wrong:** The current `NodeDao.getPendingChanges(sinceHlc, deviceId)` has no `user_id` filter. If a second user ever authenticates on the same device (different Google account), their nodes would be included in another user's sync push.

**Why it happens:** Tech debt TD-4 was identified in the v0.4 audit as "safe in single-user self-hosted model" but architecturally inconsistent. In Phase 12, this should be fixed as it is a correctness debt.

**How to avoid:** Add `userId` parameter matching `DocumentDao.getPendingChanges(userId, sinceHlc, deviceId)` pattern. Update all three call sites: SyncWorker, NodeEditorViewModel, DocumentListViewModel.

**Warning signs:** If NodeDao.getPendingChanges returns nodes from other test users in SyncWorkerTest, the filter is missing.

### Pitfall 2: TestListenableWorkerBuilder Cannot Instantiate HiltWorker Without WorkerFactory

**What goes wrong:** `TestListenableWorkerBuilder<SyncWorker>(context).build()` throws `IllegalStateException` or uses reflection to call a no-arg constructor that doesn't exist on `@AssistedInject` workers.

**Why it happens:** Hilt's `@HiltWorker` + `@AssistedInject` requires `HiltWorkerFactory` at runtime. The test builder doesn't have Hilt's component graph.

**How to avoid:** Provide `.setWorkerFactory(customFactory)` where `customFactory` constructs `SyncWorker` directly with fake/real dependencies. The factory's `createWorker()` method instantiates the worker with test doubles.

**Warning signs:** Build succeeds but test crashes at runtime on the `SyncWorker` constructor call.

### Pitfall 3: Rate Limit on Sync Push Route Breaks Batch Uploads

**What goes wrong:** If `@fastify/rate-limit` is registered globally, the POST `/api/sync/changes` route hits the limit during normal sync (one push per 15 minutes with potentially 100+ records).

**Why it happens:** Rate limit counts requests, not records per request. One batch sync push is one HTTP request, but that request body may contain hundreds of nodes.

**How to avoid:** Register rate limit only inside the auth route plugin scope. The sync routes must remain unlimited.

### Pitfall 4: zoom-in Back Stack Does Not Match System Back Behavior

**What goes wrong:** If zoom-in navigates to a new `NodeEditorScreen` composable (separate NavBackStackEntry), pressing Back should pop to the previous zoom level. If implemented as in-screen state only, Back exits the editor entirely.

**Why it happens:** Zoom-in and Back are semantically linked — the navigation back stack IS the zoom history (per ARCHITECTURE.md Section 5).

**How to avoid:** Implement zoom-in as a navigation action to the same NODE_EDITOR route with a different `rootNodeId` argument. Each zoom level is a separate back stack entry. Do NOT implement as internal ViewModel state mutation without navigation.

### Pitfall 5: `restoreNode` Clears `deletedAt` But Leaves `deletedHlc` Set

**What goes wrong:** `restoreNode` in NodeEditorViewModel (Phase 10) sets `deletedAt = null` but updates `deletedHlc` to a new HLC. This is semantically correct for LWW (new HLC proves the "undelete" operation is newer), but it means the server's `GET /sync/changes` query (`deleted_hlc > since`) will return the node again on next pull — which correctly re-applies the restore.

**Verdict:** This is working as designed. Not a bug — document it clearly in the SyncWorkerTest so future developers do not "fix" it.

### Pitfall 6: CreateDocumentRequest.kt — Dead Code (TD-2)

**What goes wrong:** `CreateDocumentRequest.kt` is defined but never imported. If Phase 11 wires direct REST document creation (instead of sync-path creation), this class becomes the natural API model. If left as dead code, it creates confusion about whether documents should be created via REST or sync.

**How to avoid:** Either delete it in Phase 12 or wire it — do not leave it as ambiguous dead code. Given the sync path is the established pattern, deletion is simpler.

### Pitfall 7: Flaky BookmarkDaoTest Remains Pre-existing

**What goes wrong:** `BookmarkDaoTest.observeAllActive_excludesOtherUsers` has a known Robolectric/Room race. It passes on retry.

**How to avoid:** Do not attempt to fix it in Phase 12 — it is pre-existing and not introduced by this phase's changes. Document it in VERIFICATION.md.

---

## Code Examples

### SyncWorker Test with Custom WorkerFactory

```kotlin
// Source: Android Developers - Worker implementation testing + Hilt worker pattern
// Adapted from: https://developer.android.com/develop/background-work/background-tasks/testing/persistent/worker-impl

class FakeSyncWorkerFactory(
    private val syncRepository: SyncRepository,
    private val authRepository: AuthRepository,
    private val nodeDao: NodeDao,
    private val documentDao: DocumentDao,
    private val bookmarkDao: BookmarkDao,
    private val settingsDao: SettingsDao,
    private val dataStore: DataStore<Preferences>,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        return if (workerClassName == SyncWorker::class.java.name) {
            SyncWorker(appContext, workerParameters, syncRepository, authRepository,
                nodeDao, documentDao, bookmarkDao, settingsDao, dataStore)
        } else null
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SyncWorkerTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(),
            produceFile = { temporaryFolder.newFile("sync_test.preferences_pb") }
        )
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun doWork_onSuccess_returnsSuccessAndUpdatesLastSyncHlc() = runBlocking {
        val fakeSyncRepo = FakeSyncRepository(
            pullResponse = SyncChangesResponse(nodes = emptyList(), documents = emptyList(),
                bookmarks = emptyList(), settings = null, serverHlc = "9999999999999-00001-server")
        )
        val fakeAuthRepo = FakeAuthRepository(userId = "user-uuid-123", deviceId = "device-abc")

        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(FakeSyncWorkerFactory(
                syncRepository = fakeSyncRepo,
                authRepository = fakeAuthRepo,
                nodeDao = db.nodeDao(),
                documentDao = db.documentDao(),
                bookmarkDao = db.bookmarkDao(),
                settingsDao = db.settingsDao(),
                dataStore = dataStore,
            ))
            .build()

        val result = worker.doWork()
        assertThat(result, `is`(Result.success()))
    }
}
```

### Backend Rate Limit on Auth Routes

```typescript
// Source: https://github.com/fastify/fastify-rate-limit (v10.3.0)
// Register in createAuthRoutes plugin scope, NOT in buildApp()
import rateLimit from '@fastify/rate-limit'

export function createAuthRoutes(sqlite, verifyGoogle?) {
    return async function authRoutes(fastify: FastifyInstance) {
        // Rate limit ONLY on auth routes — 10 attempts per minute per IP
        await fastify.register(rateLimit, {
            max: 10,
            timeWindow: '1 minute',
            // keyGenerator: (req) => req.ip  // default behavior
        })

        fastify.post('/auth/google', async (req, reply) => { ... })
        fastify.post('/auth/refresh', async (req, reply) => { ... })
        // GET /auth/me is exempt (requires valid JWT, not brute-forceable)
    }
}
```

### Tombstone Purge in startServer()

```typescript
// Source: ARCHITECTURE.md Section 3 - Soft deletes with 90-day tombstone retention
function purgeTombstones(sqlite: InstanceType<typeof Database>): void {
    const cutoff = Date.now() - 90 * 24 * 60 * 60 * 1000
    const tables = ['nodes', 'documents', 'bookmarks']
    for (const table of tables) {
        const result = sqlite
            .prepare(`DELETE FROM ${table} WHERE deleted_at IS NOT NULL AND deleted_at < ?`)
            .run(cutoff)
        console.log(`Purged ${result.changes} tombstones from ${table}`)
    }
}

// In startServer(), after runMigrations():
await runMigrations(sqlite)
purgeTombstones(sqlite)  // runs once per container restart
const app = buildApp(sqlite)
```

### LazyColumn Key Verification (Already Correct)

```kotlin
// NodeEditorScreen.kt lines 188-190 — ALREADY uses stable keys
// Source: https://developer.android.com/develop/ui/compose/performance/bestpractices
LazyColumn {
    items(
        items = state.flatNodes,
        key = { it.entity.id },  // stable UUID key — correct
        contentType = { "node" }  // optimization: all same type
    ) { flatNode -> ... }
}
// No change needed. contentType could be added for minor optimization.
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Context menu (ModalBottomSheet) | Swipe gestures + NodeActionToolbar | Phase 10 | Context menu entirely removed |
| `KEEP` WorkManager policy | `UPDATE` policy | Phase 07 | Prevents deprecated API warning |
| JWT as userId for settings | `getUserId()` UUID | Phase 07 | Closes GAP-A |
| Settings never pushed | Settings in SyncPushPayload | Phase 08 | Closes GAP-B |
| `onGlyphTap = { /* zoom in */ }` | Zoom-in navigation (Phase 12) | This phase | Wires core UX feature |
| Dead `CreateDocumentRequest.kt` | Deleted | This phase | Removes confusion |
| `NodeDao.getPendingChanges` no userId | userId-filtered | This phase | Closes TD-4 |

**Deprecated/outdated:**
- `ExistingPeriodicWorkPolicy.KEEP`: replaced with `UPDATE` in Phase 07
- `ModalBottomSheet` context menu: removed entirely in Phase 10
- `showContextMenu`/`dismissContextMenu` in NodeEditorViewModel/Screen: removed Phase 10

---

## Known Tech Debt (v0.4 Final Audit) — What Phase 12 Must Close

These are the four non-blocking items from the v0.4 final audit that belong in Phase 12:

| ID | Item | Action |
|----|------|--------|
| TD-1 | `SettingsSyncRecord.id = userId` (cosmetic) | Document only; no code change needed unless REST settings routes are wired |
| TD-2 | `CreateDocumentRequest.kt` — orphaned dead code | Delete the file |
| TD-3 | `DocumentDetailScreen` / `BookmarksScreen` — unreachable routes | Wire these in Phase 12 (Phase 11 implements them; Phase 12 connects navigation) |
| TD-4 | `NodeDao.getPendingChanges` lacks userId scope | Fix: add `userId` parameter |

Additionally:
- `onGlyphTap = { /* zoom in */ }` — wire zoom-in navigation (anti-pattern per CLAUDE.md: "Tap glyph = zoom in. Never collapse.")

---

## Open Questions

1. **Phase 11 completion scope**
   - What we know: Phase 11 (search-export-bookmarks) is planned but not yet researched/executed
   - What's unclear: Exactly which screens/routes Phase 11 produces. Phase 12 must connect them to navigation.
   - Recommendation: Phase 12 planning should create a stub task "Wire Phase 11 screens into AppNavHost" that is marked as conditional on Phase 11 output. Phase 12 plan must be updated after Phase 11 executes.

2. **Zoom-in back stack depth limit**
   - What we know: Compose Navigation back stack is bounded by Android memory; each zoom level is a NavBackStackEntry
   - What's unclear: Whether a deeply nested document (20+ levels) could cause stack issues
   - Recommendation: Do not add artificial depth limit. Log a note in VERIFICATION.md about the theoretical risk but do not implement a cap unless observed.

3. **SyncWorker test setup with Hilt**
   - What we know: `TestListenableWorkerBuilder` + custom `WorkerFactory` is the correct pattern
   - What's unclear: Whether the existing `hilt-android-testing` setup in the test module makes `HiltWorkerFactory` available without the full component graph
   - Recommendation: Use manual `FakeSyncWorkerFactory` (not Hilt component), keeping the test hermetic. Faster, more reliable.

4. **TD-1: SettingsSyncRecord.id = userId — leave or fix**
   - What we know: The v0.4 audit explicitly calls this "cosmetic — functionally safe"
   - What's unclear: Whether Phase 11's bookmarks screen triggers any direct REST settings calls that would expose the inconsistency
   - Recommendation: Leave as-is. Document in VERIFICATION.md. The sync path handles it correctly.

---

## Phase 12 Scope Definition (Recommended)

Based on research, Phase 12 should contain exactly these task groups:

### Group A: Tech Debt Closure (must-haves)
- A1: Delete `CreateDocumentRequest.kt` (TD-2)
- A2: Fix `NodeDao.getPendingChanges` userId filter (TD-4) + update all 3 call sites
- A3: Wire `BookmarksScreen` and `DocumentDetailScreen` navigation (TD-3) — after Phase 11 delivers them

### Group B: Feature Wiring (must-haves)
- B1: Implement zoom-in on glyph tap — `NodeEditorViewModel.zoomIn()` + navigation to nested NODE_EDITOR route

### Group C: Backend Hardening (must-haves)
- C1: Add `@fastify/rate-limit` on auth routes + Vitest test
- C2: Add `purgeTombstones()` to `startServer()` + Vitest test

### Group D: Integration Testing (must-haves)
- D1: `SyncWorkerTest` — full pull-then-push cycle using `TestListenableWorkerBuilder` + fake dependencies
- D2: Manual VERIFICATION.md checklist covering all E2E flows

### Group E: Polish (nice-to-haves, only if time permits)
- E1: Add `contentType = { "node" }` to LazyColumn items (minor recomposition optimization)
- E2: Surface sync status indicator on NodeEditorScreen topBar (already in UiState, just not displayed)

---

## Sources

### Primary (HIGH confidence)
- Android Developers — Testing Worker implementation: https://developer.android.com/develop/background-work/background-tasks/testing/persistent/worker-impl
- Android Developers — Compose Performance Best Practices: https://developer.android.com/develop/ui/compose/performance/bestpractices
- Android Developers — Compose Common Testing Patterns: https://developer.android.com/develop/ui/compose/testing/common-patterns
- Project v0.4 milestone audit: `.planning/v0.4-MILESTONE-AUDIT.md` — all tech debt items documented
- Project ARCHITECTURE.md — tombstone purge (90-day), zoom-in navigation, NodeDao patterns
- Project CLAUDE.md — "Tap glyph = zoom in. Never collapse." (hard rule)
- Codebase inspection: NodeEditorScreen.kt:272 (`onGlyphTap` stub), NodeDao.kt (no userId filter), NodeEditorViewModel.kt:780 (`restoreNode` pattern)

### Secondary (MEDIUM confidence)
- `@fastify/rate-limit` v10.3.0 compatibility with Fastify ^5: GitHub fastify/fastify-rate-limit (devDependencies shows `"fastify": "^5.0.0"`)
- WorkManager integration testing patterns: https://developer.android.com/guide/background/testing/persistent/integration-testing (fetch blocked; content inferred from worker-impl page + WebSearch)
- `TestListenableWorkerBuilder` for `CoroutineWorker`: WebSearch verified with Android official docs fetch

### Tertiary (LOW confidence)
- Rate limit version 10.3.0 current: WebSearch result claiming "current version 10.3.0" — not directly verified from npm registry. Confirm with `pnpm info @fastify/rate-limit version` before installing.

---

## Metadata

**Confidence breakdown:**
- Tech debt closure scope: HIGH — directly from v0.4-MILESTONE-AUDIT.md
- SyncWorkerTest pattern: MEDIUM — Android docs fetch confirmed TestListenableWorkerBuilder; Hilt WorkerFactory adaptation is standard but not officially documented in one place
- Backend rate limiting: MEDIUM — @fastify/rate-limit exists and is Fastify v5 compatible; exact version needs npm confirmation
- Zoom-in architecture: HIGH — ARCHITECTURE.md explicitly specifies "back stack IS the zoom history"
- LazyColumn already uses keys: HIGH — confirmed by code inspection (NodeEditorScreen.kt:190)
- Tombstone purge absence: HIGH — grep of entire backend/src/ confirmed no purge code exists

**Research date:** 2026-03-04
**Valid until:** 2026-04-04 (stable project — no fast-moving dependencies introduced)
