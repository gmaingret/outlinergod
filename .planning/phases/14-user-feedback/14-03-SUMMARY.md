---
phase: 14
plan: "03"
subsystem: android
tags: [file-attachments, upload, ktor, repository, viewmodel, snackbar]
dependency_graph:
  requires: [FileRepository, NodeEditorViewModel, NodeEditorScreen, RepositoryModule]
  provides: [file-upload-flow, attachment-link-in-content, ShowError-snackbar]
  affects: [NodeEditorViewModel, NodeEditorScreen, RepositoryModule]
tech_stack:
  added: [Ktor submitFormWithBinaryData, FileRepository, FileRepositoryImpl]
  patterns: [Orbit MVI intent for async upload, hoisted snackbar state, Result.fold error handling]
key_files:
  created:
    - android/app/src/main/java/com/gmaingret/outlinergod/repository/FileRepository.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/repository/impl/FileRepositoryImpl.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/repository/FileRepositoryImplTest.kt
  modified:
    - android/app/src/main/java/com/gmaingret/outlinergod/di/RepositoryModule.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModel.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorScreen.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModelTest.kt
    - android/gradle.properties
decisions:
  - "D38: snackbarHostState and scope hoisted to composable level (above when branch) so ShowError side effect can access them regardless of status"
  - "D39: ksp.incremental.intermodule=false added to gradle.properties to fix KSP FileAlreadyExistsException on Windows for debugUnitTest sourceset"
metrics:
  duration_seconds: 1869
  tasks_completed: 5
  files_changed: 14
  completed_date: "2026-03-05"
---

# Phase 14 Plan 03: File Attachments Summary

File attachment upload fully wired end-to-end: `FileRepository` interface + `FileRepositoryImpl` using Ktor multipart, `uploadAttachment()` Orbit MVI intent appending `[filename](url)` to node content, `GetContent` callback wired in `NodeEditorScreen`, and `ShowError` side effect now shows a snackbar.

## What Was Built

### Task A: FileRepository interface + implementation
- `FileRepository` interface with `uploadFile(nodeId, uri, mimeType): Result<UploadedFile>`
- `FileRepositoryImpl` uses Ktor's `submitFormWithBinaryData` to POST to `$baseUrl/api/files/upload`
- Reads URI bytes via `context.contentResolver.openInputStream(uri)`
- Registered in `RepositoryModule` as `@Singleton`

### Task B: GetContent callback wired in NodeEditorScreen
- `attachmentPickerLauncher` callback now calls `viewModel.uploadAttachment(nodeId, uri, mimeType)`
- `LocalContext.current` used to resolve MIME type from ContentResolver
- `state.focusedNodeId` used to identify which node receives the attachment

### Task C: uploadAttachment intent in NodeEditorViewModel
- `fileRepository: FileRepository` added to constructor (Hilt injected)
- `uploadAttachment()` intent: sets `SyncStatus.Syncing`, calls `fileRepository.uploadFile()`, on success appends `[filename](url)` to node content via `onContentChanged()`, sets `SyncStatus.Idle`; on failure sets `SyncStatus.Error` and posts `ShowError` side effect

### Task D: ShowError side effect wired to snackbar
- `scope` and `snackbarHostState` hoisted above the `when (state.status)` branch
- `ShowError` handler now calls `scope.launch { snackbarHostState.showSnackbar(message) }`
- Existing swipe-to-delete snackbar reuses the same hoisted state (no duplication)

### Task E: File display (deferred per plan)
- Per plan spec: display of embedded links is deferred; raw `[filename](url)` text shows in node content

## Tests

**FileRepositoryImplTest.kt** (3 tests):
- `uploadFile_success_returnsUploadedFile` — mock returns 201 with JSON, verifies UploadedFile fields
- `uploadFile_serverError_returnsFailure` — mock returns 500, verifies `Result.isFailure`
- `uploadFile_tooLarge_returnsFailure` — mock returns 413, verifies `Result.isFailure`

**NodeEditorViewModelTest.kt** (2 new tests):
- `uploadAttachment_success_appendsUrlToContent` — mock FileRepository returns success, verifies content updated with attachment link and syncStatus transitions
- `uploadAttachment_failure_postsShowError` — mock returns failure, verifies `ShowError` side effect posted

**Test results: 313/313 passing (0 failures, 0 errors)**

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing] Hoisted snackbarHostState and scope**
- **Found during:** Task D
- **Issue:** `snackbarHostState` and `scope` were declared inside `NodeEditorStatus.Success` branch; the `ShowError` handler in the `LaunchedEffect` collector runs at the composable level and cannot access branch-local variables
- **Fix:** Hoisted both to the top of the composable function
- **Files modified:** NodeEditorScreen.kt

**2. [Rule 3 - Blocking] ksp.incremental.intermodule=false**
- **Found during:** All tasks (build phase)
- **Issue:** KSP generated `FileAlreadyExistsException` for `debugUnitTest` sourceset on Windows, blocking test compilation
- **Fix:** Added `ksp.incremental.intermodule=false` to `android/gradle.properties`
- **Files modified:** gradle.properties

## Decisions Made

| ID | Decision |
|----|----------|
| D38 | `snackbarHostState` and `scope` hoisted to composable level so ShowError side effect can access them from any status branch |
| D39 | `ksp.incremental.intermodule=false` added to fix KSP FileAlreadyExistsException on Windows (debugUnitTest sourceset) |

## Self-Check: PASSED

- FileRepository.kt: FOUND
- FileRepositoryImpl.kt: FOUND
- FileRepositoryImplTest.kt: FOUND
- Commit b143986: FOUND
- 313/313 tests passing
