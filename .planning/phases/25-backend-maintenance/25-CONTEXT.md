# Phase 25: Backend maintenance - Context

**Gathered:** 2026-03-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Backend-only housekeeping — no new features, no Android changes, no UI changes. Four independent cleanup tasks:
1. Move SettingsSyncRecord + mergeSettings into merge.ts with tests
2. Add periodic 24h tombstone purge (startup already exists)
3. Add max_hlc STORED generated column + index on nodes/documents/bookmarks; use it in sync pull query
4. Upgrade Docker from node:20-alpine to node:24-alpine across all stages

</domain>

<decisions>
## Implementation Decisions

### mergeSettings relocation
- Move `SettingsSyncRecord` type, `mergeSettings()` function, and `isSettingsFullyAccepted()` helper from `sync.ts` to `merge.ts`
- All three are exported from `merge.ts`; `sync.ts` imports them
- Fix the tiebreaker: use `lwwStr` for `theme`/`density`, `lwwInt` for `show_guide_lines`/`show_backlink_badge` — consistent with mergeNodes/mergeDocuments/mergeBookmarks, commutative on equal HLCs
- Add `mergeSettings` tests to `merge.test.ts` (not a separate file) — commutativity test + field LWW behavior, consistent with existing tests for the other 3 entity types

### Periodic tombstone purge
- Add `setInterval(() => purgeTombstones(sqlite), 24 * 60 * 60 * 1000)` in `startServer()`, fire-and-forget
- Placed immediately after the existing startup `purgeTombstones(sqlite)` call
- No handle stored/returned — tests use `buildApp()` not `startServer()`, so no cleanup needed
- No changes to `tombstone.ts` signature

### max_hlc generated column
- Add a `max_hlc TEXT GENERATED ALWAYS AS (...) STORED` column to `nodes`, `documents`, and `bookmarks` tables
- `nodes.max_hlc` expression: `MAX(content_hlc, note_hlc, parent_id_hlc, sort_order_hlc, completed_hlc, color_hlc, collapsed_hlc, deleted_hlc)`
- `documents.max_hlc` expression: `MAX(title_hlc, parent_id_hlc, sort_order_hlc, collapsed_hlc, deleted_hlc)`
- `bookmarks.max_hlc` expression: `MAX(title_hlc, target_type_hlc, target_document_id_hlc, target_node_id_hlc, query_hlc, sort_order_hlc, deleted_hlc)`
- Create a `CREATE INDEX` on `max_hlc` for each table
- Implementation: raw SQL migration only — no changes to Drizzle schema `.ts` files (Drizzle doesn't support SQLite generated columns natively; consistent with how FTS5 was handled on Android)
- Refactor sync pull query in `sync.ts`: replace the 8+ OR conditions per table with `max_hlc > ?` (single column check). Applies to both the isFullSync and non-fullSync variants of each query

### Node.js version upgrade
- Bump all 3 Dockerfile stages to `node:24-alpine`: web-builder, ts-builder, runner
- Update `engines.node` in `backend/package.json` to `>=24`
- Consistent with dev host already running Node.js 24

### Claude's Discretion
- Exact migration file name/numbering for the max_hlc addition
- Whether to add max_hlc to the settings table (settings pull uses individual HLC columns — not a perf concern with single-row table, skip)
- Test assertions in merge.test.ts for mergeSettings (follow same pattern as mergeNodes tests)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `lwwStr`, `lwwInt`, `lwwNullableInt`, `maxStr` helpers in `merge.ts` — already exported; mergeSettings will use lwwStr and lwwInt
- `purgeTombstones(sqlite)` in `tombstone.ts` — signature unchanged; just add setInterval call at the callsite
- Existing `merge.test.ts` — add mergeSettings describe block following the same structure as mergeNodes tests

### Established Patterns
- Raw SQL migration for schema features Drizzle can't express (FTS5 on Android, same approach here)
- `isNodeFullyAccepted`, `isDocumentFullyAccepted`, `isBookmarkFullyAccepted` all live in `sync.ts` currently — `isSettingsFullyAccepted` moves to `merge.ts` alongside the others (only settings moves; the node/doc/bookmark helpers stay in sync.ts since they're only needed there)
- `startServer()` in `index.ts` is the production entrypoint; `buildApp()` is the test-safe factory — keep side effects (timers) in startServer only

### Integration Points
- `sync.ts` imports from `merge.ts` — add `SettingsSyncRecord`, `mergeSettings`, `isSettingsFullyAccepted` to that import
- Drizzle migration files live in `backend/drizzle/` — add a new migration SQL file there
- `backend/Dockerfile` (3 FROM statements), `docker-compose.yml` references `backend/Dockerfile` — both need the node version update (Dockerfile only; docker-compose just references the Dockerfile)

</code_context>

<specifics>
## Specific Ideas

- The settings table is a single row per user — no index benefit on max_hlc for settings. Skip the generated column for the settings table.
- The current mergeSettings non-commutativity bug (stored wins on equal HLC instead of value-based tiebreak) can't occur in production (HLCs embed device_id, guaranteeing uniqueness), but fixing it while moving the code makes the implementation consistent and correct.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 25-backend-maintenance*
*Context gathered: 2026-03-06*
