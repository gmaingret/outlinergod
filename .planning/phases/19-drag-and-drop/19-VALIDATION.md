---
phase: 19
slug: drag-and-drop
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-06
---

# Phase 19 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest 4.x |
| **Config file** | `web/vitest.config.ts` |
| **Quick run command** | `cd web && pnpm test --run` |
| **Full suite command** | `cd web && pnpm test --run` |
| **Estimated runtime** | ~10 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd web && pnpm test --run`
- **After every plan wave:** Run `cd web && pnpm test --run`
- **Before `/gsd:verify-work`:** Full suite must be green + manual drag verification in browser
- **Max feedback latency:** ~10 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 19-01-01 | 01 | 1 | EDIT-08 | unit | `cd web && pnpm test --run treeHelpers` | ❌ Wave 0 | ⬜ pending |
| 19-01-02 | 01 | 1 | EDIT-08 | unit | `cd web && pnpm test --run treeHelpers` | ❌ Wave 0 | ⬜ pending |
| 19-02-01 | 02 | 2 | EDIT-08 | render | `cd web && pnpm test --run FlatNodeList` | ❌ Wave 0 | ⬜ pending |
| 19-02-02 | 02 | 2 | EDIT-08 | manual | N/A | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Add `flattenTree` and `reorderNode` tests to `web/src/editor/treeHelpers.test.ts`
- [ ] Create `web/src/editor/FlatNodeList.test.tsx` — render assertions for grip handles (EDIT-08)
- [ ] Install dnd-kit packages: `cd web && pnpm add @dnd-kit/core@6.3.1 @dnd-kit/sortable@10.0.0 @dnd-kit/utilities@3.2.2`

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Drag node up/down reorders tree immediately | EDIT-08 | jsdom `getBoundingClientRect()` returns zeros; dnd-kit PointerSensor never activates in jsdom | Open NodeEditorPage in browser, drag ☰ grip of a node up or down, verify drop indicator and tree order update |
| Horizontal drag changes node depth (reparent) | EDIT-08 | Same jsdom limitation | During a drag, move cursor left/right and verify blue insertion line indentation changes; drop and verify parent_id updated |
| Dragging collapsed parent moves entire subtree | EDIT-08 | Same jsdom limitation | Collapse a parent with children, drag to new position, verify children follow |
| After drag, sync pushes updated sort_order and parent_id | EDIT-08 | Requires real network + server | Trigger a drag, wait for sync (or force sync), verify server DB reflects new parent_id/sort_order |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
