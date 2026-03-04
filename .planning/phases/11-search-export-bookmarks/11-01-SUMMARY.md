---
plan: 11-01
status: complete
completed: 2026-03-04
commits:
  - f9ea6a2 feat(11-01): FTS5 database migration + NodeDao @RawQuery
  - a680ac3 feat(11-01): SearchRepository + SearchQueryParser + AppDatabase FTS5 refactor
  - f4fb025 test(11-01): FTS5 migration + SearchRepository + SearchQueryParser tests
---

## What Was Built

FTS5 full-text search data layer: Room DB migration 1→2 with external content FTS5 virtual table, SearchRepository with structured query parsing, and comprehensive tests.

## Deliverables

### AppDatabase.kt (version 2)
- `MIGRATION_1_2`: creates `nodes_fts` FTS5 virtual table (porter unicode61 tokenizer, external content)
- `createFts5()` helper called by both migration and `FTS5_CALLBACK` (fresh install path)
- 3 triggers: `nodes_fts_insert`, `nodes_fts_update`, `nodes_fts_delete` (uses special `'delete'` INSERT for external content safety)
- Population: `INSERT INTO nodes_fts ... SELECT FROM nodes WHERE deleted_at IS NULL`
- `buildInMemory()` excludes FTS5_CALLBACK (Robolectric sqlite4java lacks FTS5)

### NodeDao.kt
- `@RawQuery suspend fun searchFts(query: SupportSQLiteQuery): List<NodeEntity>` — one-shot FTS5 query

### SearchQueryParser.kt
- `ParsedQuery(ftsTerms, isCompleted, color, inNote, inTitle)` data class
- Parses `is:completed`, `is:not-completed`, `color:red/orange/yellow/green/blue/purple`, `in:note`, `in:title`
- Appends `*` to last word for prefix matching

### SearchRepository.kt / SearchRepositoryImpl.kt
- `searchNodes(query, userId)` builds parameterized `SimpleSQLiteQuery` from ParsedQuery
- FTS5 MATCH on all columns; `in:note`/`in:title` implemented as **post-filters** on result set
- Guards against empty ftsTerms (returns emptyList() to avoid SQLite exception)

### RepositoryModule.kt
- `provideSearchRepository()` Hilt singleton binding

## Tests (32 new)
- `AppDatabaseMigrationTest`: 5 tests — FTS5 table creation, node indexing, soft-delete exclusion
- `SearchRepositoryTest`: 11 tests — basic search, is:completed, in:note/in:title post-filtering
- `SearchQueryParserTest`: 16 tests — operator extraction, prefix matching, edge cases

## Issues / Deviations

None. `in:note` and `in:title` use post-filtering (not FTS5 column-prefix MATCH) per plan spec.
