# Phase 17: Document List - Context

**Gathered:** 2026-03-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Full CRUD document list for authenticated web users: view all documents, create, rename, and delete. Clicking a document navigates to the node editor (Phase 18 builds the editor). No sync UI in this phase — that belongs to Phase 18.

</domain>

<decisions>
## Implementation Decisions

### Layout & information density
- Vertical full-width list (not card grid)
- Each row shows title only — no metadata (no last-modified date)
- Document icon (📄) prefix on each row
- Sort: most recently updated first (updated_at DESC)

### Create
- "+ New document" button at the top of the list
- Clicking it appends a blank row at the top with an auto-focused inline title input
- Press Enter or blur to confirm creation (fires POST /api/documents)
- Press Escape to cancel without creating

### Rename
- Right-click on a row opens a small context menu with "Rename" and "Delete" options
- Selecting "Rename" replaces the title text with an inline editable input (pre-filled with current title)
- Press Enter or blur to confirm (fires PATCH /api/documents/:id)
- Press Escape to cancel

### Delete
- Right-click → context menu → "Delete" → confirmation dialog
- Dialog text: "Delete '[title]'? This cannot be undone."
- Confirm fires soft-delete (PATCH /api/documents/:id with deleted_at timestamp via the existing backend route)

### Empty state
- Simple text: "No documents yet. Click '+ New document' to create one."
- Same layout as populated state — "+ New document" button still visible at top

### Loading state
- Skeleton rows (2-3 grey placeholder rows with animate-pulse) while GET /api/documents is in-flight

### Navigation
- Clicking anywhere on a document row navigates to `/editor/:id` (same tab, React Router)
- Visual affordance: hover highlight (bg-gray-50 or similar) + cursor:pointer

### Page header
- Top bar: "OutlinerGod" on the left, "Sign out" button on the right
- Sign out calls AuthContext.logout() and navigates to /login

### Claude's Discretion
- Exact Tailwind class choices (colors, spacing, typography sizes)
- Context menu positioning (anchored to click position vs. row-relative)
- Whether to use a `<dialog>` element or a div overlay for the confirmation modal
- Exact skeleton row dimensions

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AuthContext.tsx` / `useAuth()` — provides `state.accessToken` for Bearer auth headers, `state.userId` for scoping, and `logout()` for sign-out
- `App.tsx` — `DocumentListPage` placeholder at `/` is the integration point; replace with real component
- React Router `useNavigate()` — already available via react-router-dom for navigating to `/editor/:id`

### Established Patterns
- Tailwind v4 via `@tailwindcss/vite` — no config file; use utility classes directly
- Fetch calls made directly (no API client layer) — follow same pattern as AuthContext.tsx: `fetch('/api/...', { headers: { Authorization: 'Bearer ...' } })`
- No shared component library exists yet — build what this phase needs (no premature abstraction)

### Integration Points
- Replace `DocumentListPage` stub in `web/src/App.tsx` with a proper `DocumentListPage` component in `web/src/pages/` or `web/src/components/`
- Backend endpoints ready: `GET /api/documents`, `POST /api/documents`, `PATCH /api/documents/:id`
- Soft-delete: PATCH with `{ deleted_at: <hlc_string> }` — check existing PATCH handler to confirm exact field name

</code_context>

<specifics>
## Specific Ideas

- Final page shape (confirmed by user):
  ```
  ┌────────────────────────────────┐
  │ OutlinerGod        [Sign out] │
  ├────────────────────────────────┤
  │ [+ New document]               │
  ├────────────────────────────────┤
  │ 📄 My notes                    │
  │ 📄 Work journal                │
  └────────────────────────────────┘
  ```

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 17-document-list*
*Context gathered: 2026-03-06*
