---
phase: 14-user-feedback
verified: 2026-03-05T15:00:00Z
status: passed
score: 3/3 must-haves verified
re_verification: false
human_verification:
  - test: "Delete node with children, tap toolbar Undo"
    expected: "Node and all its children reappear in place"
    why_human: "Requires running app on device; flow involves Orbit MVI state, Room reactive flow, and Compose recomposition"
  - test: "Drag L1 node between two L2 nodes"
    expected: "Dragged node and its children become L2/L3 and persist after app restart"
    why_human: "Drag gesture interaction and persistence requires device execution"
  - test: "Tap AttachFile in toolbar, pick a file"
    expected: "File picker opens, file uploads, [filename](url) appended to node content, spinner visible during upload"
    why_human: "Requires live backend connection and device execution; upload flow includes network I/O"
---

# Phase 14: User Feedback Verification Report

**Phase Goal:** Fix three user-reported issues from live app usage
**Verified:** 2026-03-05T15:00:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

The phase addressed three distinct user-reported bugs. Each bug maps to a plan (14-01, 14-02, 14-03) and has been verified against the actual codebase.

### Observable Truths

| #  | Truth                                                                          | Status     | Evidence                                                                                              |
|----|--------------------------------------------------------------------------------|------------|-------------------------------------------------------------------------------------------------------|
| 1  | Deleting a node pushes to undo stack so toolbar Undo restores it with subtree  | VERIFIED   | `deleteNode()` pushes to `undoStack` + `undoDeletedIds`; `undo()` calls `nodeDao.restoreNodes()`     |
| 2  | Dragging a node to a different depth level reparents it correctly              | VERIFIED   | `reorderNodes()` has bi-directional `[minAllowedDepth, maxAllowedDepth]` bracket with parentId fix   |
| 3  | Selecting a file via AttachFile uploads it and appends a link to node content  | VERIFIED   | `FileRepositoryImpl`, `uploadAttachment()` intent, `attachmentPickerLauncher` callback all wired     |

**Score:** 3/3 truths verified

---

### Required Artifacts

| Artifact                                                                        | Expected                                 | Status     | Details                                                              |
|---------------------------------------------------------------------------------|------------------------------------------|------------|----------------------------------------------------------------------|
| `db/dao/NodeDao.kt`                                                             | `restoreNodes()` + `getNodesByDocumentIncludingDeleted()` | VERIFIED | Both @Query methods present at lines 49-53                      |
| `ui/screen/nodeeditor/NodeEditorViewModel.kt`                                   | `undoDeletedIds` parallel stack, updated `deleteNode()`, `undo()`, `restoreNode()`, `reorderNodes()`, `uploadAttachment()` | VERIFIED | All confirmed present and substantive                          |
| `ui/screen/nodeeditor/NodeEditorScreen.kt`                                      | `attachmentPickerLauncher` wired, `ShowError` shows snackbar | VERIFIED | Both wired at lines 106-130; `snackbarHostState` hoisted       |
| `repository/FileRepository.kt`                                                  | Interface + `UploadedFile` data class    | VERIFIED   | 9-line file, non-stub, correct signature                             |
| `repository/impl/FileRepositoryImpl.kt`                                         | Ktor multipart upload to `/api/files/upload` | VERIFIED | Full implementation using `submitFormWithBinaryData`, 58 lines  |
| `di/RepositoryModule.kt`                                                        | `FileRepository` → `FileRepositoryImpl` binding | VERIFIED | `provideFileRepository` present at line 41                      |
| `test/.../repository/FileRepositoryImplTest.kt`                                 | 3 tests covering success, 500, 413       | VERIFIED   | All 3 `@Test` methods present with MockEngine                        |
| `test/.../nodeeditor/NodeEditorViewModelTest.kt`                                | 5 delete-undo tests + 5 reorderNodes tests + 2 upload tests | VERIFIED | All 12 test methods confirmed present by grep                  |

---

### Key Link Verification

