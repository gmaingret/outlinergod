# OutlinerGod — Roadmap

## v0.4: android-core *(complete)*

All 8 phases complete. All audit gaps (GAP-A, GAP-B) closed. Ready for milestone audit.

### Phase 01: backend-foundation ✅
**Goal:** Fastify scaffold, SQLite + Drizzle, HLC, JWT, Docker
**Status:** Complete

### Phase 02: backend-api ✅
**Goal:** All CRUD + sync + file + auth routes deployed
**Status:** Complete

### Phase 03: android-setup ✅
**Goal:** Room, Hilt, Ktor, navigation scaffold, HLC + merge logic
**Status:** Complete

### Phase 04: android-core ✅
**Goal:** Full editing experience — auth, document CRUD (local), node editor, DnD, settings, background sync
**Status:** Complete (VERIFICATION.md present, 17/17 must-haves verified)

### Phase 05: sync-integration-fixes ✅
**Goal:** Fix all three critical integration blockers so Android↔backend sync works end-to-end
**Requirements:** GAP-1 (HLC format), GAP-2 (userId = JWT), GAP-3 (camelCase POST body)
**Gap Closure:** Closes all gaps from v0.4 audit — must complete before v0.5 work begins
**Plans:** 2 plans
Plans:
- [x] 05-01-PLAN.md — HLC decimal format + AuthRepository getUserId() infrastructure
- [x] 05-02-PLAN.md — ViewModel consumer migration + test fixture updates
**Status:** Complete (VERIFICATION.md present, 10/10 must-haves verified)

### Phase 06: tech-debt-cleanup ✅
**Goal:** Fix the "should" sort-order divergence and remove dead code, missing docs, verification stubs
**Requirements:** DocumentListScreen fractional index fix, orphaned syncStatus removal, VERIFICATION.md stubs
**Gap Closure:** Closes non-blocking tech debt from v0.4 audit
**Plans:** 2 plans
Plans:
- [x] 06-01-PLAN.md — Fix generateNextSortOrder + remove dead syncStatus entity field
- [x] 06-02-PLAN.md — VERIFICATION.md stubs for phases 01-03 + project README
**Status:** Complete (VERIFICATION.md present, 9/9 must-haves verified, 2026-03-03)

### Phase 07: settings-display-and-techdebt ✅
**Goal:** Fix MainActivity JWT-as-userId bug so settings visually apply; remove orphaned SyncStatus enum; fix WorkManager policy; extract LAST_SYNC_HLC_KEY to shared constant
**Requirements:** GAP-A (MainActivity reads settings with wrong key), tech debt cleanup
**Gap Closure:** Closes GAP-A from v0.4 re-audit + 3 non-blocking tech debt items
**Plans:** 1 plan
Plans:
- [x] 07-01-PLAN.md — MainActivity getUserId() fix + SyncStatus deletion + KEEP→UPDATE + LAST_SYNC_HLC_KEY constant
**Status:** Complete (VERIFICATION.md present, 6/6 must-haves verified, 2026-03-03)

### Phase 08: settings-sync-push ✅
**Goal:** Wire settings into the sync push pipeline so Android-originating settings changes reach the backend
**Requirements:** GAP-B (settings never pushed to server)
**Gap Closure:** Closes GAP-B from v0.4 re-audit — final gap before v0.4 complete
**Plans:** 1 plan
Plans:
- [x] 08-01-PLAN.md — SettingsDao.getPendingSettings + toSettingsSyncRecord mapper + SyncPushPayload.settings + SyncWorker/ViewModel wiring + tests
**Status:** Complete (VERIFICATION.md present, 5/5 must-haves verified, 2026-03-03)

---

## v0.5: android-advanced *(planned)*

### Phase 09: android-ux-polish ✅
**Goal:** Fix density scale (compact = current size; comfortable/cozy add more spacing); add logout button to SettingsScreen; extend drag handle to full node row instead of glyph-only
**Requirements:**
- Density: current font-size/spacing = compact. Comfortable and cozy must add progressively more line height, node padding, and font size so users have room to breathe.
- Logout: SettingsScreen gets a logout button that calls AuthRepository.logout() and navigates to LoginScreen
- Full-row drag: reorderable drag handle covers the entire node row, not only the glyph icon
**Plans:** 1 plan
Plans:
- [x] 09-01-PLAN.md — Density scale fix + logout (AuthRepository, SettingsViewModel, SettingsScreen) + full-row drag handle
**Status:** Complete (VERIFICATION.md present, 4/4 must-haves verified, 2026-03-03)

### Phase 10: android-node-actions ✅
**Goal:** Swipe gestures on node rows (swipe right = mark complete, swipe left = delete with undo snackbar); persistent selection toolbar replaces long-press context menu
**Requirements:**
- Swipe right on a node row → marks node as complete (visual strike-through or check indicator); swipe again to unmark
- Swipe left on a node row → deletes node with snackbar undo (restores within ~5 seconds)
- When a node is selected (tapped/focused), a persistent toolbar appears above the keyboard with: Indent, Outdent, Add Child, Toggle Complete, Delete actions
- Long-press context menu (ModalBottomSheet) is removed entirely; all its actions move to the toolbar
**Plans:** 1 plan
Plans:
- [x] 10-01-PLAN.md — Swipe gestures (SwipeToDismissBox), persistent NodeActionToolbar, snackbar undo, remove ModalBottomSheet
**Status:** Complete (VERIFICATION.md present, 6/6 must-haves verified, 2026-03-04)

### Phase 11: search-export-bookmarks
**Goal:** Client-side FTS5 search with structured operators, full-account ZIP export via ShareSheet, and functional bookmarks screen with CRUD
**Plans:** 4 plans
Plans:
- [ ] 11-01-PLAN.md — FTS5 database migration + SearchRepository + SearchQueryParser
- [ ] 11-02-PLAN.md — Export (FileProvider + ExportRepository + SettingsScreen button)
- [ ] 11-03-PLAN.md — Bookmarks screen (BookmarkListViewModel + BookmarksScreen UI + navigation)
- [ ] 11-04-PLAN.md — Search screen (SearchViewModel + SearchScreen + DocumentList search button)
**Status:** Planned

---

## v0.6: integration-polish *(planned)*

### Phase 12: integration-e2e
**Goal:** Integration, polish, end-to-end testing
**Status:** Planned (was Phase 10)
