# OutlinerGod Project State

## Current Position

Phase: 06 of 06 (tech-debt-cleanup) — COMPLETE
Plan: 02 of 2 (all complete)
Status: v0.4 milestone complete — ready for audit or v0.5
Last activity: 2026-03-03 - Phase 06 complete — 9/9 must-haves verified (VERIFICATION.md passed)

Progress: ████████████████ (completed: 01, 02, 03, 04, 05, 06 | v0.4 milestone done)

## Accumulated Decisions

| ID | Decision | Phase-Plan | Impact |
|----|----------|------------|--------|
| D1 | sort_order is always TEXT (fractional indexing) | architecture | all DAOs |
| D2 | flatMapLatest on getAccessToken() for settings observation | 04-04 | MainActivity theme |
| D3 | Default theme null/unknown maps to dark | 04-04 | OutlinerGodTheme |
| D4 | ViewModel logic in companion object pure functions for JVM testability | 04-07 | all ViewModels |
| D5 | reorderable 2.5.1 (not 3.0.0 which doesn't exist) | 04-10 | DnD |
| D6 | Orbit MVI 10.0.0 for all ViewModels | 04-01 | all screens |
| D7 | Glyph horizontal drag uses PointerEventPass.Initial (capture phase) so it fires before longPressDraggableHandle (Main pass) | 04-05 | NodeEditorScreen gesture arbitration |
| D8 | Long-press on text content uses PointerEventPass.Initial on parent Column (not BasicTextField modifier); Initial pass precedes BasicTextField's text-selection handler (Main pass) | 04-05 | NodeEditorScreen long-press context menu |
| D9 | HLC format changed from hex to decimal: 13-digit decimal wall + 5-digit decimal counter (matches backend hlc.ts) | 05-01 | HlcClock, all sync records |
| D10 | Property test for HLC wall length uses range 1_000_000_000_000..9_999_999_999_999 (13-digit epoch window, covers 2001-2286) | 05-01 | HlcClockTest |
| D11 | getUserId() used for all DAO-facing userId queries; getAccessToken() retained only for JWT token usage (Ktor interceptor, LoginViewModel) | 05-02 | DocumentListViewModel, NodeEditorViewModel, SettingsViewModel, SyncWorker |
| D12 | CreateDocumentRequest uses @Serializable + @SerialName for snake_case POST body (parent_id, sort_order) | 05-02 | DocumentListViewModel POST /api/documents |
| D13 | Phase 01 VERIFICATION status is code_confirmed_unverified (not passed) — UAT tests were never formally run; transitive evidence from Phase 02 confirms code works | 06-02 | Audit trail accuracy |
| D14 | Phase 02 VERIFICATION status is code_confirmed_doc_gap — 6/7 UAT passed; test 2 failure was documentation gap (pnpm not on Docker host), not a code bug | 06-02 | Audit trail distinguishes code correctness from doc completeness |
| D15 | FractionalIndex.generateKeyBetween(null, null) returns "aV" not "aP" — DIGITS is 62 chars, midpoint index 31 = 'V' (not 'P' at index 25); tests and code updated | 06-01 | generateNextSortOrder, sort-order tests |
| D16 | syncStatus field removed from NodeEntity/DocumentEntity/BookmarkEntity using Option B (drop Kotlin field, keep SQLite column); Room ignores unmapped columns, no migration needed | 06-01 | Room entity schema, no autoMigrations entry required |

## Blockers / Concerns

- UAT gap: Empty document has no initial node (root node not created on document creation) - diagnosed in 04-04-UAT (note: NodeEditorViewModel now creates root node on empty document load as fallback)
- UAT test 16 (collapse/expand) was previously skipped; now unblocked — use Add Child via context menu to create a child node, then verify
- BookmarkDaoTest.observeAllActive_excludesOtherUsers has a flaky Robolectric/Room Invalidation Tracker race condition (pre-existing, passes on retry)

## Session Continuity

Last session: 2026-03-03T18:40:00Z
Stopped at: Phase 06 complete — 9/9 must-haves verified, v0.4 milestone done
Resume file: None
