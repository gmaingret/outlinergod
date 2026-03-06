# Codebase Structure

**Analysis Date:** 2026-03-06

## Directory Layout

```
OutlinerGod/
├── android/                    # Android app (Kotlin + Jetpack Compose)
│   └── app/
│       └── src/
│           ├── main/
│           │   ├── java/com/gmaingret/outlinergod/
│           │   │   ├── MainActivity.kt          # Single-activity entry point
│           │   │   ├── OutlinerGodApp.kt         # @HiltAndroidApp + WorkManager config
│           │   │   ├── db/                       # Room database
│           │   │   │   ├── AppDatabase.kt
│           │   │   │   ├── dao/                  # NodeDao, DocumentDao, BookmarkDao, SettingsDao
│           │   │   │   └── entity/               # NodeEntity, DocumentEntity, BookmarkEntity, SettingsEntity
│           │   │   ├── di/                       # Hilt modules
│           │   │   │   ├── DatabaseModule.kt
│           │   │   │   ├── NetworkModule.kt
│           │   │   │   ├── RepositoryModule.kt
│           │   │   │   ├── ClockModule.kt
│           │   │   │   ├── AuthModule.kt
│           │   │   │   └── WorkManagerModule.kt
│           │   │   ├── network/
│           │   │   │   ├── KtorClientFactory.kt
│           │   │   │   └── model/               # Auth.kt (request/response DTOs), Sync.kt
│           │   │   ├── prototype/               # Phase 0 code — NOT fully removed, see note
│           │   │   │   ├── FractionalIndex.kt   # STILL USED in production (NodeEditorViewModel)
│           │   │   │   ├── MarkdownVisualTransformation.kt  # STILL USED in NodeEditorScreen
│           │   │   │   ├── HlcClock.kt          # Old hex format — superseded, not used
│           │   │   │   ├── DndPrototypeActivity.kt
│           │   │   │   └── WysiwygPrototypeActivity.kt
│           │   │   ├── repository/
│           │   │   │   ├── AuthRepository.kt    # Interface
│           │   │   │   ├── SyncRepository.kt    # Interface
│           │   │   │   ├── SearchRepository.kt  # Interface
│           │   │   │   ├── ExportRepository.kt  # Interface
│           │   │   │   ├── FileRepository.kt    # Interface
│           │   │   │   └── impl/                # AuthRepositoryImpl, SyncRepositoryImpl, etc.
│           │   │   ├── search/
│           │   │   │   └── SearchQueryParser.kt # Parses is:X, color:X, in:X operators
│           │   │   ├── sync/
│           │   │   │   ├── HlcClock.kt          # PRODUCTION HLC (decimal format)
│           │   │   │   ├── NodeMerge.kt         # LWW merge for NodeEntity
│           │   │   │   ├── DocumentMerge.kt     # LWW merge for DocumentEntity
│           │   │   │   ├── BookmarkMerge.kt     # LWW merge for BookmarkEntity
│           │   │   │   ├── SyncMappers.kt       # Entity ↔ SyncRecord extension fns
│           │   │   │   ├── SyncWorker.kt        # WorkManager HiltWorker
│           │   │   │   ├── SyncScheduler.kt     # Enqueues periodic work
│           │   │   │   └── SyncConstants.kt     # LAST_SYNC_HLC_KEY DataStore key
│           │   │   ├── ui/
│           │   │   │   ├── common/
│           │   │   │   │   ├── SyncStatus.kt    # Shared enum: Idle/Syncing/Error
│           │   │   │   │   └── MarkdownVisualTransformation.kt  # Duplicate — also in prototype/
│           │   │   │   ├── mapper/
│           │   │   │   │   ├── FlatNode.kt      # Data class with entity + depth + hasChildren
│           │   │   │   │   └── FlatNodeMapper.kt  # mapToFlatList() pure function
│           │   │   │   ├── navigation/
│           │   │   │   │   ├── AppRoutes.kt     # Route constants + nodeEditor() builder
│           │   │   │   │   └── AppNavHost.kt    # NavHost composable
│           │   │   │   ├── screen/
│           │   │   │   │   ├── DocumentDetailScreen.kt   # Stub / unused
│           │   │   │   │   ├── bookmarks/               # BookmarksScreen + BookmarkListViewModel
│           │   │   │   │   ├── documentlist/            # DocumentListScreen + DocumentListViewModel
│           │   │   │   │   ├── login/                   # LoginScreen + LoginViewModel
│           │   │   │   │   ├── nodeeditor/              # NodeEditorScreen + NodeEditorViewModel
│           │   │   │   │   ├── search/                  # SearchScreen + SearchViewModel
│           │   │   │   │   └── settings/                # SettingsScreen + SettingsViewModel
│           │   │   │   └── theme/
│           │   │   │       └── OutlinerGodTheme.kt
│           │   │   └── util/
│           │   │       └── SyncLogger.kt
│           │   ├── AndroidManifest.xml
│           │   └── res/
│           └── test/                              # JVM unit tests (mirror structure of main/)
│               └── java/com/gmaingret/outlinergod/
├── backend/
│   └── src/
│       ├── index.ts             # buildApp() factory + startServer()
│       ├── server.ts            # Production entry point — reads env, calls startServer()
│       ├── merge.ts             # LWW merge for Node/Document/Bookmark sync records
│       ├── tombstone.ts         # purgeTombstones() — cleanup on startup
│       ├── hlc.ts               # LEGACY top-level file (partially implemented prototype)
│       ├── hlc/
│       │   └── hlc.ts           # PRODUCTION HLC (canonical)
│       ├── middleware/
│       │   └── auth.ts          # authPlugin (global) + requireAuth (per-route)
│       ├── routes/
│       │   ├── auth.ts          # POST /api/auth/google, /api/auth/refresh, /api/auth/me, DELETE /api/auth/logout
│       │   ├── documents.ts     # CRUD /api/documents
│       │   ├── nodes.ts         # CRUD /api/nodes
│       │   ├── sync.ts          # GET+POST /api/sync/changes
│       │   ├── files.ts         # POST/GET /api/files
│       │   ├── settings.ts      # GET+PATCH /api/settings
│       │   ├── bookmarks.ts     # CRUD /api/bookmarks
│       │   ├── export.ts        # GET /api/export
│       │   └── health.ts        # GET /health
│       ├── db/
│       │   ├── connection.ts    # createConnection() — WAL mode, FK enforcement
│       │   ├── migrate.ts       # runMigrations() using drizzle-kit
│       │   ├── index.ts         # Re-exports
│       │   ├── schema.ts        # LEGACY — partial schema, not the canonical source
│       │   └── schema/
│       │       ├── index.ts     # Re-exports all schema modules
│       │       ├── nodes.ts     # Drizzle nodes table definition
│       │       ├── documents.ts
│       │       ├── bookmarks.ts
│       │       ├── users.ts
│       │       ├── settings.ts
│       │       ├── refresh_tokens.ts
│       │       └── files.ts
│       └── test-helpers/
│           └── createTestDb.ts  # Builds in-memory SQLite with migrations for tests
├── drizzle/                     # Migration SQL files (generated by drizzle-kit)
│   └── meta/                    # Migration metadata
├── web/                         # React SPA (served as static files)
├── data/                        # Docker volume mount (SQLite db, uploads)
├── docker-compose.yml
├── PLAN.md, PLAN_PHASE*.md      # Phase planning documents (one per phase)
├── PRD.md, ARCHITECTURE.md, API.md, CLAUDE.md
└── .planning/                   # GSD planning artifacts
```

