# OutlineGod — Self-Hosted Android Outliner
## Product Requirements Document v4

**Project**: Self-hosted outliner combining Dynalist's organizational power with WorkFlowy's clean, minimal UX  
**Platform**: Android APK (Kotlin + Jetpack Compose + Material Design 3)  
**Backend**: Node.js/TypeScript in Docker container  
**Database**: SQLite (local + server)  
**Authentication**: Google SSO (multi-account, each account gets its own independent space)  
**Sync model**: Offline-first (local DB + sync on reconnect)  
**File storage**: Local filesystem in Docker volume  

---

## 1. Design Philosophy

The guiding principle is **progressive disclosure through touch**: the interface shows what you need, when you need it. The default view is clean text, bullet glyphs, and whitespace. Power reveals itself through contextual menus and gesture interactions — never through a cluttered static toolbar.

### Visual design principles

- **Generous whitespace** between nodes; content breathes
- **Full-width layout** with regular horizontal padding — no artificial max-width cap
- **Monochrome base palette** (dark by default) with color used only for semantic meaning (tags, labels, links)
- **Smooth micro-animations** on expand/collapse, drag, zoom transitions (200–300ms ease)
- **Typography-first**: one clean sans-serif font family, clear hierarchy through weight and size alone
- **Material Design 3** throughout — components, gestures, and interactions follow MD3 specifications
- **Node glyphs are always visible** — the bullet glyph is a permanent element, not hidden on hover
- **Accessibility-compliant color palette** — all color combinations meet WCAG AA contrast ratios
- **Dark mode by default**

---

## 2. Core Data Model

### Two-tier hierarchy

The **file layer** contains **folders** and **documents** organized in a tree (left sidebar). The **node layer** contains **items** (bullets) nested infinitely within each document. Only the active document is loaded into memory.

### Node schema

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Unique identifier; document root is always `"root"` |
| `content` | String | Primary text (supports inline Markdown formatting) |
| `note` | String | Supplementary note text displayed below content |
| `created` | Long | Unix timestamp in milliseconds |
| `modified` | Long | Unix timestamp in milliseconds |
| `parent_id` | UUID | ID of the parent node (`null` for root) |
| `completed` | Boolean | Whether the item is marked as complete |
| `color` | Integer | Color label 0–6 (0 = none, 1–6 = six distinct colors) |
| `collapsed` | Boolean | Whether children are visually collapsed (default: true) |
| `sort_order` | TEXT | String-based fractional index for ordering among siblings |

**Tree reconstruction**: Documents are stored as a flat array of nodes. The tree is reconstructed at load time by grouping all nodes by `parent_id` and sorting each group by `sort_order` (TEXT lexicographic order). There is no `children` array — `parent_id` and `sort_order` are the sole source of truth for tree structure and ordering.

### File schema

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Unique file identifier |
| `title` | String | Display name |
| `type` | String | `"document"` or `"folder"` |
| `collapsed` | Boolean | Whether folder is collapsed in sidebar |
| `parent_id` | UUID | ID of parent folder (`null` for root) |
| `sort_order` | TEXT | String-based fractional index for sidebar ordering |

### Item ↔ document fluidity

Users can drag an item from the document area to the sidebar to convert it into a standalone document. The reverse — moving a document into an item position — is also supported.

---

## 3. Infinite Nesting, Zooming, and Navigation

### Nesting

No depth limit. Each level adds horizontal indent with a subtle vertical guide line between sibling nodes. Indenting and outdenting is done via the mobile toolbar buttons or drag-and-drop.

### Zooming (hoisting)

Zooming into a node makes it the temporary root — only its subtree is visible. This is the core navigation pattern.

**Zoom trigger**: tap the node glyph (bullet). A single tap on the glyph always zooms into that node — it never collapses it.

### Breadcrumb navigation

When zoomed, a breadcrumb trail appears at the top: `Document Name > Parent > Current Node`. Each segment is tappable to navigate to that level.

### Expand and collapse

- **Nodes are collapsed by default**
- **Tapping the glyph (bullet) zooms in** — it never toggles collapse
- **Collapse/expand is controlled by a directional arrow**, aligned to the right edge of each node that has children:
  - Right-pointing arrow (▶) when the node is collapsed
  - Down-pointing arrow (▼) when the node is expanded
  - Tapping the arrow toggles between collapsed and expanded
- Collapsed state persists per node across sessions

### Drag-to-reorder

- **Long-press the node glyph then move** to initiate drag-and-drop reorder with haptic feedback
- **Long-press the node glyph then release** (without moving) to open the contextual menu
- A visual drop-line indicator shows the target position during drag
- All children move with the dragged item
- Fractional indexing (`sort_order`) ensures conflict-free reordering

### Alphabetic sorting

