---
phase: 17-document-list
plan: 02
subsystem: ui
tags: [react, typescript, tailwind, vitest, testing-library, fetch, crud]

# Dependency graph
requires:
  - phase: 17-01
    provides: 12 failing DocumentListPage tests (RED state scaffold)
  - phase: 16
    provides: AuthContext with useAuth(), accessToken, logout()
provides:
  - DocumentListPage React component with full CRUD (DOC-01..04)
  - App.tsx wired with real import (stub removed)
affects:
  - 18-node-editor (navigation target /editor/:id is now reachable from DocumentListPage)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Async IIFE in useEffect for async fetch (avoids .then chaining that breaks vi.fn mocks)"
    - "Empty state uses child <span> for partial text assertion compatibility with RTL getByText"
    - "Context menu via onContextMenu + fixed div at clientX/clientY; dismissed by window click listener"
    - "role=dialog on delete confirmation overlay div for RTL getByRole('dialog') assertion"

key-files:
  created:
    - web/src/pages/DocumentListPage.tsx
  modified:
    - web/src/App.tsx

key-decisions:
  - "Use async IIFE inside useEffect instead of .then() chaining — vi.fn().mockReturnValueOnce returns a non-Promise object; await wraps it in Promise.resolve automatically"
  - "Split empty state text: <span>No documents yet</span> separate from the rest — RTL getByText exact-matches the element text content"

patterns-established:
  - "Pattern: async IIFE in useEffect — use (async () => { ... })() for fetch logic to avoid .then chaining issues with vitest mocks"
  - "Pattern: separate inline text nodes for partial RTL text assertions"

requirements-completed: [DOC-01, DOC-02, DOC-03, DOC-04]

# Metrics
duration: 3min
completed: 2026-03-06
---

# Phase 17 Plan 02: Document List Implementation Summary

**React DocumentListPage with full CRUD — view/create/rename/delete documents using fetch + Tailwind, all 12 tests green**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-06T10:29:19Z
- **Completed:** 2026-03-06T10:32:11Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- DocumentListPage.tsx: 170 lines, exports named `DocumentListPage` with all 4 DOC requirements
- DOC-01: GET /api/documents with Bearer token, client-side sort by updated_at DESC, animate-pulse skeleton rows
- DOC-02: Inline create flow — "+ New document" button, autoFocus input, Enter/Escape handling, POST /api/documents
- DOC-03: Right-click context menu with Rename inline pre-filled input, Enter calls PATCH /api/documents/:id
- DOC-04: Delete confirmation overlay with `role="dialog"`, Confirm calls DELETE /api/documents/:id, row removed
- App.tsx: stub function removed, import from `./pages/DocumentListPage` wired in
- pnpm test exits 0 (18/18 tests), pnpm build exits 0 (241 kB bundle)

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement DocumentListPage.tsx** - `98df2da` (feat)
2. **Task 2: Wire DocumentListPage into App.tsx** - `5911a39` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified
- `web/src/pages/DocumentListPage.tsx` - Full CRUD document list page (170 lines)
- `web/src/App.tsx` - Removed stub, added import from ./pages/DocumentListPage

## Decisions Made
- Use async IIFE inside useEffect instead of .then() chaining — vitest `mockReturnValueOnce` returns a plain object (not a Promise); `await fetch(...)` wraps it in Promise.resolve automatically, but `.then()` on a plain object throws "not a function"
- Split empty state text: `<span>No documents yet</span>` separate from the remainder — RTL `getByText('No documents yet')` does exact element text matching; the full sentence in a single element fails the assertion

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Changed fetch from .then() chain to async IIFE**
- **Found during:** Task 1 (first test run — 3 failures)
- **Issue:** vitest `mockReturnValueOnce` returns a raw object (not a Promise); `.then()` called on it throws "fetch(...).then is not a function", crashing Test 1 and triggering cascading failures
- **Fix:** Replaced `.then()` chain with `async IIFE` inside useEffect; `await fetch()` automatically wraps the mock object in Promise.resolve
- **Files modified:** web/src/pages/DocumentListPage.tsx
- **Verification:** All 12 DocumentListPage tests pass
- **Committed in:** 98df2da (Task 1 commit)

**2. [Rule 1 - Bug] Split empty state text for RTL partial text matching**
- **Found during:** Task 1 (first test run — Tests 3 and 4 failing)
- **Issue:** RTL `getByText('No documents yet')` requires the full text of an element to match exactly. Single `<p>` with the full sentence "No documents yet. Click '+ New document' to create one." didn't match.
- **Fix:** Wrapped "No documents yet" in a child `<span>`, leaving the rest of the sentence as a sibling text node
- **Files modified:** web/src/pages/DocumentListPage.tsx
- **Verification:** Tests 3 and 4 pass; empty state renders correctly
- **Committed in:** 98df2da (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1 - bug)
**Impact on plan:** Both fixes necessary for test compatibility. No scope creep. Implementation matches plan spec exactly.

## Issues Encountered
None beyond the auto-fixed deviations above.

## Next Phase Readiness
- Phase 17 complete — all 4 DOC requirements implemented and verified
- Phase 18 (Node Editor + Sync) can begin: DocumentListPage navigation to /editor/:id is wired
- Manual Docker verification can proceed: sign in → document list → CRUD → navigate to editor

---
*Phase: 17-document-list*
*Completed: 2026-03-06*
