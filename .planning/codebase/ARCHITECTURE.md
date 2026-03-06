# Architecture

**Analysis Date:** 2026-03-06

## Pattern Overview

**Overall:** Client-server with offline-first Android client and a stateless REST backend.

**Key Characteristics:**
- Android app uses Orbit MVI (Model-View-Intent) with one `UiState` data class per screen
- Backend is a thin Fastify service with no business logic layer — routes call SQLite directly
- Sync is pull-based HLC (Hybrid Logical Clock) + LWW (Last-Write-Wins) per field; no real-time push
- Every mutable field in the database has a companion `*_hlc` column; this doubles column count per table
- Tree structure is reconstructed from `parent_id` + `sort_order` (TEXT fractional index) client-side only

## Android Layers

**UI Layer:**
- Purpose: Compose screens + Orbit MVI ViewModels
- Location: `android/app/src/main/java/com/gmaingret/outlinergod/ui/`
- Contains: `screen/` (paired Screen+ViewModel per route), `navigation/` (AppNavHost, AppRoutes), `mapper/` (FlatNodeMapper), `common/` (SyncStatus enum, MarkdownVisualTransformation), `theme/`
- Depends on: Repository layer, DAOs (directly — see concerns), HlcClock, DataStore
- Used by: Nothing (leaf layer)

**Repository Layer:**
- Purpose: Abstracts network and auth behind interfaces
- Location: `android/app/src/main/java/com/gmaingret/outlinergod/repository/`
- Contains: `AuthRepository`, `SyncRepository`, `SearchRepository`, `ExportRepository`, `FileRepository` interfaces + `impl/` implementations
- Depends on: Ktor HTTP client, Room DAOs (in SearchRepositoryImpl), DataStore
- Used by: ViewModels, SyncWorker

**Sync Layer:**
- Purpose: HLC clock, per-entity LWW merge logic, entity↔SyncRecord mappers, background sync
- Location: `android/app/src/main/java/com/gmaingret/outlinergod/sync/`
- Contains: `HlcClock.kt`, `NodeMerge.kt`, `DocumentMerge.kt`, `BookmarkMerge.kt`, `SyncMappers.kt`, `SyncWorker.kt`, `SyncScheduler.kt`, `SyncConstants.kt`
- Depends on: DAOs, AuthRepository, SyncRepository, DataStore
- Used by: ViewModels (for mappers/clock), WorkManager

**Database Layer:**
- Purpose: Room persistence — entities and DAOs
- Location: `android/app/src/main/java/com/gmaingret/outlinergod/db/`
- Contains: `AppDatabase.kt`, `dao/` (NodeDao, DocumentDao, BookmarkDao, SettingsDao), `entity/` (NodeEntity, DocumentEntity, BookmarkEntity, SettingsEntity)
- Depends on: Nothing above it
- Used by: ViewModels, SyncWorker, repositories

**DI Layer:**
- Purpose: Hilt module wiring
- Location: `android/app/src/main/java/com/gmaingret/outlinergod/di/`
- Contains: `DatabaseModule.kt`, `NetworkModule.kt`, `RepositoryModule.kt`, `ClockModule.kt`, `AuthModule.kt`, `WorkManagerModule.kt`
- Used by: Hilt component graph

**Prototype Layer (should not exist in production code):**
- Purpose: Phase 0 proof-of-concept code that was never removed
- Location: `android/app/src/main/java/com/gmaingret/outlinergod/prototype/`
- Contains: `HlcClock.kt` (old hex format — superseded by `sync/HlcClock.kt`), `FractionalIndex.kt` (still imported in production!), `MarkdownVisualTransformation.kt` (still imported in production!), `DndPrototypeActivity.kt`, `WysiwygPrototypeActivity.kt`, `SyncRepository.kt` (prototype — different from production interface)
- **Critical:** `FractionalIndex` from `prototype/` is imported in `NodeEditorViewModel.kt`. `MarkdownVisualTransformation` from `prototype/` is imported in `NodeEditorScreen.kt`. These are production dependencies on prototype code.

