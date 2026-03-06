---
phase: 18
slug: node-editor-sync
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-06
---

# Phase 18 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest 4.0.18 |
| **Config file** | `web/vitest.config.ts` |
| **Quick run command** | `cd web && pnpm test --run` |
| **Full suite command** | `cd web && pnpm test --run` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd web && pnpm test --run`
- **After every plan wave:** Run `cd web && pnpm test --run`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 18-??-01 | TBD | 0 | EDIT-01 | unit | `cd web && pnpm test --run treeHelpers` | ❌ Wave 0 | ⬜ pending |
| 18-??-02 | TBD | 0 | EDIT-01 | component | `cd web && pnpm test --run NodeEditorPage` | ❌ Wave 0 | ⬜ pending |
| 18-??-03 | TBD | 0 | EDIT-02 | component | `cd web && pnpm test --run NodeEditorPage` | ❌ Wave 0 | ⬜ pending |
| 18-??-04 | TBD | 0 | EDIT-03 | unit+component | `cd web && pnpm test --run` | ❌ Wave 0 | ⬜ pending |
| 18-??-05 | TBD | 0 | EDIT-04 | unit+component | `cd web && pnpm test --run` | ❌ Wave 0 | ⬜ pending |
| 18-??-06 | TBD | 0 | EDIT-05 | unit+component | `cd web && pnpm test --run` | ❌ Wave 0 | ⬜ pending |
| 18-??-07 | TBD | 0 | EDIT-06 | component | `cd web && pnpm test --run NodeEditorPage` | ❌ Wave 0 | ⬜ pending |
| 18-??-08 | TBD | 0 | EDIT-07 | component | `cd web && pnpm test --run NodeEditorPage` | ❌ Wave 0 | ⬜ pending |
| 18-??-09 | TBD | 0 | SYNC-01 | component | `cd web && pnpm test --run NodeEditorPage` | ❌ Wave 0 | ⬜ pending |
| 18-??-10 | TBD | 0 | SYNC-02 | component (fake timers) | `cd web && pnpm test --run NodeEditorPage` | ❌ Wave 0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `web/src/editor/treeHelpers.test.ts` — covers EDIT-01 (buildTree), EDIT-03 (insertSiblingNode), EDIT-04 (indentNode), EDIT-05 (outdentNode), filterSubtree BFS
- [ ] `web/src/pages/NodeEditorPage.test.tsx` — covers EDIT-01 through EDIT-07, SYNC-01, SYNC-02
- [ ] `web/src/sync/hlc.ts` — HLC port (Wave 0 prerequisite, not a test file)
- [ ] `web/src/sync/hlc.test.ts` — HLC port validation against backend test vectors

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Changes made in browser appear on Android after sync | SYNC-02 | Cross-platform round-trip requires two running devices | 1. Edit node in browser. 2. Trigger sync on Android. 3. Verify content matches. |
| Inline bold/italic renders correctly as user types | EDIT-06 | Visual rendering correctness — jsdom does not render CSS | 1. Type `**hello**` in a node. 2. Verify bold markers are visible inline. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
