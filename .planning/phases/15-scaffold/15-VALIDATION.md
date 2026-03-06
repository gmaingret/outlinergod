---
phase: 15
slug: scaffold
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-06
---

# Phase 15 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest ^2 (backend — already installed) |
| **Config file** | `backend/vitest.config.ts` |
| **Quick run command** | `cd backend && pnpm test` |
| **Full suite command** | `cd backend && pnpm test` |
| **Estimated runtime** | ~10 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd backend && pnpm test`
- **After every plan wave:** Run `cd backend && pnpm test` + `docker compose build`
- **Before `/gsd:verify-work`:** Full suite must be green + manual browser check
- **Max feedback latency:** ~10 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 15-01-01 | 01 | 1 | SETUP-01 | smoke | `docker compose build` (exits 0) | ❌ W0 | ⬜ pending |
| 15-01-02 | 01 | 1 | SETUP-02 | manual | navigate to localhost:3000 — shows "OutlinerGod" | ❌ manual | ⬜ pending |
| 15-01-03 | 01 | 1 | SETUP-03 | regression | `cd backend && pnpm test` | ✅ existing | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Manual smoke test checklist: browser at `localhost:3000` (shows "OutlinerGod"), deep link at `localhost:3000/foo/bar` (still shows "OutlinerGod"), `curl localhost:3000/api/` returns JSON

*No new test files needed — existing Vitest backend suite covers SETUP-03; SETUP-01/02 verified by Docker build + browser.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Browser loads React page at / | SETUP-02 | No headless browser in test suite | Run `docker compose up`, navigate to localhost:3000, verify "OutlinerGod" heading visible |
| Deep links return index.html | SETUP-02 | Requires real HTTP server + browser | Navigate to localhost:3000/foo/bar, verify same "OutlinerGod" page (not 404) |
| Production URL loads React page | SETUP-02 | Requires Docker deploy to 192.168.1.50 | After deploy: navigate to https://notes.gregorymaingret.fr, verify page loads |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