| From                        | To                              | Via                                       | Status  | Details                                                              |
|-----------------------------|---------------------------------|-------------------------------------------|---------|----------------------------------------------------------------------|
| `deleteNode()`              | `undoStack` + `undoDeletedIds`  | Direct push at lines 750-752              | WIRED   | Both stacks updated before `softDeleteNodes` call                    |
| `pushUndoSnapshot()`        | `undoDeletedIds`                | `undoDeletedIds.addLast(emptyList())`     | WIRED   | Always pushes empty list for non-delete ops — stacks stay aligned    |
| `undo()`                    | `nodeDao.restoreNodes()`        | `if (deletedIds.isNotEmpty())` at line 493-497 | WIRED | Only fires for delete ops; structural ops unaffected             |
| `restoreNode()`             | subtree restore by timestamp    | `getNodesByDocumentIncludingDeleted()` + filter on `deletedAt` at lines 768-771 | WIRED | Full subtree restore confirmed |
| `reorderNodes()`            | bi-directional depth clamp      | `when { blockHeadDepth > max / < min }` at lines 330-356 | WIRED | Both branches update `parentId` and `depth` for entire block |
| `attachmentPickerLauncher`  | `viewModel.uploadAttachment()`  | Callback at lines 109-112 in NodeEditorScreen | WIRED | `nodeId` from `state.focusedNodeId`, `mimeType` from ContentResolver |
| `uploadAttachment()`        | `fileRepository.uploadFile()`   | Intent calls `fileRepository.uploadFile()` at line 516 | WIRED | Result.fold handles success/failure branches                    |
| `ShowError` side effect     | `snackbarHostState.showSnackbar()` | LaunchedEffect collector at lines 123-124 | WIRED | `scope` and `snackbarHostState` hoisted above branch           |
| `FileRepositoryImpl`        | `RepositoryModule`              | `provideFileRepository()` at line 41     | WIRED   | Singleton binding in Hilt module                                     |

---

### Requirements Coverage

No requirement IDs were declared for this phase (user-feedback bug fixes not in formal requirements).

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None detected | — | — | — | — |

No stubs, empty implementations, TODO/FIXME comments, or placeholder returns found in any of the modified or created files.

---

### Human Verification Required

#### 1. Delete undo end-to-end

**Test:** In the running app, create a node with two children. Long-press the parent node and select Delete from the context menu. Tap the Undo button in the toolbar.
**Expected:** The parent node and both children reappear in the tree with correct structure.
**Why human:** Orbit MVI state emission, Room reactive flow collection, and Compose recomposition must all cooperate correctly. The `restoreNodes` DB call returns async; the Flow collector must emit the restored nodes back into `flatNodes`. This chain cannot be verified by static analysis alone.

#### 2. Drag reparenting persistence

**Test:** In the running app, drag an L1 node (one with L1 children) and drop it between two L2 nodes of a different parent. Force-quit and reopen the app.
**Expected:** The dropped node is at L2 depth with correct parentId, its children are at L3, and the structure persists after restart.
**Why human:** Correctness of `recomputeFlatNodes` sort-order assignment and `persistReorderedNodes` writes to Room must be confirmed against actual DB state on disk after app restart.

#### 3. File attachment upload flow

**Test:** On a device with live backend connection, open a document, focus a node, tap the AttachFile toolbar button, pick an image from the device gallery.
**Expected:** Upload spinner appears during upload, node content gains `[filename.jpg](/api/files/...)` suffix, the file is physically present at `/data/uploads/` on the server.
**Why human:** Requires a running backend with a valid JWT, network access, and ContentResolver access to a real file URI — none of which can be verified statically.

---

### Gaps Summary

No gaps. All three issues have complete implementations:

- **14-01 (Delete Undo):** `undoDeletedIds` parallel stack, `deleteNode()` and `pushUndoSnapshot()` alignment, `undo()` restore call, `restoreNode()` subtree expansion via `deletedAt` timestamp, and `NodeDao.restoreNodes()` / `getNodesByDocumentIncludingDeleted()` all verified substantive and wired.

- **14-02 (Drag Reparenting):** `reorderNodes()` bi-directional depth clamp with `minAllowedDepth = aboveDepth` and `maxAllowedDepth = aboveDepth + 1` (or 0 at top), plus correct `parentId` assignment in both "too deep" and "too shallow" branches, verified present and logic-complete.

- **14-03 (File Attachments):** `FileRepository` interface, `FileRepositoryImpl` Ktor multipart upload, `uploadAttachment()` Orbit MVI intent, `attachmentPickerLauncher` callback wired to `viewModel.uploadAttachment()`, `ShowError` side effect showing snackbar, and Hilt binding in `RepositoryModule` all verified present and wired end-to-end.

All 294 `@Test` annotations confirmed across the test suite (313 per summary includes parameterized/Kotest cases not counted by annotation alone). No test stubs found.

---

_Verified: 2026-03-05T15:00:00Z_
_Verifier: Claude (gsd-verifier)_
