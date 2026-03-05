# OutlinerGod — Retrospective

---

## Milestone: v0.4 — android-core

**Shipped:** 2026-03-03
**Phases:** 8 | **Plans:** 14

### What Was Built

- Fastify v5 + Drizzle + SQLite backend with HLC timestamps, JWT/Google auth, Docker deployment
- Full REST API (nodes, documents, bookmarks, settings, files) with HLC sync protocol
- Android scaffold: Room 2.8.4, Hilt, Ktor client, navigation, HLC clock + LWW merge
- Node editor with BasicTextField WYSIWYG, drag-and-drop (reorderable 2.5.1), DnD reparenting
- Background WorkManager sync + Settings (theme/density) persisted via DataStore
- 3 audit gap-closure phases (05, 06, 07, 08) to fix HLC format, userId/JWT separation, settings key bug, and settings sync push

### What Worked

- **Orbit MVI companion-object pattern:** Pure functions in companion objects made ViewModel logic testable on JVM without Android emulator. Every ViewModel tested this way had high confidence.
- **Gap-closure phases:** Treating audit gaps as first-class phases (with PLAN + VERIFICATION) ensured systematic closure. No gap was forgotten.
- **HLC property tests:** Commutativity and idempotency tests caught edge cases that unit tests alone would have missed.

### What Was Inefficient

- **getUserId() vs getAccessToken() confusion:** The JWT token was used as a DAO key before GAP-A discovery, meaning all settings saved to DB were silently unreachable. A clearer naming convention at the start (e.g., `authToken` vs `userId`) would have prevented this.
- **HLC format change mid-project:** The hex→decimal format change in Phase 05 required touching many test fixtures. A format decision in Phase 03 would have saved rework.
- **No v0.5 formal milestone:** Phases 09-11 were labeled v0.5 in ROADMAP but v0.5 was never formally completed. This created a gap where the milestone audit for v0.6 had to cover 5 phases instead of 2.

### Key Lessons

- Decide on auth token naming conventions before writing DAOs: `userId` = UUID from DataStore, `accessToken` = JWT for HTTP headers — never cross-use.
- HLC wire format (hex vs decimal) must be fixed before any phase writes sync records to the DB.
- Always formally complete milestones before starting the next one, even if it's just a single audit+complete cycle.

---

## Milestone: v0.6 — integration-polish (+ v0.5 android-advanced)

**Shipped:** 2026-03-05
**Phases:** 5 (09–13) | **Plans:** 12 | **Duration:** 4 days

### What Was Built

- **Phase 09:** Corrected density scale (compact=tightest), logout button (fail-open), full-row drag handle
- **Phase 10:** SwipeToDismissBox (complete/delete + snackbar undo), NodeActionToolbar above keyboard, ModalBottomSheet removed
- **Phase 11:** FTS4 client-side search with structured operators, ZIP export via ShareSheet, bookmarks screen CRUD
- **Phase 12:** Zoom-in navigation (filterSubtree BFS, back-stack per zoom), userId-scoped sync push fix, backend rate limiting + tombstone purge, SyncWorker integration test with real Room DB
- **Phase 13:** Closed all 5 v0.6 audit gaps: search nodeId wiring, NodeEditorScreen lifecycle sync, bookmark zoom-in, FTS4 docstring, dead import removal

### What Worked

- **Milestone audit before completion:** The audit revealed 2 critical wiring gaps (GAP-V1, GAP-V2) that were not visible in single-phase verification. The pattern of "audit → gap-closure phase → re-audit" is worth keeping.
- **Integration checker subagent:** Cross-phase wiring verification in a dedicated subagent found gaps the individual phase verifiers missed. Especially valuable for navigation side-effects where the chain spans 4+ files.
- **FTS4 over FTS5 decision:** Choosing FTS4 for device SQLite compatibility and Robolectric test support was the right call. The constraint was discovered during implementation (not planning), but the recovery was fast.
- **filterSubtree BFS + back-stack zoom:** The zoom-in implementation using NavBackStackEntry is clean — System Back naturally zooms out with no in-screen state management needed.

### What Was Inefficient

- **SwipeToDismissBox toolbar spec drift:** Phase 10 plan specified 5 toolbar actions; implementation shipped 8. The design change was reasonable but not documented, creating TD-D. Plans should be updated when implementation deviates.
- **12-05 plan without SUMMARY:** Plan 12-05 was implemented (commit d798e35) but no SUMMARY.md was written. The code was verified but the documentation trail is incomplete. SUMMARY.md should be a commit gate, not an afterthought.
- **FTS4 tokenizer discovery:** `porter unicode61` is FTS5-only; `unicode61` is the correct FTS4 tokenizer. This caused a launch crash on device that was caught during UAT. Parser/tokenizer syntax should be validated against SQLite version in the research phase.

### Patterns Established

- **Lifecycle wiring pattern for Compose screens:** `DisposableEffect(lifecycleOwner)` with `LifecycleEventObserver` is the canonical way to connect ON_RESUME/ON_PAUSE to ViewModel methods. `DocumentListScreen` and `NodeEditorScreen` both use this pattern.
- **Shared TestCoroutineScheduler for debounce tests:** `val scheduler = TestCoroutineScheduler(); val testDispatcher = StandardTestDispatcher(scheduler)` — share the scheduler between `runTest(testDispatcher)` and viewModelScope to control virtual time for debounce/delay verification.
- **orbit-test timer cancellation order:** In orbit-test, call `onScreenPaused()` before `advanceUntilIdle()` to cancel the inactivity timer. Otherwise `runTest`'s scheduler advances past the 30s delay, causing a second sync cycle.

### Key Lessons

- Cross-phase integration gaps are the most dangerous: they pass all individual phase verifications but break user-visible flows. Run `audit-milestone` after every 3-4 phases, not just at the end.
- Navigation side-effect chains are fragile: `ViewModel → SideEffect → Screen handler → NavHost callback → AppRoutes` — every link must be verified. A missing `nodeId` field in a data class breaks the whole chain silently.
- Write SUMMARY.md at commit time, not at phase-completion time. If the code is committed, the SUMMARY should exist.

### Cost Observations

- Sessions: ~8 sessions across 4 days
- Model: claude-sonnet-4-6 throughout
- Notable: Gap-closure phases (05, 06, 07, 08, 13) accounted for ~40% of total work — planning and architecture debt is expensive to fix retroactively.

---

## Cross-Milestone Trends

| Trend | v0.4 | v0.6 | Direction |
|-------|------|------|-----------|
| Requirements satisfied | 9/9 | 13/13 | Stable (100%) |
| Gap-closure phases needed | 4 | 1 | Improving |
| Integration gaps found at audit | 2 (GAP-A, GAP-B) | 2 (GAP-V1, GAP-V2) | Stable |
| Test count at completion | 216 | 298 | Growing |
| Days per milestone | ~7 | 4 | Faster |

**Observation:** The number of integration gaps found at audit has held steady at 2 per milestone. This suggests the audit step is calibrated correctly — it consistently finds real issues that individual phase verification misses. The gap-closure pattern is working.
