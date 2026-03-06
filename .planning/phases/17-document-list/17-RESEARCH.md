# Phase 17: Document List - Research

**Researched:** 2026-03-06
**Domain:** React 19 + Tailwind v4 document CRUD UI
**Confidence:** HIGH

## Summary

Phase 17 builds the document list page — a full-width list of the user's documents with inline
create, rename, delete, and navigation to the editor. The page is protected behind `ProtectedRoute`
and replaces the existing `DocumentListPage` stub in `App.tsx`.

All backend endpoints needed for this phase exist and are verified: `GET /api/documents` (list),
`POST /api/documents` (create), `PATCH /api/documents/:id` (rename or soft-delete),
`DELETE /api/documents/:id` (hard cascade-soft-delete). The web client calls them with a plain
`fetch` and a `Bearer` token from `useAuth().state.accessToken`, following the pattern established
in `AuthContext.tsx`. No API client layer exists; calls are made directly.

The test pattern is well established: `@testing-library/react` + `vitest` + `jsdom`, mocking
`global.fetch`. The existing test files (`AuthContext.test.tsx`, `ProtectedRoute.test.tsx`) provide
the exact template: `MemoryRouter` for routing, `AuthProvider` wrapper, `waitFor` for async
assertions, and `vi.fn()` on `global.fetch` per test.

**Primary recommendation:** Build `DocumentListPage` as a single file at
`web/src/pages/DocumentListPage.tsx` using `useState` + `useEffect` for data fetching, inline
editing state for create/rename, a right-click `contextmenu` handler for the action menu, and a
`<dialog>` element (or simple div overlay) for the delete confirmation. Test it with
`@testing-library/react` + `userEvent` following the established mock-fetch pattern.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Layout & information density**
- Vertical full-width list (not card grid)
- Each row shows title only — no metadata (no last-modified date)
- Document icon (📄) prefix on each row
- Sort: most recently updated first (updated_at DESC)

**Create**
- "+ New document" button at the top of the list
- Clicking it appends a blank row at the top with an auto-focused inline title input
- Press Enter or blur to confirm creation (fires POST /api/documents)
- Press Escape to cancel without creating

**Rename**
- Right-click on a row opens a small context menu with "Rename" and "Delete" options
- Selecting "Rename" replaces the title text with an inline editable input (pre-filled with current title)
- Press Enter or blur to confirm (fires PATCH /api/documents/:id)
- Press Escape to cancel

**Delete**
- Right-click → context menu → "Delete" → confirmation dialog
- Dialog text: "Delete '[title]'? This cannot be undone."
- Confirm fires soft-delete (PATCH /api/documents/:id with deleted_at timestamp via the existing backend route)

**Empty state**
- Simple text: "No documents yet. Click '+ New document' to create one."
- Same layout as populated state — "+ New document" button still visible at top

**Loading state**
- Skeleton rows (2-3 grey placeholder rows with animate-pulse) while GET /api/documents is in-flight

**Navigation**
- Clicking anywhere on a document row navigates to `/editor/:id` (same tab, React Router)
- Visual affordance: hover highlight (bg-gray-50 or similar) + cursor:pointer

**Page header**
- Top bar: "OutlinerGod" on the left, "Sign out" button on the right
- Sign out calls AuthContext.logout() and navigates to /login

### Claude's Discretion
- Exact Tailwind class choices (colors, spacing, typography sizes)
- Context menu positioning (anchored to click position vs. row-relative)
- Whether to use a `<dialog>` element or a div overlay for the confirmation modal
- Exact skeleton row dimensions

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| DOC-01 | User can see their list of documents | `GET /api/documents` returns `{ items: [...] }` sorted by updated_at DESC at query level; client renders list |
| DOC-02 | User can create a new document | `POST /api/documents` requires `{ title, type, sort_order }` — client supplies `type: 'document'` and an initial sort_order |
| DOC-03 | User can rename a document | `PATCH /api/documents/:id` with `{ title }` — confirmed in backend route; returns updated document |
| DOC-04 | User can delete a document | `PATCH /api/documents/:id` with `{ deleted_at }` field — BUT see critical finding below: PATCH does NOT accept deleted_at; use `DELETE /api/documents/:id` instead |
</phase_requirements>

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| React | 19.2.0 | UI rendering | Already installed, all existing components use it |
| react-router-dom | 7.13.1 | `useNavigate` for row click → /editor/:id | Already installed, `useNavigate` used in LoginPage |
| Tailwind v4 | 4.2.1 | Styling via utility classes | Already configured via `@tailwindcss/vite`, no config file needed |
| @testing-library/react | 16.3.2 | Component tests | Already installed, pattern established in existing tests |
| @testing-library/user-event | 14.6.1 | Simulating clicks, typing, keyboard | Already installed |
| vitest | 4.0.18 | Test runner | Already configured in `vitest.config.ts` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| @testing-library/jest-dom | 6.9.1 | `toBeInTheDocument`, `toHaveValue` matchers | Imported via `test-setup.ts` — available in all tests |

