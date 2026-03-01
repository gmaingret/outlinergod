## Phase 2 — Backend API Implementation

**Goal**: Every route defined in API.md is implemented, tested, and registered. `pnpm test` exits 0 with zero failures across all test files. All routes use the `requireAuth` preHandler from P1-14. All test files use `createTestDb()` from P1-11 and `@fastify/inject` — no real HTTP port, no real Google APIs, no real filesystem where avoidable.

**Prerequisite**: All Phase 1 tasks must be complete and `pnpm test` must exit 0 before beginning Phase 2.

Tasks run in task ID order. Each task is scoped to approximately 1–2 hours.

---

### P2-1: POST /api/auth/google

- **Task ID**: P2-1
- **Title**: Exchange Google ID token for backend JWT and refresh token
- **What to build**: Create `backend/src/routes/auth.ts` as a Fastify plugin. Implement `POST /api/auth/google` (no auth required). The handler: (1) validates that `id_token` is present in the request body — return 400 `{ error: 'Missing id_token' }` if absent or not a non-empty string. (2) Calls `OAuth2Client.verifyIdToken({ idToken, audience: process.env.GOOGLE_CLIENT_ID })` from `google-auth-library`; on failure returns 401 `{ error: 'Invalid Google token' }`. (3) Extracts `sub`, `email`, `name`, `picture` from the payload. (4) Upserts the user in the `users` table (insert or update on `google_sub` conflict). (5) Generates a signed HS256 JWT via `new SignJWT({ sub: user.id, email, name, picture }).setExpiresIn('1h').sign(...)` from `jose`. (6) Generates a 32-byte random hex refresh token, inserts a row into `refresh_tokens` with `expires_at = Date.now() + 30 * 24 * 60 * 60 * 1000`, `revoked = 0`. (7) Returns 200 `{ token, refresh_token, user: { id, google_sub, email, name, picture }, is_new_user: boolean }` where `is_new_user` is true if the user row was just inserted (not found before the upsert).
- **Inputs required**: P1-1 (buildApp), P1-5 (users schema), P1-6 (refresh_tokens schema), P1-14 (auth middleware); `jose` and `google-auth-library` installed
- **Output artifact**: `backend/src/routes/auth.ts` (created), `backend/src/routes/auth.test.ts` (created)
- **How to test**:
  - **Test file**: `backend/src/routes/auth.test.ts`
  - **Setup**: At the top of the test file, use `vi.mock('google-auth-library')` to mock `OAuth2Client.prototype.verifyIdToken`. Each test controls the mock return value.
  - **Test cases** (`describe('POST /api/auth/google')`):
    1. `returns200_withTokensAndUser_onValidIdToken` — mock `verifyIdToken` resolves with `{ getPayload: () => ({ sub: 'g-sub-1', email: 'a@b.com', name: 'Alice', picture: 'http://pic' }) }`; inject `POST /api/auth/google` with body `{ id_token: 'valid' }`; assert status 200, body contains `token` (string), `refresh_token` (string), `user.id` (string), `user.google_sub === 'g-sub-1'`, `user.email === 'a@b.com'`, `is_new_user === true`.
    2. `isNewUser_false_onSecondCall` — call the route twice with the same `google_sub`; assert second response has `is_new_user === false`.
    3. `returns400_whenIdTokenMissing` — inject with body `{}`; assert status 400, body `{ error: 'Missing id_token' }`.
    4. `returns400_whenIdTokenIsEmptyString` — inject with body `{ id_token: '' }`; assert status 400.
    5. `returns401_whenVerifyIdTokenThrows` — mock `verifyIdToken` to throw `new Error('Invalid token')`; inject with any `id_token`; assert status 401, body `{ error: 'Invalid Google token' }`.
    6. `refreshToken_isStoredInDb` — after success, query `refresh_tokens` table via `createTestDb()`; assert one row exists with `revoked === 0` and `expires_at > Date.now()`.
    7. `jwt_canBeVerifiedWithJwtSecret` — take the returned `token`; call `jwtVerify(token, secret)` from `jose`; assert no error and `payload.sub` equals the returned `user.id`.
  - **Pass criteria**: `pnpm test` exits 0, all 7 cases green.
- **Risk**: MEDIUM — `google-auth-library` mocking requires `vi.mock` at module scope. The `verifyIdToken` call is async; the mock must return a thenable that resolves with a `LoginTicket`-shaped object. Ensure `OAuth2Client` is instantiated once (not per request) to avoid test interference.

---

### P2-2: POST /api/auth/refresh

- **Task ID**: P2-2
- **Title**: Rotate refresh token and issue new JWT
- **What to build**: Extend `backend/src/routes/auth.ts` to add `POST /api/auth/refresh` (no auth required). The handler: (1) validates that `refresh_token` is present — return 400 `{ error: 'Missing refresh_token' }` if absent. (2) Queries `refresh_tokens` by the token value; if not found, return 401 `{ error: 'Invalid refresh token' }`. (3) If `revoked === 1` or `expires_at < Date.now()`, return 401 `{ error: 'Refresh token expired or revoked' }`. (4) Marks the old token `revoked = 1`. (5) Generates a new JWT and a new refresh token row for the same `user_id` and `device_id`. (6) Queries the user row, returns 200 `{ token: newJwt, refresh_token: newToken }`.
- **Inputs required**: P2-1 (auth.ts file exists with route plugin scaffolding)
- **Output artifact**: `backend/src/routes/auth.ts` (extended), `backend/src/routes/auth.test.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/auth.test.ts` (append new `describe` block)
  - **Test cases** (`describe('POST /api/auth/refresh')`):
    1. `returns200_withNewTokens_onValidRefreshToken` — seed a user and a non-revoked, non-expired refresh token in the test db; inject `POST /api/auth/refresh` with body `{ refresh_token: '<seeded>' }`; assert status 200, body contains `token` (string) and `refresh_token` (string different from the seeded one).
    2. `oldRefreshToken_isRevoked_afterRotation` — after a successful refresh, inject the same old `refresh_token` again; assert status 401.
    3. `returns400_whenRefreshTokenMissing` — inject with body `{}`; assert status 400.
    4. `returns401_onUnknownToken` — inject with `refresh_token: 'nonexistent'`; assert status 401.
    5. `returns401_onRevokedToken` — seed a token with `revoked = 1`; inject; assert status 401.
    6. `returns401_onExpiredToken` — seed a token with `expires_at = Date.now() - 1`; inject; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 6 cases green.
- **Risk**: LOW — straightforward token rotation with no external dependencies.

---

### P2-3: GET /api/auth/me

- **Task ID**: P2-3
- **Title**: Return authenticated user profile
- **What to build**: Extend `backend/src/routes/auth.ts` to add `GET /api/auth/me` (JWT required via `requireAuth`). The handler reads `req.user` (populated by the auth middleware from P1-13) and returns 200 `{ id: req.user.id, google_sub: string, email, name, picture }`. The `google_sub` is not stored in `req.user` by the middleware; the handler must query the `users` table by `req.user.id` to retrieve it.
- **Inputs required**: P2-1 (auth.ts exists), P1-14 (requireAuth, auth middleware)
- **Output artifact**: `backend/src/routes/auth.ts` (extended), `backend/src/routes/auth.test.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/auth.test.ts` (append new `describe` block)
  - **Test cases** (`describe('GET /api/auth/me')`):
    1. `returns200_withUserProfile_onValidJwt` — seed a user row; sign a JWT with `sub = user.id`; inject `GET /api/auth/me` with Bearer header; assert status 200, body has `id`, `google_sub`, `email`, `name`, `picture` all matching the seeded row.
    2. `returns401_withNoAuthHeader` — inject without Authorization header; assert status 401 and body `{ error: 'Unauthorized' }`.
    3. `returns401_withExpiredJwt` — sign JWT with `exp` in the past; inject; assert status 401.
    4. `returns401_withMalformedToken` — inject with `Authorization: Bearer not-a-jwt`; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 4 cases green.
- **Risk**: LOW — reads from `req.user` plus one DB lookup. `requireAuth` from P1-13 handles the 401 path.

---

### P2-4: POST /api/auth/logout

