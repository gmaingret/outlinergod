# Requirements: OutlinerGod

**Defined:** 2026-03-06
**Core Value:** Self-hosted, offline-first outliner that works identically on Android and in the browser — your notes stay on your server.

## v0.8 Requirements — web-client

### Setup & Deployment

- [ ] **SETUP-01**: Web client builds and deploys inside the existing Docker container at 192.168.1.50
- [ ] **SETUP-02**: Web app is accessible at https://notes.gregorymaingret.fr in a browser
- [ ] **SETUP-03**: /api/* routes continue to work for the Android app (no regression)

### Authentication

- [ ] **AUTH-01**: User can sign in with Google (same account as on Android)
- [ ] **AUTH-02**: User stays logged in after refreshing the browser
- [ ] **AUTH-03**: User is redirected to login page if not authenticated

### Documents

- [ ] **DOC-01**: User can see their list of documents
- [ ] **DOC-02**: User can create a new document
- [ ] **DOC-03**: User can rename a document
- [ ] **DOC-04**: User can delete a document

### Node Editor

- [ ] **EDIT-01**: User can open a document and read all its nodes
- [ ] **EDIT-02**: User can type in a node to edit its content
- [ ] **EDIT-03**: Pressing Enter creates a new sibling node below
- [ ] **EDIT-04**: Pressing Tab indents a node (makes it a child of the one above)
- [ ] **EDIT-05**: Pressing Shift+Tab outdents a node (promotes it one level up)
- [ ] **EDIT-06**: Bold, italic, and code formatting renders inline as the user types
- [ ] **EDIT-07**: User can click the bullet glyph to zoom into a node's subtree
- [ ] **EDIT-08**: User can drag nodes to reorder them

### Sync

- [ ] **SYNC-01**: Document loads with latest data from the server
- [ ] **SYNC-02**: Changes made in the browser sync to the server (and become visible on Android)

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
| SETUP-01 | Phase 15 | Pending |
| SETUP-02 | Phase 15 | Pending |
| SETUP-03 | Phase 15 | Pending |
| AUTH-01 | Phase 16 | Pending |
| AUTH-02 | Phase 16 | Pending |
| AUTH-03 | Phase 16 | Pending |
| DOC-01 | Phase 17 | Pending |
| DOC-02 | Phase 17 | Pending |
| DOC-03 | Phase 17 | Pending |
| DOC-04 | Phase 17 | Pending |
| EDIT-01 | Phase 18 | Pending |
| EDIT-02 | Phase 18 | Pending |
| EDIT-03 | Phase 18 | Pending |
| EDIT-04 | Phase 18 | Pending |
| EDIT-05 | Phase 18 | Pending |
| EDIT-06 | Phase 18 | Pending |
| EDIT-07 | Phase 18 | Pending |
| EDIT-08 | Phase 19 | Pending |
| SYNC-01 | Phase 18 | Pending |
| SYNC-02 | Phase 18 | Pending |

**Coverage:**
- v0.8 requirements: 20 total
- Mapped to phases: 20
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-06*
*Last updated: 2026-03-06 after roadmap creation (v0.8 phases 15–19)*
