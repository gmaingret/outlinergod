# Phase 05 Plan 02: ViewModel Consumer Migration + Test Fixture Updates Summary

## Status
Complete

## One-liner
getUserId() wired into all 3 ViewModels and SyncWorker replacing getAccessToken() misuse; CreateDocumentRequest adds snake_case POST body; all test fixtures migrated to decimal HLC format.

## Deliverables
- DocumentListViewModel: getUserId() for loadDocuments, triggerSync, and createDocument Room queries
- NodeEditorViewModel: getUserId() for loadDocument and triggerSync Room queries
- SettingsViewModel: getUserId() for loadSettings Room query
- SyncWorker: getUserId() for PUSH phase userId in background sync
- CreateDocumentRequest.kt: new @Serializable data class with @SerialName snake_case for parent_id and sort_order
- All test setUp methods: getUserId() mock added alongside getAccessToken() where needed
- All testHlcValue fixtures: hex format replaced with decimal (1636300202430-00000-device-1)
- HLC regex assertions in tests updated from `^[0-9a-f]{16}-[0-9a-f]{4}-.*` to `^[0-9]{13}-[0-9]{5}-.*`
- Full test suite: 222 tests, 221 passing (1 pre-existing flaky BookmarkDaoTest race condition)

## Commits
- 17b38c4: feat(05-02): replace getAccessToken() with getUserId() in ViewModels and SyncWorker, fix POST body
- 16f32a0: test(05-02): update all test fixtures -- getUserId() mocks and decimal HLC strings

## Files Created
- android/app/src/main/java/com/gmaingret/outlinergod/network/model/CreateDocumentRequest.kt

## Files Modified
- android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListViewModel.kt
- android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModel.kt
- android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/settings/SettingsViewModel.kt
- android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncWorker.kt
- android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListViewModelTest.kt
- android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListSyncTest.kt
- android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModelTest.kt
- android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorSyncTest.kt
- android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorPersistenceTest.kt
- android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeContextMenuTest.kt
- android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NoteEditorTest.kt
- android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/DragAndDropTest.kt
- android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/settings/SettingsViewModelTest.kt
- android/app/src/test/java/com/gmaingret/outlinergod/sync/SyncWorkerTest.kt

## Deviations
### Auto-fixed Issues
**1. [Rule 1 - Bug] Removed redundant getAccessToken() mock in NodeEditorViewModelTest**

- Found during: Task 2
- Issue: NodeEditorViewModelTest had a per-test `every { authRepository.getAccessToken() } returns flowOf("user-1")` in `loadDocument empty inserts root node` which was only needed because loadDocument() was calling getAccessToken(). After fixing the production code to use getUserId(), the old mock was no longer needed and getUserId() is already set up in setUp().
- Fix: Removed the test-local getAccessToken() mock; getUserId() mock in setUp() covers the same need.
- Files modified: NodeEditorViewModelTest.kt

**2. [Rule 1 - Bug] HLC regex assertions in DocumentListViewModelTest and SettingsViewModelTest**

- Found during: Task 2
- Issue: Two tests used regex `^[0-9a-f]{16}-[0-9a-f]{4}-.*` to assert HLC format — this matched the old hex format. After migrating to decimal HLC, these regexes would never match the new `1636300202430-00000-device-1` format.
- Fix: Updated regexes to `^[0-9]{13}-[0-9]{5}-.*` to match the new decimal format.
- Files modified: DocumentListViewModelTest.kt, SettingsViewModelTest.kt

## Test Results
222 tests completed, 221 passing.

1 pre-existing flaky failure: `BookmarkDaoTest > observeAllActive_excludesOtherUsers`
- This is a known Robolectric/Room Invalidation Tracker race condition documented in STATE.md (Blockers section).
- Not caused by any changes in this plan.
- Passes on retry (confirmed: second run passes cleanly).

## Decisions Made
| ID | Decision | Impact |
|----|----------|--------|
| - | getUserId() used in all DAO-facing code; getAccessToken() retained only in Ktor auth interceptor and LoginViewModel (correct JWT usage) | All ViewModels, SyncWorker |
| - | CreateDocumentRequest uses @SerialName annotations for snake_case; Ktor serialization plugin handles conversion | DocumentListViewModel POST |

## Duration
Approx 30 minutes (2026-03-03)
Completed: 2026-03-03
