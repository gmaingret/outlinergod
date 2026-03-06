# Requirements: OutlinerGod

**Defined:** 2026-03-06
**Core Value:** Self-hosted, offline-first outliner that works identically on Android and in the browser — your notes stay on your server.

## v0.8 Requirements — web-client

### Setup & Deployment

- [x] **SETUP-01**: Web client builds and deploys inside the existing Docker container at 192.168.1.50
- [x] **SETUP-02**: Web app is accessible at https://notes.gregorymaingret.fr in a browser
- [x] **SETUP-03**: /api/* routes continue to work for the Android app (no regression)

### Authentication

- [x] **AUTH-01**: User can sign in with Google (same account as on Android)
- [x] **AUTH-02**: User stays logged in after refreshing the browser
- [x] **AUTH-03**: User is redirected to login page if not authenticated

### Documents

- [x] **DOC-01**: User can see their list of documents
- [x] **DOC-02**: User can create a new document
- [x] **DOC-03**: User can rename a document
- [x] **DOC-04**: User can delete a document

### Node Editor

- [x] **EDIT-01**: User can open a document and read all its nodes
- [x] **EDIT-02**: User can type in a node to edit its content
- [x] **EDIT-03**: Pressing Enter creates a new sibling node below
- [x] **EDIT-04**: Pressing Tab indents a node (makes it a child of the one above)
- [x] **EDIT-05**: Pressing Shift+Tab outdents a node (promotes it one level up)
- [x] **EDIT-06**: Bold, italic, and code formatting renders inline as the user types
- [x] **EDIT-07**: User can click the bullet glyph to zoom into a node's subtree
- [x] **EDIT-08**: User can drag nodes to reorder them

### Sync

- [x] **SYNC-01**: Document loads with latest data from the server
- [x] **SYNC-02**: Changes made in the browser sync to the server (and become visible on Android)

## Future Requirements

### v0.9+

- Search in web client (FTS needs different approach than Android Room FTS4)
- Bookmarks panel in web client
- File attachment upload in web client
- Export (HTML, Markdown, OPML) from web client
- Tags / people / dates (#, @, !) in web client
- Offline support (PWA / service worker)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Separate Docker container for web | Single container keeps deployment simple |
| Mobile-responsive design | Desktop-first for v0.8 |
| Offline / PWA | Not required for self-hosted internal tool |
| Real-time collaboration | No WebSockets; pull-based sync only |
| Search in web client | Defer to v0.9; FTS4 is Android Room-specific |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| SETUP-01 | Phase 15 | Complete |
| SETUP-02 | Phase 15 | Complete |
| SETUP-03 | Phase 15 | Complete |
| AUTH-01 | Phase 16 | Complete |
| AUTH-02 | Phase 16 | Complete |
| AUTH-03 | Phase 16 | Complete |
| DOC-01 | Phase 17 | Complete |
| DOC-02 | Phase 17 | Complete |
| DOC-03 | Phase 17 | Complete |
| DOC-04 | Phase 17 | Complete |
| EDIT-01 | Phase 18 | Complete |
| EDIT-02 | Phase 18 | Complete |
| EDIT-03 | Phase 18 | Complete |
| EDIT-04 | Phase 18 | Complete |
| EDIT-05 | Phase 18 | Complete |
| EDIT-06 | Phase 18 | Complete |
| EDIT-07 | Phase 18 | Complete |
| EDIT-08 | Phase 19 | Complete |
| SYNC-01 | Phase 18 | Complete |
| SYNC-02 | Phase 18 | Complete |

**Coverage:**
- v0.8 requirements: 20 total
- Mapped to phases: 20
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-06*
*Last updated: 2026-03-06 after roadmap creation (v0.8 phases 15–19)*