- **Task ID**: P2-4
- **Title**: Revoke refresh token on sign-out
- **What to build**: Extend `backend/src/routes/auth.ts` to add `POST /api/auth/logout` (JWT required via `requireAuth`). The handler: (1) validates that `refresh_token` is present in the body — return 400 `{ error: 'Missing refresh_token' }` if absent. (2) Queries `refresh_tokens` by token value; if not found, still return 200 `{ ok: true }` (idempotent — already revoked or never issued is not an error for the client). (3) Verifies the token's `user_id` matches `req.user.id`; if not, silently continue (do not leak existence). (4) Sets `revoked = 1` on the token. (5) Returns 200 `{ ok: true }`.
- **Inputs required**: P2-1 (auth.ts exists), P1-14 (requireAuth)
- **Output artifact**: `backend/src/routes/auth.ts` (extended), `backend/src/routes/auth.test.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/auth.test.ts` (append new `describe` block)
  - **Test cases** (`describe('POST /api/auth/logout')`):
    1. `returns200_ok_andRevokesToken` — seed user + active refresh token; inject `POST /api/auth/logout` with valid JWT + body `{ refresh_token: '<seeded>' }`; assert status 200, body `{ ok: true }`, and querying the token row shows `revoked === 1`.
    2. `returns200_ok_whenTokenNotFound` — inject with `refresh_token: 'nonexistent'`; assert status 200, body `{ ok: true }`.
    3. `returns400_whenRefreshTokenMissing` — inject with valid JWT but body `{}`; assert status 400.
    4. `returns401_withNoAuthHeader` — inject without Bearer header; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 4 cases green.
- **Risk**: LOW — revocation is a single UPDATE. Idempotency (returning 200 even if token not found) is intentional per API.md.

---

### P2-5: GET /api/documents

- **Task ID**: P2-5
- **Title**: List all active documents and folders for the authenticated user
- **What to build**: Create `backend/src/routes/documents.ts` as a Fastify plugin. Implement `GET /api/documents` (JWT required). The handler queries the `documents` table for all rows where `user_id = req.user.id` AND `deleted_at IS NULL`, returning them as `{ items: Document[] }` with status 200. The response array may be unordered. Each document object must include all fields from the Document schema in API.md §2: `id`, `title`, `type`, `parent_id`, `sort_order`, `collapsed` (as boolean — `collapsed === 1`), `deleted_at`, `created_at`, `updated_at`.
- **Inputs required**: P1-7 (documents schema), P1-14 (requireAuth), P1-1 (buildApp)
- **Output artifact**: `backend/src/routes/documents.ts` (created), `backend/src/routes/documents.test.ts` (created)
- **How to test**:
  - **Test file**: `backend/src/routes/documents.test.ts`
  - **Test cases** (`describe('GET /api/documents')`):
    1. `returns200_withItems_forAuthenticatedUser` — seed 2 active documents for user A; inject with user A's JWT; assert status 200, `items` array has length 2, each item has `id`, `title`, `type`, `sort_order`.
    2. `excludes_deletedDocuments` — seed 1 active + 1 soft-deleted (`deleted_at` set) document; assert `items` has length 1.
    3. `excludes_otherUsersDocuments` — seed 1 doc for user A, 1 doc for user B; inject as user A; assert `items` has length 1.
    4. `returns200_withEmptyArray_whenNoDocs` — inject as new user with no documents; assert `items` is `[]`.
    5. `collapsed_isReturnedAsBoolean` — seed document with `collapsed = 1`; assert `items[0].collapsed === true`.
    6. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 6 cases green.
- **Risk**: LOW — simple SELECT with user scoping and soft-delete filter.

---

### P2-6: POST /api/documents

- **Task ID**: P2-6
- **Title**: Create a new document or folder
- **What to build**: Extend `backend/src/routes/documents.ts` to add `POST /api/documents` (JWT required). The handler: (1) validates that `title` (non-empty string), `type` (`'document'` or `'folder'`), and `sort_order` (non-empty string) are all present — return 400 `{ error: 'Missing required fields' }` if any are absent or invalid. (2) If `parent_id` is provided (non-null), queries `documents` to verify the parent exists and belongs to `req.user.id` — return 404 `{ error: 'Parent not found' }` if not. (3) Generates a UUID for the new document, sets `created_at = updated_at = Date.now()`, inserts the row with all HLC columns set to `''` (HLC columns are only populated via sync; direct creation uses empty HLCs), and `device_id = ''`. (4) Returns 201 with the created Document object.
- **Inputs required**: P2-5 (documents.ts exists)
- **Output artifact**: `backend/src/routes/documents.ts` (extended), `backend/src/routes/documents.test.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/documents.test.ts` (append `describe` block)
  - **Test cases** (`describe('POST /api/documents')`):
    1. `returns201_withDocumentObject_onValidBody` — inject `POST /api/documents` with body `{ title: 'My Doc', type: 'document', sort_order: 'V' }`; assert status 201, body has `id` (UUID string), `title === 'My Doc'`, `type === 'document'`, `parent_id === null`, `sort_order === 'V'`, `deleted_at === null`.
    2. `creates_folder_withType_folder` — inject with `type: 'folder'`; assert status 201 and `type === 'folder'`.
    3. `creates_child_document_withValidParentId` — first create a folder (get its `id`); then create a document with `parent_id = folder.id`; assert 201 and `parent_id` matches.
    4. `returns400_whenTitleMissing` — body `{ type: 'document', sort_order: 'V' }`; assert status 400.
    5. `returns400_whenTypeInvalid` — body `{ title: 'X', type: 'heading', sort_order: 'V' }`; assert status 400.
    6. `returns400_whenSortOrderMissing` — body `{ title: 'X', type: 'document' }`; assert status 400.
    7. `returns404_whenParentIdNotFound` — body with `parent_id: 'nonexistent-uuid'`; assert status 404.
    8. `returns404_whenParentBelongsToOtherUser` — seed folder for user B; inject as user A with that folder's id as `parent_id`; assert status 404.
    9. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 9 cases green.
- **Risk**: LOW — insert with optional parent validation.

---

### P2-7: GET /api/documents/:id

- **Task ID**: P2-7
- **Title**: Fetch a single document or folder by ID
- **What to build**: Extend `backend/src/routes/documents.ts` to add `GET /api/documents/:id` (JWT required). The handler queries `documents` where `id = params.id` AND `user_id = req.user.id`. If no row found (or `deleted_at IS NOT NULL`), return 404 `{ error: 'Document not found' }`. Otherwise return 200 with the Document object, with `collapsed` cast to boolean.
- **Inputs required**: P2-5 (documents.ts exists)
- **Output artifact**: `backend/src/routes/documents.ts` (extended), `backend/src/routes/documents.test.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/documents.test.ts` (append `describe` block)
  - **Test cases** (`describe('GET /api/documents/:id')`):
    1. `returns200_withDocument_onValidId` — seed document for user; inject `GET /api/documents/:id`; assert status 200 and body matches all seeded fields.
    2. `returns404_whenDocumentNotFound` — inject with unknown UUID; assert status 404.
    3. `returns404_whenDocumentBelongsToOtherUser` — seed doc for user B; inject as user A; assert status 404.
    4. `returns404_whenDocumentIsSoftDeleted` — seed doc with `deleted_at` set; assert status 404.
    5. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 5 cases green.
- **Risk**: LOW — single-row SELECT with user and soft-delete guards.

---

### P2-8: PATCH /api/documents/:id

