# OutlinerGod — Roadmap

## Milestones

- ✅ **v0.4 android-core** — Phases 01–08 (shipped 2026-03-03)
- ✅ **v0.6 integration-polish** — Phases 09–13 (shipped 2026-03-05)
- ✅ **v0.7 user-feedback** — Phase 14 (shipped 2026-03-05)
- ✅ **v0.8 web-client** — Phases 15–19 (shipped 2026-03-06)
- 🔜 **v0.9 quality-hardening** — Phases 20–26 (not started)

---

## v0.9 — quality-hardening (NOT STARTED)

**Goal:** Eliminate all security bugs, critical tech debt, and performance bottlenecks identified in the codebase audit. No new features — this milestone makes the existing codebase production-safe and maintainable.

### Phases

- [x] **Phase 20: Security fixes** — File DELETE ownership, JWT_SECRET startup guard, refresh token purge (completed 2026-03-06)
- [ ] **Phase 21: Sync architecture** — Extract SyncOrchestrator, eliminate 3-copy sync logic across NodeEditorViewModel / DocumentListViewModel / SyncWorker
- [ ] **Phase 22: Android performance** — `getNodeByIdSync` on keystroke, batch DnD `persistReorderedNodes`, fix `recomputeFlatNodes` 62-sibling overflow, wrap SyncLogger in BuildConfig.DEBUG
- [ ] **Phase 23: Data model & structure** — Proper attachment columns (not pipe-delimited string), move FractionalIndex to util/, delete orphan `backend/src/hlc.ts`, enable Room `exportSchema = true`, unify UndoSnapshot into single sealed class
- [ ] **Phase 24: Test coverage gaps** — `DELETE /files` ownership test, NodeEditorViewModel debounce E2E test, FractionalIndex large-n edge cases, SyncWorker real conflict-resolution integration test, search post-filter multi-word test
- [ ] **Phase 25: Backend maintenance** — Move SettingsSyncRecord/mergeSettings to merge.ts with tests, add periodic tombstone purge (every 24h), add `max_hlc` index on sync tables, align Node.js dev/Docker versions
- [ ] **Phase 26: Android UX redesign** — Breadcrumb home icon + font reduction, last-opened document on launch, remove back arrow from node screen, context-sensitive bottom toolbar (editing vs. browsing modes), hamburger drawer (doc list + add + settings), glyph top-aligned

### Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 20. Security fixes | 1/1 | Complete    | 2026-03-06 |
| 21. Sync architecture | 0/2 | Planned     | — |
| 22. Android performance | 0/? | Not started | — |
| 23. Data model & structure | 0/? | Not started | — |
| 24. Test coverage gaps | 0/? | Not started | — |
| 25. Backend maintenance | 0/? | Not started | — |
| 26. Android UX redesign | 0/? | Not started | — |

### Phase Details

#### Phase 20: Security fixes
**Goal:** Close all security bugs identified in the codebase audit — no authenticated user can delete another user's files, JWT_SECRET is validated at startup, and stale refresh tokens are periodically purged.
**Depends on:** Nothing
**Plans:** 1/1 plans complete
Plans:
- [ ] 20-01-PLAN.md — DELETE ownership check, JWT_SECRET guard, refresh token purge
**Requirements:** (security audit)
**Success Criteria:**
  1. `DELETE /api/files/:filename` returns 403 if the file belongs to a different user
  2. Server refuses to start if `JWT_SECRET` is absent or shorter than 32 characters
  3. A background timer purges refresh tokens older than 90 days every 24 hours
  4. All existing backend tests still pass; new ownership test added to `files.test.ts`

#### Phase 21: Sync architecture
**Goal:** The full pull-then-push sync cycle lives in exactly one place (`SyncOrchestrator`). ViewModels and SyncWorker delegate to it — no copy-paste sync logic.
**Depends on:** Nothing
**Requirements:** (tech debt audit)
**Success Criteria:**
  1. A `SyncOrchestrator` class (or extended `SyncRepository`) encapsulates pull/push/upsert/conflict-resolution/HLC-update
  2. `NodeEditorViewModel.triggerSync()`, `DocumentListViewModel.triggerSync()`, and `SyncWorker.doWork()` all call the orchestrator — no direct pull/push logic in any of them
  3. All 222+ Android tests still pass
  4. A sync bug fix or new entity type requires editing exactly one file

