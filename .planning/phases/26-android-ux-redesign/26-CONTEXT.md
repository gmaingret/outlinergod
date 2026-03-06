# Phase 26: Android UX redesign - Context

**Gathered:** 2026-03-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Overhaul Android navigation chrome and toolbar layout: replace back arrow with a home icon, persist and auto-open the last-viewed document on cold launch, add a context-sensitive bottom toolbar (browse vs. edit mode), add a hamburger drawer for document management, and fix glyph top-alignment. No data model changes, no new sync endpoints.

</domain>

<decisions>
## Implementation Decisions

### Breadcrumb & TopAppBar (NodeEditorScreen)

- **Root level (not zoomed):** Home icon (house) as `navigationIcon`, title area is empty — no document name shown
- **Zoomed in:** Title shows `"Home > [node content text]"` with `TextOverflow.Ellipsis, maxLines=1`
- **Text style:** `MaterialTheme.typography.titleSmall` (reduced from current titleLarge default)
- **Right side actions:** None — TopAppBar is empty on the right; all doc-level actions move to the drawer
- The home icon (`Icons.Default.Home`) replaces the `ArrowBack` icon; it navigates to DocumentListScreen

### Last-opened document persistence

- **Storage:** DataStore (`PreferencesDataStore`), key `"last_opened_document_id"` — no Room migration needed, not synced, acceptable to lose on reinstall
- **Write timing:** Written at the moment `onNavigateToNodeEditor(docId)` is called (in AppNavHost or DocumentListViewModel) — before navigating
- **Cold launch flow:**
  1. Read `last_opened_document_id` from DataStore
  2. If present and document exists in Room → open `NodeEditorScreen` directly
  3. If absent, or document no longer in Room → fall through to `DocumentListScreen`
- **First launch (new user):** No stored ID → show `DocumentListScreen`

### Hamburger drawer (inside NodeEditorScreen)

- **Scope:** `ModalNavigationDrawer` lives inside `NodeEditorScreen`'s composable — not in `MainActivity` or wrapping `NavHost`
- **Document list:** All documents, flat (no folder nesting), full-featured — long-press shows rename/delete context menu inline
- **Drawer contents (top to bottom):** document list rows → divider → "Add document" button → "Settings" button
- **Close behavior:** Drawer closes on any navigation action (tap doc, tap Add, tap Settings)
- **"Add document":** Creates document and navigates to its NodeEditorScreen (same as current FAB behavior)
- **"Settings":** Navigates to SettingsScreen

### Context-sensitive bottom toolbar

- **Browse mode (focusedNodeId == null):** Global bottom bar visible with exactly 3 buttons: hamburger (left), search (centre-left), bookmark (centre-right) — right side empty
- **Edit mode (focusedNodeId != null):** `NodeActionToolbar` appears above keyboard; global bar hidden
- **Transition trigger:** Immediately when `focusedNodeId` changes — no IME wait
- **Hamburger button:** Opens the `ModalNavigationDrawer`

### System back gesture (NodeEditorScreen)

- **At root level (not zoomed):** System back navigates to `DocumentListScreen` (same as tapping home icon)
- **When zoomed in:** System back pops the zoom back stack entry (current behavior via `navigateUp()`)
- Implemented with `BackHandler` composable to intercept the back gesture when at root level

### Glyph alignment

- Bullet/zoom glyph uses `Alignment.Top` on the `Row` or `Box` containing glyph + content — applies to all node rows regardless of content line count
- No change to glyph tap behavior (zoom-in) or expand/collapse arrow

### Claude's Discretion

- Exact drawer animation / `DrawerState` opening/closing mechanics
- Loading state when resolving last-opened document on cold launch (brief spinner acceptable)
- Visual styling of the global browse bar (icon sizes, tint, padding)
- Whether `BackHandler` at root level pops to `DocumentListScreen` via `navigate` or `popBackStack` (use navigate with `popUpTo` to clear the back stack cleanly)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets

- `BottomAppBar` — already imported in `NodeEditorScreen.kt`; can host the 3 browse-mode buttons
- `NodeActionToolbar` — existing composable; stays as-is, just conditionally shown when `focusedNodeId != null`
- `DocumentListScreen` / `DocumentListItem` — document list with rename/delete already built; drawer reuses same pattern
- `AuthRepository` — already uses `PreferencesDataStore` for tokens; `last_opened_document_id` key can use the same DataStore instance
- `ModalNavigationDrawer` — Material3 component, no new dependency needed
- `Icons.Default.Home` — available in `androidx.compose.material.icons`

### Established Patterns

- Orbit MVI `intent {}` blocks for ViewModel state mutations
- `BackHandler` composable available via `androidx.activity.compose.BackHandler`
- `navController.navigate(route) { popUpTo(X) { inclusive = true } }` pattern used in `AppNavHost` for clearing back stacks
- DataStore read in `ViewModel.init {}` via `authRepository` — same approach for reading `last_opened_document_id`

### Integration Points

- `AppNavHost.kt`: cold-launch routing logic (read DataStore, set `startDestination` or navigate immediately after Login)
- `NodeEditorScreen.kt`: TopAppBar overhaul + `ModalNavigationDrawer` wrapper + `BottomAppBar` browse bar
- `DocumentListViewModel.kt` or `AppNavHost.kt`: write `last_opened_document_id` on navigate
- `NodeEditorViewModel.kt`: `BackHandler` to intercept back at root level (`rootNodeId == null`)

</code_context>

<specifics>
## Specific Ideas

- "Home > Node content..." uses literal " > " separator (not an icon chevron) — keep it text for simplicity
- The drawer doc list is flat even when folders exist — nesting is the DocumentListScreen's job
- The full-featured rename/delete in the drawer means the same long-press DropdownMenu used in `DocumentListItem` — no new component needed, just reuse or extract the item composable

</specifics>

<deferred>
## Deferred Ideas

- None — discussion stayed within phase scope

</deferred>

---

*Phase: 26-android-ux-redesign*
*Context gathered: 2026-03-06*
