# Coding Conventions

**Analysis Date:** 2026-03-06

## Naming Patterns

**Files (Backend):**
- Route files: `routes/<resource>.ts` and `routes/<resource>.test.ts` (co-located)
- Middleware files: `middleware/auth.ts`
- Utility files: flat naming e.g. `merge.ts`, `tombstone.ts`
- NOTE: There is a stale top-level `src/hlc.ts` (hex-format prototype) alongside the active `src/hlc/hlc.ts` (decimal-format). The top-level file is unused but not deleted.

**Files (Android):**
- ViewModels: `<Screen>ViewModel.kt` in `ui/screen/<screen>/`
- Screens: `<Screen>Screen.kt` co-located with ViewModel
- DAOs: `<Entity>Dao.kt` in `db/dao/`
- Entities: `<Entity>Entity.kt` in `db/entity/`
- Repository interfaces: `<Name>Repository.kt` in `repository/`
- Repository implementations: `<Name>RepositoryImpl.kt` in `repository/impl/`

**Functions (Backend):**
- Route factory functions: `create<Resource>Routes(sqlite)` returning async plugin
- Route helpers (internal): `toNodeResponse(row)`, `upsertNode(sqlite, rec)`, `seedUser(sqlite, id)` in test files
- Pure merge functions: `mergeNodes()`, `mergeDocuments()`, `mergeBookmarks()` in `src/merge.ts`
- HLC functions: `hlcGenerate()`, `hlcReceive()`, `hlcParse()`, `hlcCompare()` (verb-prefix + noun)

**Functions (Android):**
- ViewModel intents: camelCase verbs — `loadDocument()`, `triggerSync()`, `createDocument()`, `renameDocument()`
- DAO queries: `getNodesByDocument()`, `getPendingChanges()`, `upsertNodes()`, `softDeleteDocument()`
- Mapper functions: `mapToFlatList()`, `toNodeSyncRecord()`, `toNodeEntity()`
- Compose screens: PascalCase `@Composable` functions — `NodeEditorScreen()`, `DocumentListScreen()`

**Variables:**
- Backend: `camelCase` throughout TypeScript; DB row types use `snake_case` field names to match SQLite columns
- Android: `camelCase` for Kotlin properties; `snake_case` used in `@ColumnInfo(name = ...)` annotations and JSON serialization `@SerialName`

**Types:**
- Backend interfaces for DB rows: `NodeRow`, `DocumentRow` — defined locally inside route files (no shared type file)
- Android entities: `NodeEntity`, `DocumentEntity`, `BookmarkEntity`, `SettingsEntity`
- Android UiState: sealed classes with `Loading`, `Success`, `Error` subclasses per screen
- Android SideEffects: sealed classes with named data classes e.g. `DocumentListSideEffect.ShowError(message)`

## Code Style

**Formatting:**
- No Prettier config found (`.prettierrc` absent). Backend TypeScript formatting appears consistent (2-space indent, single quotes, trailing commas) but is enforced only by convention — no automated formatter is configured.
- Android: no ktlint or detekt config found. Code follows standard Kotlin style.

**Linting:**
- No ESLint config in the project root or `backend/`. Only node_modules carry `.eslintrc` files.
- TypeScript strict mode is enabled in `backend/tsconfig.json` (`"strict": true`) — this is the primary type safety enforcement.
- Android: no static analysis tool configured.

## Import Organization

**Backend pattern (TypeScript):**
```typescript
// Node built-ins first
import { randomUUID } from 'node:crypto'
// Framework/library types
import type { FastifyInstance } from 'fastify'
import type Database from 'better-sqlite3'
// Internal imports
import { requireAuth } from '../middleware/auth.js'
import { mergeNodes } from '../merge.js'
```
Note: `.js` extension is required on all internal imports (ESM with NodeNext resolution).

**Android pattern (Kotlin):**
```kotlin
// Android/framework imports first
import androidx.room.ColumnInfo
import dagger.hilt.android.lifecycle.HiltViewModel
// Library imports
import io.ktor.client.*
import io.mockk.mockk
// Internal imports (same package last)
import com.gmaingret.outlinergod.db.entity.NodeEntity
```
Imports are NOT auto-sorted — ordering varies across files.

**Path Aliases:**
- None configured in either project.

## Error Handling

**Backend pattern:**
- Route handlers use early `reply.status(N).send({ error: '...' })` returns for validation failures.
- Auth middleware uses silent catch: `catch { req.user = null }` — swallows JWT errors intentionally.
- Some route files use bare `catch {}` (no error variable) for intentional swallow-and-continue paths:
  ```typescript
  // src/middleware/auth.ts:46
  } catch {
    req.user = null
  }
  ```
