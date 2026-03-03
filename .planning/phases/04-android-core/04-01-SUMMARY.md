---
phase: 04-android-core
plan: 01
subsystem: ui
tags: [kotlin, room, orbit-mvi, viewmodel]

requires:
  - phase: 03-android-setup
    provides: Room DAOs, HLC clock, auth repository

provides:
  - DocumentListViewModel.createDocument() inserts root NodeEntity
  - NodeEditorViewModel.loadDocument() isEmpty guard

affects: [android-core, node-editor]

tech-stack:
  added: []
  patterns: [root node creation on document creation]

key-files:
  modified:
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListViewModel.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModel.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListViewModelTest.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorViewModelTest.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorSyncTest.kt

key-decisions:
  - "parentId of root node equals documentId (root node convention)"
  - "sortOrder of root node is 'a0'"
  - "isEmpty guard returns early (return@collect) so Flow re-emits with new node, never emits Success with empty flatNodes"

patterns-established:
  - "createDocument() always creates a companion root NodeEntity"

duration: ~47min
completed: 2026-03-03
---

# Plan 04-01: Fix Empty-Document Bug Summary

**Root NodeEntity created on document creation — freshly created documents always show an editable node**

## Performance

- **Duration:** 47 minutes
- **Tasks:** 2
- **Files modified:** 5 (4 planned + 1 NodeEditorSyncTest deviation fix)

## Accomplishments
- `createDocument()` inserts root NodeEntity (parentId=documentId, sortOrder="a0") immediately after DocumentEntity
- `loadDocument()` has isEmpty guard that inserts root node when document has no nodes and returns early (Flow will re-emit with the new node)
- Tests updated to cover new behaviour
- NodeEditorSyncTest updated to use non-empty node fixtures so sync tests are not affected by the isEmpty guard

## Task Commits

1. **Task 1: Insert root NodeEntity in createDocument()** - `57f0aef` (feat)
2. **Task 2: Add isEmpty() guard in loadDocument() + update tests** - `3f8d4c5` (feat/test)

**Plan metadata:** (docs commit — see below)

## Files Created/Modified
- `DocumentListViewModel.kt` — createDocument() now inserts root NodeEntity with parentId=doc.id, sortOrder="a0" after documentDao.insertDocument(doc)
- `NodeEditorViewModel.kt` — loadDocument() has isEmpty guard: inserts root node and return@collect when node list is empty
- `DocumentListViewModelTest.kt` — new test `createDocument also inserts a root node with parentId equal to documentId`; added NodeEntity import
- `NodeEditorViewModelTest.kt` — replaced `loadDocument empty setsSuccessStatus withEmptyFlatNodes` with `loadDocument empty inserts root node and does not emit empty success`
- `NodeEditorSyncTest.kt` — updated 5 tests that used `flowOf(emptyList())` to use `fakeNode` fixtures (deviation fix to keep sync tests green)

## Decisions Made
- Root node parentId equals documentId (existing convention from architecture)
- Safety net in NodeEditorViewModel handles documents arriving via sync without root nodes
- Sync tests updated to use real node fixtures rather than empty lists — more representative of actual runtime state

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] NodeEditorSyncTest tests used flowOf(emptyList()) and expected empty Success state**

- **Found during:** Task 2 test run
- **Issue:** 5 tests in `NodeEditorSyncTest` used `flowOf(emptyList())` as a convenience fixture and expected `NodeEditorUiState(status=Success)` with empty flatNodes. After adding the isEmpty guard, loadDocument() no longer emits Success for empty node lists — it inserts a node and returns early. This caused 5 sync tests to fail.
- **Fix:** Updated all 5 affected tests to use `fakeNode(id="n1", content="Hello")` fixture, matching the `onContentChanged resetsInactivityTimer` test which already used real nodes. Updated expected state assertions to include the expected flatNodes list.
- **Files modified:** `NodeEditorSyncTest.kt`
- **Commit:** `3f8d4c5`

## Issues Encountered
- `BookmarkDaoTest > observeAllActive_excludesOtherUsers` failed intermittently (flaky) due to Robolectric in-memory Room test ordering. Test passes in isolation and in clean runs. Pre-existing issue unrelated to this plan's changes; confirmed by verifying the test file was not modified in this plan.

## Next Phase Readiness
UAT tests 7 and 8 closed. Empty document bug fixed. Newly created documents always have an initial root node to type in.

---
*Phase: 04-android-core*
*Completed: 2026-03-03*
