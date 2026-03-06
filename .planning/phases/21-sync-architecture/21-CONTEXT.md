# Phase 21: Sync architecture - Context

**Gathered:** 2026-03-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Extract the pull-then-push sync cycle into a single `SyncOrchestrator` class so that `NodeEditorViewModel.triggerSync()`, `DocumentListViewModel.triggerSync()`, and `SyncWorker.doWork()` all delegate to it. No new sync behaviour — pure consolidation of existing copy-pasted logic.

</domain>

<decisions>
## Implementation Decisions

### Orchestrator shape
- **New standalone class** — `SyncOrchestrator` is a new interface + `SyncOrchestratorImpl` in the `sync/` package (alongside `SyncWorker`, `SyncConstants`, `SyncMappers`)
- `SyncRepository` stays thin — only `pull()` and `push()`; it does not get a `fullSync()` method
- `SyncOrchestrator` is a new Hilt binding in `RepositoryModule` (or a new `SyncModule`)
- The `sync/` package becomes the single place to edit for any sync logic change

### Caller interface
- `suspend fun fullSync(): Result<Unit>` — simple, idiomatic with the codebase's `runCatching` pattern
- Orchestrator is **fully self-contained**: it reads DataStore for `lastSyncHlc`, runs pull/upsert/push/conflict resolution, and **writes the new `serverHlc` back to DataStore**
- Callers just do: `val result = orchestrator.fullSync()` and map to `SyncStatus` themselves
- ViewModels still own `SyncStatus` state updates (Syncing → Idle/Error) based on the Result

### Token refresh ownership
- **Orchestrator handles refresh**: `fullSync()` calls `authRepository.refreshToken()` as its first step
- If `refreshToken()` fails → `fullSync()` returns `Result.failure()` immediately (no pull or push attempted)
- This matches `SyncWorker`'s current behaviour (returns `Result.failure()` on auth failure)
- Callers no longer call `refreshToken()` themselves — it's absorbed into `fullSync()`

### What callers become after refactor
- `DocumentListViewModel.triggerSync()`: `reduce { Syncing }` → `orchestrator.fullSync()` → `reduce { Idle/Error }`
- `NodeEditorViewModel.triggerSync()`: same 3-line pattern
- `SyncWorker.doWork()`: remove manual refresh + entire pull/push block → delegate to `orchestrator.fullSync()`; map `Result.failure()` → `Result.failure()` (WorkManager), `Result.success()` → `Result.success()`

### Claude's Discretion
- Exact Hilt module placement for `SyncOrchestrator` binding (new module vs. extension of RepositoryModule)
- Whether `SyncOrchestrator` interface lives in `sync/` or `repository/` (impl always in `sync/`)
- Whether `SyncWorker.doWork()` needs a retry on orchestrator failure or delegates retry to WorkManager (keep current `Result.retry()` behaviour)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SyncMappers.kt` — all `toNodeEntity()`, `toDocumentEntity()`, `toBookmarkSyncRecord()` etc. extension functions; orchestrator uses these directly
- `SyncConstants.lastSyncHlcKey(userId)` — DataStore key for last HLC; orchestrator reads/writes this
- `SyncRepository` (pull/push) — orchestrator wraps these two calls
- `SyncStatus` enum at `ui/common/SyncStatus.kt` — callers still use this; orchestrator doesn't touch it

### Established Patterns
- `runCatching {}` used in `SyncRepositoryImpl` for network calls — orchestrator should use the same pattern
- `authRepository.getDeviceId().first()` / `getUserId().filterNotNull().first()` — standard pattern for auth values in coroutines
- `dataStore.edit { prefs -> prefs[key] = value }` — pattern for persisting HLC

### Integration Points
- **3 callers to gut**: `NodeEditorViewModel.triggerSync()` (line 883), `DocumentListViewModel.triggerSync()` (line 67), `SyncWorker.doWork()` (line 39)
- **New Hilt injection**: All 3 callers need `private val syncOrchestrator: SyncOrchestrator` added to their constructors (replaces the direct DAO calls for sync)
- **DAOs stay in ViewModels**: NodeEditorViewModel and DocumentListViewModel still inject DAOs for their own local mutations (creating nodes, renaming documents, etc.) — only the sync block is delegated
- **SyncWorker** still injects `SyncRepository` via the orchestrator; the orchestrator absorbs all the DAO injection that SyncWorker currently has for sync purposes

</code_context>

<specifics>
## Specific Ideas

No specific references — the existing three `triggerSync()` blocks are the source of truth for what `SyncOrchestrator.fullSync()` must encapsulate.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 21-sync-architecture*
*Context gathered: 2026-03-06*
