## Phase 1 — Backend Foundation

**Goal**: A running, tested Docker container. `GET /health` returns `{"status":"ok","db":"ok","version":"1.0.0","uptime":<number>}`. `pnpm test` exits 0 with zero failures across all test files.

**Prerequisite**: All Phase 0 prototypes must have passing tests before beginning Phase 1.

Tasks run in task ID order. Each task is scoped to approximately 1–2 hours. One table = one task. One module = one task. Phase 1 has 16 tasks total (P1-1 through P1-16).

---

### P1-1: Backend project scaffold

- **Task ID**: P1-1
- **Title**: TypeScript + Fastify v5 project skeleton
- **What to build**: First, initialize the project root with `git init`, create a comprehensive `.gitignore` (excluding `node_modules/`, `dist/`, `.env`, `.gradle/`, `build/`, `local.properties`, `*.db`), and `git remote add origin https://github.com/gmaingret/outlinergod.git`. Then initialize the `backend/` directory as a Node.js/TypeScript project using `pnpm init`. Create `package.json` with all production and dev dependencies pinned to exact versions: Fastify `5.7.4`, `@fastify/type-provider-typebox`, `better-sqlite3 12.6.2`, `drizzle-orm 0.45.1`, `drizzle-kit` (dev), `jose 6.1.3`, `google-auth-library 10.6.1`, `@fastify/static`, `@fastify/multipart`, `fastify-plugin`, `vitest ^2` (dev), `typescript` (dev). Create `tsconfig.json` targeting ESM output (`"module": "NodeNext"`, `"moduleResolution": "NodeNext"`) to `dist/`. Create `src/index.ts` that exports a `buildApp()` factory function: constructs a Fastify instance with Pino logger enabled and the TypeBox type provider, registers a `GET /` placeholder, and returns the instance without calling `.listen()`. The `listen()` call lives in a separate `src/server.ts` entry point that imports `buildApp()`, calls `migrate()`, then binds to `0.0.0.0:${PORT}`. Create directory scaffolding: `src/routes/`, `src/db/`, `src/middleware/`, `src/test-helpers/`. After tests pass: `git add -A && git commit -m "P1-1: Backend project scaffold" && git push origin main`.
- **Inputs required**: Node.js 20 LTS, pnpm 9.x; no prior tasks
- **Output artifact**:
  - `.gitignore` (project root)
  - `backend/package.json`
  - `backend/tsconfig.json`
  - `backend/src/index.ts`
  - `backend/src/server.ts`
