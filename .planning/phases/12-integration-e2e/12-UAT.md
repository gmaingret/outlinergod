---
status: diagnosed
phase: 12-integration-e2e
source: [12-01-SUMMARY.md, 12-02-SUMMARY.md, 12-03-SUMMARY.md, 12-04-SUMMARY.md]
started: 2026-03-04T18:00:00Z
updated: 2026-03-04T18:02:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Zoom In on Glyph Tap
expected: In the node editor, tap the dot glyph (bullet/circle icon) to the left of a parent node that has children. The view should reload scoped to that node's subtree — only that node's direct and indirect children are shown as the list content. The tapped node itself does not appear in the list; you see only its descendants.
result: issue
reported: "fail"
severity: major

### 2. Zoom Out via Back Button
expected: While zoomed into a node's subtree (from test 1), press the system Back button. The view should return to the previous (parent) level, showing the full document or parent context you were viewing before zooming in.
result: skipped
reason: Can't zoom (blocked by test 1 failure)

### 3. Auth Rate Limiting
expected: If you make 11 or more rapid POST requests to the backend's /api/auth/google endpoint within one minute (from the same IP), the 11th request returns HTTP 429 (Too Many Requests). Normal login attempts (1-2 per session) are unaffected.
result: pass

## Summary

total: 3
passed: 1
issues: 1
pending: 0
skipped: 1

## Gaps

- truth: "Tapping the dot glyph on a parent node zooms in to that node's subtree"
  status: failed
  reason: "User reported: fail"
  severity: major
  test: 1
  root_cause: "In NodeEditorScreen.kt, the hasChildren==true branch renders only an IconButton(onClick=onToggleCollapse) with no zoom-in wiring. The clickable dot glyph (onGlyphTap) only exists in the else (leaf node) branch — so zoom-in is never triggerable on nodes that have children, which is the only case it matters."
  artifacts:
    - path: "android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorScreen.kt"
      issue: "hasChildren branch (lines 360-394) missing tappable dot glyph; onGlyphTap only wired in leaf branch (line 420)"
  missing:
    - "Add tappable dot (Canvas circle + clickable onGlyphTap) to the hasChildren branch alongside the existing collapse arrow IconButton"
  debug_session: ".planning/debug/zoom-in-glyph-tap.md"