- **Task ID**: P2-8
- **Title**: Update mutable fields on a document or folder
- **What to build**: Extend `backend/src/routes/documents.ts` to add `PATCH /api/documents/:id` (JWT required). The handler: (1) Fetches the document (same 404 logic as P2-7). (2) Validates that at least one updatable field is present in the body (`title`, `parent_id`, `sort_order`, `collapsed`) — return 400 `{ error: 'No updatable fields supplied' }` if body is empty or has no recognized keys. (3) If `parent_id` is being set to a non-null value, checks that it would not create a circular reference — walk up the ancestor chain from `parent_id` to root; if any ancestor equals `params.id`, return 400 `{ error: 'Circular reference detected' }`. (4) Builds an UPDATE statement with only the supplied fields plus `updated_at = Date.now()`. (5) Returns 200 with the updated Document object.
- **Inputs required**: P2-7 (GET /api/documents/:id logic)
- **Output artifact**: `backend/src/routes/documents.ts` (extended), `backend/src/routes/documents.test.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/documents.test.ts` (append `describe` block)
  - **Test cases** (`describe('PATCH /api/documents/:id')`):
    1. `returns200_updatesTitle` — seed document; inject `PATCH` with body `{ title: 'New Title' }`; assert 200 and `title === 'New Title'`.
    2. `returns200_updatesSortOrder` — inject with `{ sort_order: 'b' }`; assert 200 and `sort_order === 'b'`.
    3. `returns200_updatesCollapsed` — inject with `{ collapsed: true }`; assert 200 and `collapsed === true`.
    4. `returns200_movesDocumentToNewParent` — create a folder; inject PATCH with `parent_id: folder.id`; assert `parent_id === folder.id`.
    5. `returns400_whenNoUpdatableFields` — inject with body `{}`; assert status 400.
    6. `returns400_onCircularReference` — create doc A with no parent; create folder B as child of A; inject PATCH A with `parent_id: B.id`; assert status 400 `{ error: 'Circular reference detected' }`.
    7. `returns404_whenDocumentNotFound` — inject with unknown UUID; assert status 404.
    8. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 8 cases green.
- **Risk**: MEDIUM — circular reference detection requires iterative ancestor traversal in SQLite (loop up the `parent_id` chain until null or cycle found). Use a depth cap of 100 iterations to prevent infinite loops on malformed data.

---

### P2-9: DELETE /api/documents/:id

- **Task ID**: P2-9
- **Title**: Soft-delete a document/folder and all descendants recursively
- **What to build**: Extend `backend/src/routes/documents.ts` to add `DELETE /api/documents/:id` (JWT required). The handler: (1) Verifies the document exists and belongs to the user (404 if not). (2) Collects all descendant document IDs using a recursive CTE or iterative BFS over the `documents` table (following `parent_id` links). (3) Sets `deleted_at = Date.now()` on all collected IDs. (4) For each deleted document, also sets `deleted_at` on all `nodes` rows where `document_id` is in the deleted set. (5) For each deleted node, permanently deletes any files in `UPLOADS_PATH` whose `filename` starts with the node's ID (or is associated via a separate `files` table — since there is no `files` table in the schema, scan the uploads directory for files whose UUID prefix matches deleted node IDs, or skip filesystem deletion in tests and mock `fs.rm`). (6) Returns 200 `{ deleted_ids: string[] }` with all soft-deleted document IDs.
- **Inputs required**: P2-7 (document ownership check pattern), P1-8 (nodes schema), P1-7 (documents schema)
- **Output artifact**: `backend/src/routes/documents.ts` (extended), `backend/src/routes/documents.test.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/documents.test.ts` (append `describe` block)
  - **Test cases** (`describe('DELETE /api/documents/:id')`):
    1. `returns200_withDeletedIds_forSingleDocument` — seed document with no children; inject `DELETE`; assert status 200, `deleted_ids` is array containing the document's id, and querying the document shows `deleted_at` is set.
    2. `recursivelyDeletesChildDocuments` — seed folder with 2 child docs; delete the folder; assert `deleted_ids` contains all 3 IDs and all 3 rows have `deleted_at` set.
    3. `softDeletesNodesWithinDeletedDocuments` — seed document with 2 nodes; delete document; assert nodes rows have `deleted_at` set.
    4. `returns404_whenDocumentNotFound` — inject with unknown UUID; assert status 404.
    5. `returns404_whenDocumentBelongsToOtherUser` — seed doc for user B; inject as user A; assert status 404.
    6. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
    7. `alreadyDeletedDocument_returns404` — seed doc, delete it, try to delete again; assert status 404.
  - **Pass criteria**: `pnpm test` exits 0, all 7 cases green.
- **Risk**: MEDIUM — recursive descendant collection must be correct (BFS or recursive CTE). Filesystem deletion is side-effectful; use `vi.mock('node:fs/promises')` in tests to avoid touching the real filesystem.

---

### P2-10: GET /api/documents/:id/nodes

- **Task ID**: P2-10
- **Title**: Fetch all active nodes for a document
- **What to build**: Create `backend/src/routes/nodes.ts` as a Fastify plugin. Implement `GET /api/documents/:id/nodes` (JWT required). The handler: (1) Verifies the document exists and belongs to `req.user.id` — return 404 if not. (2) Queries all `nodes` rows where `document_id = params.id` AND `deleted_at IS NULL`. (3) Returns 200 `{ nodes: Node[] }`. Each Node object must include all fields from the Node schema in API.md §3: `id`, `document_id`, `content`, `note`, `parent_id`, `sort_order`, `completed` (boolean), `color`, `collapsed` (boolean), `deleted_at`, `created_at`, `updated_at`. Cast `completed` and `collapsed` integer columns to booleans.
- **Inputs required**: P1-8 (nodes schema), P1-7 (documents schema), P1-14 (requireAuth)
- **Output artifact**: `backend/src/routes/nodes.ts` (created), `backend/src/routes/nodes.test.ts` (created)
- **How to test**:
  - **Test file**: `backend/src/routes/nodes.test.ts`
  - **Test cases** (`describe('GET /api/documents/:id/nodes')`):
    1. `returns200_withNodes_forValidDocument` — seed document + 3 nodes; inject; assert status 200, `nodes` has length 3.
    2. `excludes_deletedNodes` — seed 2 active + 1 deleted node; assert `nodes` has length 2.
    3. `returns200_emptyArray_whenNoNodes` — seed document with no nodes; assert `nodes === []`.
    4. `completed_isReturnedAsBoolean` — seed node with `completed = 1`; assert `nodes[0].completed === true`.
    5. `collapsed_isReturnedAsBoolean` — seed node with `collapsed = 1`; assert `nodes[0].collapsed === true`.
    6. `returns404_whenDocumentNotFound` — inject with unknown document UUID; assert status 404.
    7. `returns404_whenDocumentBelongsToOtherUser` — seed doc for user B; inject as user A; assert status 404.
    8. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 8 cases green.
- **Risk**: LOW — straightforward SELECT with document ownership gate and soft-delete filter.

---

### P2-11: POST /api/documents/:id/nodes/batch

- **Task ID**: P2-11
- **Title**: Batch upsert up to 500 nodes for a document
- **What to build**: Extend `backend/src/routes/nodes.ts` to add `POST /api/documents/:id/nodes/batch` (JWT required). The handler: (1) Verifies document exists and belongs to user (404 if not). (2) Validates that `nodes` is an array present in the body and has at most 500 items — return 400 `{ error: 'Batch exceeds 500 nodes' }` if over limit, or 400 `{ error: 'nodes must be a non-empty array' }` if missing or not an array. (3) Validates each node object has the required fields: `id`, `content`, `note`, `parent_id` (null is valid), `sort_order`, `completed` (boolean), `color` (0–6 integer), `collapsed` (boolean) — return 400 `{ error: 'Malformed node at index N' }` on first invalid node. (4) Performs `INSERT OR REPLACE INTO nodes` for each node in a single transaction, setting `user_id = req.user.id`, `document_id = params.id`, `updated_at = Date.now()`, and all HLC columns to `''` unless the caller provides them. (5) Returns 200 `{ upserted: number }` where `upserted` is the count of nodes processed.
- **Inputs required**: P2-10 (nodes.ts exists, document ownership check pattern)
- **Output artifact**: `backend/src/routes/nodes.ts` (extended), `backend/src/routes/nodes.test.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/nodes.test.ts` (append `describe` block)
  - **Test cases** (`describe('POST /api/documents/:id/nodes/batch')`):
    1. `returns200_withUpsertedCount` — inject with valid array of 3 nodes; assert status 200, `upserted === 3`, and nodes are readable via GET.
    2. `upserts_existingNodes_byId` — insert node via batch; call batch again with same `id` but different `content`; assert GET returns updated `content`.
    3. `returns400_whenNodesMissing` — inject with body `{}`; assert status 400.
    4. `returns400_whenNodesExceed500` — inject with array of 501 nodes; assert status 400 with message containing `500`.
    5. `returns400_onMalformedNode` — inject with one node missing `sort_order`; assert status 400 with message containing `index`.
    6. `returns400_whenColorOutOfRange` — inject node with `color: 7`; assert status 400.
    7. `returns404_whenDocumentNotFound` — inject with unknown document UUID; assert status 404.
    8. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 8 cases green.
