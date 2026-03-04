---
phase: 12
status: passed
date: 2026-03-04
score: 14/14
---

# Phase 12: integration-e2e — Verification

## Score: 14/14 must-haves verified

---

## Verified

### Plan 12-01: TD-2 — NodeDao.getPendingChanges userId parameter

- `NodeDao.getPendingChanges(userId: String, sinceHlc: String, deviceId: String)` signature confirmed at line 47 of NodeDao.kt — matches the DocumentDao and BookmarkDao pattern exactly
- `SyncWorker.kt` line 71: `nodeDao.getPendingChanges(userId, lastSyncHlc, deviceId)` — userId passed as first arg
- `NodeEditorViewModel.kt` line 804: `nodeDao.getPendingChanges(userId, lastSyncHlc, deviceId)` — userId passed as first arg
- `DocumentListViewModel.kt` line 103: `nodeDao.getPendingChanges(userId, lastSyncHlc, deviceId)` — userId passed as first arg
- `CreateDocumentRequest.kt` does NOT exist (Glob search returned no matches) — dead code deleted

### Plan 12-02: Zoom-in navigation

- `AppRoutes.kt`: `NODE_EDITOR = "node_editor/{documentId}?rootNodeId={rootNodeId}"` and `fun nodeEditor(documentId: String, rootNodeId: String? = null)` helper — rootNodeId is an optional query param
- `AppNavHost.kt` lines 61–75: `navArgument("rootNodeId")` registered with `defaultValue = ""`, rootNodeId extracted and passed to `NodeEditorScreen`, `onZoomIn = { nodeId -> navController.navigate(AppRoutes.nodeEditor(documentId, nodeId)) }` — each tap creates a new back-stack entry via `navigate()`
- `NodeEditorScreen.kt` line 94: `onZoomIn: (String) -> Unit = {}` parameter accepted
- `NodeEditorScreen.kt` line 282: `onGlyphTap = { onZoomIn(flatNode.entity.id) }` — glyph tap fires onZoomIn
- `NodeEditorScreen.kt` lines 283–285: `onToggleCollapse = { viewModel.toggleCollapsed(flatNode.entity.id) }` — directional arrow triggers collapse, not zoom
- `NodeEditorScreen.kt` lines 360–393: nodes with children show directional arrow IconButton with `onToggleCollapse`; leaf nodes show filled-dot Box with `onGlyphTap` — the two interactions are on distinct glyph types as specified
- System Back uses `navController.navigateUp()` wired to `onNavigateUp` — returns to previous zoom level

### Plan 12-03: Backend hardening

- `backend/src/routes/auth.ts` lines 6 and 62–65: `import rateLimit from '@fastify/rate-limit'` and `await fastify.register(rateLimit, { max: 10, timeWindow: '1 minute' })` registered inside the `authRoutes` plugin only — scoped to this plugin, not global
- No other route file imports or registers rate-limit (confirmed by grep across `backend/src/routes/*.ts`)
- `backend/src/tombstone.ts`: `purgeTombstones(sqlite)` function exists, deletes records where `deleted_at IS NOT NULL AND deleted_at < cutoff` from nodes, documents, and bookmarks tables — 90-day cutoff computed as `Date.now() - 90 * 24 * 60 * 60 * 1000`
- `backend/src/index.ts` line 117: `purgeTombstones(sqlite)` called in `startServer()` after `runMigrations()` completes and before `buildApp()` — runs on every container startup

### Plan 12-04: SyncWorker integration test + LazyColumn contentType

- `android/app/src/test/java/com/gmaingret/outlinergod/sync/SyncWorkerIntegrationTest.kt` exists (213 lines, 3 test methods)
- Test uses `AppDatabase.buildInMemory(context)` — real Room database, not mocked DAOs
- Test `pullThenPush_upsertsNodeIntoRealDb`: calls `nodeDao.getNodesByDocumentSync("doc-integration-1")` after worker runs and asserts 1 node persisted with correct id/content/HLC — verifies DB persistence
- Test `pullThenPush_updatesLastSyncHlcInDataStore`: reads DataStore after worker runs and asserts `LAST_SYNC_HLC_KEY` equals the server-returned HLC — verifies DataStore write
- Test `doWork_returnsRetryOnPullFailure`: asserts `Result.retry()` on pull failure and confirms DataStore HLC is still null — verifies error path
- `NodeEditorScreen.kt` line 195: `contentType = { "node" }` passed to `items()` in the LazyColumn — recomposition hint present

---

## Human Verification Required

### 1. Zoom-in visual behavior on device

**Test:** Open a document, tap the filled dot glyph on a leaf node.
**Expected:** Screen reloads showing only the subtree rooted at that node. The TopAppBar Back button returns to the full document view.
**Why human:** Back-stack push is structurally wired but visual rendering of the zoomed subtree (correct root filtering, no phantom nodes) requires device observation.

### 2. Rate-limit enforcement on auth routes

**Test:** Send 11 rapid POST requests to `/api/auth/google` from the same IP.
**Expected:** First 10 return 401 (invalid token), 11th returns 429 Too Many Requests.
**Why human:** The rate-limit plugin is registered against the test-injection infrastructure in `auth.test.ts`, but production enforcement needs a live Docker instance to confirm the plugin loads correctly with the production Fastify setup.

### 3. Tombstone purge on container startup

**Test:** Insert a node with `deleted_at` = 91 days ago directly into the SQLite database, then restart the Docker container.
**Expected:** The node is no longer present in the database after startup.
**Why human:** `purgeTombstones()` is called in `startServer()` which only runs in the container entry point, not in unit tests.

---

## Notes

- The `filterSubtree()` companion object function in `NodeEditorViewModel` correctly implements BFS from the zoom root and filters the node list before passing to `mapToFlatList()`. This ensures no orphan-handling noise when zooming.
- Rate limiting is plugin-scoped inside `createAuthRoutes` closure, not registered globally on the Fastify instance. This correctly limits only `POST /auth/google` and `POST /auth/refresh` (and the other auth sub-routes) while leaving sync, documents, and node routes unaffected.
- `SyncWorkerIntegrationTest` uses `mockk(relaxed = true)` for `SyncRepository` and `AuthRepository` but a real `AppDatabase` — this is the correct hybrid for testing DB wiring without real network.
- The pre-existing flaky test (`BookmarkDaoTest.observeAllActive_excludesOtherUsers`) is unrelated to phase 12 changes and does not affect this verification.

---

_Verified: 2026-03-04_
_Verifier: Claude (gsd-verifier)_
