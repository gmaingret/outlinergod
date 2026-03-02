---
status: diagnosed
phase: 04-android-core
source: PLAN_PHASE4.md
started: 2026-03-02T00:00:00Z
updated: 2026-03-02T00:01:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Login Screen Renders
expected: Launching the app cold (signed out) shows the Login screen with a Google Sign-In button (or the Credential Manager bottom sheet appears automatically). A loading spinner appears while authentication is in progress.
result: pass

### 2. Document List Screen
expected: After signing in, the Document List screen shows all documents. Documents are listed by name. A FAB or button exists to create a new document. Tapping a document opens the Node Editor.
result: issue
reported: "no because sign in fails. I start the app, click on sign in with google, select my account, then I'm stuck on the sign in animation"
severity: blocker

### 3. Create a Document
expected: Tapping the create button prompts for a document name. Entering a name and confirming adds it to the list immediately.
result: skipped
reason: blocked by sign-in failure (test 2)

### 4. Rename a Document
expected: Long-pressing or tapping a document menu shows a Rename option. Entering a new name updates it in the list.
result: skipped
reason: blocked by sign-in failure (test 2)

### 5. Delete a Document
expected: Long-pressing or tapping a document menu shows a Delete option. Confirming removes it from the list.
result: skipped
reason: blocked by sign-in failure (test 2)

### 6. Node Editor Opens
expected: Tapping a document navigates to the Node Editor screen. An empty document shows at least one empty node ready to type in.
result: skipped
reason: blocked by sign-in failure (test 2)

### 7. Typing in a Node
expected: Tapping a node and typing text updates the node content. The text appears inline in real time.
result: skipped
reason: blocked by sign-in failure (test 2)

### 8. Enter Key Creates New Node
expected: Pressing Enter in a node's content field creates a new sibling node below it. Focus moves to the new node. Enter does NOT insert a newline.
result: skipped
reason: blocked by sign-in failure (test 2)

### 9. Backspace on Empty Node Deletes It
expected: Pressing Backspace in an empty node deletes the node and moves focus to the previous node.
result: skipped
reason: blocked by sign-in failure (test 2)

### 10. Inline Markdown Formatting
expected: Typing **bold** shows text with bold markers visible (asterisks may be shown or hidden). Typing _italic_ similarly shows italic-style text. Formatting renders visually distinct from plain text.
result: skipped
reason: blocked by sign-in failure (test 2)

### 11. Drag-and-Drop Reorder
expected: Long-pressing a node activates drag mode. Dragging up/down reorders nodes in the list. Releasing drops the node in the new position. The reorder persists after releasing.
result: skipped
reason: blocked by sign-in failure (test 2)

### 12. Indent / Outdent (Horizontal Drag)
expected: Dragging a node horizontally to the right indents it (makes it a child of the node above). Dragging left outdents it. The tree nesting is visually reflected by indentation.
result: skipped
reason: blocked by sign-in failure (test 2)

### 13. Node Context Menu
expected: Long-pressing and releasing (without dragging) shows a context menu bottom sheet with options like "Add Child" (and possibly other actions). Tapping "Add Child" adds a nested child node.
result: skipped
reason: blocked by sign-in failure (test 2)

### 14. Node Note Editor
expected: A node with a note field can be expanded to show the note. Tapping the note area allows typing multi-line text (Enter = newline in notes, not a new node). A node with a non-blank note shows the note area by default.
result: skipped
reason: blocked by sign-in failure (test 2)

### 15. Collapse and Expand Nodes
expected: A node with children shows a directional arrow. Tapping the arrow collapses the children (hides them). Tapping again expands them. Tapping the node glyph (bullet/circle) zooms into that subtree rather than collapsing.
result: skipped
reason: blocked by sign-in failure (test 2)

### 16. Settings Screen
expected: Navigating to Settings shows options for Theme (e.g., System/Light/Dark chips), Density (Normal/Compact chips), and toggles for Guide Lines and Backlink Badge. Changing a setting reflects immediately or after navigation.
result: skipped
reason: blocked by sign-in failure (test 2)

### 17. Background Sync
expected: With the app installed and network connected, sync happens automatically in the background (approximately every 15 minutes via WorkManager). A sync status indicator is visible somewhere in the Node Editor or Document List while sync is in progress.
result: skipped
reason: blocked by sign-in failure (test 2)

## Summary

total: 17
passed: 1
issues: 1
pending: 0
skipped: 15

## Gaps

- truth: "After selecting Google account, app navigates to Document List screen"
  status: failed
  reason: "User reported: no because sign in fails. I start the app, click on sign in with google, select my account, then I'm stuck on the sign in animation"
  severity: blocker
  test: 2
  root_cause: "BASE_URL is hardcoded to http://10.0.2.2:3000 (emulator loopback) in build.gradle.kts instead of reading from local.properties where the real URL https://notes.gregorymaingret.fr lives. On a real device the POST to /api/auth/google times out after 10-30s, stuck on Loading state with no visible error. Three additional bugs co-exist: (1) tokenProvider/tokenRefresher in NetworkModule are { null } placeholders (all auth'd calls will 401 after login), (2) Retry button in LoginScreen has empty onClick, (3) GetCredentialException handler calls handleGoogleSignIn('') silently resetting to Idle with no error message."
  artifacts:
    - path: "android/app/build.gradle.kts"
      issue: "BASE_URL hardcoded to emulator address instead of reading from local.properties (line 31)"
    - path: "android/app/src/main/java/com/gmaingret/outlinergod/di/NetworkModule.kt"
      issue: "tokenProvider = { null } and tokenRefresher = { null } — no Bearer token ever sent (lines 19-22)"
    - path: "android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/login/LoginScreen.kt"
      issue: "Retry button onClick is empty (lines 109-112); GetCredentialException handler silently resets to Idle instead of showing error"
  missing:
    - "Read BASE_URL from local.properties in build.gradle.kts (same pattern as GOOGLE_CLIENT_ID)"
    - "Wire tokenProvider and tokenRefresher in NetworkModule to AuthRepository (use Lazy<AuthRepository> or TokenStore to avoid circular dep)"
    - "Fix Retry button to re-trigger credential request"
    - "Show error message when GetCredentialException is caught instead of silently resetting"
  debug_session: ".planning/debug/google-signin-hang.md"