## Directory Purposes

**`android/app/src/main/java/.../db/`:**
- Purpose: Room persistence — all database entities and DAOs
- Contains: One entity per domain object, one DAO per entity, AppDatabase wiring
- Key files: `AppDatabase.kt` (Room DB, FTS4 migration), `dao/NodeDao.kt` (most complex — has `@RawQuery` for FTS search and `getPendingChanges`)

**`android/app/src/main/java/.../sync/`:**
- Purpose: All sync-related logic — clock, merge, mappers, background worker
- Contains: The canonical `HlcClock`, three merge objects, entity↔record mappers, WorkManager worker
- Key files: `SyncWorker.kt` (full pull→push cycle), `SyncMappers.kt` (all conversion extension functions)

**`android/app/src/main/java/.../prototype/`:**
- Purpose: Originally Phase 0 proof-of-concept code
- **CAUTION:** `FractionalIndex.kt` and `MarkdownVisualTransformation.kt` are imported by production ViewModels and Screens. Do NOT delete this package without moving these files to a proper package first.

**`android/app/src/main/java/.../repository/`:**
- Purpose: Network and auth abstraction behind interfaces
- Contains: Interface definitions at top level; `impl/` holds concrete implementations
- Key files: `AuthRepositoryImpl.kt` (DataStore, Mutex-guarded refresh, device ID), `SyncRepositoryImpl.kt` (Ktor GET/POST wrappers)

