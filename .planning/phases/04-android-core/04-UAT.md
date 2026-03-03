---
status: complete
phase: 04-android-core
source: 04-01-SUMMARY.md, 04-02-SUMMARY.md, 04-03-SUMMARY.md, 04-04-SUMMARY.md
started: 2026-03-03T10:00:00Z
updated: 2026-03-03T11:00:00Z
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
result: pass

### 8. Typing in a Node
expected: Tapping a node and typing text updates the node content. The text appears inline in real time. Characters appear in correct left-to-right order.
result: pass

### 9. Enter Key Creates New Node
expected: Pressing Enter in a node's content field creates a new sibling node below it. Focus moves to the new node. Enter does NOT insert a newline.
result: pass

### 10. Backspace on Empty Node Deletes It
expected: Pressing Backspace in an empty node deletes the node and moves focus to the previous node. (Previously broken — ZWS sentinel fix applied in 04-03.)
result: pass

### 11. Inline Markdown Formatting
expected: Typing **bold** shows text with bold markers visible (asterisks may be shown or hidden). Typing _italic_ similarly shows italic-style text. Formatting renders visually distinct from plain text.
result: pass
note: User noted markers should be hidden — marker-hiding is intentionally deferred to a future phase (architecture: Phase 1 = visible markers)

### 12. Drag-and-Drop Reorder
expected: Long-pressing a node activates drag mode. Dragging up/down reorders nodes in the list. Releasing drops the node in the new position. The reorder persists after releasing.
result: pass

### 13. Indent / Outdent (Horizontal Drag)
expected: Long-pressing the glyph (bullet/circle) and dragging right >40dp indents the node (makes it a child of the node above). Dragging left >40dp outdents it. The tree nesting is visually reflected by indentation. (Previously broken — pointerInput gesture wired in 04-03.)
result: issue
reported: "fail, no horizontal drag"
severity: major

### 14. Node Context Menu
expected: Long-pressing the text area of a node (not the glyph) shows a context menu bottom sheet with options like "Add Child". Tapping "Add Child" adds a nested child node. Long-pressing the glyph still activates drag-to-reorder. (Previously broken — gesture zone separation applied in 04-03.)
result: issue
reported: "fail, no context menu"
severity: major

### 15. Node Note Editor
expected: A node with a note field can be expanded to show the note. Tapping the note area allows typing multi-line text (Enter = newline in notes, not a new node). A node with a non-blank note shows the note area by default.
result: pass

### 16. Collapse and Expand Nodes
expected: A node with children shows a directional arrow. Tapping the arrow collapses the children (hides them). Tapping again expands them. Tapping the node glyph (bullet/circle) zooms into that subtree rather than collapsing. (Previously skipped — now testable after context menu fix.)
result: skipped
reason: context menu still broken (test 14) — can't create child nodes to test collapse/expand

### 17. Settings Screen Controls Work
expected: Navigating to Settings shows Theme chips (System/Light/Dark), Density chips (Normal/Compact), and toggles for Guide Lines and Backlink Badge. Selecting Dark theme makes the app go dark. Selecting Light makes it light. Density chips visibly affect spacing. (Previously broken — OutlinerGodTheme now wired to SettingsDao in 04-04.)
result: pass

### 18. Auth Persistence After Restart
expected: After signing in and creating documents, killing the app and relaunching it goes directly to the Document List screen (no login required). All documents and nodes are still present. (Previously broken — CheckingSession state + session check on startup fixed in 04-02.)
result: pass

## Summary

total: 18
passed: 15
issues: 2
issues: 0
pending: 0
skipped: 1

## Gaps

- truth: "Dragging the glyph horizontally >40dp indents or outdents the node"
  status: failed
  reason: "User reported: fail, no horizontal drag"
  severity: major
  test: 13
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""

- truth: "Long-pressing the text area of a node shows a context menu bottom sheet"
  status: failed
  reason: "User reported: fail, no context menu"
  severity: major
  test: 14
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""