- **Risk**: LOW — INSERT OR REPLACE in a transaction. The 500-limit and per-node validation are straightforward.

---

### P2-12: DELETE /api/nodes/:nodeId

- **Task ID**: P2-12
- **Title**: Soft-delete a node and all its descendants
- **What to build**: Extend `backend/src/routes/nodes.ts` to add `DELETE /api/nodes/:nodeId` (JWT required). The handler: (1) Queries the node by `nodeId`; verifies it exists and `user_id = req.user.id` — return 404 if not. (2) Collects the node and all its descendant node IDs by walking the `parent_id` tree (iterative BFS within the same `document_id`). (3) Sets `deleted_at = Date.now()` on all collected node IDs. (4) For file cleanup: use `vi.mock('node:fs/promises')` in tests; in production code call `fs.rm` on any associated upload files (stub out if no `files` table exists — log a TODO). (5) Returns 200 `{ deleted_ids: string[] }` with all soft-deleted node IDs.
- **Inputs required**: P2-10 (nodes.ts exists, node ownership pattern)
- **Output artifact**: `backend/src/routes/nodes.ts` (extended), `backend/src/routes/nodes.test.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/nodes.test.ts` (append `describe` block)
  - **Test cases** (`describe('DELETE /api/nodes/:nodeId')`):
    1. `returns200_withDeletedIds_forLeafNode` — seed document + node; inject `DELETE /api/nodes/:nodeId`; assert status 200, `deleted_ids` contains the node id, and the node has `deleted_at` set.
    2. `recursivelyDeletesChildNodes` — seed parent with 2 child nodes; delete parent; assert `deleted_ids` has 3 entries and all 3 nodes have `deleted_at` set.
    3. `returns404_whenNodeNotFound` — inject with unknown UUID; assert status 404.
    4. `returns404_whenNodeBelongsToOtherUser` — seed node for user B; inject as user A; assert status 404.
    5. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 5 cases green.
- **Risk**: LOW — mirrors the document delete pattern but within nodes. BFS is contained to a single document.

---

### P2-13: POST /api/nodes/:nodeId/convert-to-document

- **Task ID**: P2-13
- **Title**: Promote a node to a standalone document
- **What to build**: Extend `backend/src/routes/nodes.ts` to add `POST /api/nodes/:nodeId/convert-to-document` (JWT required). The handler: (1) Fetches the node by `nodeId` (404 if not found or not owned by user). (2) Returns 409 `{ error: 'Node has no content' }` if `node.content` is empty string or whitespace only. (3) Creates a new document row with `title = node.content`, `type = 'document'`, `parent_id = null`, `sort_order = node.sort_order`, `user_id = req.user.id`, `created_at = updated_at = Date.now()`. (4) Updates all non-deleted nodes where `document_id = node.document_id` AND `parent_id = nodeId` to have `parent_id = newDocument.id` and `document_id = newDocument.id`. Recursively updates all deeper descendants to `document_id = newDocument.id` (their `parent_id` links within the subtree are preserved). (5) Soft-deletes the original node (`deleted_at = Date.now()`). (6) Returns 200 `{ document: Document }` with the new document object.
- **Inputs required**: P2-10 (nodes.ts exists), P2-5 (document ownership + document response shape)
- **Output artifact**: `backend/src/routes/nodes.ts` (extended), `backend/src/routes/nodes.test.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/nodes.test.ts` (append `describe` block)
  - **Test cases** (`describe('POST /api/nodes/:nodeId/convert-to-document')`):
    1. `returns200_withNewDocument` — seed document + node with content `'My Note'`; inject; assert status 200, `document.title === 'My Note'`, `document.type === 'document'`.
    2. `childNodes_areMigrated_toNewDocument` — seed parent node with 2 child nodes; convert parent; assert child nodes' `document_id` equals new document id.
    3. `originalNode_isSoftDeleted` — after conversion, query the original node; assert `deleted_at` is set.
    4. `returns409_whenNodeContentIsEmpty` — seed node with `content = ''`; inject; assert status 409.
    5. `returns404_whenNodeNotFound` — inject with unknown UUID; assert status 404.
    6. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 6 cases green.
- **Risk**: MEDIUM — the descendant `document_id` migration requires updating all nodes in the subtree, not just direct children. A recursive CTE or iterative BFS is needed. Must run in a single transaction to avoid partial state on error.

---

### P2-14: POST /api/documents/:id/convert-to-node

- **Task ID**: P2-14
- **Title**: Fold a document into a node inside another document
- **What to build**: Extend `backend/src/routes/documents.ts` to add `POST /api/documents/:id/convert-to-node` (JWT required). The handler: (1) Validates body has `target_document_id` (string), `target_parent_id` (string), and `sort_order` (string) — return 400 if any missing. (2) Fetches source document (`params.id`) — return 404 if not found or not owned. (3) Fetches target document (`body.target_document_id`) — return 404 if not found or not owned. (4) Checks that `target_parent_id` is not a descendant of the source document (circular reference guard) — return 400 `{ error: 'Circular reference detected' }` if so. (5) Creates a new node in the target document with `content = sourceDocument.title`, `parent_id = body.target_parent_id`, `document_id = body.target_document_id`, `sort_order = body.sort_order`, `user_id = req.user.id`. (6) Migrates all non-deleted nodes from the source document to the target document: updates their `document_id = body.target_document_id`; updates top-level nodes (those with `parent_id = sourceDocument.id` — the root node) to have `parent_id = newNode.id`. (7) Soft-deletes the source document. (8) Returns 200 `{ node: Node }` with the newly created node.
- **Inputs required**: P2-9 (DELETE documents logic for soft-delete), P2-10 (nodes.ts for node creation), P2-7 (document ownership check)
- **Output artifact**: `backend/src/routes/documents.ts` (extended), `backend/src/routes/documents.test.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/documents.test.ts` (append `describe` block)
  - **Test cases** (`describe('POST /api/documents/:id/convert-to-node')`):
    1. `returns200_withNewNode` — seed source doc (title `'MyDoc'`) and target doc; inject; assert status 200, `node.content === 'MyDoc'`, `node.document_id === target.id`.
    2. `sourceDocs_nodes_areMigrated_toTargetDocument` — seed source doc with 2 nodes; convert; assert those nodes now have `document_id === target.id`.
    3. `sourceDocument_isSoftDeleted` — after conversion, query source doc; assert `deleted_at` is set.
    4. `returns400_whenTargetDocumentIdMissing` — inject without `target_document_id`; assert status 400.
    5. `returns404_whenSourceDocumentNotFound` — inject with unknown source id; assert status 404.
    6. `returns404_whenTargetDocumentNotFound` — inject with unknown `target_document_id`; assert status 404.
    7. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 7 cases green.
- **Risk**: MEDIUM — node migration must update `document_id` for the full subtree plus re-point top-level source nodes to the new parent node. Must run in a single transaction.

---

### P2-15: GET /api/sync/changes

