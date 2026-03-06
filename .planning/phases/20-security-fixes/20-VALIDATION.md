---
phase: 20
slug: security-fixes
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-06
---

# Phase 20 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest ^2 |
| **Config file** | `backend/vitest.config.ts` |
| **Quick run command** | `cd backend && pnpm test -- --reporter=verbose files.test.ts` |
| **Full suite command** | `cd backend && pnpm test` |
| **Estimated runtime** | ~10 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd backend && pnpm test -- files.test.ts`
- **After every plan wave:** Run `cd backend && pnpm test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 20-01-01 | 01 | 1 | DELETE ownership | integration | `cd backend && pnpm test -- files.test.ts` | ❌ W0 | ⬜ pending |
| 20-01-02 | 01 | 1 | DELETE owner still 200 | regression | `cd backend && pnpm test -- files.test.ts` | ✅ (needs seedFileRow) | ⬜ pending |
| 20-01-03 | 01 | 1 | DELETE orphan 404 | integration | `cd backend && pnpm test -- files.test.ts` | ❌ W0 | ⬜ pending |
| 20-01-04 | 01 | 1 | JWT_SECRET guard absent | integration | `cd backend && pnpm test -- index.test.ts` | ❌ W0 | ⬜ pending |
| 20-01-05 | 01 | 1 | JWT_SECRET guard short | integration | `cd backend && pnpm test -- index.test.ts` | ❌ W0 | ⬜ pending |
| 20-01-06 | 01 | 1 | purgeStaleRefreshTokens deletes old | unit | `cd backend && pnpm test -- refresh-token-purge.test.ts` | ❌ W0 | ⬜ pending |
| 20-01-07 | 01 | 1 | purgeStaleRefreshTokens keeps recent | unit | `cd backend && pnpm test -- refresh-token-purge.test.ts` | ❌ W0 | ⬜ pending |
| 20-01-08 | 01 | 1 | Full regression | regression | `cd backend && pnpm test` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `backend/src/routes/files.test.ts` — add `it('returns 403 when user B deletes user A file')` and `it('returns 404 for orphaned file')`; update `returns200_deletedTrue_onSuccess` to seed DB row via `seedFileRow()`
- [ ] `backend/src/routes/files.test.ts` — cross-user ownership test uses existing `seedUser()` and `seedFileRow()` helpers
- [ ] `backend/src/refresh-token-purge.test.ts` — unit tests for `purgeStaleRefreshTokens()` (delete old, keep recent)
- [ ] Optional: `backend/src/index.test.ts` — JWT_SECRET guard tests (may require mocking `process.exit`)

*Wave 0 must complete before Wave 1 tasks are marked done.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| setInterval fires after 24 hours | SC-3 | Not practical in unit tests | Review code diff — confirm `setInterval(purgeStaleRefreshTokens, 24 * 60 * 60 * 1000)` in startServer() |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