**Plans:** 2 plans
Plans:
- [ ] 21-01-PLAN.md — SyncOrchestrator interface + SyncOrchestratorImpl + unit and integration tests (TDD)
- [ ] 21-02-PLAN.md — Gut SyncWorker + DocumentListViewModel + NodeEditorViewModel + update caller tests


#### Phase 22: Android performance
**Goal:** Eliminate the four identified Android performance bottlenecks: per-keystroke full-document fetch, unbatched DnD updates, 62-sibling sort-order overflow, and SyncLogger always active in release builds.
**Depends on:** Nothing
**Requirements:** (performance audit)
**Success Criteria:**
  1. `persistContentChange` calls `NodeDao.getNodeByIdSync(nodeId)` — not `getNodesByDocumentSync(documentId)`
  2. `persistReorderedNodes` wraps all updates in a single Room transaction (or batch `@Query`)
  3. A document with 100+ siblings at the same depth has stable, unique sort orders after any DnD operation
  4. `SyncLogger.log()` is a no-op in release builds (`BuildConfig.DEBUG` guard)
  5. All existing tests pass; a new test verifies sort-order uniqueness for n=100 siblings

#### Phase 23: Data model & structure
**Goal:** Attachment metadata lives in proper DB columns, FractionalIndex is in a production package, the orphan backend HLC file is deleted, Room schema export is enabled, and the undo stack uses a single cohesive type.
**Depends on:** Nothing
**Requirements:** (tech debt audit)
**Success Criteria:**
  1. `NodeEntity` has `attachment_url: String?` and `attachment_mime: String?` columns; no node content starts with `"ATTACH|"`
  2. `FractionalIndex.kt` lives at `android/.../util/FractionalIndex.kt` — no production import from `prototype.*`
  3. `backend/src/hlc.ts` (old hex-format file) is deleted; only `backend/src/hlc/hlc.ts` remains
  4. `AppDatabase` sets `exportSchema = true`; schema JSON files are committed under `android/app/schemas/`
  5. Undo state uses a single `UndoSnapshot` data class; `undoStack` and `undoDeletedIds` are replaced by one `ArrayDeque<UndoSnapshot>`
  6. All existing tests pass; Room migration test covers the new attachment columns

#### Phase 24: Test coverage gaps
**Goal:** Close the five highest-priority test gaps identified in the audit.
**Depends on:** Phase 21 (SyncOrchestrator exists before integration-testing it), Phase 23 (attachment columns before testing them)
**Requirements:** (test audit)
**Success Criteria:**
  1. `files.test.ts` has a test: authenticated user B cannot delete user A's file (expects 403)
  2. `NodeEditorViewModelTest` has an E2E test: rapid typing → focus-lost → sync-trigger does not double-write and correctly flushes to Room
  3. `FractionalIndexTest` has edge-case tests for n ≥ 62 siblings and closely-bounded key pairs
  4. `SyncWorkerIntegrationTest` exercises conflict resolution against a real in-memory Room DB (no mock SyncRepository)
  5. `SearchRepositoryImpl` has tests for multi-word FTS queries and `in:note`/`in:title` with prefix `*` stripping

#### Phase 26: Android UX redesign
**Goal:** Overhaul Android navigation and toolbar chrome so the app feels more focused: no redundant navigation affordances, a persistent browsing toolbar when not editing, a drawer for document management, and consistent glyph alignment.
**Depends on:** Nothing (pure UI changes, no data model impact)
**Requirements:** (UX audit)
**Success Criteria:**
  1. **Breadcrumb** — Home icon (house) at the leftmost position navigates to the root document list; document name removed from breadcrumb trail; breadcrumb text is visually smaller than the current size
  2. **Last-opened document** — When the app launches cold (or returns from background with no back stack), it opens the most recently viewed document directly instead of the document list; the home icon is the only way back to the list
  3. **Back arrow** — The system/top-app-bar back arrow is hidden on the NodeEditorScreen; navigation back happens only via the breadcrumb home icon or Android system gesture
  4. **Context-sensitive bottom toolbar** — When no node has focus (browse mode), the NodeActionToolbar is hidden and replaced by a global bottom bar with three buttons: hamburger menu (left), search (centre-left), bookmark (centre-right); when a node gains focus (edit mode), the global bar disappears and the NodeActionToolbar appears above the keyboard
  5. **Hamburger drawer** — Tapping the hamburger icon slides open a modal drawer containing: the full document list (tapping navigates), an "Add document" button (creates + navigates), and a "Settings" button (navigates to SettingsScreen); the drawer closes on any navigation action
  6. **Glyph alignment** — The bullet/zoom glyph in each node row is `Alignment.Top` aligned with the first line of the node content, regardless of how many lines the content wraps to
  7. All existing 298+ Android tests pass; new ViewModel tests cover last-opened document persistence and toolbar mode switching

