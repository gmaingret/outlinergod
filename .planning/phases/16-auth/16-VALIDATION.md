---
phase: 16
slug: auth
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-06
---

# Phase 16 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest |
| **Config file** | `web/vitest.config.ts` — does NOT exist yet (Wave 0 installs) |
| **Quick run command** | `cd web && pnpm test --run` |
| **Full suite command** | `cd web && pnpm test --run` |
| **Estimated runtime** | ~10 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd web && pnpm test --run`
- **After every plan wave:** Run `cd web && pnpm test --run`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~10 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 16-W0-setup | 01 | 0 | AUTH-01, AUTH-02, AUTH-03 | infrastructure | `cd web && pnpm test --run` | ❌ W0 | ⬜ pending |
| 16-AUTH-01 | 01 | 1 | AUTH-01 | unit | `cd web && pnpm test --run src/auth/AuthContext.test.tsx` | ❌ W0 | ⬜ pending |
| 16-AUTH-02 | 01 | 1 | AUTH-02 | unit | `cd web && pnpm test --run src/auth/AuthContext.test.tsx` | ❌ W0 | ⬜ pending |
| 16-AUTH-03 | 01 | 1 | AUTH-03 | unit | `cd web && pnpm test --run src/components/ProtectedRoute.test.tsx` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `web/vitest.config.ts` — Vitest config with jsdom environment and react plugin
- [ ] `web/src/test-setup.ts` — jest-dom import + afterEach cleanup
- [ ] `web/src/auth/AuthContext.test.tsx` — test stubs for AUTH-01, AUTH-02
- [ ] `web/src/components/ProtectedRoute.test.tsx` — test stubs for AUTH-03
- [ ] Framework install: `cd web && pnpm add -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom`
- [ ] Add `"test": "vitest"` to `web/package.json` scripts

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Google Sign-In button renders and triggers OAuth popup | AUTH-01 | Requires real GIS SDK + Google account | 1. Load app at localhost:5173/login. 2. Click "Sign in with Google". 3. Complete OAuth flow. 4. Verify redirect to /. |
| Stay signed in after browser refresh | AUTH-02 | Requires real refresh token round-trip | 1. Sign in. 2. Reload page. 3. Verify still on document list (not /login). |
| Redirect to /login when navigating to /editor/:id unauthenticated | AUTH-03 | Integration with real routing | 1. Clear localStorage. 2. Navigate to /editor/test. 3. Verify redirect to /login. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 10s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
