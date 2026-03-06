# Phase 24: Test coverage gaps - Context

**Gathered:** 2026-03-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Close the five highest-priority test gaps identified in the audit:
1. `files.test.ts` — ownership test for DELETE (user B cannot delete user A's file)
2. `NodeEditorViewModelTest` — debounce E2E: rapid typing → focus-lost → flush → sync-trigger
3. `FractionalIndexTest` — n ≥ 62 siblings and closely-bounded key pair edge cases
4. `SyncWorkerIntegrationTest` — conflict resolution with real SyncOrchestrator + real Room DB
5. `SearchRepositoryTest` — multi-word FTS AND semantics and in:note/in:title prefix `*` stripping

No new features. No production code changes. Pure test additions (plus migration of existing SyncWorker integration tests to the Phase 21 SyncOrchestrator API).

</domain>

<decisions>
## Implementation Decisions

### SC1: files.test.ts ownership test (fully locked from Phase 20 context)
- Add one test in the `DELETE /api/files/:filename` describe block
- Authenticated user B cannot delete user A's file → expects 403
- Use existing `seedUser()` and `seedFileRow()` helpers — no new infrastructure needed

### SC2: NodeEditorViewModel debounce E2E
- **Timer advancement**: use `TestCoroutineScheduler.advanceTimeBy()` — same shared scheduler pattern as Phase 11 (D30); `StandardTestDispatcher` shares one virtual clock with the orbit test scope
  - `advanceTimeBy(300)` to fire the debounce flush
  - `advanceTimeBy(30_000)` to fire the inactivity sync timer
- **Assertion**: verify `nodeDao.updateNode()` was called `exactly = 1` with the final typed content (not an earlier intermediate value)
- **Sync assertion**: also verify `syncOrchestrator.fullSync()` was called once after the inactivity timer fires — after Phase 21, sync goes through the orchestrator; mock it in the test
- **Scenario**: call `onContentChanged(nodeId, "a")`, `onContentChanged(nodeId, "ab")`, `onContentChanged(nodeId, "abc")` in rapid succession (within debounce window); then `onNodeFocusLost(nodeId)`; then advance time

### SC3: FractionalIndex edge cases
- **Location**: new `FractionalIndexTest.kt` at `src/test/.../util/FractionalIndexTest.kt` (imports from `util` package — matches Phase 23 relocation)
- **n ≥ 62 test**: call `generateNKeysBetween(null, null, 100)`; assert 100 distinct keys, all ascending, all unique
- **Closely-bounded — adjacent chars**: `generateKeyBetween("aP", "aQ")` — exercises `diff==1` branch in `midpointStr` that appends suffix + `MID_CHAR`; result must be strictly between `"aP"` and `"aQ"` lexicographically
- **Closely-bounded — deep prefix**: `generateKeyBetween("aPPPPP", "aPPPPQ")` — tests depth traversal without overflow or error; result must be strictly between the two keys
- These two "closely-bounded" tests cover the two hardest algorithmic paths in `midpointStr`

### SC4: SyncWorkerIntegrationTest — conflict resolution
- **Structure**: update all 3 existing tests to inject `SyncOrchestratorImpl` instead of wiring `SyncWorker` directly with a mocked `SyncRepository` (SyncWorker now delegates to orchestrator after Phase 21)
- **What's real**: `SyncOrchestratorImpl` (real), Room DAOs (real in-memory DB), DataStore (real temp-folder backed)
- **What's mocked**: `SyncRepository.pull()` and `SyncRepository.push()` (network layer only) — the orchestrator's upsert/merge/HLC-update path runs for real
- **New conflict test scenario — server wins (newer HLC)**:
  - Pre-populate Room with a node (contentHlc = "HLC-LOCAL-OLD")
  - Mock `pull()` to return the same nodeId with newer contentHlc = "HLC-SERVER-NEW" and different content
  - Run `orchestrator.fullSync()`
  - Assert Room row now has server's content (LWW correctly overwrote local)
- Existing 3 tests (upsert, HLC write to DataStore, retry on pull failure) are retained — just migrated to the SyncOrchestrator API

### SC5: SearchRepositoryTest — multi-word + prefix stripping
- **Multi-word AND semantics**:
  - Test: `searchNodes("buy groceries", "user1")`
  - Assert the SQL MATCH parameter contains both words (FTS4 AND semantics — both must appear)
  - Document with comment: "FTS4 default is AND — both terms must appear in matched nodes"
- **Prefix `*` stripping in post-filter** (combined behavior):
  - SQL MATCH gets `"grocer*"` (the `*` stays in for FTS prefix matching)
  - In-memory post-filter (for `in:note` / `in:title`) strips `"*"` before `contains()` check
  - Test: `searchNodes("grocer in:note", "user1")` with a node where `note = "groceries list"`
  - Mock DAO to return that node; assert it passes the post-filter (i.e., `"grocer"` is found in note after `*` stripping)
  - A second assertion: a node where note does NOT contain `"grocer"` is filtered out even though it might have been in the FTS result set

### Claude's Discretion
- Exact `advanceTimeBy()` values if debounce or inactivity timer durations differ from 300ms/30_000ms
- Whether to use `coVerify` capture slot or `argumentCaptor` for the `updateNode` content assertion
- DataStore setup in the updated SyncWorkerIntegrationTest (reuse existing `TemporaryFolder` pattern)
- Whether the "retry on pull failure" test needs adjustment after Phase 21 (orchestrator may wrap failure differently)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SyncWorkerIntegrationTest.setUp()` — existing `TemporaryFolder`, `AppDatabase.buildInMemory()`, `WorkManagerTestInitHelper` — all reusable; only the worker-factory and mock setup changes
- `SearchRepositoryTest.makeNode()` helper — existing factory for `NodeEntity`; reuse for new tests
- `NodeEditorViewModelTest.createViewModel()` — existing factory; new debounce test reuses same setup + adds `SyncOrchestrator` mock
- `files.test.ts`: `seedUser()` and `seedFileRow()` helpers already present in test file

### Established Patterns
- Shared `TestCoroutineScheduler` pattern (D30 from Phase 11): `val scheduler = TestCoroutineScheduler(); val testDispatcher = StandardTestDispatcher(scheduler)` — both viewModelScope and orbit TestScope share one virtual clock
- SQL slot capture: `val querySlot = slot<SupportSQLiteQuery>()` + `coEvery { nodeDao.searchFts(capture(querySlot)) }` — existing pattern in SearchRepositoryTest
- `coVerify(exactly = N) { ... }` — used throughout NodeEditorViewModelTest for DAO call count assertions

### Integration Points
- After Phase 21: `NodeEditorViewModel` injects `SyncOrchestrator` — new debounce E2E test must add `syncOrchestrator: SyncOrchestrator` mock to `createViewModel()` call
- After Phase 21: `SyncWorkerIntegrationTest` must inject `SyncOrchestratorImpl` (not mock) — `SyncOrchestratorImpl` needs `SyncRepository` (mocked for network) + real Room DAOs + DataStore
- After Phase 23: `FractionalIndexTest` imports from `com.gmaingret.outlinergod.util.FractionalIndex` (not `prototype`)

</code_context>

<specifics>
## Specific Ideas

- Debounce E2E test outline:
  ```kotlin
  // Rapid typing (all within debounce window)
  viewModel.onContentChanged("n1", "a")
  viewModel.onContentChanged("n1", "ab")
  viewModel.onContentChanged("n1", "abc")
  // Focus-lost flushes immediately
  viewModel.onNodeFocusLost("n1")
  testScheduler.advanceTimeBy(300) // debounce fires
  // Verify exactly one write with final content
  coVerify(exactly = 1) { nodeDao.updateNode(match { it.content == "abc" }) }
  // Advance inactivity timer → sync trigger
  testScheduler.advanceTimeBy(30_000)
  coVerify(exactly = 1) { syncOrchestrator.fullSync() }
  ```
- `FractionalIndexTest` n=100: `val keys = FractionalIndex.generateNKeysBetween(null, null, 100); assertEquals(100, keys.toSet().size); keys.zipWithNext { a, b -> assertTrue(a < b) }`
- FTS4 AND behavior comment: "FTS4 treats space-separated terms as AND by default — no explicit AND operator needed in MATCH query"

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 24-test-coverage-gaps*
*Context gathered: 2026-03-06*