#### Phase 25: Backend maintenance
**Goal:** `SettingsSyncRecord` and `mergeSettings` live in `merge.ts` with full test coverage, tombstones are purged periodically (not just at startup), the sync pull endpoint has an indexed `max_hlc` column, and dev/Docker Node.js versions are aligned.
**Depends on:** Nothing
**Requirements:** (backend audit)
**Success Criteria:**
  1. `SettingsSyncRecord` and `mergeSettings()` are in `backend/src/merge.ts`; `sync.ts` imports them; merge tests cover settings
  2. `purgeTombstones()` is called at startup AND on a 24-hour `setInterval`
  3. `nodes`, `documents`, and `bookmarks` tables have an indexed `max_hlc TEXT GENERATED ALWAYS AS (MAX(...))` column used by the sync pull query
  4. `backend/Dockerfile` and `docker-compose.yml` use `node:24-alpine`; no version mismatch with dev host
  5. All 267+ backend tests pass

---

## v0.8 — web-client (SHIPPED 2026-03-06) ✅

**Goal:** Deliver a full-featured web outliner at https://notes.gregorymaingret.fr — auth, document CRUD, node editor with WYSIWYG, zoom navigation, sync, and DnD reordering.

### Phases

- [x] **Phase 15: Scaffold** — Web client builds and is served by the existing Fastify container (completed 2026-03-06)
- [x] **Phase 16: Auth** — User can sign in with Google and stay logged in across browser sessions (completed 2026-03-06)
- [x] **Phase 17: Document List** — User can create, view, rename, and delete documents (completed 2026-03-06)
- [x] **Phase 18: Node Editor + Sync** — User can read and edit nodes with formatting, keyboard shortcuts, zoom navigation, and data sync (completed 2026-03-06)
- [x] **Phase 19: Drag-and-Drop** — User can drag nodes to reorder and reparent them (completed 2026-03-06)

### Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 15. Scaffold | 2/2 | Complete    | 2026-03-06 |
| 16. Auth | 3/3 | Complete    | 2026-03-06 |
| 17. Document List | 2/2 | Complete    | 2026-03-06 |
| 18. Node Editor + Sync | 3/3 | Complete    | 2026-03-06 |
| 19. Drag-and-Drop | 2/2 | Complete    | 2026-03-06 |

### Phase Details