## Backend Layers

**Entry Points:**
- `backend/src/server.ts` — reads env vars (PORT, DATABASE_PATH), calls `startServer()`
- `backend/src/index.ts` — `buildApp()` factory (used by tests), `startServer()` (used by production)

**Route Layer:**
- Purpose: HTTP request handling — validation, auth enforcement, SQLite queries, response shaping
- Location: `backend/src/routes/`
- Contains: `auth.ts`, `documents.ts`, `nodes.ts`, `sync.ts`, `files.ts`, `settings.ts`, `bookmarks.ts`, `export.ts`, `health.ts`
- **No service layer exists** — routes contain all business logic and raw SQL directly

**Middleware:**
- `backend/src/middleware/auth.ts` — global JWT verification plugin + `requireAuth` preHandler
- Pattern: global plugin sets `req.user = AuthUser | null`; individual routes call `requireAuth` as preHandler

**Database Layer:**
- `backend/src/db/connection.ts` — creates better-sqlite3 instance with WAL mode and FK enforcement
- `backend/src/db/migrate.ts` — runs Drizzle migrations automatically on startup
- `backend/src/db/schema/` — Drizzle schema definitions per entity (nodes.ts, documents.ts, bookmarks.ts, users.ts, settings.ts, refresh_tokens.ts, files.ts)
- `backend/src/db/schema.ts` — orphaned legacy file with partial schema (not the canonical schema — see concerns)

**Core Logic (top-level files):**
- `backend/src/merge.ts` — per-field LWW merge for NodeSyncRecord, DocumentSyncRecord, BookmarkSyncRecord
- `backend/src/hlc/hlc.ts` — server-side HLC generate/receive
- `backend/src/tombstone.ts` — purges soft-deleted rows older than 90 days on startup

## Data Flow

**Android Edit → Persist → Sync:**
1. User types in `NodeEditorScreen` → `NodeEditorViewModel.onContentChanged()`
2. ViewModel starts 500ms debounce timer; on expiry writes `NodeEntity` to `NodeDao` with a freshly generated HLC via `HlcClock.generate(deviceId)`
3. On screen pause or 30-second inactivity, `NodeEditorViewModel.triggerSync()` fires
4. `triggerSync()` calls `SyncRepository.pull()` then `SyncRepository.push()` directly (sync logic is inline in the ViewModel — see concerns)
5. Pull response is upserted into Room via DAOs; push payload is assembled by querying pending changes from DAOs
6. Server receives `POST /api/sync/changes`, applies per-field LWW merge (`merge.ts`), returns conflicts
7. Conflicts are upserted back into Room; `LAST_SYNC_HLC_KEY` in DataStore is updated to `server_hlc`

**Tree Rendering:**
1. `NodeDao.getNodesByDocument(documentId)` returns all nodes as a flat Room Flow
2. `mapToFlatList(nodes, documentId)` in `FlatNodeMapper.kt` performs DFS from `parentId == documentId`, building `List<FlatNode>` with depth integers
3. `NodeEditorScreen` renders via `LazyColumn` + `ReorderableItem` from `sh.calvin.reorderable`
4. Drag produces `onReorder(from, to)` → `NodeEditorViewModel.reorderNodes()` → updates `sort_order` and `parent_id` via `FractionalIndex.generateKeyBetween()`

**Background Sync:**
1. `SyncScheduler.schedule()` enqueues `SyncWorker` as a periodic WorkManager task (15-min interval, `NetworkType.CONNECTED`)
2. `SyncWorker.doWork()` performs the same pull→push→conflict sequence as `triggerSync()` in ViewModels — this logic is duplicated in three places (SyncWorker, DocumentListViewModel, NodeEditorViewModel)

**Authentication:**
1. User taps "Sign in with Google" on `LoginScreen`
2. Android Credential Manager API returns Google ID token
3. `AuthRepository.googleSignIn(idToken)` POSTs to `POST /api/auth/google`
4. Backend verifies with `google-auth-library`, creates/updates user, issues JWT + refresh token
5. JWT (1 hour) and refresh token stored in DataStore via `AuthRepository`
6. Ktor client in `KtorClientFactory` intercepts 401 responses and calls `refreshToken()` automatically