- Repository operations on Android use `runCatching { }` → returns `Result<T>`, errors propagate as `Result.failure`.
- ViewModel `intent {}` blocks use broad `catch (e: Exception)` → post `ShowError` side effects.
- `triggerSync()` uses `catch (_: Exception)` (blank identifier) — hides error type and prevents differentiated handling.

**Android ViewModel pattern:**
```kotlin
fun createDocument(...) = intent {
    try {
        // ...
    } catch (e: Exception) {
        postSideEffect(DocumentListSideEffect.ShowError(e.message ?: "Failed to create document"))
    }
}
```
All ViewModel intents follow this try/catch-to-sideeffect pattern. The sync error path uses `catch (_: Exception)` which makes debugging harder — the exception type and message are discarded.

## Logging

**Android:** Custom `SyncLogger` object at `util/SyncLogger.kt`. Ring buffer (max 1000 entries) mirrored to Logcat tag `OutlinerGodSync`. Used exclusively in `SyncRepositoryImpl`. ViewModels and DAOs do not log.

**Backend:** No logging framework. All errors are handled by Fastify's default error handler or swallowed in `catch {}` blocks. No structured logging.

## Comments

**When to Comment:**
- Public functions in `hlc/hlc.ts`, `merge.ts`, and `FlatNodeMapper.kt` have JSDoc/KDoc explaining the algorithm and invariants.
- Route handlers use section separator comments (`// ------- ... -------`) to delineate endpoints within a file.
- Test files open with a block comment listing the route paths under test.

**KDoc/JSDoc:**
- Used for algorithmic code (HLC, merge, FractionalIndex).
- NOT used for ViewModel methods, DAOs, or screen composables.

## Function Design

**Size:**
- `NodeEditorViewModel.kt` is 1053 lines — far beyond single-responsibility. It handles node loading, editing, persistence debounce, drag-and-drop, undo/redo, sync, zoom-in, bookmarking, file attachment, and inactivity timers all in one class.
- `NodeEditorScreen.kt` is 728 lines — monolithic composable with no helper composables extracted.
- `sync.ts` (534 lines) contains the entire push handler inline as `handleSyncPush()`.

**Parameters:**
- Backend route factories use dependency injection via closure: `createNodeRoutes(sqlite)` captures `sqlite`.
- Android ViewModels use constructor injection via Hilt: all dependencies injected via `@Inject constructor(...)`.

**Return Values:**
- Backend routes: `return reply.status(N).send({...})` for errors, plain object for success (Fastify serializes).
- Android repositories: `Result<T>` from `runCatching {}`.
- Android DAOs: `Flow<List<T>>` for reactive queries, `suspend fun` returning `Unit` or entity for mutations.

## Module Design

**Backend exports:**
- Each route file exports one factory function: `export function createNodeRoutes(...)`.
- `merge.ts` exports the three merge functions and their `*SyncRecord` interfaces.
- Internal helpers (row mappers, seed helpers) are NOT exported.

**Android:**
- No barrel files (index.kt). Imports use full package paths.
- Repository interfaces and implementations are separate files in separate packages.
- UiState and SideEffect sealed classes are defined at the bottom of the ViewModel file they belong to.

## Patterns Worth Noting

**Duplicated constants:**
- `SERVER_DEVICE_ID = 'server'` is defined identically in three backend files: `routes/documents.ts`, `routes/bookmarks.ts`, and `routes/sync.ts`. No shared constants module exists.

**Duplicated interface definitions:**
- `DocumentRow` interface is defined in both `routes/nodes.ts` (line 31) and `routes/documents.ts` (line 9). These are identical but not shared.

**Type unsafety in sync route:**
- `sync.ts` uses `as unknown as NodeSyncRecord` and `(req as unknown as { user: ... })` type casts due to mismatch between the DB row type (includes `user_id`, `created_at`) and `NodeSyncRecord` interface (does not). These are workarounds for missing type alignment.

**Non-null assertions:**
- `req.user!.id` is used throughout route handlers after `requireAuth` validates presence. This is safe but not enforced by the type system elegantly.
- In Android prototype code (`FractionalIndex.kt`, `MarkdownVisualTransformation.kt`), `!!` is used on regex group captures — safe given the regex guarantees but brittle.

---

*Convention analysis: 2026-03-06*
