---
phase: 17
slug: document-list
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-06
---

# Phase 17 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest 4.0.18 |
| **Config file** | `web/vitest.config.ts` |
| **Quick run command** | `cd web && pnpm test --run` |
| **Full suite command** | `cd web && pnpm test --run` |
| **Estimated runtime** | ~10 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd web && pnpm test --run`
- **After every plan wave:** Run `cd web && pnpm test --run`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 10 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 17-01-01 | 01 | 0 | DOC-01..04 | unit | `cd web && pnpm test --run DocumentListPage` | ❌ W0 | ⬜ pending |
| 17-01-02 | 01 | 1 | DOC-01 | unit | `cd web && pnpm test --run DocumentListPage` | ✅ W0 | ⬜ pending |
| 17-01-03 | 01 | 1 | DOC-02 | unit | `cd web && pnpm test --run DocumentListPage` | ✅ W0 | ⬜ pending |
| 17-01-04 | 01 | 1 | DOC-03 | unit | `cd web && pnpm test --run DocumentListPage` | ✅ W0 | ⬜ pending |
| 17-01-05 | 01 | 1 | DOC-04 | unit | `cd web && pnpm test --run DocumentListPage` | ✅ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `web/src/pages/DocumentListPage.test.tsx` — stub test file covering DOC-01..04 requirements
- No framework install needed — Vitest + @testing-library/react + @testing-library/user-event already installed and configured

*All Wave 0 stubs must be created before any Wave 1 implementation tasks.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Hover highlight on document row (cursor:pointer + bg change) | DOC-01 | Visual CSS effect not easily asserted in jsdom | Load app in browser, hover over a row, verify highlight appears |
| Context menu positioning (anchored to click position) | DOC-03 | Pixel-accurate positioning depends on real browser layout | Right-click a row, verify menu appears near cursor position |
| Skeleton animation (animate-pulse) | DOC-01 | CSS animation not functional in jsdom | Slow network throttle, verify grey animated rows appear on load |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 10s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
