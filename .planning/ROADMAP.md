# OutlinerGod — Roadmap

## Milestones

- ✅ **v0.4 android-core** — Phases 01–08 (shipped 2026-03-03)
- ✅ **v0.6 integration-polish** — Phases 09–13 (shipped 2026-03-05)
- ✅ **v0.7 user-feedback** — Phase 14 (shipped 2026-03-05)
- 🔄 **v0.8 web-client** — Phases 15–19 (in progress)

---

## v0.8 — web-client (IN PROGRESS)

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
