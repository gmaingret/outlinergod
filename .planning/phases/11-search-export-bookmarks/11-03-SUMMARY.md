---
plan: 11-03
status: complete
completed: 2026-03-04
commits:
  - e5b14cb feat(11-03): BookmarkListViewModel + BookmarksScreen UI + Navigation wiring
  - 53c201c test(11-03): BookmarkListViewModel tests
---

## What Was Built

Functional bookmarks screen replacing the empty Box{} stub: BookmarkListViewModel (Orbit MVI), BookmarksScreen (LazyColumn with loading/empty/success states), AppNavHost wiring.

## Deliverables

### BookmarkListViewModel.kt (`ui/screen/bookmarks/`)
- States: `Loading` (initial), `Success(bookmarks)`, `Empty`
- Side effects: `NavigateToDocument(documentId)`, `NavigateToNodeEditor(documentId)`, `ShowError`
- `loadBookmarks()`: observes `bookmarkDao.observeAllActive(userId)` via Flow
- `onBookmarkTapped()`: routes to NavigateToDocument or NavigateToNodeEditor by targetType
- `softDeleteBookmark()`: HLC timestamp via hlcClock.generate(), calls bookmarkDao.softDeleteBookmark()

### BookmarksScreen.kt (`ui/screen/bookmarks/`)
- Scaffold with TopAppBar ("Bookmarks", back arrow)
- Loading: CircularProgressIndicator; Empty: "No bookmarks yet" text
- Success: LazyColumn of Cards with bookmark label, targetType chip, delete IconButton
- Handles NavigateToDocument/NavigateToNodeEditor side effects via callbacks

### AppNavHost.kt
- BOOKMARKS route replaced stub with new `BookmarksScreen` (new package)
- Navigation callbacks: onNavigateToDocument/onNavigateToNodeEditor → `node_editor/{documentId}`, onNavigateBack → popBackStack

### Old stub deleted
- `ui/screen/BookmarksScreen.kt` removed

## Tests (5 new)
- `BookmarkListViewModelTest`: 5 tests covering loadBookmarks success/empty, onBookmarkTapped document/node navigation, softDeleteBookmark DAO call
- Correctly treats `Loading` as orbit-test baseline (not emitted as state transition)

## Issues / Deviations

None.