### No New Dependencies
No new packages required. Everything needed is already installed.

---

## Architecture Patterns

### Recommended Project Structure
```
web/src/
├── auth/                    # AuthContext.tsx, api.ts (existing)
├── components/              # Shared UI components (ProtectedRoute, LoginPage — existing)
├── pages/
│   └── DocumentListPage.tsx # New — Phase 17 target
└── App.tsx                  # Replace stub, import DocumentListPage
```

`DocumentListPage` in `web/src/pages/` keeps screens separate from shared components. No subdirectory
needed for Phase 17 — a single file is the right scope.

### Pattern 1: Data Fetching with useState + useEffect

The project uses no data-fetching library (no React Query, no SWR). Follow `AuthContext.tsx`'s
direct `fetch` pattern.

```typescript
// Established pattern — same as AuthContext.tsx
const { state } = useAuth()

const [documents, setDocuments] = useState<Document[]>([])
const [isLoading, setIsLoading] = useState(true)
const [error, setError] = useState<string | null>(null)

useEffect(() => {
  if (!state.accessToken) return
  fetch('/api/documents', {
    headers: { Authorization: `Bearer ${state.accessToken}` },
  })
    .then(r => (r.ok ? r.json() : Promise.reject()))
    .then((data: { items: Document[] }) => {
      // Backend returns items sorted by updated_at DESC
      setDocuments(data.items)
      setIsLoading(false)
    })
    .catch(() => {
      setError('Failed to load documents')
      setIsLoading(false)
    })
}, [state.accessToken])
```

**Note on sort order:** The backend `GET /api/documents` query does NOT include `ORDER BY updated_at DESC`
in the existing route — it does `SELECT * FROM documents WHERE user_id = ? AND deleted_at IS NULL`
with no ORDER BY. The client MUST sort by `updated_at` descending after receiving the response.
Confirm: `data.items.sort((a, b) => b.updated_at - a.updated_at)` on the client.

### Pattern 2: Inline Editing State

Use a single "editing" state union to manage create/rename modes. Avoids multiple overlapping
booleans.

```typescript
type EditingState =
  | { mode: 'none' }
  | { mode: 'creating'; title: string }
  | { mode: 'renaming'; id: string; title: string }

const [editing, setEditing] = useState<EditingState>({ mode: 'none' })
```

For "creating": render a temporary input row at the top of the list (before all document rows).
For "renaming": render the matching document row with an `<input>` instead of title text.

### Pattern 3: Right-Click Context Menu

Browser's native `contextmenu` event. Intercept with `e.preventDefault()`, capture click
coordinates, render a positioned div overlay.

```typescript
const [contextMenu, setContextMenu] = useState<{
  x: number
  y: number
  documentId: string
  documentTitle: string
} | null>(null)

function handleContextMenu(e: React.MouseEvent, doc: Document) {
  e.preventDefault()
  setContextMenu({ x: e.clientX, y: e.clientY, documentId: doc.id, documentTitle: doc.title })
}
```

Dismiss on: clicking a menu item, clicking outside (window `click` listener in `useEffect`).

```typescript
useEffect(() => {
  if (!contextMenu) return
  const dismiss = () => setContextMenu(null)
  window.addEventListener('click', dismiss)
  return () => window.removeEventListener('click', dismiss)
}, [contextMenu])
```

Positioning: fixed `{ top: contextMenu.y, left: contextMenu.x }`. The user left this to Claude's
discretion.

### Pattern 4: Skeleton Loading State

Tailwind's `animate-pulse` with grey placeholder divs. Render 3 rows while `isLoading` is true.

```tsx
{isLoading && Array.from({ length: 3 }).map((_, i) => (
  <div key={i} className="animate-pulse flex items-center px-4 py-3 border-b border-gray-100">
    <div className="h-4 bg-gray-200 rounded w-48" />
  </div>
))}
```

### Pattern 5: Create Document API Call

`POST /api/documents` requires `title`, `type`, and `sort_order`. The client must supply all three.