#### Phase 15: Scaffold
**Goal:** The web client builds successfully and is served by the existing Fastify container; /api/* routes are unaffected.
**Depends on:** Nothing (builds on existing backend)
**Requirements:** SETUP-01, SETUP-02, SETUP-03
**Success Criteria** (what must be TRUE when this phase completes):
  1. Navigating to https://notes.gregorymaingret.fr in a browser loads a React page (not a 404 or blank screen)
  2. The Fastify container serves the React SPA for any non-/api path (deep links return index.html, not 404)
  3. The Android app's sync calls to /api/* continue to return correct responses (no regression)
  4. A developer can run `pnpm dev` in `web/` and edit React code with hot reload at localhost:5173, with /api/* proxied to localhost:3000
**Plans:** 2/2 plans complete
Plans:
- [ ] 15-01-PLAN.md — Scaffold web/ + Fastify SPA serving
- [ ] 15-02-PLAN.md — Docker three-stage build + compose update

#### Phase 16: Auth
**Goal:** Users can sign in with Google in the browser using the same account as on Android, and stay signed in across page refreshes.
**Depends on:** Phase 15
**Requirements:** AUTH-01, AUTH-02, AUTH-03
**Success Criteria** (what must be TRUE when this phase completes):
  1. User can click "Sign in with Google" and complete the OAuth flow to reach the document list
  2. After refreshing the browser, the user is still on the document list (not redirected to login)
  3. Navigating directly to /editor/:id without being signed in redirects to the login page
  4. The sign-in uses the same Google account that owns the user's Android data (data is visible after auth)
**Plans:** 3/3 plans complete
Plans:
- [ ] 16-01-PLAN.md — Vitest infrastructure + test stubs (Wave 0)
- [ ] 16-02-PLAN.md — AuthContext + backend dual-audience update (Wave 1)
- [ ] 16-03-PLAN.md — ProtectedRoute + LoginPage + App.tsx + Docker plumbing (Wave 2)

#### Phase 17: Document List
**Goal:** Users can see all their documents and perform full CRUD from the browser.
**Depends on:** Phase 16
**Requirements:** DOC-01, DOC-02, DOC-03, DOC-04
**Success Criteria** (what must be TRUE when this phase completes):
  1. After signing in, the user sees a list of all their documents (same documents visible on Android)
  2. User can create a new document and see it appear in the list immediately
  3. User can rename a document and see the new name reflected in the list
  4. User can delete a document and it is removed from the list (and no longer visible on Android after sync)
**Plans:** 2/2 plans complete
Plans:
- [ ] 17-01-PLAN.md — DocumentListPage test scaffold (TDD Wave 0)
- [ ] 17-02-PLAN.md — DocumentListPage implementation + App.tsx wiring (Wave 1)

#### Phase 18: Node Editor + Sync
**Goal:** Users can open a document, read and edit all its nodes with inline formatting and keyboard shortcuts, zoom into subtrees, and have changes sync to the server.
**Depends on:** Phase 17
**Requirements:** EDIT-01, EDIT-02, EDIT-03, EDIT-04, EDIT-05, EDIT-06, EDIT-07, SYNC-01, SYNC-02
**Success Criteria** (what must be TRUE when this phase completes):
  1. Opening a document from the list displays all its nodes in the correct tree hierarchy, with the latest server data
  2. Typing in a node updates its content; pressing Enter creates a new sibling node below the cursor
  3. Pressing Tab indents the current node (it becomes a child of the node above); pressing Shift+Tab outdents it one level
  4. Wrapping text in **bold** or *italic* markdown renders inline formatting as the user types
  5. Clicking a bullet glyph zooms the view into that node's subtree; pressing Back returns to the parent view
  6. Changes made in the browser appear on the Android app after the Android app syncs (and vice versa)
**Plans:** 3/3 plans complete
Plans:
- [ ] 18-01-PLAN.md — Wave 0: HLC port + test scaffolds (treeHelpers + NodeEditorPage)
- [ ] 18-02-PLAN.md — Tree helper pure functions (buildTree, filterSubtree, indent/outdent)
- [ ] 18-03-PLAN.md — NodeEditorPage component + App.tsx wiring

#### Phase 19: Drag-and-Drop
**Goal:** Users can drag any node to a new position in the tree, including reparenting to a different depth level.
**Depends on:** Phase 18
**Requirements:** EDIT-08
**Success Criteria** (what must be TRUE when this phase completes):
  1. A user can drag a node up or down the list and drop it in a new position; the tree order updates immediately
  2. Dragging a node horizontally while repositioning changes its depth (reparents it as a child or sibling of the surrounding context)
  3. Dragging a node also moves its entire subtree — children follow the parent
  4. After a drag, the new order syncs to the server and is visible on Android after sync
**Plans:** 2/2 plans complete
Plans:
- [ ] 19-01-PLAN.md — Install dnd-kit + flattenTree() + reorderNode() pure functions
- [ ] 19-02-PLAN.md — FlatNodeList component + NodeEditorPage integration

---

## v0.7 — user-feedback (SHIPPED 2026-03-05) ✅

### Phase 14: user-feedback ✅
**Goal:** Fix three user-reported issues from live app usage
**Plans:** 3/3 plans complete

#### 14-01: Delete undo via toolbar ✅
- `deleteNode()` pushes to undoStack so toolbar Undo restores deleted nodes
- Fix `restoreNode()` to restore full subtree (parent + descendants)
- Add `NodeDao.restoreNodes()` batch un-delete

#### 14-02: Drag reparenting fix ✅
- Dragging a node between nodes of a different depth now adopts that depth
- Block head's parentId updated to match the surrounding context (sibling of nodeAbove)
- Fixes: L1 dragged between L2 nodes becomes L2 with correct parentId; children shift accordingly

#### 14-03: File attachments ✅
- Implement the empty `GetContent()` launcher callback
- New `FileRepository` uploads to `POST /api/files/upload` via Ktor multipart
- On success, appends `[filename](url)` to node content
- Wire `ShowError` side effect to snackbar
- 313/313 tests passing

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

Run `/gsd:plan-phase 15` to begin planning Phase 15: Scaffold.

Current known tech debt for consideration:
- TD-D: NodeActionToolbar button set (8 buttons) diverged from Phase 10 plan spec (5 buttons) — documentation drift, no code issue
- TD-E: Drag/swipe gesture conflict not runtime-verified on device — needs manual device testing