- **Task ID**: P2-15
- **Title**: Pull changes for nodes, documents, settings, and bookmarks since a given HLC
- **What to build**: Create `backend/src/routes/sync.ts` as a Fastify plugin (or extend from P0-3 if passing). Implement `GET /api/sync/changes` (JWT required). Query parameters: `since` (required HLC string) and `device_id` (required string) — return 400 if either is missing. The handler: (1) Validates `since` is present and `device_id` is a non-empty string. (2) For each entity type, queries all rows belonging to `req.user.id` where the MAX of all `_hlc` columns is lexicographically greater than `since` AND `device_id != query.device_id` (echo suppression — exclude records whose `device_id` matches the requester's device). (3) For `settings`, returns a single `SettingsSyncRecord | null` (null if no settings row or no change since `since`). (4) Generates the current server HLC via `generate(serverDeviceId)` from `hlc.ts`. (5) Returns 200 `{ server_hlc, nodes: NodeSyncRecord[], documents: DocumentSyncRecord[], settings: SettingsSyncRecord | null, bookmarks: BookmarkSyncRecord[] }`. Each sync record includes ALL HLC companion columns as returned directly from the DB — no field stripping.
- **Inputs required**: P1-12 (hlc.ts), P1-13 (merge.ts types), P1-7 through P1-10 (all schemas), P1-14 (requireAuth)
- **Output artifact**: `backend/src/routes/sync.ts` (created or extended from P0-3), `backend/src/routes/sync.test.ts` (created or extended)
- **How to test**:
  - **Test file**: `backend/src/routes/sync.test.ts`
  - **Test cases** (`describe('GET /api/sync/changes')`):
    1. `returns200_withAllEntityArrays` — inject `GET /api/sync/changes?since=0&device_id=deviceB`; assert status 200, body has keys `server_hlc`, `nodes`, `documents`, `settings`, `bookmarks`.
    2. `returns_nodes_changedAfterSince` — seed a node with `content_hlc = 'AAAA'`; inject with `since = '0'`; assert `nodes` contains that node.
    3. `excludes_nodes_fromSameDevice` — seed node with `device_id = 'deviceA'`; inject with `device_id = 'deviceA'`; assert `nodes` is empty (echo suppression).
    4. `excludes_nodes_notChangedAfterSince` — seed node with all HLCs set to `'0001'`; inject with `since = '0002'`; assert `nodes` is empty.
    5. `returns_tombstones_forDeletedNodes` — seed node with `deleted_at` set and `deleted_hlc > since`; inject; assert node appears in `nodes` with `deleted_at` set.
    6. `returns_settings_whenChanged` — seed settings row for user with `theme_hlc > since`; assert `settings` is non-null and has `theme` field.
    7. `settings_isNull_whenNoneExist` — user has no settings row; assert `settings === null`.
    8. `returns400_whenSinceMissing` — inject without `since` param; assert status 400.
    9. `returns400_whenDeviceIdMissing` — inject without `device_id` param; assert status 400.
    10. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
    11. `server_hlc_isLexicographicallyValid` — assert `server_hlc` matches `/^[0-9a-f]{16}-[0-9a-f]{4}-/`.
  - **Pass criteria**: `pnpm test` exits 0, all 11 cases green.
- **Risk**: HIGH — the HLC comparison across all `_hlc` columns (using `MAX()` or individual column `>` filters) is subtle. Using `since = '0'` as the sentinel (per API.md) must work correctly because all real HLC strings begin with a 16-char hex wall clock. Echo suppression (`device_id !=`) must be exact string match. The query must be scoped to `user_id = req.user.id` for every entity type.

---

### P2-16: POST /api/sync/changes

- **Task ID**: P2-16
- **Title**: Push locally pending changes with per-field LWW merge
- **What to build**: Extend `backend/src/routes/sync.ts` to add `POST /api/sync/changes` (JWT required). The handler: (1) Validates `device_id` is present — return 400 if not. (2) Enforces a 5 MB payload limit (configure via Fastify's `bodyLimit` or check `req.headers['content-length']`). (3) For each incoming node in `body.nodes` (if present): fetch the stored node from DB; if it exists, call `mergeNodes(stored, incoming)` from `merge.ts` and write the merged result back; if it does not exist, insert it (treating it as a new node with `user_id = req.user.id`). Track which node IDs had every field won by the incoming record (`accepted_node_ids`) vs. those where at least one field kept the stored value (`conflicts.nodes`). (4) Apply same LWW pattern for `body.documents` (using `mergeDocuments`) and `body.bookmarks` (using `mergeBookmarks`). (5) For `body.settings` (if present): fetch stored settings; merge per-field; write back. (6) Call `receive(incoming_hlcs, serverDeviceId)` to advance the server HLC. (7) Returns 200 `{ server_hlc, accepted_node_ids, accepted_document_ids, accepted_bookmark_ids, conflicts: { nodes, documents, bookmarks, settings } }`.
- **Inputs required**: P2-15 (sync.ts exists), P1-13 (merge.ts with mergeNodes/mergeDocuments/mergeBookmarks), P1-12 (hlc.ts receive)
- **Output artifact**: `backend/src/routes/sync.ts` (extended), `backend/src/routes/sync.test.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/sync.test.ts` (append `describe` block)
  - **Test cases** (`describe('POST /api/sync/changes')`):
    1. `returns200_acceptsNewNode` — inject node that does not exist in DB; assert `accepted_node_ids` contains the node id and node is stored in DB.
    2. `returns200_incomingContentWins_whenHigherHlc` — pre-seed node with `content = 'old'`, `content_hlc = 'AAA'`; push same node with `content = 'new'`, `content_hlc = 'ZZZ'`; assert `accepted_node_ids` contains the id and DB has `content = 'new'`.
    3. `serverVersionWins_returnsConflict` — pre-seed node with `content_hlc = 'ZZZ'`; push same node with `content_hlc = 'AAA'`; assert `conflicts.nodes` contains the stored version, node id is NOT in `accepted_node_ids`.
    4. `deleteWins_overConcurrentEdits` — pre-seed node with all field HLCs at `'BBB'`; push same node with `deleted_at` set and `deleted_hlc = 'ZZZ'` but lower `content_hlc = 'AAA'`; assert DB node has `deleted_at` set.
    5. `settings_mergedPerField` — pre-seed settings with `theme = 'dark'`, `theme_hlc = 'ZZZ'`; push settings with `theme = 'light'`, `theme_hlc = 'AAA'`; assert DB still has `theme = 'dark'` (stored wins).
    6. `returns400_whenDeviceIdMissing` — inject without `device_id`; assert status 400.
    7. `returns413_whenPayloadTooLarge` — send a request with `content-length` header set to > 5 MB, or configure `bodyLimit` and send oversized body; assert status 413.
    8. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
    9. `accepted_and_conflict_areMutuallyExclusive` — push 2 nodes (one wins, one loses); assert no id appears in both `accepted_node_ids` and `conflicts.nodes`.
  - **Pass criteria**: `pnpm test` exits 0, all 9 cases green.
- **Risk**: HIGH — per-field LWW is the core of the sync system. The merge must be applied per-field independently; updating only the fields that the incoming record wins is required. Inserting a brand-new node (not yet in DB) must not attempt to merge against a null row. The `conflicts` array must contain full sync records (all fields + all HLC companions), not just the differing fields.

---

### P2-17: POST /api/files

- **Task ID**: P2-17
- **Title**: Upload a file attachment and store in Docker volume
- **What to build**: Create `backend/src/routes/files.ts` as a Fastify plugin. Implement `POST /api/files` (JWT required) using `@fastify/multipart`. The handler: (1) Reads the multipart request; if no `file` part is present, return 400 `{ error: 'Missing file' }`. (2) Validates file size does not exceed `MAX_UPLOAD_BYTES` (default 52428800) — return 413 if exceeded. (3) Generates a new UUID for the file. Determines the file extension from the original filename or MIME type. Constructs the storage path as `path.join(process.env.UPLOADS_PATH!, '<uuid>.<ext>')`. Validates the filename matches `^[0-9a-f-]{36}\.[a-z0-9]+$` before writing. (4) Writes the file to disk. (5) Inserts a row into the `files` metadata table with `filename = '<uuid>.<ext>'`, `user_id = req.user.id`, `node_id = body.node_id || null`, `mime_type`, `size`, `created_at = Date.now()`. (6) Returns 201 `{ url: '/api/files/<uuid>.<ext>', uuid: string, filename: string, size: number, mime_type: string }`. In tests, mock `fs.writeFile` via `vi.mock('node:fs/promises')`.
- **Inputs required**: P1-14 (requireAuth), P1-1 (buildApp); `@fastify/multipart` installed
- **Output artifact**: `backend/src/routes/files.ts` (created), `backend/src/routes/files.test.ts` (created)
- **How to test**:
  - **Test file**: `backend/src/routes/files.test.ts`
  - **Test cases** (`describe('POST /api/files')`):
    1. `returns201_withFileMetadata_onValidUpload` — mock `fs.writeFile`; inject multipart with a small file; assert status 201, body has `url` matching `/^\/api\/files\/[0-9a-f-]{36}\.[a-z0-9]+$/`, `uuid` (string), `filename`, `size` (number), `mime_type`.
    2. `filename_matchesUuidPattern` — assert `filename` returned in body matches `^[0-9a-f-]{36}\.[a-z0-9]+$`.
    3. `returns400_whenFilePartMissing` — inject multipart with no `file` part; assert status 400.
    4. `returns413_whenFileTooLarge` — inject file exceeding `MAX_UPLOAD_BYTES`; assert status 413.
    5. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 5 cases green.
- **Risk**: LOW — multipart parsing via `@fastify/multipart` is straightforward. UUID filename validation prevents path traversal. File write is mocked in tests.

---

### P2-18: GET /api/files/:filename

- **Task ID**: P2-18
- **Title**: Serve a stored file attachment
- **What to build**: Extend `backend/src/routes/files.ts` to add `GET /api/files/:filename` (JWT required). The handler: (1) Validates `filename` matches `^[0-9a-f-]{36}\.[a-z0-9]+$` — return 400 `{ error: 'Invalid filename' }` if not. (2) Queries the `files` table by `filename`; if no row found, check disk and return 404 `{ error: 'File not found' }` if not on disk either. (3) If a `files` row exists and `user_id !== req.user.id`, return 403 `{ error: 'Forbidden' }`. (4) Constructs the full path as `path.join(UPLOADS_PATH, filename)`. Checks if the file exists on disk; return 404 if not. (5) Sets `Cache-Control: max-age=2592000, immutable` header. (6) Pipes the file to the response with the correct `Content-Type`. In tests, mock `fs.stat` and use `@fastify/static`'s `reply.sendFile` or `reply.type(...).send(buffer)`.
- **Inputs required**: P2-17 (files.ts exists); `@fastify/static` installed
- **Output artifact**: `backend/src/routes/files.ts` (extended), `backend/src/routes/files.test.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/files.test.ts` (append `describe` block)
  - **Test cases** (`describe('GET /api/files/:filename')`):
    1. `returns200_withFileBytes_onValidFilename` — mock `fs.readFile` to return a buffer; inject `GET /api/files/<uuid>.jpg`; assert status 200 and `Content-Type: image/jpeg`.
    2. `returns_cacheControl_immutable` — assert response header `cache-control` contains `max-age=2592000` and `immutable`.
    3. `returns400_whenFilenamePatternInvalid` — inject `GET /api/files/../secret.txt`; assert status 400.
    4. `returns400_whenFilenameHasNoExtension` — inject `GET /api/files/3fa85f64-5717-4562-b3fc-2c963f66afa6`; assert status 400.
    5. `returns404_whenFileNotFound` — mock `fs.stat` to throw `ENOENT`; assert status 404.
    6. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 6 cases green.
- **Risk**: LOW — path traversal is fully prevented by regex validation before any filesystem operation.

---

### P2-19: DELETE /api/files/:filename

- **Task ID**: P2-19
- **Title**: Permanently delete a file attachment from the Docker volume
- **What to build**: Extend `backend/src/routes/files.ts` to add `DELETE /api/files/:filename` (JWT required). The handler: (1) Validates `filename` matches `^[0-9a-f-]{36}\.[a-z0-9]+$` — return 400 if not. (2) Constructs the full path. (3) Checks the file exists — return 404 if not. (4) Calls `fs.rm(fullPath)`. (5) Returns 200 `{ deleted: true }`. In tests, mock `fs.rm` and `fs.stat`.
- **Inputs required**: P2-17 (files.ts exists)
- **Output artifact**: `backend/src/routes/files.ts` (extended), `backend/src/routes/files.test.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/files.test.ts` (append `describe` block)
  - **Test cases** (`describe('DELETE /api/files/:filename')`):
    1. `returns200_deletedTrue_onSuccess` — mock `fs.stat` (file exists) and `fs.rm`; inject `DELETE /api/files/<uuid>.jpg`; assert status 200, body `{ deleted: true }`, and `fs.rm` was called with the correct path.
    2. `returns400_whenFilenamePatternInvalid` — inject with `../etc/passwd`; assert status 400.
    3. `returns404_whenFileNotFound` — mock `fs.stat` to throw `ENOENT`; assert status 404.
    4. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 4 cases green.
- **Risk**: LOW — single `fs.rm` call after validation. Permanently destructive but the API contract specifies this.

---

### P2-20: GET /api/settings

- **Task ID**: P2-20
- **Title**: Fetch user preferences, returning defaults if never saved
- **What to build**: Create `backend/src/routes/settings.ts` as a Fastify plugin. Implement `GET /api/settings` (JWT required). The handler queries `settings` where `user_id = req.user.id`. If no row exists, return the default Settings object: `{ theme: 'dark', density: 'cozy', show_guide_lines: true, show_backlink_badge: true, updated_at: 0 }`. If a row exists, map `show_guide_lines` and `show_backlink_badge` from integers to booleans and return the Settings object. Returns 200 in both cases.
- **Inputs required**: P1-10 (settings schema), P1-14 (requireAuth)
- **Output artifact**: `backend/src/routes/settings.ts` (created), `backend/src/routes/settings.test.ts` (created)
- **How to test**:
  - **Test file**: `backend/src/routes/settings.test.ts`
  - **Test cases** (`describe('GET /api/settings')`):
    1. `returns200_withDefaults_whenNoSettingsRow` — inject as new user with no settings row; assert status 200, body `{ theme: 'dark', density: 'cozy', show_guide_lines: true, show_backlink_badge: true }`.
    2. `returns200_withStoredSettings_whenRowExists` — seed settings row with `theme = 'light'`, `density = 'compact'`; inject; assert `theme === 'light'`, `density === 'compact'`.
    3. `showGuideLines_isReturnedAsBoolean` — seed row with `show_guide_lines = 0`; assert `show_guide_lines === false`.
    4. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 4 cases green.
- **Risk**: LOW — single SELECT with default fallback.

---

### P2-21: PUT /api/settings

- **Task ID**: P2-21
- **Title**: Replace user preferences and auto-generate HLC timestamps
- **What to build**: Extend `backend/src/routes/settings.ts` to add `PUT /api/settings` (JWT required). The handler: (1) Validates all four required fields are present: `theme` (`'dark'` or `'light'`), `density` (`'cozy'`, `'comfortable'`, or `'compact'`), `show_guide_lines` (boolean), `show_backlink_badge` (boolean) — return 400 `{ error: 'Missing or invalid fields' }` if any are missing or invalid. (2) Generates a new server HLC string via `generate(serverDeviceId)` from `hlc.ts`. (3) Performs `INSERT OR REPLACE INTO settings` with all four field values (booleans stored as integers), all four HLC columns set to the generated HLC, `updated_at = Date.now()`, and `device_id = serverDeviceId`. (4) Returns 200 with the Settings object (booleans as booleans, no HLC columns in response).
- **Inputs required**: P2-20 (settings.ts exists), P1-12 (hlc.ts generate)
- **Output artifact**: `backend/src/routes/settings.ts` (extended), `backend/src/routes/settings.test.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/settings.test.ts` (append `describe` block)
  - **Test cases** (`describe('PUT /api/settings')`):
    1. `returns200_withUpdatedSettings` — inject `PUT /api/settings` with valid body `{ theme: 'light', density: 'compact', show_guide_lines: false, show_backlink_badge: true }`; assert 200, `theme === 'light'`, `density === 'compact'`, `show_guide_lines === false`.
    2. `createsSettingsRow_whenNoneExists` — inject on user with no prior settings; assert 200 and settings row exists in DB.
    3. `replacesExistingSettings_fully` — seed settings with `theme = 'dark'`; inject PUT with `theme = 'light'`; assert `theme === 'light'`.
    4. `hlcColumns_arePopulated_afterPut` — after PUT, query the raw settings row from DB; assert `theme_hlc` is a non-empty string matching HLC format.
    5. `returns400_whenThemeMissing` — inject without `theme`; assert status 400.
    6. `returns400_whenThemeInvalid` — inject with `theme: 'sepia'`; assert status 400.
    7. `returns400_whenDensityInvalid` — inject with `density: 'sparse'`; assert status 400.
    8. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 8 cases green.
- **Risk**: LOW — INSERT OR REPLACE with HLC generation. Validation is a straightforward allowlist check.

---

### P2-22: GET /api/bookmarks

- **Task ID**: P2-22
- **Title**: List all active bookmarks for the authenticated user
- **What to build**: Create `backend/src/routes/bookmarks.ts` as a Fastify plugin. Implement `GET /api/bookmarks` (JWT required). The handler queries `bookmarks` where `user_id = req.user.id` AND `deleted_at IS NULL`. Returns 200 `{ bookmarks: Bookmark[] }`. Each Bookmark object includes all fields from the Bookmark schema in API.md §7: `id`, `title`, `target_type`, `target_document_id`, `target_node_id`, `query`, `sort_order`, `deleted_at`, `created_at`, `updated_at`.
- **Inputs required**: P1-9 (bookmarks schema), P1-14 (requireAuth)
- **Output artifact**: `backend/src/routes/bookmarks.ts` (created), `backend/src/routes/bookmarks.test.ts` (created)
- **How to test**:
  - **Test file**: `backend/src/routes/bookmarks.test.ts`
  - **Test cases** (`describe('GET /api/bookmarks')`):
    1. `returns200_withBookmarks` — seed 2 active bookmarks for user; inject; assert status 200, `bookmarks` has length 2.
    2. `excludes_softDeletedBookmarks` — seed 1 active + 1 deleted bookmark; assert `bookmarks` has length 1.
    3. `excludes_otherUsersBookmarks` — seed bookmark for user B; inject as user A; assert `bookmarks` is empty.
    4. `returns200_emptyArray_whenNoBookmarks` — inject as user with no bookmarks; assert `bookmarks === []`.
    5. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 5 cases green.
- **Risk**: LOW — identical pattern to GET /api/documents.

---

### P2-23: POST /api/bookmarks

- **Task ID**: P2-23
- **Title**: Create a new bookmark
- **What to build**: Extend `backend/src/routes/bookmarks.ts` to add `POST /api/bookmarks` (JWT required). The handler: (1) Validates required fields: `title` (non-empty string), `target_type` (`'document'`, `'node'`, or `'search'`), `sort_order` (non-empty string) — return 400 if any missing or invalid. (2) Validates target-type consistency: if `target_type === 'document'` or `'node'`, `target_document_id` must be a non-null string; if `target_type === 'node'`, `target_node_id` must also be a non-null string; if `target_type === 'search'`, `query` must be a non-null string. Return 400 `{ error: 'Invalid target combination' }` if these constraints fail. (3) Generates a UUID, inserts the bookmark row with `user_id = req.user.id`, `created_at = updated_at = Date.now()`, HLC columns set to `''`. (4) Returns 201 with the created Bookmark object.
- **Inputs required**: P2-22 (bookmarks.ts exists)
- **Output artifact**: `backend/src/routes/bookmarks.ts` (extended), `backend/src/routes/bookmarks.test.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/bookmarks.test.ts` (append `describe` block)
  - **Test cases** (`describe('POST /api/bookmarks')`):
    1. `returns201_forDocumentBookmark` — inject with `{ title: 'B', target_type: 'document', target_document_id: 'some-uuid', sort_order: 'V' }`; assert status 201, body has `id`, `target_type === 'document'`.
    2. `returns201_forNodeBookmark` — inject with `target_type: 'node'`, both `target_document_id` and `target_node_id` set; assert 201.
    3. `returns201_forSearchBookmark` — inject with `target_type: 'search'`, `query: 'hello'`; assert 201.
    4. `returns400_whenTitleMissing` — inject without `title`; assert status 400.
    5. `returns400_whenTargetTypeInvalid` — inject with `target_type: 'tag'`; assert status 400.
    6. `returns400_forDocumentType_withoutTargetDocumentId` — inject with `target_type: 'document'` and no `target_document_id`; assert status 400.
    7. `returns400_forNodeType_withoutTargetNodeId` — inject with `target_type: 'node'`, `target_document_id` set but no `target_node_id`; assert status 400.
    8. `returns400_forSearchType_withoutQuery` — inject with `target_type: 'search'` and no `query`; assert status 400.
    9. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 9 cases green.
- **Risk**: LOW — insert with target-type consistency validation.

---

### P2-24: PATCH /api/bookmarks/:id

- **Task ID**: P2-24
- **Title**: Update a bookmark's title, sort_order, or query
- **What to build**: Extend `backend/src/routes/bookmarks.ts` to add `PATCH /api/bookmarks/:id` (JWT required). The handler: (1) Queries the bookmark by `params.id` where `user_id = req.user.id` AND `deleted_at IS NULL` — return 404 if not found. (2) Validates at least one of `title`, `sort_order`, or `query` is present in the body — return 400 `{ error: 'No updatable fields supplied' }` otherwise. (3) Note: `target_type`, `target_document_id`, `target_node_id` are immutable after creation — ignore if supplied. (4) Updates only the supplied fields plus `updated_at = Date.now()`. (5) Returns 200 with the updated Bookmark object.
- **Inputs required**: P2-22 (bookmarks.ts exists, bookmark ownership check pattern)
- **Output artifact**: `backend/src/routes/bookmarks.ts` (extended), `backend/src/routes/bookmarks.test.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/bookmarks.test.ts` (append `describe` block)
  - **Test cases** (`describe('PATCH /api/bookmarks/:id')`):
    1. `returns200_updatesTitle` — seed bookmark; inject PATCH with `{ title: 'New Title' }`; assert 200, `title === 'New Title'`.
    2. `returns200_updatesSortOrder` — inject with `{ sort_order: 'b' }`; assert 200.
    3. `targetType_isImmutable` — inject with `{ title: 'X', target_type: 'search' }` on a `document` bookmark; assert `target_type` is still `'document'` in response.
    4. `returns400_whenNoUpdatableFields` — inject with body `{}`; assert status 400.
    5. `returns404_whenBookmarkNotFound` — inject with unknown UUID; assert status 404.
    6. `returns404_whenBookmarkBelongsToOtherUser` — seed bookmark for user B; inject as user A; assert status 404.
    7. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 7 cases green.
- **Risk**: LOW — partial update with immutability enforcement on target fields.

---

### P2-25: DELETE /api/bookmarks/:id

- **Task ID**: P2-25
- **Title**: Soft-delete a bookmark
- **What to build**: Extend `backend/src/routes/bookmarks.ts` to add `DELETE /api/bookmarks/:id` (JWT required). The handler: (1) Queries bookmark by `params.id` where `user_id = req.user.id` AND `deleted_at IS NULL` — return 404 if not found. (2) Sets `deleted_at = Date.now()` on the bookmark row. (3) Returns 200 `{ deleted: true }`.
- **Inputs required**: P2-22 (bookmarks.ts exists)
- **Output artifact**: `backend/src/routes/bookmarks.ts` (extended), `backend/src/routes/bookmarks.test.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/bookmarks.test.ts` (append `describe` block)
  - **Test cases** (`describe('DELETE /api/bookmarks/:id')`):
    1. `returns200_deletedTrue_andSetsTombstone` — seed bookmark; inject DELETE; assert status 200, `{ deleted: true }`, and querying the row shows `deleted_at` is set.
    2. `tombstone_isExcluded_fromGetBookmarks` — after delete, inject GET /api/bookmarks; assert deleted bookmark is not in the list.
    3. `returns404_whenBookmarkNotFound` — inject with unknown UUID; assert status 404.
    4. `returns404_whenBookmarkBelongsToOtherUser` — seed for user B; inject as user A; assert status 404.
    5. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 5 cases green.
- **Risk**: LOW — soft-delete with a single UPDATE and tombstone validation.

---

### P2-26: GET /api/export

- **Task ID**: P2-26
- **Title**: Stream a ZIP archive of the user's entire data
- **What to build**: Create `backend/src/routes/export.ts` as a Fastify plugin. Implement `GET /api/export` (JWT required). The handler: (1) Uses the `archiver` npm package (or Node.js native `zlib` + `tar` — prefer `archiver` for ZIP format). (2) Streams a ZIP with: `documents.json` (all non-deleted documents for the user as a JSON array), one `nodes/<document_uuid>.json` file per document containing all non-deleted nodes for that document, and `attachments/<uuid>.<ext>` for each file found in `UPLOADS_PATH` that matches the user's scope (since no `files` table exists, list all files in the uploads directory and include them — or scope by reading a user-to-file mapping from node content if available; simplest correct implementation is to include all files in the uploads directory). (3) Sets response headers: `Content-Type: application/zip`, `Content-Disposition: attachment; filename="outlinegod-export-<timestamp>.zip"`. (4) Pipes the archiver output stream to the Fastify reply. In tests, mock `fs.readdir` and the archiver library.
- **Inputs required**: P1-7 (documents schema), P1-8 (nodes schema), P1-14 (requireAuth); `archiver` package installed
- **Output artifact**: `backend/src/routes/export.ts` (created), `backend/src/routes/export.test.ts` (created)
- **How to test**:
  - **Test file**: `backend/src/routes/export.test.ts`
  - **Test cases** (`describe('GET /api/export')`):
    1. `returns200_withZipContentType` — seed 1 document + 2 nodes; inject `GET /api/export`; assert status 200 and response header `content-type` contains `application/zip`.
    2. `returns_contentDisposition_withTimestamp` — assert response header `content-disposition` matches `attachment; filename="outlinegod-export-`.
    3. `response_isNonEmptyBuffer` — assert response body byte length > 0.
    4. `returns401_withNoAuthHeader` — inject without Bearer; assert status 401.
  - **Pass criteria**: `pnpm test` exits 0, all 4 cases green.
- **Risk**: MEDIUM — streaming a ZIP in Fastify requires piping an archiver stream to `reply.raw` or using `reply.send(stream)`. The test must receive the full buffered response without timing out. If `archiver` streaming is problematic in tests, use a synchronous in-memory ZIP approach via `jszip` instead.

---

### P2-27: Register all routes in src/index.ts

- **Task ID**: P2-27
- **Title**: Wire all Phase 2 route plugins into the Fastify app under /api prefix
- **What to build**: Update `backend/src/index.ts` (the `buildApp()` factory). Register all Phase 2 route plugins under a `/api` prefix scope using `app.register(async (api) => { api.register(authRoutes); api.register(documentsRoutes); ... }, { prefix: '/api' })`. The specific registrations: `authRoutes` (from `routes/auth.ts`), `documentsRoutes` (from `routes/documents.ts`) — which handles both documents and the conversion route `POST /api/documents/:id/convert-to-node`, `nodesRoutes` (from `routes/nodes.ts`) — which handles nodes and conversion route `POST /api/nodes/:nodeId/convert-to-document`, `syncRoutes` (from `routes/sync.ts`), `filesRoutes` (from `routes/files.ts`), `settingsRoutes` (from `routes/settings.ts`), `bookmarksRoutes` (from `routes/bookmarks.ts`), `exportRoutes` (from `routes/export.ts`). The `GET /health` route registered in P1-14 stays outside the `/api` prefix. After registration, verify that `buildApp()` resolves without error and all routes are listed by `app.printRoutes()`.
- **Inputs required**: All P2-1 through P2-26 (all route files exist), P1-1 (buildApp scaffold)
- **Output artifact**: `backend/src/index.ts` (extended)
- **How to test**:
  - **Test file**: `backend/src/routes/index.test.ts` (new file)
  - **Test cases**:
    1. `buildApp_registersAllApiRoutes` — call `buildApp()`; call `app.printRoutes()` and capture the output string; assert all 26 API route paths are present (e.g., `/api/auth/google`, `/api/documents`, `/api/documents/:id`, `/api/documents/:id/nodes`, `/api/documents/:id/nodes/batch`, `/api/nodes/:nodeId`, `/api/nodes/:nodeId/convert-to-document`, `/api/documents/:id/convert-to-node`, `/api/sync/changes`, `/api/files`, `/api/files/:filename`, `/api/settings`, `/api/bookmarks`, `/api/bookmarks/:id`, `/api/export`, `/health`).
    2. `buildApp_healthRoute_isOutsideApiPrefix` — inject `GET /health`; assert status 200 (verifies health is not double-prefixed as `/api/health`).
    3. `buildApp_unknownRoute_returns404` — inject `GET /api/nonexistent`; assert status 404.
    4. `buildApp_doesNotThrow_onDoubleReady` — call `await app.ready()` twice sequentially; assert no error.
  - **Pass criteria**: `pnpm test` exits 0, all 4 cases green. `pnpm test` across ALL test files (P1-1 through P2-27) exits 0 with zero failures and zero skipped tests.
- **Risk**: LOW — wiring Fastify plugins is deterministic once all route files exist. The only risk is plugin scoping: if `requireAuth` is registered as a global hook inside the `/api` prefix scope, it must not accidentally intercept `/health`.

---

**Phase 2 exit criteria**: All 28 tasks complete. `pnpm test` exits 0 (zero failures, zero skipped tests, all Phase 1 and Phase 2 test files passing). Every route in API.md Appendix §Path Summary is registered and returns the correct status codes for both happy-path and documented error cases. `docker compose build` succeeds. Backend is deployed to `greg@192.168.1.50` and `curl http://192.168.1.50:3000/health` returns `{"status":"ok",...}`.

---

### P2-28: Deploy backend to Docker server

- **Task ID**: P2-28
- **Title**: Deploy containerized backend to production server at 192.168.1.50
- **What to build**: Create `docker-compose.yml` at project root (if not already present) with a single `backend` service: build context `./backend`, expose port `3000`, mount a named volume `outlinergod-data` to `/data` (for SQLite DB and uploads), and pass environment variables from `.env`. Create `backend/Dockerfile`: multi-stage build — stage 1 `pnpm install && pnpm build`, stage 2 copies `dist/` and `node_modules/` into a slim Node 20 Alpine image, runs as non-root user, `CMD ["node", "dist/server.js"]`. Create `.env.example` documenting all required variables: `DATABASE_PATH=/data/outlinegod.db`, `JWT_SECRET=<generate-32-byte-hex>`, `GOOGLE_CLIENT_ID=<from-google-cloud-console>`, `PORT=3000`, `UPLOADS_PATH=/data/uploads`. Then deploy: (1) SSH to `greg@192.168.1.50`; (2) copy `docker-compose.yml`, `backend/`, and a populated `.env` to `/root/outlinergod/`; (3) run `docker compose up -d --build` from `/root/outlinergod/`; (4) verify `curl http://localhost:3000/health` returns 200 with `{"status":"ok",...}`. Generate `JWT_SECRET` with `openssl rand -hex 32`. Push `docker-compose.yml`, `Dockerfile`, and `.env.example` to git (never push `.env` itself).
- **Inputs required**: P2-27 (all routes wired), P1-1 (git repo configured)
- **Output artifact**:
  - `docker-compose.yml`
  - `backend/Dockerfile`
  - `.env.example`
- **How to test**:
  - **Test cases**:
    1. `docker_compose_build_succeeds` — run `docker compose build` locally; assert exit code 0.
    2. `docker_compose_up_starts_container` — run `docker compose up -d`; assert `docker ps` shows the backend container running.
    3. `health_endpoint_responds` — `curl http://192.168.1.50:3000/health`; assert response contains `"status":"ok"`.
    4. `env_example_documentsAllVars` — assert `.env.example` contains `DATABASE_PATH`, `JWT_SECRET`, `GOOGLE_CLIENT_ID`, `PORT`, `UPLOADS_PATH`.
  - **Pass criteria**: Container running on `192.168.1.50:3000`, health check passes.
- **Risk**: MEDIUM — SSH access and network config. If SSH fails, output the `docker-compose.yml` and deployment instructions for Greg to run manually.