```typescript
// type is always 'document' for user-created documents
// sort_order: put the new document first — use a timestamp-derived string or "aA" for now.
// The existing fractional index library is Android-only. For web, supply a simple sort_order.
// Recommended: use Date.now().toString() as sort_order for new documents (lexicographic ordering
// by creation time is sufficient for Phase 17 since we sort client-side by updated_at anyway).

const body = {
  title: editing.title.trim() || 'Untitled',
  type: 'document',
  sort_order: Date.now().toString(),
}
```

**CRITICAL:** `POST /api/documents` returns 201 on success with the created document object. Add
returned document to the front of the list (since list is sorted by updated_at DESC, the newest
document goes first).

### Pattern 6: Delete — Use DELETE Endpoint, Not PATCH

**CRITICAL FINDING:** The CONTEXT.md says "fires soft-delete (PATCH /api/documents/:id with
deleted_at timestamp via the existing backend route)". This is incorrect for this project.

Reading the actual `backend/src/routes/documents.ts`:
- `PATCH /api/documents/:id` accepts: `title`, `parent_id`, `sort_order`, `collapsed` — it does NOT
  accept a `deleted_at` field. Supplying `{ deleted_at: ... }` will return 400 "No updatable fields
  supplied".
- `DELETE /api/documents/:id` performs the soft-delete (sets `deleted_at` on the document and all
  its nodes).

**Use `DELETE /api/documents/:id` for deletion.** The planner should NOT follow the CONTEXT.md
note about PATCH with deleted_at — that's an incorrect assumption about the backend.

### Anti-Patterns to Avoid
- **Using PATCH for delete:** The backend PATCH handler only accepts `title`, `parent_id`,
  `sort_order`, `collapsed`. Use `DELETE /api/documents/:id` for soft-delete.
- **Sorting on the backend:** The backend returns items unsorted. Sort on the client after fetch.
- **Global state for document list:** `useState` in the page component is sufficient; no context or
  global store needed for Phase 17.
- **Complex router wrapping in tests:** Use `MemoryRouter` from react-router-dom (as in
  `ProtectedRoute.test.tsx`) rather than `BrowserRouter`.
- **Mutating the `editing` state inside fetch callbacks directly:** Set loading state before fetch,
  then update documents and clear editing state in the `.then()` — avoids stale closure issues.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Animation on skeleton | Custom CSS keyframes | `animate-pulse` (Tailwind built-in) | Already available |
| Keyboard event handling | Custom key listener on window | `onKeyDown` on the `<input>` element | Simpler, scoped to the input |
| Context menu dismiss | Complex event bubbling logic | Single `window.addEventListener('click', ...)` in `useEffect` | Two lines, well understood |
| UUID generation | Custom ID | `crypto.randomUUID()` | Browser built-in, already used in `api.ts` |

**Key insight:** This phase is pure React + fetch. No new libraries. The complexity is in UI state
management (creating/renaming/context-menu/dialog interactions), not in infrastructure.

---

## Common Pitfalls

### Pitfall 1: Inline Input Loses Focus on Re-render
**What goes wrong:** When the inline create/rename input is rendered, a state update elsewhere
(e.g., from the fetch resolving) causes a re-render that removes focus from the input.
**Why it happens:** React re-creates DOM nodes if the component key or position changes between
renders.
**How to avoid:** Use `autoFocus` on the `<input>` element; avoid changing the document list
structure while the input is visible (only update list state after creation confirms, not during).
**Warning signs:** Typing in the inline input causes unexpected behavior after the first keystroke.

### Pitfall 2: Context Menu Stays Open After Navigation
**What goes wrong:** User right-clicks, then clicks a document row directly — context menu
stays visible after navigation.
**Why it happens:** Navigation happens before the dismiss click listener fires.
**How to avoid:** Call `setContextMenu(null)` explicitly in the row click handler before navigating.

### Pitfall 3: Right-Click Triggers Row Navigation
**What goes wrong:** The `contextmenu` handler fires but the row's `onClick` also fires or vice
versa, navigating away unintentionally.
**Why it happens:** `contextmenu` and `click` are separate events; right-click does NOT trigger
`onClick` in browsers. This is NOT a real risk — document it to avoid over-engineering a fix.
**How to avoid:** No special handling needed. Confirm via test.

### Pitfall 4: Empty Title on Create/Rename
**What goes wrong:** User presses Enter with empty input — sends `{ title: '' }` which the backend
accepts (no server-side validation on title length currently).
**Why it happens:** No client-side guard.
**How to avoid:** If `editing.title.trim() === ''`, use `'Untitled'` as fallback (or cancel if
creating). Add this to the confirm handler.