**`android/app/src/main/java/.../ui/screen/`:**
- Purpose: One subdirectory per screen, each containing exactly `*Screen.kt` + `*ViewModel.kt`
- `UiState` and `SideEffect` sealed classes are co-located in the ViewModel file (not separate files)

**`backend/src/routes/`:**
- Purpose: All HTTP endpoints — no service layer exists; routes contain SQL directly
- Each file exports one `createXRoutes(sqlite)` factory function
- Tests are co-located: `auth.test.ts` next to `auth.ts`

**`backend/src/db/schema/`:**
- Purpose: Canonical Drizzle schema — one file per entity
- **CAUTION:** `backend/src/db/schema.ts` (top-level, singular) is a legacy file with an incomplete schema. Do NOT import from it. Use `backend/src/db/schema/index.ts`.

## Key File Locations

**Entry Points:**
- `android/app/src/main/java/com/gmaingret/outlinergod/MainActivity.kt` — Android entry
- `android/app/src/main/java/com/gmaingret/outlinergod/OutlinerGodApp.kt` — Application class
- `backend/src/server.ts` — Backend production entry
- `backend/src/index.ts` — Backend `buildApp()` factory (used by tests)

**Configuration:**
- `android/app/build.gradle.kts` — Android dependencies, BuildConfig values (BASE_URL)
- `android/local.properties` — `BASE_URL`, `GOOGLE_CLIENT_ID` (not committed)
- `backend/drizzle.config.ts` — Drizzle migration configuration
- `docker-compose.yml` — Docker service definition

**Core Logic:**
- `android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncWorker.kt` — Full sync cycle
- `android/app/src/main/java/com/gmaingret/outlinergod/ui/mapper/FlatNodeMapper.kt` — Tree → flat list
- `android/app/src/main/java/com/gmaingret/outlinergod/prototype/FractionalIndex.kt` — Sort order generation
- `backend/src/merge.ts` — Per-field LWW merge
- `backend/src/routes/sync.ts` — Server-side sync push/pull

**Testing:**
- `android/app/src/test/` — JVM unit tests (mirrors `main/` structure)
- `backend/src/routes/*.test.ts` — Co-located route tests
- `backend/src/test-helpers/createTestDb.ts` — Shared in-memory DB factory for backend tests

## Naming Conventions

**Android Files:**
- Screens: `PascalCaseScreen.kt` (e.g., `NodeEditorScreen.kt`)
- ViewModels: `PascalCaseViewModel.kt` (co-located with screen)
- DAOs: `PascalCaseDao.kt`
- Entities: `PascalCaseEntity.kt`
- DI Modules: `PascalCaseModule.kt`
- Merge objects: `PascalCaseMerge.kt`