## Key Abstractions

**HlcClock:**
- Purpose: Monotonic timestamp for per-field LWW sync, format `<13-digit-ms>-<5-digit-counter>-<deviceId>`
- Production: `android/app/src/main/java/com/gmaingret/outlinergod/sync/HlcClock.kt`
- Prototype (old hex format, still in codebase): `android/app/src/main/java/com/gmaingret/outlinergod/prototype/HlcClock.kt`
- Backend mirror: `backend/src/hlc/hlc.ts`
- Pattern: Singleton injected via `ClockModule`; `@Synchronized` for thread safety

**FractionalIndex:**
- Purpose: Generates TEXT sort_order keys between two existing keys (Rocicorp-compatible algorithm)
- Location: `android/app/src/main/java/com/gmaingret/outlinergod/prototype/FractionalIndex.kt` (still in prototype package)
- Pattern: Object with `generateKeyBetween(before, after)` and `generateNKeysBetween()`; produces keys like `aV`, `aP`

**FlatNodeMapper:**
- Purpose: Converts flat `List<NodeEntity>` to depth-annotated `List<FlatNode>` for tree rendering
- Location: `android/app/src/main/java/com/gmaingret/outlinergod/ui/mapper/FlatNodeMapper.kt`
- Pattern: Pure function, DFS from document root; handles orphan nodes by appending at depth 0

**SyncMappers:**
- Purpose: Bidirectional conversion between Room entities and network sync records
- Location: `android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncMappers.kt`
- Pattern: Extension functions on both types (`NodeEntity.toNodeSyncRecord()`, `NodeSyncRecord.toNodeEntity()`)

**Orbit MVI ViewModels:**
- Purpose: Screen state management; each screen has `UiState` sealed class and `SideEffect` sealed class co-located in the ViewModel file
- Pattern: `ContainerHost<UiState, SideEffect>` with `intent {}` coroutine blocks, `reduce {}` for state, `postSideEffect()` for one-shot events
- Examples: `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListViewModel.kt`, `nodeeditor/NodeEditorViewModel.kt`

## Entry Points

**Android:**
- `android/app/src/main/java/com/gmaingret/outlinergod/MainActivity.kt` — `@AndroidEntryPoint`, hosts `AppNavHost`
- `android/app/src/main/java/com/gmaingret/outlinergod/OutlinerGodApp.kt` — `@HiltAndroidApp`, WorkManager configuration provider
- Navigation start destination: `AppRoutes.LOGIN`

**Backend:**
- `backend/src/server.ts` — production entry point (called by `node dist/server.js`)
- `backend/src/index.ts` — `buildApp()` (testable factory, no side effects)

## Error Handling

**Strategy:** Kotlin `Result<T>` wrapping for repository calls; Orbit MVI `postSideEffect(ShowError(...))` for UI errors; Fastify returns structured `{ error: string }` JSON.

**Patterns:**
- Android: `runCatching {}` in repository implementations; `try/catch` in ViewModel intents, showing error via side effect
- Backend: Fastify's default error handler for unhandled exceptions; explicit `reply.status(4xx).send({ error })` for business errors
- Network: `KtorClientFactory` intercepts 401 and auto-refreshes; `WorkManager` retries on sync failure with exponential backoff

## Cross-Cutting Concerns

**Logging:** `SyncLogger.kt` (`android/app/src/main/java/com/gmaingret/outlinergod/util/SyncLogger.kt`) wraps Android Log calls with structured sync context. Fastify uses built-in Pino logger (`logger: true`).

**Validation:** Backend routes perform inline validation (`if (!body?.title)`) without a schema validation framework. TypeBox provider is registered but route schemas are not consistently applied.

**Authentication:** `requireAuth` preHandler on every protected backend route; Ktor auto-refresh on 401 client-side; `getUserId()` (not `getAccessToken()`) used for DAO queries.

---

*Architecture analysis: 2026-03-06*