### Pitfall 5: Sort Order for New Documents
**What goes wrong:** All new documents get the same `sort_order` causing undefined ordering.
**Why it happens:** Using a static constant (e.g., `"aV"`) for all new documents.
**How to avoid:** Use `Date.now().toString()` as `sort_order` — guaranteed unique per creation.
The list is sorted client-side by `updated_at` anyway, so lexicographic sort_order is only relevant
to the Android sync merge logic, not to this display.

### Pitfall 6: Backend Query Has No ORDER BY
**What goes wrong:** Documents appear in insertion order (or arbitrary SQLite order) instead of
most-recently-updated first.
**Why it happens:** The backend `GET /api/documents` route has no `ORDER BY` clause.
**How to avoid:** Always sort the received `items` array on the client:
`data.items.sort((a, b) => b.updated_at - a.updated_at)` before setting state.

### Pitfall 7: Test Wrapping with BrowserRouter vs MemoryRouter
**What goes wrong:** Tests that wrap components in `<BrowserRouter>` fail or behave oddly because
BrowserRouter uses the real history API.
**Why it happens:** `window.location` is not fully mocked in jsdom.
**How to avoid:** Use `<MemoryRouter initialEntries={['/']}> ` for all test renders — exact pattern
in `ProtectedRoute.test.tsx`.

---

## Code Examples

Verified patterns from existing project files:

### GET /api/documents fetch (follows AuthContext.tsx pattern)
```typescript
// Established fetch pattern — source: web/src/auth/AuthContext.tsx
fetch('/api/documents', {
  headers: { Authorization: `Bearer ${state.accessToken}` },
})
  .then(r => (r.ok ? r.json() : Promise.reject()))
  .then((data: { items: DocumentItem[] }) => { ... })
  .catch(() => { ... })
```