Any parent node's children can be sorted alphabetically (A→Z or Z→A) via the contextual menu. This is a one-time reorder, not a persistent sort mode.

---

## 4. Editing Model — Live Inline Formatting

The app uses **live WYSIWYG inline formatting**: bold text appears bold as you type, links render inline. This provides a smooth, modern editing experience.

### Formatting syntax

| Format | Syntax | Rendering |
|--------|--------|-----------|
| Bold | `**text**` | Renders bold inline |
| Italic | `_text_` | Standard Markdown italic (single underscores) |
| Inline code | `` `text` `` | Monospace with subtle background |
| Strikethrough | `~~text~~` | Line-through |
| Highlight | `==text==` | Highlight band |
| Named link | `[text](url)` | Blue clickable text |
| Image | `![alt](url)` | Inline image display (generated by the app; see File Attachments) |

### Bullet type

There is only one bullet type: the standard bullet dot. No to-do checkboxes, no numbered lists, no paragraph items, no headings, no quote blocks, no code blocks.

### Enter key behavior — content field

- **Enter**: creates a new sibling item and moves the cursor to it — the defining outliner interaction
- **Enter in the middle of text**: splits the item in two at the cursor position; left half stays, right half becomes the new sibling below
- **Enter at the beginning of a non-empty item**: inserts a blank item above

### Enter key behavior — note field

- **Enter in the note field**: inserts a newline within the note (notes are multi-line; Enter does not create a new node)
- **Shift+Enter in the note field**: same as Enter — inserts a newline
- The note field never triggers node creation or splitting

### Note field

Each item has an optional **note** field displayed below the content in smaller, lighter type. Notes support the same inline formatting as content. The note field is **always visible when non-empty** — it cannot be hidden and is not togglable.

---

## 5. Tags, People, Dates, and Metadata

### Tags

`#` triggers a tag. For example: `#France`. Typing `#` opens an autocomplete popup showing existing tags sorted by frequency. Tags are case-insensitive for matching. Clicking a rendered tag initiates a global search filtered to that tag. Tags work in both content and notes.

### People

`@` triggers a person tag — a distinct category from regular tags. For example: `@Alice`. Behavior is identical to `#` tags but People and Tags are kept separate in the tag panel, in search results, and in autocomplete suggestions.

### Tag panel

