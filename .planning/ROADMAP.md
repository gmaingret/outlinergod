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

### Phase 09: search-export-bookmarks
**Goal:** Search (FTS5), export, bookmarks screen
**Status:** Planned (was Phase 07 before gap closure phases inserted)

---

## v0.6: integration-polish *(planned)*

### Phase 10: integration-e2e
**Goal:** Integration, polish, end-to-end testing
**Status:** Planned (was Phase 08 before gap closure phases inserted)
