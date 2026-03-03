/**
 * P2-5 through P2-9: Documents CRUD route tests
 *
 * Uses createTestDb() for in-memory SQLite and @fastify/inject for HTTP.
 * JWT helper creates valid tokens for isolated user identities.
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { randomUUID } from 'node:crypto'
import Database from 'better-sqlite3'
import { SignJWT } from 'jose'
import { buildApp } from '../index.js'
import { createTestDb } from '../test-helpers/createTestDb.js'

const TEST_SECRET = 'test-jwt-secret-for-documents-route-tests-must-be-long!!'

async function signTestJwt(userId: string): Promise<string> {
  const secret = new TextEncoder().encode(TEST_SECRET)
  return new SignJWT({ email: 'test@example.com', name: 'Test', picture: '' })
    .setProtectedHeader({ alg: 'HS256' })
    .setSubject(userId)
    .setIssuedAt()
    .setExpirationTime('1h')
    .sign(secret)
}

function seedUser(sqlite: InstanceType<typeof Database>, id: string): void {
  const now = Date.now()
  sqlite
    .prepare(
      'INSERT INTO users (id, google_sub, email, name, picture, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)',
    )
    .run(id, `gsub-${id}`, `${id}@example.com`, 'User', '', now, now)
}

function seedDocument(
  sqlite: InstanceType<typeof Database>,
  overrides: Partial<{
    id: string
    user_id: string
    title: string
    type: string
    parent_id: string | null
    sort_order: string
    collapsed: number
    deleted_at: number | null
  }> = {},
): string {
  const id = overrides.id ?? randomUUID()
  const now = Date.now()
  sqlite
    .prepare(
      `INSERT INTO documents (id, user_id, title, title_hlc, type, parent_id, parent_id_hlc, sort_order, sort_order_hlc, collapsed, collapsed_hlc, deleted_at, deleted_hlc, device_id, created_at, updated_at)
       VALUES (?, ?, ?, '', ?, ?, '', ?, '', ?, '', ?, '', '', ?, ?)`,
    )
    .run(
      id,
      overrides.user_id ?? 'user-a',
      overrides.title ?? 'Test Doc',
      overrides.type ?? 'document',
      overrides.parent_id ?? null,
      overrides.sort_order ?? 'a',
      overrides.collapsed ?? 0,
      overrides.deleted_at ?? null,
      now,
      now,
    )
  return id
}

function seedNode(
  sqlite: InstanceType<typeof Database>,
  documentId: string,
  userId: string,
): string {
  const id = randomUUID()
  const now = Date.now()
  sqlite
    .prepare(
      `INSERT INTO nodes (id, document_id, user_id, content, content_hlc, note, note_hlc, parent_id, parent_id_hlc, sort_order, sort_order_hlc, completed, completed_hlc, color, color_hlc, collapsed, collapsed_hlc, deleted_at, deleted_hlc, device_id, created_at, updated_at)
       VALUES (?, ?, ?, '', '', '', '', NULL, '', 'a', '', 0, '', 0, '', 0, '', NULL, '', '', ?, ?)`,
    )
    .run(id, documentId, userId, now, now)
  return id
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('Document routes', () => {
  let app: ReturnType<typeof buildApp>
  let sqlite: InstanceType<typeof Database>
  let tokenA: string
  let tokenB: string

  beforeEach(async () => {
    process.env.JWT_SECRET = TEST_SECRET

    const testDb = createTestDb()
    sqlite = testDb.sqlite
    app = buildApp(sqlite)
    await app.ready()

    seedUser(sqlite, 'user-a')
    seedUser(sqlite, 'user-b')
    tokenA = await signTestJwt('user-a')
    tokenB = await signTestJwt('user-b')
  })

  afterEach(async () => {
    await app.close()
    sqlite.close()
  })

  // =========================================================================
  // GET /api/documents
  // =========================================================================
  describe('GET /api/documents', () => {
    it('returns200_withItems_forAuthenticatedUser', async () => {
      seedDocument(sqlite, { user_id: 'user-a', title: 'Doc 1' })
      seedDocument(sqlite, { user_id: 'user-a', title: 'Doc 2' })

      const res = await app.inject({
        method: 'GET',
        url: '/api/documents',
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.items).toHaveLength(2)
      expect(body.items[0]).toHaveProperty('id')
      expect(body.items[0]).toHaveProperty('title')
      expect(body.items[0]).toHaveProperty('type')
      expect(body.items[0]).toHaveProperty('sort_order')
    })

    it('excludes_deletedDocuments', async () => {
      seedDocument(sqlite, { user_id: 'user-a', title: 'Active' })
      seedDocument(sqlite, { user_id: 'user-a', title: 'Deleted', deleted_at: Date.now() })

      const res = await app.inject({
        method: 'GET',
        url: '/api/documents',
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      expect(res.json().items).toHaveLength(1)
    })

    it('excludes_otherUsersDocuments', async () => {
      seedDocument(sqlite, { user_id: 'user-a', title: 'A doc' })
      seedDocument(sqlite, { user_id: 'user-b', title: 'B doc' })

      const res = await app.inject({
        method: 'GET',
        url: '/api/documents',
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      expect(res.json().items).toHaveLength(1)
      expect(res.json().items[0].title).toBe('A doc')
    })

    it('returns200_withEmptyArray_whenNoDocs', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/api/documents',
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      expect(res.json().items).toEqual([])
    })

    it('collapsed_isReturnedAsBoolean', async () => {
      seedDocument(sqlite, { user_id: 'user-a', collapsed: 1 })

      const res = await app.inject({
        method: 'GET',
        url: '/api/documents',
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      expect(res.json().items[0].collapsed).toBe(true)
    })

    it('returns401_withNoAuthHeader', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/api/documents',
      })

      expect(res.statusCode).toBe(401)
      expect(res.json()).toEqual({ error: 'Unauthorized' })
    })
  })

  // =========================================================================
  // POST /api/documents
  // =========================================================================
  describe('POST /api/documents', () => {
    it('returns201_withDocumentObject_onValidBody', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/documents',
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ title: 'My Doc', type: 'document', sort_order: 'V' }),
      })

      expect(res.statusCode).toBe(201)
      const body = res.json()
      expect(typeof body.id).toBe('string')
      expect(body.title).toBe('My Doc')
      expect(body.type).toBe('document')
      expect(body.parent_id).toBeNull()
      expect(body.sort_order).toBe('V')
      expect(body.deleted_at).toBeNull()
    })

    it('uses_client_provided_id_when_valid_uuid', async () => {
      const clientId = randomUUID()
      const res = await app.inject({
        method: 'POST',
        url: '/api/documents',
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ id: clientId, title: 'Client ID Doc', type: 'document', sort_order: 'V' }),
      })

      expect(res.statusCode).toBe(201)
      expect(res.json().id).toBe(clientId)
    })

    it('generates_server_id_when_no_id_provided', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/documents',
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ title: 'Server ID Doc', type: 'document', sort_order: 'V' }),
      })

      expect(res.statusCode).toBe(201)
      expect(typeof res.json().id).toBe('string')
    })

    it('creates_folder_withType_folder', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/documents',
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ title: 'My Folder', type: 'folder', sort_order: 'a' }),
      })

      expect(res.statusCode).toBe(201)
      expect(res.json().type).toBe('folder')
    })

    it('creates_child_document_withValidParentId', async () => {
      // Create parent folder first
      const folderRes = await app.inject({
        method: 'POST',
        url: '/api/documents',
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ title: 'Parent Folder', type: 'folder', sort_order: 'a' }),
      })
      const folderId = folderRes.json().id

      const res = await app.inject({
        method: 'POST',
        url: '/api/documents',
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ title: 'Child', type: 'document', sort_order: 'b', parent_id: folderId }),
      })

      expect(res.statusCode).toBe(201)
      expect(res.json().parent_id).toBe(folderId)
    })

    it('returns400_whenTitleMissing', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/documents',
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ type: 'document', sort_order: 'V' }),
      })

      expect(res.statusCode).toBe(400)
      expect(res.json()).toEqual({ error: 'Missing required fields' })
    })

    it('returns400_whenTypeInvalid', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/documents',
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ title: 'X', type: 'heading', sort_order: 'V' }),
      })

      expect(res.statusCode).toBe(400)
      expect(res.json()).toEqual({ error: 'Missing required fields' })
    })

    it('returns400_whenSortOrderMissing', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/documents',
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ title: 'X', type: 'document' }),
      })

      expect(res.statusCode).toBe(400)
      expect(res.json()).toEqual({ error: 'Missing required fields' })
    })

    it('returns404_whenParentIdNotFound', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/documents',
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ title: 'X', type: 'document', sort_order: 'a', parent_id: 'nonexistent-uuid' }),
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'Parent not found' })
    })

    it('returns404_whenParentBelongsToOtherUser', async () => {
      const folderId = seedDocument(sqlite, { user_id: 'user-b', type: 'folder' })

      const res = await app.inject({
        method: 'POST',
        url: '/api/documents',
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ title: 'X', type: 'document', sort_order: 'a', parent_id: folderId }),
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'Parent not found' })
    })

    it('returns401_withNoAuthHeader', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/documents',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ title: 'X', type: 'document', sort_order: 'a' }),
      })

      expect(res.statusCode).toBe(401)
    })
  })

  // =========================================================================
  // GET /api/documents/:id
  // =========================================================================
  describe('GET /api/documents/:id', () => {
    it('returns200_withDocument_onValidId', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a', title: 'Fetched Doc', sort_order: 'z' })

      const res = await app.inject({
        method: 'GET',
        url: `/api/documents/${docId}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.id).toBe(docId)
      expect(body.title).toBe('Fetched Doc')
      expect(body.sort_order).toBe('z')
      expect(body.collapsed).toBe(false)
    })

    it('returns404_whenDocumentNotFound', async () => {
      const res = await app.inject({
        method: 'GET',
        url: `/api/documents/${randomUUID()}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'Document not found' })
    })

    it('returns404_whenDocumentBelongsToOtherUser', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-b' })

      const res = await app.inject({
        method: 'GET',
        url: `/api/documents/${docId}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'Document not found' })
    })

    it('returns404_whenDocumentIsSoftDeleted', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a', deleted_at: Date.now() })

      const res = await app.inject({
        method: 'GET',
        url: `/api/documents/${docId}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'Document not found' })
    })

    it('returns401_withNoAuthHeader', async () => {
      const res = await app.inject({
        method: 'GET',
        url: `/api/documents/${randomUUID()}`,
      })

      expect(res.statusCode).toBe(401)
    })
  })

  // =========================================================================
  // PATCH /api/documents/:id
  // =========================================================================
  describe('PATCH /api/documents/:id', () => {
    it('returns200_updatesTitle', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a', title: 'Old Title' })

      const res = await app.inject({
        method: 'PATCH',
        url: `/api/documents/${docId}`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ title: 'New Title' }),
      })

      expect(res.statusCode).toBe(200)
      expect(res.json().title).toBe('New Title')
    })

    it('returns200_updatesSortOrder', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a', sort_order: 'a' })

      const res = await app.inject({
        method: 'PATCH',
        url: `/api/documents/${docId}`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ sort_order: 'b' }),
      })

      expect(res.statusCode).toBe(200)
      expect(res.json().sort_order).toBe('b')
    })

    it('returns200_updatesCollapsed', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a', collapsed: 0 })

      const res = await app.inject({
        method: 'PATCH',
        url: `/api/documents/${docId}`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ collapsed: true }),
      })

      expect(res.statusCode).toBe(200)
      expect(res.json().collapsed).toBe(true)
    })

    it('returns200_movesDocumentToNewParent', async () => {
      const folderId = seedDocument(sqlite, { user_id: 'user-a', type: 'folder', title: 'Folder' })
      const docId = seedDocument(sqlite, { user_id: 'user-a', title: 'Child' })

      const res = await app.inject({
        method: 'PATCH',
        url: `/api/documents/${docId}`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ parent_id: folderId }),
      })

      expect(res.statusCode).toBe(200)
      expect(res.json().parent_id).toBe(folderId)
    })

    it('returns400_whenNoUpdatableFields', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })

      const res = await app.inject({
        method: 'PATCH',
        url: `/api/documents/${docId}`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({}),
      })

      expect(res.statusCode).toBe(400)
      expect(res.json()).toEqual({ error: 'No updatable fields supplied' })
    })

    it('returns400_onCircularReference', async () => {
      // A is root, B is child of A
      const docA = seedDocument(sqlite, { user_id: 'user-a', title: 'A' })
      const docB = seedDocument(sqlite, { user_id: 'user-a', title: 'B', parent_id: docA })

      // Try to make A a child of B -> circular
      const res = await app.inject({
        method: 'PATCH',
        url: `/api/documents/${docA}`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ parent_id: docB }),
      })

      expect(res.statusCode).toBe(400)
      expect(res.json()).toEqual({ error: 'Circular reference detected' })
    })

    it('returns404_whenDocumentNotFound', async () => {
      const res = await app.inject({
        method: 'PATCH',
        url: `/api/documents/${randomUUID()}`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ title: 'New' }),
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'Document not found' })
    })

    it('returns401_withNoAuthHeader', async () => {
      const res = await app.inject({
        method: 'PATCH',
        url: `/api/documents/${randomUUID()}`,
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ title: 'New' }),
      })

      expect(res.statusCode).toBe(401)
    })
  })

  // =========================================================================
  // DELETE /api/documents/:id
  // =========================================================================
  describe('DELETE /api/documents/:id', () => {
    it('returns200_withDeletedIds_forSingleDocument', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })

      const res = await app.inject({
        method: 'DELETE',
        url: `/api/documents/${docId}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.deleted_ids).toContain(docId)
      expect(body.deleted_ids).toHaveLength(1)

      // Verify deleted_at is set
      const row = sqlite.prepare('SELECT deleted_at FROM documents WHERE id = ?').get(docId) as { deleted_at: number }
      expect(row.deleted_at).toBeGreaterThan(0)
    })

    it('recursivelyDeletesChildDocuments', async () => {
      const folderId = seedDocument(sqlite, { user_id: 'user-a', type: 'folder', title: 'Folder' })
      const child1 = seedDocument(sqlite, { user_id: 'user-a', parent_id: folderId, title: 'Child 1' })
      const child2 = seedDocument(sqlite, { user_id: 'user-a', parent_id: folderId, title: 'Child 2' })

      const res = await app.inject({
        method: 'DELETE',
        url: `/api/documents/${folderId}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.deleted_ids).toHaveLength(3)
      expect(body.deleted_ids).toContain(folderId)
      expect(body.deleted_ids).toContain(child1)
      expect(body.deleted_ids).toContain(child2)

      // All three should have deleted_at set
      for (const id of [folderId, child1, child2]) {
        const row = sqlite.prepare('SELECT deleted_at FROM documents WHERE id = ?').get(id) as { deleted_at: number }
        expect(row.deleted_at).toBeGreaterThan(0)
      }
    })

    it('softDeletesNodesWithinDeletedDocuments', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })
      const nodeId1 = seedNode(sqlite, docId, 'user-a')
      const nodeId2 = seedNode(sqlite, docId, 'user-a')

      const res = await app.inject({
        method: 'DELETE',
        url: `/api/documents/${docId}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)

      // Verify nodes have deleted_at set
      for (const nodeId of [nodeId1, nodeId2]) {
        const row = sqlite.prepare('SELECT deleted_at FROM nodes WHERE id = ?').get(nodeId) as { deleted_at: number }
        expect(row.deleted_at).toBeGreaterThan(0)
      }
    })

    it('returns404_whenDocumentNotFound', async () => {
      const res = await app.inject({
        method: 'DELETE',
        url: `/api/documents/${randomUUID()}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'Document not found' })
    })

    it('returns404_whenDocumentBelongsToOtherUser', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-b' })

      const res = await app.inject({
        method: 'DELETE',
        url: `/api/documents/${docId}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'Document not found' })
    })

    it('returns401_withNoAuthHeader', async () => {
      const res = await app.inject({
        method: 'DELETE',
        url: `/api/documents/${randomUUID()}`,
      })

      expect(res.statusCode).toBe(401)
    })

    it('alreadyDeletedDocument_returns404', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })

      // Delete once
      await app.inject({
        method: 'DELETE',
        url: `/api/documents/${docId}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      // Try to delete again
      const res = await app.inject({
        method: 'DELETE',
        url: `/api/documents/${docId}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'Document not found' })
    })
  })

  // =========================================================================
  // POST /api/documents/:id/convert-to-node
  // =========================================================================
  describe('POST /api/documents/:id/convert-to-node', () => {
    it('returns200_withNewNode', async () => {
      const sourceId = seedDocument(sqlite, { user_id: 'user-a', title: 'MyDoc' })
      const targetId = seedDocument(sqlite, { user_id: 'user-a', title: 'Target Doc' })

      const res = await app.inject({
        method: 'POST',
        url: `/api/documents/${sourceId}/convert-to-node`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ target_document_id: targetId, target_parent_id: null, sort_order: 'a' }),
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.node.content).toBe('MyDoc')
      expect(body.node.document_id).toBe(targetId)
      expect(body.node.parent_id).toBeNull()
      expect(body.node.sort_order).toBe('a')
      expect(body.node.completed).toBe(false)
      expect(body.node.collapsed).toBe(false)
    })

    it('sourceDocs_nodes_areMigrated_toTargetDocument', async () => {
      const sourceId = seedDocument(sqlite, { user_id: 'user-a', title: 'Source' })
      const targetId = seedDocument(sqlite, { user_id: 'user-a', title: 'Target' })
      const node1 = seedNode(sqlite, sourceId, 'user-a')
      const node2 = seedNode(sqlite, sourceId, 'user-a')

      const res = await app.inject({
        method: 'POST',
        url: `/api/documents/${sourceId}/convert-to-node`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ target_document_id: targetId, target_parent_id: null, sort_order: 'a' }),
      })

      expect(res.statusCode).toBe(200)

      // Both nodes should now belong to target document
      const n1 = sqlite.prepare('SELECT document_id, parent_id FROM nodes WHERE id = ?').get(node1) as { document_id: string; parent_id: string | null }
      const n2 = sqlite.prepare('SELECT document_id, parent_id FROM nodes WHERE id = ?').get(node2) as { document_id: string; parent_id: string | null }
      expect(n1.document_id).toBe(targetId)
      expect(n2.document_id).toBe(targetId)

      // Top-level nodes (parent_id was NULL) should now point to the new node
      const newNodeId = res.json().node.id
      expect(n1.parent_id).toBe(newNodeId)
      expect(n2.parent_id).toBe(newNodeId)
    })

    it('sourceDocument_isSoftDeleted', async () => {
      const sourceId = seedDocument(sqlite, { user_id: 'user-a', title: 'Source' })
      const targetId = seedDocument(sqlite, { user_id: 'user-a', title: 'Target' })

      await app.inject({
        method: 'POST',
        url: `/api/documents/${sourceId}/convert-to-node`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ target_document_id: targetId, target_parent_id: null, sort_order: 'a' }),
      })

      const row = sqlite.prepare('SELECT deleted_at FROM documents WHERE id = ?').get(sourceId) as { deleted_at: number | null }
      expect(row.deleted_at).toBeGreaterThan(0)
    })

    it('returns400_whenTargetDocumentIdMissing', async () => {
      const sourceId = seedDocument(sqlite, { user_id: 'user-a', title: 'Source' })

      const res = await app.inject({
        method: 'POST',
        url: `/api/documents/${sourceId}/convert-to-node`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ target_parent_id: null, sort_order: 'a' }),
      })

      expect(res.statusCode).toBe(400)
      expect(res.json()).toEqual({ error: 'Missing required fields' })
    })

    it('returns404_whenSourceDocumentNotFound', async () => {
      const targetId = seedDocument(sqlite, { user_id: 'user-a', title: 'Target' })

      const res = await app.inject({
        method: 'POST',
        url: `/api/documents/${randomUUID()}/convert-to-node`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ target_document_id: targetId, target_parent_id: null, sort_order: 'a' }),
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'Document not found' })
    })

    it('returns404_whenTargetDocumentNotFound', async () => {
      const sourceId = seedDocument(sqlite, { user_id: 'user-a', title: 'Source' })

      const res = await app.inject({
        method: 'POST',
        url: `/api/documents/${sourceId}/convert-to-node`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ target_document_id: randomUUID(), target_parent_id: null, sort_order: 'a' }),
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'Target document not found' })
    })

    it('returns401_withNoAuthHeader', async () => {
      const res = await app.inject({
        method: 'POST',
        url: `/api/documents/${randomUUID()}/convert-to-node`,
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ target_document_id: randomUUID(), target_parent_id: null, sort_order: 'a' }),
      })

      expect(res.statusCode).toBe(401)
    })
  })
})
