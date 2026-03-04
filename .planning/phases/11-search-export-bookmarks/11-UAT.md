---
status: complete
phase: 11-search-export-bookmarks
source: 11-01-SUMMARY.md, 11-02-SUMMARY.md, 11-03-SUMMARY.md, 11-04-SUMMARY.md
started: 2026-03-04T15:48:35Z
updated: 2026-03-04T19:55:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Open Search Screen
expected: Tapping the search icon (magnifying glass) in the DocumentList TopAppBar navigates to the Search screen with an autofocused text field.
result: pass

### 2. Search Returns Results
expected: Typing a word that exists in a node's content shows matching nodes in a list. Each result shows the content snippet, and document title.
result: pass

### 3. Search No Results
expected: Typing a query with no matches shows a "no results" message (no crash, no empty list with no message).
result: pass

### 4. Navigate from Search Result
expected: Tapping a search result closes the search screen and opens the NodeEditor for that document.
result: pass

### 5. Search Operator is:completed
expected: Typing "is:completed" (or "is:completed someword") filters results to only completed nodes.
result: pass

### 6. Export All Data
expected: Settings screen has an "Export All Data" button. Tapping it shows a circular loading spinner. After a moment, Android ShareSheet opens offering to share a .zip file.
result: pass

### 7. Bookmarks Screen: List or Empty State
expected: Navigating to the Bookmarks screen (via bottom nav or back-nav) shows either a list of saved bookmarks, or "No bookmarks yet" text if none exist. No crash, no blank screen.
result: pass

### 8. Bookmarks Screen: Navigate to Document
expected: Tapping a bookmark in the list navigates to the relevant document (DocumentListScreen or NodeEditor depending on bookmark type).
result: pass

### 9. Bookmarks Screen: Delete Bookmark
expected: Tapping the delete icon on a bookmark removes it from the list immediately.
result: pass

## Summary

total: 9
passed: 9
issues: 0
pending: 0
skipped: 0

## Gaps

- truth: "Bookmarks screen is accessible via a navigation entry point; nodes/documents can be bookmarked from the UI"
  status: failed
  reason: "User reported: There is no bookmark button anywhere and no button to bookmark anything"
  severity: major
  test: 7
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""

- truth: "App launches successfully and DocumentList screen is displayed"
  status: fixed
  reason: "User reported: app crashes on launch"
  severity: blocker
  test: 1
  root_cause: "SQLiteException: no such module: fts5 — FTS5 not available on device SQLite build. MIGRATION_1_2 and FTS_CALLBACK both failed trying to CREATE VIRTUAL TABLE USING fts5."
  artifacts:
    - path: "android/app/src/main/java/com/gmaingret/outlinergod/db/AppDatabase.kt"
      issue: "USING fts5() not available on device; switched to USING fts4()"
    - path: "android/app/src/main/java/com/gmaingret/outlinergod/repository/impl/SearchRepositoryImpl.kt"
      issue: "n._rowid (invalid alias) → n.rowid; bm25() (FTS5-only) → ORDER BY n.updated_at DESC"
  missing:
    - "FTS5 → FTS4 migration in AppDatabase.createFts()"
    - "Fix rowid alias and ORDER BY in SearchRepositoryImpl"
  fix_commit: "71ae0f3"
