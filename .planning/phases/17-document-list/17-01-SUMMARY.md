---
phase: 17-document-list
plan: 01
subsystem: testing
tags: [react, vitest, testing-library, tdd, document-list]

# Dependency graph
requires:
  - phase: 16-auth
    provides: AuthProvider, ProtectedRoute, AuthContext test patterns
provides:
  - Failing test scaffold for DocumentListPage with 12 test cases (RED state)
  - Test coverage for DOC-01 (view), DOC-02 (create), DOC-03 (rename), DOC-04 (delete)
affects: [17-02-document-list-implementation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "renderDocList() helper wraps DocumentListPage in AuthProvider + MemoryRouter + ProtectedRoute"
    - "Two-mock sequence: auth refresh first, then GET /api/documents (required by AuthProvider useEffect)"
    - "Mutations get a third mock in sequence (POST/PATCH/DELETE)"
    - "localStorage.setItem('refresh_token', 'rt') before render to simulate authenticated user"

key-files:
  created:
    - web/src/pages/DocumentListPage.test.tsx
  modified: []

key-decisions:
  - "Skeleton test uses direct DOM query (.animate-pulse) not ARIA — implementation must use that CSS class"
  - "Context menu triggered by fireEvent.contextMenu (not right-click via userEvent) for cross-browser reliability"
  - "Confirmation dialog identified by role='dialog' — implementation must use semantic dialog element"

patterns-established:
  - "TDD RED pattern: test file imports non-existent component, pnpm test fails with module-not-found"
  - "Auth mock sequence: refresh call always first when localStorage has refresh_token"

requirements-completed: [DOC-01, DOC-02, DOC-03, DOC-04]

# Metrics
duration: 5min
completed: 2026-03-06
---

# Phase 17 Plan 01: Document List Test Scaffold Summary

**12-test failing scaffold for DocumentListPage covering full CRUD (view/create/rename/delete) with mocked fetch sequences establishing RED TDD state**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-06T10:25:26Z
- **Completed:** 2026-03-06T10:30:00Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Created `web/src/pages/DocumentListPage.test.tsx` with 12 test cases
- All 4 DOC requirements scaffolded: view (5 tests), create (3 tests), rename (2 tests), delete (2 tests)
- `pnpm test --run` exits non-zero with "Failed to resolve import ./DocumentListPage" — expected RED state
- Existing 6 tests (ProtectedRoute + AuthContext) remain green

## Task Commits

Each task was committed atomically:

1. **Task 1: DocumentListPage test scaffold** - `9f60f98` (test)

**Plan metadata:** pending final commit

## Files Created/Modified
- `web/src/pages/DocumentListPage.test.tsx` - 12 failing tests covering DOC-01..04 with auth mock sequences

## Decisions Made
- Skeleton test uses `.animate-pulse` CSS class selector — implementation must render skeletons with this Tailwind class
- Context menu tested with `fireEvent.contextMenu` for reliability
- Confirmation dialog uses `role="dialog"` — implementation must use semantic `<dialog>` or `role="dialog"` attribute
- Confirm button matched with `/confirm|delete|yes/i` regex — implementation has flexibility in button label

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Test scaffold is in RED state, ready for Plan 02 (DocumentListPage implementation)
- Plan 02 must make all 12 tests pass while keeping existing 6 tests green
- Key constraints for implementation from test structure:
  - Skeleton rows must have `animate-pulse` class
  - Context menu appears on `contextmenu` event
  - New document input must autoFocus
  - Delete confirmation must use `role="dialog"` element
  - Confirm button must match `/confirm|delete|yes/i`

---
*Phase: 17-document-list*
*Completed: 2026-03-06*