**Android Classes:**
- ViewModels: `@HiltViewModel` annotated, constructor-injected
- UiState: sealed class with `Loading`, `Success`, `Error` objects, co-located in ViewModel file
- SideEffect: sealed class with specific event types, co-located in ViewModel file

**Backend Files:**
- Routes: `lowercase.ts` (e.g., `documents.ts`, `sync.ts`)
- Tests: `lowercase.test.ts` co-located with source
- Schema: `lowercase.ts` per entity in `schema/` directory

**Backend Functions:**
- Route factories: `createXRoutes(sqlite)` pattern
- DB helpers: `upsertX()`, `mergeX()` inline in route files

## Where to Add New Code

**New Android Screen:**
- Create directory: `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/featurename/`
- Add `FeatureNameScreen.kt` (Composable + `@HiltViewModel` ViewModel)
- Add `FeatureNameViewModel.kt` with `UiState` and `SideEffect` sealed classes in same file
- Register route in `android/app/src/main/java/com/gmaingret/outlinergod/ui/navigation/AppRoutes.kt`
- Wire composable in `android/app/src/main/java/com/gmaingret/outlinergod/ui/navigation/AppNavHost.kt`
- Add tests at `android/app/src/test/java/.../ui/screen/featurename/FeatureNameViewModelTest.kt`

**New Android Repository:**
- Add interface to `android/app/src/main/java/com/gmaingret/outlinergod/repository/NewRepository.kt`
- Add implementation to `repository/impl/NewRepositoryImpl.kt`
- Bind in `android/app/src/main/java/com/gmaingret/outlinergod/di/RepositoryModule.kt`

**New Backend Route:**
- Add `backend/src/routes/newfeature.ts` with `createNewFeatureRoutes(sqlite)` factory
- Add `backend/src/routes/newfeature.test.ts` co-located
- Register in `backend/src/index.ts` inside `buildApp()`

**New Room Entity:**
- Add entity file in `android/app/src/main/java/com/gmaingret/outlinergod/db/entity/`
- Add DAO in `android/app/src/main/java/com/gmaingret/outlinergod/db/dao/`
- Register both in `android/app/src/main/java/com/gmaingret/outlinergod/db/AppDatabase.kt`
- Add `@Provides` in `android/app/src/main/java/com/gmaingret/outlinergod/di/DatabaseModule.kt`
- Increment `AppDatabase` `version` and add a `Migration` object

**New Sync-able Field:**
- Add `fieldName` + `fieldName_hlc TEXT` columns to both Android entity and backend Drizzle schema
- Update `*Merge.kt` (Android) and `merge.ts` (backend) with `lwwStr/lwwInt` for the new field
- Update `SyncMappers.kt` extension functions
- Update upsert SQL in `backend/src/routes/sync.ts` `upsertX()` helpers

**Utilities:**
- Shared Android helpers: `android/app/src/main/java/com/gmaingret/outlinergod/util/`
- Shared backend helpers: `backend/src/test-helpers/` (test only) or top-level `backend/src/`

## Special Directories

**`android/app/src/main/java/.../prototype/`:**
- Purpose: Phase 0 prototypes — `FractionalIndex.kt` and `MarkdownVisualTransformation.kt` are still live production dependencies
- Generated: No
- Committed: Yes

**`backend/drizzle/`:**
- Purpose: Drizzle-generated SQL migration files — source of truth for DB schema versions
- Generated: Yes (by `drizzle-kit generate`)
- Committed: Yes — required for `runMigrations()` at server startup

**`data/`:**
- Purpose: Docker volume mount for SQLite database and uploaded files
- Generated: Yes (at runtime)
- Committed: No (in `.gitignore`)

**`.planning/`:**
- Purpose: GSD planning artifacts (phases, milestones, codebase docs)
- Generated: No
- Committed: Yes

**`.claude/worktrees/`:**
- Purpose: Claude agent worktrees (auto-managed)
- Generated: Yes
- Committed: No (should be in `.gitignore`)

---

*Structure analysis: 2026-03-06*
