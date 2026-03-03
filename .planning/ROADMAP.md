# OutlinerGod — Roadmap

## v0.4: android-core *(gap closure in progress)*

Phases 01–05 are complete. Phase 06 closes non-blocking tech debt before advancing to v0.5.

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

### Phase 06: tech-debt-cleanup
**Goal:** Fix the "should" sort-order divergence and remove dead code, missing docs, verification stubs
**Requirements:** DocumentListScreen fractional index fix, orphaned syncStatus removal, VERIFICATION.md stubs
**Gap Closure:** Closes non-blocking tech debt from v0.4 audit
**Status:** Pending

---

## v0.5: android-advanced *(planned)*

### Phase 07: search-export-bookmarks
**Goal:** Search (FTS5), export, bookmarks screen
**Status:** Planned (was Phase 5 in legacy PLAN.md)

---

## v0.6: integration-polish *(planned)*

### Phase 08: integration-e2e
**Goal:** Integration, polish, end-to-end testing
**Status:** Planned (was Phase 6 in legacy PLAN.md)
