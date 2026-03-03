---
status: complete
phase: 04-android-core
source: PLAN_PHASE4.md
started: 2026-03-03T00:00:00Z
updated: 2026-03-03T01:00:00Z
---

## Current Test
<!-- OVERWRITE each test - shows where we are -->

[testing complete]

## Tests

### 1. Login Screen Renders
expected: Launching the app cold (signed out) shows the Login screen with a Google Sign-In button (or the Credential Manager bottom sheet appears automatically). A loading spinner appears while authentication is in progress.
result: pass

### 2. Google Sign-In Completes
expected: Tapping the Sign-In button opens the Google account picker. Selecting an account authenticates and navigates to the Document List screen. No spinner hang.
result: pass

### 3. Document List Screen
expected: After signing in, the Document List screen shows all documents. Documents are listed by name. A FAB or button exists to create a new document.
result: pass

### 4. Create a Document
expected: Tapping the create button prompts for a document name. Entering a name and confirming adds it to the list immediately.
result: pass

### 5. Rename a Document
expected: Long-pressing or tapping a document menu shows a Rename option. Entering a new name updates it in the list.
result: pass

### 6. Delete a Document
expected: Long-pressing or tapping a document menu shows a Delete option. Confirming removes it from the list.
result: pass

### 7. Node Editor Opens
expected: Tapping a document navigates to the Node Editor screen. An empty document shows at least one empty node ready to type in.
result: issue
reported: "Tapping a document navigates to the Node Editor screen. An empty document shows, but there is no node to type in"
severity: major

### 8. Typing in a Node
expected: Tapping a node and typing text updates the node content. The text appears inline in real time.
result: issue
reported: "can't tap a node, there is no node, and I can't add node, I can't type"
severity: major

### 9. Enter Key Creates New Node
expected: Pressing Enter in a node's content field creates a new sibling node below it. Focus moves to the new node. Enter does NOT insert a newline.
result: skipped
reason: blocked by no-node issue (tests 7-8)

### 10. Backspace on Empty Node Deletes It
expected: Pressing Backspace in an empty node deletes the node and moves focus to the previous node.
result: skipped
reason: blocked by no-node issue (tests 7-8)

### 11. Inline Markdown Formatting
expected: Typing **bold** shows text with bold markers visible (asterisks may be shown or hidden). Typing _italic_ similarly shows italic-style text. Formatting renders visually distinct from plain text.
result: skipped
reason: blocked by no-node issue (tests 7-8)

### 12. Drag-and-Drop Reorder
expected: Long-pressing a node activates drag mode. Dragging up/down reorders nodes in the list. Releasing drops the node in the new position. The reorder persists after releasing.
result: skipped
reason: blocked by no-node issue (tests 7-8)

### 13. Indent / Outdent (Horizontal Drag)
expected: Dragging a node horizontally to the right indents it (makes it a child of the node above). Dragging left outdents it. The tree nesting is visually reflected by indentation.
result: skipped
reason: blocked by no-node issue (tests 7-8)

### 14. Node Context Menu
expected: Long-pressing and releasing (without dragging) shows a context menu bottom sheet with options like "Add Child". Tapping "Add Child" adds a nested child node.
result: skipped
reason: blocked by no-node issue (tests 7-8)

### 15. Node Note Editor
expected: A node with a note field can be expanded to show the note. Tapping the note area allows typing multi-line text (Enter = newline in notes, not a new node). A node with a non-blank note shows the note area by default.
result: skipped
reason: blocked by no-node issue (tests 7-8)

### 16. Collapse and Expand Nodes
expected: A node with children shows a directional arrow. Tapping the arrow collapses the children (hides them). Tapping again expands them. Tapping the node glyph (bullet/circle) zooms into that subtree rather than collapsing.
result: skipped
reason: blocked by no-node issue (tests 7-8)

### 17. Settings Screen
expected: Navigating to Settings shows options for Theme (e.g., System/Light/Dark chips), Density (Normal/Compact chips), and toggles for Guide Lines and Backlink Badge. Changing a setting reflects immediately or after navigation.
result: skipped
reason: user requested early diagnosis

### 18. Background Sync
expected: With the app installed and network connected, sync happens automatically in the background (approximately every 15 minutes via WorkManager). A sync status indicator is visible somewhere in the Node Editor or Document List while sync is in progress.
result: skipped
reason: user requested early diagnosis

## Summary

total: 18
passed: 6
issues: 2
pending: 0
skipped: 10
skipped: 0

## Gaps

- truth: "An empty document shows at least one empty node ready to type in"
  status: failed
  reason: "User reported: Tapping a document navigates to the Node Editor screen. An empty document shows, but there is no node to type in"
  severity: major
  test: 7
  artifacts: []
  missing: []

- truth: "Tapping a node and typing text updates the node content"
  status: failed
  reason: "User reported: can't tap a node, there is no node, and I can't add node, I can't type"
  severity: major
  test: 8
  artifacts: []
  missing: []