A sidebar pane shows all tags organized by frequency with occurrence counts, split into two sections: **Tags (#)** and **People (@)**. Clicking any tag or person filters the view to matching items. Long-pressing a tag enables bulk rename across all documents.

### Dates

`!` triggers the Material Design 3 **date and time picker**. There is no conflict with image syntax: images are never inserted by typing — they are always added via the toolbar attachment button (see Section 8). The `![alt](url)` Markdown is generated and stored internally by the app, never typed manually by the user. Dates display as relative text ("Tomorrow", "In 3 days") with the absolute date visible on tap. Tapping a rendered date reopens the picker to edit it.

- Single-day dates only — no date ranges, no recurring dates
- No overdue highlighting

### Color labels

Six colors (red, orange, yellow, green, blue, purple) can be applied to any node via the contextual menu color swatches. Colors apply as a subtle background band across the item. Searchable with `color:red` etc.

### Marking as complete

- **Swipe right** on a node to mark it as complete
- A node marked as complete is displayed with **strikethrough text and dimmed opacity**
- Completed items are **always shown** — they are never auto-hidden
- Completed items remain visible until explicitly deleted by the user
- Swipe right again on a completed item to un-complete it

---

## 6. Search System

Search is accessible via the **search button in the bottom bar**. There is one search mode: **search everywhere** — a global search across all documents.

Results are presented as a list of matching items with their breadcrumb path. Tapping a result navigates to that item in context.

### Search operators

| Operator | Example | Purpose |
|----------|---------|---------| 
| `"phrase"` | `"apple pie"` | Exact phrase |
| `-term` | `-is:completed` | Exclusion |
| `OR` | `apple OR pie` | Boolean OR (AND is default) |
| `is:completed` | — | Completed items |
| `has:note` | — | Items with non-empty notes |
| `has:date` | — | Items containing dates |
| `has:color` | — | Colored items |
| `has:children` | — | Items with children |
| `has:file` | — | Items with attachments |
| `color:red` | — | Specific color label |
| `edited:-2w` | — | Edited within timeframe |
| `created:-1m` | — | Created within timeframe |
| `within:1w` | — | Dates within range |
| `since:-3d` | — | Dates since past point |
| `until:2w` | — | Dates until future point |
| `parent:term` | — | Direct parent contains keyword |
| `ancestor:term` | — | Any ancestor contains keyword |
| `in:title` | — | Search only content |
| `in:note` | — | Search only notes |

Time units: `h` (hours), `d` (days), `w` (weeks), `m` (months).

---

## 7. Cross-Document Linking and Backlinks

### Internal linking

`[[` triggers a search prompt across all documents. Selecting a result inserts a Markdown link: `[Item Title](internal://doc/<docID>/node/<nodeID>)`. Internal links auto-update when items are moved within the same document.

### Backlinks

A count badge on each bullet shows how many other items reference it. Tapping the count reveals the referencing items in a bottom sheet. When zoomed into an item, references appear below the subtree.

**Backlink computation**: Backlinks are computed client-side only. They are scanned and cached each time a document is loaded into memory, and the cache is invalidated and re-scanned on any write operation that modifies `content` or `note` fields. The server does not compute or store backlinks.

---

## 8. File Attachments and Images

### Upload mechanics

Files are uploaded via clipboard paste or the mobile toolbar attachment button. Stored on the local filesystem in a Docker volume at `/data/uploads/<uuid>.<ext>`. The server generates URLs in the format `/api/files/<uuid>.<ext>`.

### Image compression

Before uploading to the server, the app automatically reduces and compresses images to keep them **under 1 MB**. The original local file is not modified.

### Display

- Images display inline within the node
- No file size limit beyond available disk space
- No upload quota
- No restriction on file types
- No upload manager UI

### Deletion behavior

Deleting a node or a document permanently deletes all file attachments associated with that node or document from the Docker volume.

---

## 9. Bookmarks and Saved Searches

### Bookmarks

Bookmarks are shortcuts to documents, specific items (at any depth), or search queries. No limit on number of bookmarks. Bookmarks appear in the bookmark pane in the sidebar.

**Search bookmarks are dynamic** — they re-execute the query each time, showing current results. Bookmarks can be renamed and reordered via drag-and-drop.

---

## 10. Export and Import

### Export formats

Per document or per zoomed subtree:

- **Formatted (HTML)**: preserves all formatting
- **Plain text**: configurable indent characters (spaces, `*`, `-`)
- **OPML**: standard XML outline format
- **Markdown**: full Markdown with proper nesting via indentation
- **JSON**: raw node data for programmatic use

### Import

- **OPML import**: pick `.opml` files via Android file picker
- **Plain text**: paste indented text; tabs create nesting
- **Dynalist JSON**: import from Dynalist export
- **WorkFlowy export**: import from WorkFlowy's plain text export
- **Markdown**: import `.md` files, converting lists to outline structure

---

## 11. Sync and Offline Architecture

### Offline-first model

Both the Android app and backend maintain SQLite databases. The app works fully offline with all features available. Changes are queued and synced when connectivity is restored.

### Sync protocol

The sync implementation follows the per-field Hybrid Logical Clock (HLC) + Last-Write-Wins (LWW) architecture defined in the Architecture document. The PRD does not override or simplify those sync rules — the Architecture document is the authoritative specification for all sync behavior.

Summary for product context only:
1. Client pulls all server changes since last sync
2. Client pushes all locally pending changes
3. Per-field LWW with HLC timestamps determines the winner for each field independently
4. Last sync HLC is updated on successful completion

### Conflict resolution

Per-field last-write-wins using Hybrid Logical Clocks (see Architecture doc). When a node is deleted on one device and edited on another, **the deletion wins** — the node is permanently removed on both sides. There is no conflict archive.

---

## 12. Mobile-Specific Interactions (Android)

### Gestures

| Gesture | Action |
|---------|--------|
| Tap node glyph | Zoom into that node |
| Tap collapse/expand arrow | Toggle collapse/expand |
| Tap node text | Open keyboard for editing |
| Swipe right | Mark node as complete (or un-complete) |
| Swipe left | Delete node (with undo snackbar) |
| Long-press glyph, then move | Initiate drag-and-drop reorder with haptic feedback |
| Long-press glyph, then release | Open contextual menu |

### Mobile toolbar

A persistent toolbar appears **above the keyboard when editing** a node. The toolbar buttons are:

1. **Indent** — make current item a child of the item above
2. **Outdent** — promote current item one level up
3. **Move up** — move item up among its siblings
4. **Move down** — move item down among its siblings
5. **Undo**
6. **Redo**
7. **Add attachment** — open file picker or camera
8. **Switch to note** — open/focus the note field for the current node

### Bottom bar

A persistent bottom bar is always visible (when not editing). It contains:

1. **Sidebar toggle** — show/hide the file tree sidebar
2. **Search** — open the global search
3. **Bookmarks** — open the bookmark pane
4. **Tags** — open the tag/people panel
5. **Settings**

---

## 13. UI Layout and Visual Anatomy

### Main layout (3 zones)

1. **Left sidebar** (collapsible): file tree, bookmark pane, tag pane — switchable tabs
2. **Main content area**: document with breadcrumbs at top when zoomed
3. **Top bar**: hamburger sidebar toggle, document title, sync status indicator, **expand/collapse all button** (expands or collapses every node in the current document or zoomed subtree in one tap)

### Node visual anatomy

Every node follows this structure (left to right):

- **Node glyph** (always visible filled dot) — tapping zooms in; long-pressing initiates drag or contextual menu
- **Content text** (full width, wraps as needed)
- **Directional arrow** (right-aligned, only on nodes with children): ▶ when collapsed, ▼ when expanded

Below the content line, if non-empty:
- **Note text** in smaller, lighter type

All nodes are standard bullets. There are no checkboxes, numbered items, paragraph items, headings, quote blocks, or code block items.

### Context menu

Triggered by long-pressing the glyph and releasing. Options (context-sensitive based on item state):

- Zoom in
- Indent / Outdent
- Move up / Move down
- Color swatches (6 colors + clear)
- Mark as complete / Un-complete
- Add bookmark / Remove bookmark
- Alphabetic sort (for nodes with children)
- Duplicate
- Delete
- Copy internal link

### Text selection popup

Appears when text is selected within a node. Offers inline formatting options: **Bold**, **Italic**, **Code**, **Strikethrough**, **Highlight**, **Link**.

### Themes

- **Dark** (default)
- **Light**
- All colors comply with WCAG AA accessibility contrast standards
- No custom CSS, no sepia mode

### Density options

- **Cozy** (default): generous spacing
- **Comfortable**: moderate spacing
- **Compact**: tight spacing for information density

---

## 14. Settings and Preferences

All settings sync across devices for the same Google account.

### Appearance

- Theme selection (Dark / Light)
- List density (Cozy / Comfortable / Compact)
- Show/hide vertical guide lines between sibling nodes

### Behavior

- Show/hide backlink count badge on nodes (toggle)

### Backup

- **Download current node/document**: exports the currently open document (or zoomed node subtree) as a JSON file saved locally on the device
- **Export all data**: generates a ZIP file containing the complete content of the user's account, including all documents (JSON format) and all file attachments. Downloaded locally.

---

## 15. Docker Deployment

### docker-compose.yml requirements

- Single container for backend and static file serving
- Volume mounts for `/data` (persistent storage: database, uploads)
- Environment variables for: Google OAuth client ID/secret, JWT secret
- Health check endpoint
- Automatic restart policy

### Google SSO configuration

Authentication via Google Sign-In. Any Google account can sign in. **Each Google account has its own independent data space** — documents, settings, and uploads from one account are not visible to or shared with any other account. The backend validates Google ID tokens and issues JWTs for API access.

---

## 16. Implementation-Critical Edge Cases

These are non-obvious behaviors that must be implemented faithfully:

1. **Enter creates a new item, not a newline** — this is the defining outliner interaction (content field only)
2. **Enter in the middle of text splits the item in two** — cursor position determines split point; left half stays, right half becomes the new sibling below
3. **Enter at the beginning of a non-empty item inserts a blank item above**
4. **Enter in the note field inserts a newline** — never creates a new node
5. **`#` is for tags, not headings** — there are no Markdown heading styles; `#` always creates a tag
6. **Tapping the glyph always zooms** — it never toggles collapse; collapse is controlled exclusively by the directional arrow
7. **Long-press + move = drag; long-press + release = contextual menu** — the distinction is whether the finger moves before lifting
8. **Nodes are collapsed by default** — a newly created node with children shows the collapsed state
9. **Internal links via `[[`** are stored as Markdown links with internal URLs, not custom syntax; they auto-update on moves within the same document
10. **Backlinks are computed client-side at document load time**, cached in memory, and invalidated on any write to `content` or `note`
11. **Tree structure is reconstructed from `parent_id` + `sort_order`** — there is no `children` array; `sort_order` is TEXT (string fractional index), not a float
12. **Deletion wins in sync conflicts** — if a node is deleted on one device and edited on another, the deletion propagates; see Architecture doc for full HLC/LWW sync spec
13. **Fractional indexing for sort order** — `sort_order` is a TEXT column using string-based fractional indexing (never a float); see Architecture doc
14. **Images are compressed to under 1 MB** before upload to the server; the local original is unchanged
15. **Deleting a node deletes its attachments** — the Docker volume files are permanently removed
16. **Swipe right marks as complete; swipe right again un-completes** — completed items always remain visible until deleted
17. **Each Google account is an independent space** — sign-in with a different Google account creates or accesses a separate, isolated dataset on the same server
18. **Collapsed state persists per-node** — the collapsed/expanded state is stored in the database and survives app restarts
19. **Paste of indented text creates nested structure** — each indentation level becomes a child node
20. **No real-time push** — sync is pull-based only; the sync status indicator reflects local state
21. **Database migrations run automatically** on Docker container startup