### POST /api/documents — required fields (source: backend/src/routes/documents.ts)
```typescript
// Backend requires: title (string), type ('document'|'folder'), sort_order (string)
// Optional: id (UUID), parent_id (string|null)
fetch('/api/documents', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${state.accessToken}`,
  },
  body: JSON.stringify({
    title: title.trim() || 'Untitled',
    type: 'document',
    sort_order: Date.now().toString(),
  }),
})
```

### DELETE /api/documents/:id — soft-deletes document + all nodes
```typescript
// Source: backend/src/routes/documents.ts — DELETE handler
// Returns: { deleted_ids: string[] }
fetch(`/api/documents/${id}`, {
  method: 'DELETE',
  headers: { Authorization: `Bearer ${state.accessToken}` },
})
```

### PATCH /api/documents/:id — rename only (accepted fields: title, parent_id, sort_order, collapsed)
```typescript
// Source: backend/src/routes/documents.ts — PATCH handler
fetch(`/api/documents/${id}`, {
  method: 'PATCH',
  headers: {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${state.accessToken}`,
  },
  body: JSON.stringify({ title: newTitle.trim() || 'Untitled' }),
})
```

### Test pattern — mock fetch + render with router (source: web/src/components/ProtectedRoute.test.tsx)
```typescript
global.fetch = vi.fn()

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
})

it('shows documents after fetch', async () => {
  localStorage.setItem('refresh_token', 'rt')
  // Mock silent refresh first, then GET /api/documents
  ;(global.fetch as ReturnType<typeof vi.fn>)
    .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ token: 'at', refresh_token: 'rt' }) })
    .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ items: [{ id: '1', title: 'My notes', updated_at: 1000 }] }) })

  render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<DocumentListPage />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  )

  await waitFor(() => expect(screen.getByText('My notes')).toBeInTheDocument())
})
```

### Keyboard handler on inline input
```typescript
function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
  if (e.key === 'Enter') confirmEdit()
  if (e.key === 'Escape') cancelEdit()
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Mocking fetch with `jest.fn()` | `vi.fn()` + `global.fetch` | Phase 16 patterns | Same concept, vitest API |
| `@tailwindcss/postcss` config | `@tailwindcss/vite` plugin (no config file) | D-WEB-01 | Tailwind v4 requires no `tailwind.config.js` |
| DELETE via PATCH with deleted_at | `DELETE /api/documents/:id` route | Backend documents.ts | Use DELETE, not PATCH |

**Deprecated / outdated in this project:**
- `GoogleSignInClient` — Android only, not relevant here
- `wildcard: true` on @fastify/static — confirmed broken (D-WEB-02), always `wildcard: false`

---

## Open Questions

1. **Sort order collision on rapid creates**
   - What we know: `Date.now().toString()` used as sort_order; two creates within the same millisecond get identical sort_orders.
   - What's unclear: Whether the backend or Android sync logic cares about sort_order uniqueness for web-created documents.
   - Recommendation: Use `Date.now().toString()` for Phase 17; it's sufficient for a single-user self-hosted tool. Phase 18/19 can refine with fractional indexing if needed.

2. **Error handling for 401 responses**
   - What we know: `AuthContext` does not auto-retry or re-authenticate on 401 from data endpoints.
   - What's unclear: Whether a fetch to `/api/documents` with an expired token should auto-logout or show an error.
   - Recommendation: On non-ok response from document endpoints, show a generic error message. Do not implement auto-logout in Phase 17 — out of scope.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Vitest 4.0.18 |
| Config file | `web/vitest.config.ts` |
| Quick run command | `cd web && pnpm test --run` |
| Full suite command | `cd web && pnpm test --run` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DOC-01 | Renders list of documents from GET /api/documents | unit | `cd web && pnpm test --run DocumentListPage` | ❌ Wave 0 |
| DOC-01 | Shows skeleton rows while loading | unit | `cd web && pnpm test --run DocumentListPage` | ❌ Wave 0 |
| DOC-01 | Shows empty state when no documents | unit | `cd web && pnpm test --run DocumentListPage` | ❌ Wave 0 |
| DOC-02 | "+ New document" button shows inline input | unit | `cd web && pnpm test --run DocumentListPage` | ❌ Wave 0 |
| DOC-02 | Enter confirms creation and calls POST /api/documents | unit | `cd web && pnpm test --run DocumentListPage` | ❌ Wave 0 |
| DOC-02 | Escape cancels creation without API call | unit | `cd web && pnpm test --run DocumentListPage` | ❌ Wave 0 |
| DOC-03 | Right-click opens context menu with Rename + Delete | unit | `cd web && pnpm test --run DocumentListPage` | ❌ Wave 0 |
| DOC-03 | Rename replaces title with pre-filled input; Enter confirms PATCH | unit | `cd web && pnpm test --run DocumentListPage` | ❌ Wave 0 |
| DOC-03 | Escape cancels rename without API call | unit | `cd web && pnpm test --run DocumentListPage` | ❌ Wave 0 |
| DOC-04 | Delete shows confirmation dialog with correct title | unit | `cd web && pnpm test --run DocumentListPage` | ❌ Wave 0 |
| DOC-04 | Confirming delete calls DELETE /api/documents/:id and removes row | unit | `cd web && pnpm test --run DocumentListPage` | ❌ Wave 0 |
| DOC-04 | Cancelling delete dialog makes no API call | unit | `cd web && pnpm test --run DocumentListPage` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `cd web && pnpm test --run`
- **Per wave merge:** `cd web && pnpm test --run`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `web/src/pages/DocumentListPage.test.tsx` — covers all DOC-01..04 requirements
- No framework install needed — Vitest + testing-library already installed and configured

---

## Sources

### Primary (HIGH confidence)
- `backend/src/routes/documents.ts` — exact API contract for GET, POST, PATCH, DELETE endpoints
- `web/src/auth/AuthContext.tsx` — fetch pattern with Bearer token
- `web/src/components/ProtectedRoute.test.tsx` — MemoryRouter + AuthProvider test pattern
- `web/src/auth/AuthContext.test.tsx` — mock fetch test pattern with `vi.fn()`
- `web/vitest.config.ts` — test environment (jsdom, globals, setupFiles)
- `web/package.json` — all installed dependencies confirmed

### Secondary (MEDIUM confidence)
- `.planning/phases/17-document-list/17-CONTEXT.md` — user decisions (locked and discretion areas)
- `.planning/STATE.md` — v0.8 decisions log (D-WEB-01 through D-WEB-09)

### Tertiary (LOW confidence)
- None

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already installed and in active use in the project
- Architecture: HIGH — patterns copied directly from existing Phase 16 code
- Pitfalls: HIGH — identified by reading actual backend source, not by assumption
- API contract: HIGH — read directly from `backend/src/routes/documents.ts`

**Research date:** 2026-03-06
**Valid until:** 2026-04-06 (stable stack; expires sooner only if documents.ts backend routes change)

**Critical correction vs CONTEXT.md:** CONTEXT.md says to delete via `PATCH /api/documents/:id`
with a `deleted_at` field. The actual backend route does NOT accept `deleted_at` in PATCH. The
correct endpoint is `DELETE /api/documents/:id`. The planner MUST use DELETE for DOC-04, not PATCH.
