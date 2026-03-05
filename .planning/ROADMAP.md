# OutlinerGod — Roadmap

## Milestones

- ✅ **v0.4 android-core** — Phases 01–08 (shipped 2026-03-03)
- ✅ **v0.6 integration-polish** — Phases 09–13 (shipped 2026-03-05)

---

## Completed Milestones

<details>
<summary>✅ v0.4 android-core — Phases 01–08 (SHIPPED 2026-03-03)</summary>

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
**Gap Closure:** Closes all gaps from v0.4 audit (GAP-1, GAP-2, GAP-3)
**Status:** Complete (VERIFICATION.md present, 10/10 must-haves verified)

### Phase 06: tech-debt-cleanup ✅
**Goal:** Fix sort-order divergence and remove dead code, missing docs, verification stubs
**Gap Closure:** Closes non-blocking tech debt from v0.4 audit
**Status:** Complete (VERIFICATION.md present, 9/9 must-haves verified)

### Phase 07: settings-display-and-techdebt ✅
**Goal:** Fix MainActivity JWT-as-userId bug; remove orphaned SyncStatus enum; fix WorkManager policy; extract LAST_SYNC_HLC_KEY
**Gap Closure:** Closes GAP-A from v0.4 re-audit
**Status:** Complete (VERIFICATION.md present, 6/6 must-haves verified)

### Phase 08: settings-sync-push ✅
**Goal:** Wire settings into the sync push pipeline
**Gap Closure:** Closes GAP-B from v0.4 re-audit — final gap before v0.4 complete
**Status:** Complete (VERIFICATION.md present, 5/5 must-haves verified)

</details>

<details>
<summary>✅ v0.6 integration-polish — Phases 09–13 (SHIPPED 2026-03-05)</summary>

### Phase 09: android-ux-polish ✅
**Goal:** Fix density scale; add logout button to SettingsScreen; extend drag handle to full node row
**Plans:** 1 plan — 09-01-PLAN.md
**Status:** Complete (VERIFICATION.md present, 4/4 must-haves verified, 2026-03-03)

### Phase 10: android-node-actions ✅
**Goal:** Swipe gestures (complete / delete + undo); persistent NodeActionToolbar replaces long-press context menu
**Plans:** 1 plan — 10-01-PLAN.md
**Status:** Complete (VERIFICATION.md present, 6/6 must-haves verified, 2026-03-04)

### Phase 11: search-export-bookmarks ✅
**Goal:** Client-side FTS4 search with structured operators; full-account ZIP export; bookmarks screen CRUD
**Plans:** 4 plans — 11-01 through 11-04-PLAN.md
**Status:** Complete (VERIFICATION.md present, 13/13 must-haves verified, 2026-03-04)

### Phase 12: integration-e2e ✅
**Goal:** Close tech debt (TD-2, TD-4), wire zoom-in navigation, harden backend (rate limiting, tombstone purge), SyncWorker integration test
**Plans:** 5 plans — 12-01 through 12-05-PLAN.md
**Status:** Complete (all 5 plans done, 14/14 must-haves verified, implemented in commit d798e35)

### Phase 13: v0.6-gap-closure ✅
**Goal:** Close 2 critical wiring gaps (search→node navigation, NodeEditorScreen sync-on-resume) and 3 tech debt items
**Gap Closure:** Closes GAP-V1, GAP-V2, TD-A, TD-B, TD-C from v0.6-MILESTONE-AUDIT.md
**Plans:** 1 plan — 13-01-PLAN.md
**Status:** Complete (VERIFICATION.md present, 6/6 must-haves verified, 2026-03-05)

</details>

---

## Next Milestone

Run `/gsd:new-milestone` to plan the next milestone.

Current known tech debt for consideration:
- TD-D: NodeActionToolbar button set (8 buttons) diverged from Phase 10 plan spec (5 buttons) — documentation drift, no code issue
- TD-E: Drag/swipe gesture conflict not runtime-verified on device — needs manual device testing