- **How to test**:
  - **Test file**: `backend/src/index.test.ts`
  - **Test cases**:
    1. `buildApp_returnsInstance` — call `buildApp()`; assert the returned value is truthy and has an `inject` method (confirming it's a Fastify instance).
    2. `buildApp_isReadyAfterAwait` — call `await app.ready()` after `buildApp()`; assert it resolves without throwing.
    3. `buildApp_twoCallsReturnIndependentInstances` — call `buildApp()` twice; assert the two returned instances are not the same object reference.
  - **Pass criteria**: `pnpm test` exits 0, all 3 cases green. `tsc --noEmit` exits 0.
- **Risk**: LOW — standard Node.js/TypeScript project bootstrap.

---

### P1-2: Vitest configuration

- **Task ID**: P1-2
- **Title**: Production Vitest config and test scripts
- **What to build**: Create `backend/vitest.config.ts` with: `test.environment = 'node'`, `test.globals = true`, `test.pool = 'forks'` (required for better-sqlite3 native module — using `'threads'` causes segfaults), `test.include = ['src/**/*.test.ts']`, `test.coverage = { provider: 'v8', include: ['src/**/*.ts'], exclude: ['src/**/*.test.ts', 'src/test-helpers/**'] }`. Create empty `backend/src/test-helpers/` directory. Add `"test": "vitest run"` and `"test:coverage": "vitest run --coverage"` scripts to `package.json`.
- **Inputs required**: P1-1 only
- **Output artifact**:
  - `backend/vitest.config.ts`
  - `backend/src/test-helpers/` (directory)
- **How to test**:
  - **Test file**: `backend/src/vitest.test.ts`
  - **Test cases**:
    1. `vitest_canRunTests` — assert `1 + 1 === 2` (confirms Vitest is configured and test runner reaches test files).
    2. `vitest_globals_areAvailable` — assert `typeof describe === 'function'` and `typeof it === 'function'` (confirms `globals: true` is working).
  - **Pass criteria**: `pnpm test` exits 0, all 2 cases green.
- **Risk**: LOW — configuration task only. The `forks` pool setting is non-negotiable for better-sqlite3; document this in `backend/README.md`.

---

### P1-3: Docker setup

- **Task ID**: P1-3
- **Title**: Dockerfile, docker-compose.yml, and environment configuration
- **What to build**: Create a multi-stage `backend/Dockerfile`. Stage 1 (`builder`): uses `node:20-alpine`, installs pnpm via corepack, copies `package.json` and `pnpm-lock.yaml`, runs `pnpm install --frozen-lockfile`, copies `src/` and config files, runs `pnpm run build` (`tsc`). Stage 2 (`runner`): uses `node:20-alpine`, copies only `dist/` and `node_modules/` from builder, sets `USER node`, exposes port 3000, sets `CMD ["node", "dist/server.js"]`. Add a Docker `HEALTHCHECK` directive: `CMD curl -f http://localhost:3000/health || exit 1` with 30s interval. Create `docker-compose.yml` at the project root defining one service (`backend`): build context `./backend`, mounts named volume `outlinergod-data` to `/data`, exposes port `3000:3000`, passes `env_file: .env`. Create `.env.example` at project root documenting all required env vars: `DATABASE_PATH=/data/outlinergod.db`, `JWT_SECRET` (min 32 random bytes hex), `GOOGLE_CLIENT_ID`, `PORT=3000`, `UPLOADS_PATH=/data/uploads`, `MAX_UPLOAD_BYTES=52428800`. Create `backend/.dockerignore` excluding `node_modules/`, `dist/`, `.env`, `**/*.test.ts`.
- **Inputs required**: P1-1 (project scaffold with working `pnpm run build`)
- **Output artifact**:
  - `backend/Dockerfile`
  - `docker-compose.yml` (project root)
  - `.env.example` (project root)
  - `backend/.dockerignore`
- **How to test**:
  - **Test file**: none — Docker build verification is manual for this task.
  - **Manual verification**:
    1. `docker compose build` exits 0.
    2. Copy `.env.example` to `.env`, fill in test values for `JWT_SECRET` and `GOOGLE_CLIENT_ID`. `docker compose up -d` starts successfully; `docker compose logs backend` shows Fastify bound to `0.0.0.0:3000`.
    3. `docker compose down` cleanly stops the service.
  - **Note**: This task's acceptance is confirmed by manual verification only; it does not add `pnpm test` cases.
- **Risk**: LOW — standard Docker multi-stage build pattern.

---

### P1-4: Database connection module

- **Task ID**: P1-4
- **Title**: better-sqlite3 connection with WAL mode and foreign keys enabled
- **What to build**: Create `backend/src/db/connection.ts` exporting a `createConnection(dbPath: string): Database` function. The function opens a `better-sqlite3` `Database` at the given path, immediately runs `PRAGMA journal_mode = WAL` and `PRAGMA foreign_keys = ON`, then returns the instance. Create `backend/src/db/index.ts` that calls `createConnection(process.env.DATABASE_PATH ?? ':memory:')` and exports the result as the singleton `db`. Tests must always import `createConnection` directly (not the singleton) to ensure test isolation.
- **Inputs required**: P1-1 (project scaffold), `better-sqlite3` and `@types/better-sqlite3` installed
- **Output artifact**:
  - `backend/src/db/connection.ts`
  - `backend/src/db/index.ts`
- **How to test**:
  - **Test file**: `backend/src/db/connection.test.ts`
  - **Test cases**:
    1. `createConnection_opens_inMemoryDb` — call `createConnection(':memory:')`; assert returned value is truthy and `.open === true`.
    2. `walMode_isEnabled` — after `createConnection(':memory:')`, run `PRAGMA journal_mode`; assert `journal_mode === 'wal'`.
    3. `foreignKeys_areEnabled` — after `createConnection(':memory:')`, run `PRAGMA foreign_keys`; assert `foreign_keys === 1`.
    4. `twoConnections_areIndependentInstances` — call `createConnection(':memory:')` twice; assert they are not the same object reference.
  - **Pass criteria**: `pnpm test` exits 0, all 4 cases green.
- **Risk**: LOW — synchronous SQLite connection with two pragma calls.

---

### P1-5: Drizzle schema — users table

- **Task ID**: P1-5
- **Title**: Drizzle schema definition for the users table
- **What to build**: Create `backend/src/db/schema/users.ts` defining the `users` table using `sqliteTable` from `drizzle-orm/sqlite-core`. Columns: `id` (TEXT PRIMARY KEY — UUID), `google_sub` (TEXT NOT NULL UNIQUE — Google `sub` claim used as stable identity key, not email), `email` (TEXT NOT NULL), `name` (TEXT NOT NULL), `picture` (TEXT NOT NULL, default `''`), `created_at` (INTEGER NOT NULL — Unix ms), `updated_at` (INTEGER NOT NULL — Unix ms). Export the table as `users` and the inferred TypeScript types as `User` (`$inferSelect`) and `NewUser` (`$inferInsert`).
- **Inputs required**: P1-4 (db connection module), `drizzle-orm` installed
- **Output artifact**: `backend/src/db/schema/users.ts`
- **How to test**:
  - **Test file**: `backend/src/db/schema/users.test.ts`
  - **Test cases**:
    1. `users_tableName_isUsers` — call `getTableConfig(users).name`; assert it equals `'users'`.
    2. `users_idIsPrimaryKey` — inspect column config; assert the `id` column has `primaryKey: true`.
    3. `users_googleSub_hasUniqueConstraint` — assert `google_sub` column config indicates uniqueness.
    4. `users_insert_andSelect_roundtrip` — create an in-memory Drizzle instance, apply the DDL via raw `CREATE TABLE`, insert one user row, select it back; assert all fields match the inserted values.
  - **Pass criteria**: `pnpm test` exits 0, all 4 cases green.
- **Risk**: LOW — straightforward single-table Drizzle schema.

---

### P1-6: Drizzle schema — refresh_tokens table

- **Task ID**: P1-6
- **Title**: Drizzle schema definition for the refresh_tokens table
- **What to build**: Create `backend/src/db/schema/refresh_tokens.ts` defining the `refreshTokens` table. Columns: `token` (TEXT PRIMARY KEY — 32-byte random hex, opaque), `user_id` (TEXT NOT NULL, references `users.id` with `onDelete: 'cascade'`), `device_id` (TEXT NOT NULL — stable per-installation UUID), `expires_at` (INTEGER NOT NULL — Unix ms, 30 days after creation), `created_at` (INTEGER NOT NULL — Unix ms), `revoked` (INTEGER NOT NULL DEFAULT 0 — 0 = active, 1 = revoked). Export `refreshTokens`, `RefreshToken`, `NewRefreshToken`.
- **Inputs required**: P1-5 (users schema)
- **Output artifact**: `backend/src/db/schema/refresh_tokens.ts`
- **How to test**:
  - **Test file**: `backend/src/db/schema/refresh_tokens.test.ts`
  - **Test cases**:
    1. `refreshTokens_tableName` — assert `getTableConfig(refreshTokens).name === 'refresh_tokens'`.
    2. `refreshTokens_tokenIsPrimaryKey` — assert `token` column is primary key.
    3. `refreshTokens_revokedDefaultsToZero` — create in-memory DB with both tables, insert user + token without specifying `revoked`; assert `revoked === 0`.
    4. `refreshTokens_cascadeDeleteOnUser` — insert user and token; delete the user row; assert the token row no longer exists.
  - **Pass criteria**: `pnpm test` exits 0, all 4 cases green.
- **Risk**: LOW — standard foreign key with cascade delete.

---

### P1-7: Drizzle schema — documents table

- **Task ID**: P1-7
- **Title**: Drizzle schema definition for the documents table with HLC sync columns
- **What to build**: Create `backend/src/db/schema/documents.ts` defining the `documents` table. Columns per API.md §4 HLC Sync Record — Document: `id` (TEXT PK, UUID), `user_id` (TEXT NOT NULL, FK → `users.id` cascade delete), `title` (TEXT NOT NULL), `title_hlc` (TEXT NOT NULL), `type` (TEXT NOT NULL — value must be `'document'` or `'folder'`; immutable after creation), `parent_id` (TEXT NULLABLE — self-referencing FK → `documents.id`, null = root level), `parent_id_hlc` (TEXT NOT NULL), `sort_order` (TEXT NOT NULL — fractional index string, never REAL), `sort_order_hlc` (TEXT NOT NULL), `collapsed` (INTEGER NOT NULL DEFAULT 0), `collapsed_hlc` (TEXT NOT NULL), `deleted_at` (INTEGER NULLABLE), `deleted_hlc` (TEXT NOT NULL), `device_id` (TEXT NOT NULL), `created_at` (INTEGER NOT NULL), `updated_at` (INTEGER NOT NULL). Export `documents`, `Document`, `NewDocument`.
- **Inputs required**: P1-5 (users schema)
- **Output artifact**: `backend/src/db/schema/documents.ts`
- **How to test**:
  - **Test file**: `backend/src/db/schema/documents.test.ts`
    1. `documents_tableName` — assert table name is `'documents'`.
    2. `documents_hasAllFiveHlcColumns` — assert columns `title_hlc`, `parent_id_hlc`, `sort_order_hlc`, `collapsed_hlc`, `deleted_hlc` all exist in the column config.
    3. `documents_collapsedDefaultsToZero` — insert document without `collapsed`; assert `collapsed === 0`.
    4. `documents_parentId_selfReference_works` — insert root doc (`parent_id = null`), insert child doc (`parent_id = root.id`); assert both rows selectable.
    5. `documents_deletedAt_isNullable` — insert document without `deleted_at`; assert the stored value is null.
  - **Pass criteria**: `pnpm test` exits 0, all 5 cases green.
- **Risk**: LOW — self-referencing FK is supported by SQLite without restriction.

---

### P1-8: Drizzle schema — nodes table

- **Task ID**: P1-8
- **Title**: Drizzle schema definition for the nodes table with full HLC companion columns
- **What to build**: Create `backend/src/db/schema/nodes.ts` defining the `nodes` table. This is the most column-dense table. Columns per ARCHITECTURE.md §4 and API.md §4 HLC Sync Record — Node: `id` (TEXT PK), `document_id` (TEXT NOT NULL, FK → `documents.id` cascade delete), `user_id` (TEXT NOT NULL, FK → `users.id` cascade delete), `content` (TEXT NOT NULL DEFAULT `''`), `content_hlc` (TEXT NOT NULL), `note` (TEXT NOT NULL DEFAULT `''`), `note_hlc` (TEXT NOT NULL), `parent_id` (TEXT NULLABLE — self-referencing FK → `nodes.id`, null = document root node), `parent_id_hlc` (TEXT NOT NULL), `sort_order` (TEXT NOT NULL — never REAL, always TEXT), `sort_order_hlc` (TEXT NOT NULL), `completed` (INTEGER NOT NULL DEFAULT 0), `completed_hlc` (TEXT NOT NULL), `color` (INTEGER NOT NULL DEFAULT 0), `color_hlc` (TEXT NOT NULL), `collapsed` (INTEGER NOT NULL DEFAULT 0), `collapsed_hlc` (TEXT NOT NULL), `deleted_at` (INTEGER NULLABLE), `deleted_hlc` (TEXT NOT NULL), `device_id` (TEXT NOT NULL), `created_at` (INTEGER NOT NULL), `updated_at` (INTEGER NOT NULL). Do NOT add a `children` column — tree structure is always reconstructed from `parent_id` + `sort_order`. Export `nodes`, `Node`, `NewNode`.
- **Inputs required**: P1-7 (documents schema), P1-5 (users schema)
- **Output artifact**: `backend/src/db/schema/nodes.ts`
- **How to test**:
  - **Test file**: `backend/src/db/schema/nodes.test.ts`
  - **Test cases**:
    1. `nodes_tableName` — assert table name is `'nodes'`.
    2. `nodes_hasAllEightHlcColumns` — assert all eight HLC columns exist: `content_hlc`, `note_hlc`, `parent_id_hlc`, `sort_order_hlc`, `completed_hlc`, `color_hlc`, `collapsed_hlc`, `deleted_hlc`.
    3. `nodes_noChildrenColumn` — assert no column named `children` exists in the table config.
    4. `nodes_rootNode_hasNullParentId` — insert node with `parent_id = null`; assert row is selectable and `parent_id` is null.
    5. `nodes_cascadeDelete_onDocumentDelete` — in-memory DB: create user → document → node; delete the document; assert the node row is gone.
    6. `nodes_colorDefaultsToZero` — insert node without `color`; assert `color === 0`.
  - **Pass criteria**: `pnpm test` exits 0, all 6 cases green.
- **Risk**: MEDIUM — highest column count in the schema. Self-referencing FK on `parent_id` must be correctly defined. Drizzle-kit must generate valid DDL for the self-reference; verify the generated migration SQL manually after `pnpm run migrate:generate`.

---

### P1-9: Drizzle schema — bookmarks table

- **Task ID**: P1-9
- **Title**: Drizzle schema definition for the bookmarks table with HLC sync columns
- **What to build**: Create `backend/src/db/schema/bookmarks.ts` defining the `bookmarks` table. Columns per API.md §4 HLC Sync Record — Bookmark: `id` (TEXT PK, UUID), `user_id` (TEXT NOT NULL, FK → `users.id` cascade delete), `title` (TEXT NOT NULL), `title_hlc` (TEXT NOT NULL), `target_type` (TEXT NOT NULL — `'document' | 'node' | 'search'`), `target_type_hlc` (TEXT NOT NULL), `target_document_id` (TEXT NULLABLE — no FK constraint; referential integrity is application-layer only), `target_document_id_hlc` (TEXT NOT NULL), `target_node_id` (TEXT NULLABLE — no FK constraint), `target_node_id_hlc` (TEXT NOT NULL), `query` (TEXT NULLABLE), `query_hlc` (TEXT NOT NULL), `sort_order` (TEXT NOT NULL), `sort_order_hlc` (TEXT NOT NULL), `deleted_at` (INTEGER NULLABLE), `deleted_hlc` (TEXT NOT NULL), `device_id` (TEXT NOT NULL), `created_at` (INTEGER NOT NULL), `updated_at` (INTEGER NOT NULL). Note: `target_document_id` and `target_node_id` intentionally have no FK constraints — referenced documents/nodes may be deleted independently. Export `bookmarks`, `Bookmark`, `NewBookmark`.
- **Inputs required**: P1-5 (users schema)
- **Output artifact**: `backend/src/db/schema/bookmarks.ts`
- **How to test**:
  - **Test file**: `backend/src/db/schema/bookmarks.test.ts`
  - **Test cases**:
    1. `bookmarks_tableName` — assert table name is `'bookmarks'`.
    2. `bookmarks_hasAllSevenHlcColumns` — assert `title_hlc`, `target_type_hlc`, `target_document_id_hlc`, `target_node_id_hlc`, `query_hlc`, `sort_order_hlc`, `deleted_hlc` all exist.
    3. `bookmarks_targetDocumentId_isNullable` — insert bookmark with `target_document_id = null`; assert row selectable.
    4. `bookmarks_searchType_withQuery_roundtrip` — insert bookmark with `target_type = 'search'`, `query = 'my search'`, both target IDs null; select back; assert all fields match.
  - **Pass criteria**: `pnpm test` exits 0, all 4 cases green.
- **Risk**: LOW — no FK constraints on target IDs by design simplifies this table.

---

### P1-10: Drizzle schema — settings table

- **Task ID**: P1-10
- **Title**: Drizzle schema definition for the settings table, plus schema barrel export
- **What to build**: Create `backend/src/db/schema/settings.ts` defining the `settings` table (one row per user). Columns per API.md §4 HLC Sync Record — Settings: `user_id` (TEXT PRIMARY KEY, FK → `users.id` cascade delete), `theme` (TEXT NOT NULL DEFAULT `'dark'`), `theme_hlc` (TEXT NOT NULL DEFAULT `''`), `density` (TEXT NOT NULL DEFAULT `'cozy'`), `density_hlc` (TEXT NOT NULL DEFAULT `''`), `show_guide_lines` (INTEGER NOT NULL DEFAULT 1), `show_guide_lines_hlc` (TEXT NOT NULL DEFAULT `''`), `show_backlink_badge` (INTEGER NOT NULL DEFAULT 1), `show_backlink_badge_hlc` (TEXT NOT NULL DEFAULT `''`), `device_id` (TEXT NOT NULL DEFAULT `''`), `updated_at` (INTEGER NOT NULL). Export `settings`, `Settings`, `NewSettings`. Also create `backend/src/db/schema/files.ts` defining the `files` metadata table for tracking file ownership. Columns: `filename` (TEXT PRIMARY KEY — `<uuid>.<ext>` format), `user_id` (TEXT NOT NULL, FK → `users.id` cascade delete), `node_id` (TEXT NULLABLE — informational association, no FK constraint), `mime_type` (TEXT NOT NULL DEFAULT `''`), `size` (INTEGER NOT NULL DEFAULT 0), `created_at` (INTEGER NOT NULL). Export `files`, `FileRecord`, `NewFileRecord`. Also create `backend/src/db/schema/index.ts` re-exporting all seven schema tables: `users`, `refreshTokens`, `documents`, `nodes`, `bookmarks`, `settings`, `files` — this barrel is consumed by both `drizzle.config.ts` and application code.
- **Inputs required**: P1-5 through P1-9 (all prior schema files)
- **Output artifact**:
  - `backend/src/db/schema/settings.ts`
  - `backend/src/db/schema/files.ts`
  - `backend/src/db/schema/index.ts`
- **How to test**:
  - **Test file**: `backend/src/db/schema/settings.test.ts`
  - **Test cases**:
    1. `settings_userIdIsPrimaryKey` — assert `user_id` is the primary key column.
    2. `settings_themeDefaultsToDark` — insert settings without `theme`; assert `theme === 'dark'`.
    3. `settings_densityDefaultsToCozy` — insert settings without `density`; assert `density === 'cozy'`.
    4. `settings_showGuideLines_defaultsToOne` — insert settings without `show_guide_lines`; assert value is `1`.
    5. `files_filenameIsPrimaryKey` — assert `filename` is the primary key column of the `files` table.
    6. `files_cascadeDelete_onUser` — insert user + file; delete user; assert file row is gone.
    7. `schemaIndex_exportsAllSevenTables` — import from `schema/index.ts`; assert `users`, `refreshTokens`, `documents`, `nodes`, `bookmarks`, `settings`, `files` are all defined (not `undefined`).
  - **Pass criteria**: `pnpm test` exits 0, all 7 cases green.
- **Risk**: LOW — single-row-per-user pattern; default values are straightforward.

---

### P1-11: Drizzle migration runner

- **Task ID**: P1-11
- **Title**: drizzle-kit migration generation and automatic migration on container startup
- **What to build**: Create `backend/drizzle.config.ts` with: `dialect: 'sqlite'`, `schema: './src/db/schema/index.ts'`, `out: './drizzle'`, `dbCredentials: { url: process.env.DATABASE_PATH ?? ':memory:' }`. Create `backend/src/db/migrate.ts` that imports `migrate` from `drizzle-orm/better-sqlite3/migrator` and `drizzle` from `drizzle-orm/better-sqlite3`, takes a `Database` instance as a parameter, and calls `migrate(drizzle(sqlite), { migrationsFolder: new URL('../../drizzle', import.meta.url).pathname })`. Call `migrate(sqlite)` from `src/server.ts` before calling `app.listen()` so migrations run automatically on every container start. Add two scripts to `package.json`: `"migrate:generate": "drizzle-kit generate"` and `"migrate:run": "drizzle-kit migrate"`. Run `pnpm run migrate:generate` to produce the initial migration SQL files and commit them to `backend/drizzle/`. Also create `backend/src/test-helpers/createTestDb.ts` exporting `createTestDb()`: creates a fresh in-memory `better-sqlite3` `Database` via `createConnection(':memory:')`, runs the Drizzle migrator against it, returns `{ sqlite, db: drizzle(sqlite) }`. This helper is used by every test file that needs a database — never import the singleton `db` from `src/db/index.ts` in tests.
- **Inputs required**: P1-10 (all schema files and `schema/index.ts`), drizzle-kit installed as dev dependency
- **Output artifact**:
  - `backend/drizzle.config.ts`
  - `backend/src/db/migrate.ts`
  - `backend/src/test-helpers/createTestDb.ts`
  - `backend/drizzle/0000_initial.sql` (or drizzle-kit's generated filename)
  - `backend/drizzle/meta/_journal.json`
- **How to test**:
  - **Test file**: `backend/src/db/migrate.test.ts`
  - **Test cases**:
    1. `migrate_createsAllSevenTables` — call `migrate(sqlite)` against a fresh `createConnection(':memory:')` instance; run `SELECT name FROM sqlite_master WHERE type='table' ORDER BY name`; assert all seven table names are present: `bookmarks`, `documents`, `files`, `nodes`, `refresh_tokens`, `settings`, `users`.
    2. `migrate_isIdempotent` — call `migrate(sqlite)` twice on the same in-memory instance; assert no error is thrown on the second call.
    3. `migrate_doesNotResetWalMode` — after migration, run `PRAGMA journal_mode`; assert result is `'wal'` (migration must not reset the pragma set by `createConnection`).
  - **Pass criteria**: `pnpm test` exits 0, all 3 cases green. `pnpm run migrate:generate` exits 0 and produces at least one `.sql` file in `backend/drizzle/`.
- **Risk**: MEDIUM — `drizzle-kit generate` must be run once manually to produce migration files before tests can pass. Document this one-time bootstrap step in `backend/README.md`. The `migrationsFolder` path resolution must work both locally and inside the Docker container — use `import.meta.url` for path resolution rather than `__dirname`.

---

### P1-12: HLC clock module

- **Task ID**: P1-12
- **Title**: Hybrid Logical Clock generate and receive functions
- **What to build**: **If P0-3 tests are all passing and `backend/src/hlc.ts` exists**: review the file, verify it matches the spec below, and confirm existing tests still pass — no changes needed. **Otherwise, implement `backend/src/hlc.ts` from scratch**: Export `generate(deviceId: string): string` — reads `Date.now()` as wall time, compares to module-level `localHlc` state, increments the 16-bit counter on ties (max `0xFFFF`), formats as `<wall_ms_16hex>-<counter_4hex>-<deviceId>` (zero-padded hex), updates `localHlc`, returns the new string. Export `receive(incoming: string, deviceId: string): string` — parses the incoming HLC, advances `localHlc` wall to `max(localWall, incomingWall)`, resets counter to `0` if wall advanced or increments if equal, updates `localHlc`, returns the new HLC. Export `compare(a: string, b: string): number` — returns the result of `a.localeCompare(b)` (lexicographic; a higher HLC is always lexicographically greater). All wall-time hex values must be zero-padded to exactly 16 characters; counter to exactly 4 characters.
- **Inputs required**: P1-1 (project scaffold); TypeScript only — no external HLC library
- **Output artifact**: `backend/src/hlc.ts`
- **How to test**:
  - **Test file**: `backend/src/hlc.test.ts`
  - **Test cases**:
    1. `generate_matchesFormat` — call `generate('device1')`; assert matches `/^[0-9a-f]{16}-[0-9a-f]{4}-device1$/`.
    2. `generate_isMonotonicallyIncreasing` — call `generate('d')` 100 times; assert each result is lexicographically `>` the previous.
    3. `generate_incrementsCounter_onSameMillisecond` — mock `Date.now` to return a fixed value; call `generate('d')` twice; assert second HLC has counter `0001`, first has `0000`.
    4. `receive_advancesWallPastIncoming` — mock `Date.now()` to `T`; call `receive()` with incoming HLC whose wall is `T + 5000`; assert returned HLC's wall hex equals `(T + 5000).toString(16).padStart(16, '0')`.
    5. `receive_resetsCounter_whenWallAdvances` — after `generate()` with incremented counter, call `receive()` with strictly higher wall; assert counter in result is `0000`.
    6. `compare_higherTimestamp_returnsPositive` — assert `compare(hlc_T2, hlc_T1) > 0` for `T2 > T1`.
    7. `compare_higherCounter_returnsPositive` — two HLCs with same wall, counters `0000` vs `0001`; assert `compare(counter_0001, counter_0000) > 0`.
    8. `sortLexicographic_matchesChronological` — generate 50 HLCs, sort by JS string comparison, assert identical to generation order.
  - **Pass criteria**: `pnpm test` exits 0, all 8 cases green.
- **Risk**: HIGH — HLC correctness is foundational to the entire sync system. A one-character padding error in the 16-char wall-time hex makes all HLC comparisons silently wrong. The counter must not overflow in practice (max `0xFFFF` = 65,535 increments per millisecond — far above any real workload). The `receive()` function's max-wall / counter-increment logic must exactly match the spec or the LWW invariant breaks silently under clock drift.

---

### P1-13: LWW merge module

- **Task ID**: P1-13
- **Title**: Per-field Last-Write-Wins merge function for nodes, documents, and bookmarks
- **What to build**: **If P0-3 tests are all passing and `backend/src/merge.ts` exists**: review, adopt, extend to cover documents and bookmarks if not already present. **Otherwise implement from scratch**: Create `backend/src/merge.ts` exporting `mergeNodes(local: NodeSyncRecord, incoming: NodeSyncRecord): NodeSyncRecord`. For each independently syncable field pair — (`content`/`content_hlc`), (`note`/`note_hlc`), (`parent_id`/`parent_id_hlc`), (`sort_order`/`sort_order_hlc`), (`completed`/`completed_hlc`), (`color`/`color_hlc`), (`collapsed`/`collapsed_hlc`), (`deleted_at`/`deleted_hlc`) — keep the value whose `_hlc` is lexicographically greater using `compare()` from `hlc.ts`. On equal HLCs, keep local (idempotency). For fields where incoming wins, use `incoming.device_id`; for fields where local wins, keep `local.device_id`. The final `device_id` on the returned record is the `device_id` of whichever side won the most fields (or local on tie). Export `mergeDocuments` and `mergeBookmarks` following the same pattern. Export TypeScript interfaces `NodeSyncRecord`, `DocumentSyncRecord`, `BookmarkSyncRecord` (or import them from a companion `backend/src/types.ts`).
- **Inputs required**: P1-12 (hlc.ts for `compare()`), P1-8 (nodes schema for field names)
- **Output artifact**: `backend/src/merge.ts` (and `backend/src/types.ts` if needed)
- **How to test**:
  - **Test file**: `backend/src/merge.test.ts`
  - **Test cases**:
    1. `mergeNodes_incomingWins_whenHigherContentHlc` — two records differing only in `content`/`content_hlc`; assert merged `content` comes from the record with the higher `content_hlc`.
    2. `mergeNodes_localWins_onEqualHlc` — identical `content_hlc` on both sides; assert merged `content` equals local value.
    3. `mergeNodes_isIdempotent` — assert `mergeNodes(A, mergeNodes(A, B))` deep-equals `mergeNodes(A, B)`.
    4. `mergeNodes_isCommutative_perField` — assert each field in `mergeNodes(A, B)` equals the corresponding field in `mergeNodes(B, A)` (fields independently commute; `device_id` may differ).
    5. `mergeNodes_deleteWins_whenDeletedHlcIsHighest` — B has `deleted_at` set and `deleted_hlc` lexicographically greater than all of A's field HLCs; assert merged `deleted_at` is set.
    6. `mergeNodes_contentFieldIndependent_ofDeletion` — A edits `content` at HLC T=100; B only sets `deleted_at` at HLC T=200 (B never set `content_hlc`); assert merge has `deleted_at` set (B's `deleted_hlc` wins that field) and `content` from A (A's `content_hlc` > B's absent `content_hlc`).
    7. `mergeDocuments_titleField_higherHlcWins` — same pattern as test 1, for documents.
    8. `mergeBookmarks_sortOrderField_higherHlcWins` — same pattern, for bookmarks.
  - **Pass criteria**: `pnpm test` exits 0, all 8 cases green.
- **Risk**: MEDIUM — conceptually simple but easy to misimplement by mutating input objects or mispairing field/HLC columns. Test 6 verifies field-independence, which is the most subtle invariant.

---

### P1-14: Auth middleware

- **Task ID**: P1-14
- **Title**: JWT verification Fastify plugin using jose, attaches user to request
- **What to build**: Create `backend/src/middleware/auth.ts` exporting a Fastify plugin (wrapped with `fastify-plugin` to prevent scope encapsulation) that: (1) decorates the Fastify instance with `request.user` typed as `{ id: string; email: string; name: string; picture: string } | null` using `fastify.decorateRequest('user', null)`. (2) Adds a global `preHandler` hook that reads `req.headers.authorization`, extracts the Bearer token, calls `jwtVerify(token, new TextEncoder().encode(process.env.JWT_SECRET!), { algorithms: ['HS256'] })` from `jose`, and on success sets `req.user = { id: payload.sub!, email: payload.email as string, name: payload.name as string, picture: payload.picture as string }`. On any failure (missing header, invalid token, expired, wrong algorithm), sets `req.user = null` without throwing. (3) Exports a `requireAuth` preHandler factory that returns a hook which checks `req.user !== null` and replies `401 { error: 'Unauthorized' }` if not. Add TypeScript module augmentation for `FastifyRequest` to include the `user` property. Register this plugin in `src/index.ts`.
- **Inputs required**: P1-1 (project scaffold), `jose` installed, `fastify-plugin` installed
- **Output artifact**: `backend/src/middleware/auth.ts`
- **How to test**:
  - **Test file**: `backend/src/middleware/auth.test.ts`
  - **Test cases**:
    1. `auth_setsUser_onValidJwt` — build test Fastify app with auth plugin; add test route returning `req.user`; sign a valid HS256 JWT (sub=`'user-uuid'`, email, name, picture) with test secret; inject GET with Bearer token; assert response body equals `{ id: 'user-uuid', email: '...', name: '...', picture: '...' }`.
    2. `auth_setsNull_onMissingHeader` — inject GET without Authorization header; assert route returns `null` user (middleware does not reply 401 on its own — only `requireAuth` does).
    3. `auth_setsNull_onExpiredToken` — sign JWT with `exp: Math.floor(Date.now()/1000) - 1`; inject; assert `req.user` is null.
    4. `auth_setsNull_onWrongSecret` — sign JWT with `'wrong-secret'`; inject with correct test secret in env; assert `req.user` is null.
    5. `requireAuth_returns401_whenNoUser` — add route protected with `requireAuth`; inject without token; assert status 401 and body `{ error: 'Unauthorized' }`.
    6. `requireAuth_allows_whenUserIsSet` — inject with valid JWT on `requireAuth`-protected route; assert 200.
  - **Pass criteria**: `pnpm test` exits 0, all 6 cases green.
- **Risk**: MEDIUM — Fastify request decoration and TypeScript module augmentation require wrapping with `fastify-plugin`. Omitting `fastify-plugin` scopes the decoration to a child context, causing `req.user` to be `undefined` on sibling route plugins.

---

### P1-15: Health check endpoint

- **Task ID**: P1-15
- **Title**: GET /health — db ping, version, and uptime response
- **What to build**: Create `backend/src/routes/health.ts` exporting a Fastify plugin that registers `GET /health` (no `/api` prefix). The handler runs `db.prepare('SELECT 1').get()` inside a try/catch; on success sets `db: 'ok'`, on error sets `db: 'error'`. Response body: `{ status: 'ok', version: string, db: 'ok' | 'error', uptime: number }` where `version` is read from `package.json` (import via `createRequire` or JSON import assertion) and `uptime` is `process.uptime()`. The endpoint is unprotected (no auth). Register this plugin in `src/index.ts` via `app.register(healthRoute)` before adding any `/api` prefix scope.
- **Inputs required**: P1-4 (db connection), P1-1 (buildApp), P1-11 (migration runner so schema exists for the db ping)
- **Output artifact**: `backend/src/routes/health.ts`
- **How to test**:
  - **Test file**: `backend/src/routes/health.test.ts`
  - **Test cases**:
    1. `GET_health_returns200` — `buildApp()` with in-memory test db; inject `GET /health`; assert status 200.
    2. `GET_health_statusIsOk` — assert response body `status === 'ok'`.
    3. `GET_health_dbIsOk` — assert response body `db === 'ok'` when db is accessible.
    4. `GET_health_versionMatchesPackageJson` — assert response body `version` equals the `version` field from `backend/package.json`.
    5. `GET_health_uptimeIsPositiveNumber` — assert `typeof uptime === 'number'` and `uptime > 0`.
    6. `GET_health_dbIsError_whenDbThrows` — construct app with a mock db that throws on `SELECT 1`; inject `GET /health`; assert `db === 'error'` and status still 200.
  - **Pass criteria**: `pnpm test` exits 0, all 6 cases green.
- **Risk**: LOW — no auth, no complex logic. The only edge case is the error-path test (test 6).

---

**Phase 1 exit criteria**: All 16 tasks complete. `pnpm test` exits 0 (zero failures, zero skipped tests, all prior test files still passing). `docker compose build` succeeds. `docker compose up` starts the container. `curl http://localhost:3000/health` returns `{"status":"ok","db":"ok","version":"1.0.0","uptime":<positive number>}`.

---

### P1-16: Phase 1 smoke test — exit gate

- **Task ID**: P1-16
- **Title**: Suite smoke test — validates all tables exist, health returns full shape, no skipped tests
- **What to build**: Create `backend/src/smoke.test.ts` as the Phase 1 exit gate. This test file verifies the entire Phase 1 integration.
- **Inputs required**: All P1-1 through P1-15 must be complete
- **Output artifact**: `backend/src/smoke.test.ts`
- **How to test**:
  - **Test file**: `backend/src/smoke.test.ts`
  - **Test cases**:
    1. `smoke_health_returnsFullShape` — `buildApp()` → inject `GET /health` → assert response body matches `{ status: 'ok', db: 'ok', version: expect.any(String), uptime: expect.any(Number) }`.
    2. `smoke_allSevenTables_existAfterMigration` — `createTestDb()` → `sqlite.prepare("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").all()` → assert result includes all seven names: `bookmarks`, `documents`, `files`, `nodes`, `refresh_tokens`, `settings`, `users`.
    3. `smoke_noSkippedTests_inSuite` — read all `src/**/*.test.ts` file contents using Node `fs`; grep for `/\b(it|test)\.skip\b|\.todo\b/`; assert total match count is 0.
  - **Pass criteria**: `pnpm test` exits 0 across all test files — all `*.test.ts` files from P1-1 through P1-16. Zero failures. Zero skipped tests.
- **Risk**: LOW — integration verification only.