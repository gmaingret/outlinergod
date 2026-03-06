---
phase: 18-node-editor-sync
plan: "01"
subsystem: web-client
tags: [hlc, testing, tdd, red-phase, tree-helpers, node-editor]
dependency_graph:
  requires: []
  provides: [hlc-web-port, treeHelpers-tests, NodeEditorPage-tests]
  affects: [18-02, 18-03]
tech_stack:
  added: []
  patterns: [vitest-globals, testing-library-react, fake-timers, vi-stub-global]
key_files:
  created:
    - web/src/sync/hlc.ts
    - web/src/sync/hlc.test.ts
    - web/src/editor/treeHelpers.test.ts
    - web/src/pages/NodeEditorPage.test.tsx
  modified: []
decisions: []
metrics:
  duration_minutes: 3
  completed_date: "2026-03-06"
  tasks_completed: 3
  tasks_total: 3
  files_created: 4
  files_modified: 0
---

# Phase 18 Plan 01: Test Scaffolds and HLC Port Summary

**One-liner:** HLC module ported verbatim from backend with 8 passing tests; treeHelpers and NodeEditorPage test scaffolds written in RED state covering all 9 EDIT/SYNC requirements.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Port HLC module and write HLC tests | c88ea8d | web/src/sync/hlc.ts, web/src/sync/hlc.test.ts |
| 2 | Write failing unit tests for tree helper functions | c42e876 | web/src/editor/treeHelpers.test.ts |
| 3 | Write failing component tests for NodeEditorPage | a92f3f4 | web/src/pages/NodeEditorPage.test.tsx |

## Verification Results

- `pnpm test --run hlc`: 8/8 tests PASS
- `pnpm test --run treeHelpers`: FAIL — "Cannot find module ./treeHelpers" (RED state confirmed)
- `pnpm test --run NodeEditorPage`: FAIL — "Cannot find module ./NodeEditorPage" (RED state confirmed)

## Deviations from Plan

None — plan executed exactly as written.

## Test Coverage Summary

### hlc.test.ts (8 tests — all GREEN)
- hlcGenerate format validation (/^\d{13}-\d{5}-.+$/)
- hlcGenerate strict monotonicity (same deviceId)
- hlcGenerate with different deviceIds
- hlcParse known HLC string into {ms, counter, nodeId}
- hlcParse nodeId containing dashes
- hlcCompare negative/zero/positive
- hlcReceive advances clock past local and remote state
- resetHlcForTesting resets counter to 0

### treeHelpers.test.ts (11 test cases — all RED on missing module)
- buildTree empty input
- buildTree root-only nodes sorted by sort_order lexicographically
- buildTree parent_id links produce nested children
- buildTree orphan nodes become roots
- filterSubtree finds node by id via BFS
- filterSubtree returns null for missing id
- filterSubtree finds root node
- insertSiblingNode inserts after target node
- insertSiblingNode at last position
- indentNode makes target a child of preceding sibling
- indentNode fails gracefully (first child — no preceding sibling)
- outdentNode promotes to sibling of parent
- outdentNode fails gracefully (already at root)
- recomputeSortOrders assigns valid sort_order after insert

### NodeEditorPage.test.tsx (13 test cases — all RED on missing module)
- SYNC-01: GET /api/nodes with Authorization header
- EDIT-01: node content in inputs after load
- EDIT-01: nested DOM structure for child nodes
- EDIT-02: typing updates displayed value
- EDIT-03: Enter creates new input
- EDIT-04: Tab indents (no crash)
- EDIT-05: Shift+Tab outdents (no crash)
- EDIT-06: **markdown** markers visible
- EDIT-07: glyph click calls navigate with ?root=nodeId
- Header: Back link to /
- Header: document title displayed
- SYNC-02: POST /api/sync/push after 2s debounce
- SYNC-02: Saved indicator after successful push

## Self-Check: PASSED
