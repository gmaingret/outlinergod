/**
 * P2-10 through P2-13: Nodes route tests
 *
 * Uses createTestDb() for in-memory SQLite and @fastify/inject for HTTP.
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { randomUUID } from 'node:crypto'
import Database from 'better-sqlite3'
import { SignJWT } from 'jose'
import { buildApp } from '../index.js'
import { createTestDb } from '../test-helpers/createTestDb.js'

const TEST_SECRET = 'test-jwt-secret-for-nodes-route-tests-must-be-long!!'

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
  overrides: Partial<{
    id: string
    document_id: string
    user_id: string
    content: string
    note: string
    parent_id: string | null
    sort_order: string
    completed: number
    color: number
    collapsed: number
    deleted_at: number | null
  }>,
): string {
  const id = overrides.id ?? randomUUID()
  const now = Date.now()
  sqlite
    .prepare(
      `INSERT INTO nodes (id, document_id, user_id, content, content_hlc, note, note_hlc, parent_id, parent_id_hlc, sort_order, sort_order_hlc, completed, completed_hlc, color, color_hlc, collapsed, collapsed_hlc, deleted_at, deleted_hlc, device_id, created_at, updated_at)
       VALUES (?, ?, ?, ?, '', ?, '', ?, '', ?, '', ?, '', ?, '', ?, '', ?, '', '', ?, ?)`,
    )
    .run(
      id,
      overrides.document_id ?? '',
      overrides.user_id ?? 'user-a',
      overrides.content ?? '',
      overrides.note ?? '',
      overrides.parent_id ?? null,
      overrides.sort_order ?? 'a',
      overrides.completed ?? 0,
      overrides.color ?? 0,
      overrides.collapsed ?? 0,
      overrides.deleted_at ?? null,
      now,
      now,
    )
  return id
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('Node routes', () => {
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
  // GET /api/documents/:id/nodes
  // =========================================================================
  describe('GET /api/documents/:id/nodes', () => {
    it('returns200_withNodes_forValidDocument', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })
      seedNode(sqlite, { document_id: docId, user_id: 'user-a', content: 'Node 1' })
      seedNode(sqlite, { document_id: docId, user_id: 'user-a', content: 'Node 2' })
      seedNode(sqlite, { document_id: docId, user_id: 'user-a', content: 'Node 3' })

      const res = await app.inject({
        method: 'GET',
        url: `/api/documents/${docId}/nodes`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.nodes).toHaveLength(3)
      expect(body.nodes[0]).toHaveProperty('id')
      expect(body.nodes[0]).toHaveProperty('document_id')
      expect(body.nodes[0]).toHaveProperty('content')
      expect(body.nodes[0]).toHaveProperty('sort_order')
    })

    it('excludes_deletedNodes', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })
      seedNode(sqlite, { document_id: docId, user_id: 'user-a', content: 'Active 1' })
      seedNode(sqlite, { document_id: docId, user_id: 'user-a', content: 'Active 2' })
      seedNode(sqlite, { document_id: docId, user_id: 'user-a', content: 'Deleted', deleted_at: Date.now() })

      const res = await app.inject({
        method: 'GET',
        url: `/api/documents/${docId}/nodes`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      expect(res.json().nodes).toHaveLength(2)
    })

    it('returns200_emptyArray_whenNoNodes', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })

      const res = await app.inject({
        method: 'GET',
        url: `/api/documents/${docId}/nodes`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      expect(res.json().nodes).toEqual([])
    })

    it('completed_isReturnedAsBoolean', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })
      seedNode(sqlite, { document_id: docId, user_id: 'user-a', completed: 1 })

      const res = await app.inject({
        method: 'GET',
        url: `/api/documents/${docId}/nodes`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      expect(res.json().nodes[0].completed).toBe(true)
    })

    it('collapsed_isReturnedAsBoolean', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })
      seedNode(sqlite, { document_id: docId, user_id: 'user-a', collapsed: 1 })

      const res = await app.inject({
        method: 'GET',
        url: `/api/documents/${docId}/nodes`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      expect(res.json().nodes[0].collapsed).toBe(true)
    })

    it('returns404_whenDocumentNotFound', async () => {
      const res = await app.inject({
        method: 'GET',
        url: `/api/documents/${randomUUID()}/nodes`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'Document not found' })
    })

    it('returns404_whenDocumentBelongsToOtherUser', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-b' })

      const res = await app.inject({
        method: 'GET',
        url: `/api/documents/${docId}/nodes`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'Document not found' })
    })

    it('returns401_withNoAuthHeader', async () => {
      const res = await app.inject({
        method: 'GET',
        url: `/api/documents/${randomUUID()}/nodes`,
      })

      expect(res.statusCode).toBe(401)
      expect(res.json()).toEqual({ error: 'Unauthorized' })
    })
  })

  // =========================================================================
  // POST /api/documents/:id/nodes/batch
  // =========================================================================
  describe('POST /api/documents/:id/nodes/batch', () => {
    function makeValidNode(overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
      return {
        id: randomUUID(),
        content: 'Test content',
        note: '',
        parent_id: null,
        sort_order: 'a',
        completed: false,
        color: 0,
        collapsed: false,
        ...overrides,
      }
    }

    it('returns200_withUpsertedCount', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })
      const nodes = [makeValidNode(), makeValidNode(), makeValidNode()]

      const res = await app.inject({
        method: 'POST',
        url: `/api/documents/${docId}/nodes/batch`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ nodes }),
      })

      expect(res.statusCode).toBe(200)
      expect(res.json().upserted).toBe(3)

      // Verify nodes are readable via GET
      const getRes = await app.inject({
        method: 'GET',
        url: `/api/documents/${docId}/nodes`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(getRes.json().nodes).toHaveLength(3)
    })

    it('upserts_existingNodes_byId', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })
      const nodeId = randomUUID()

      // Insert first
      await app.inject({
        method: 'POST',
        url: `/api/documents/${docId}/nodes/batch`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ nodes: [makeValidNode({ id: nodeId, content: 'Original' })] }),
      })

      // Upsert with same id but different content
      const res = await app.inject({
        method: 'POST',
        url: `/api/documents/${docId}/nodes/batch`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ nodes: [makeValidNode({ id: nodeId, content: 'Updated' })] }),
      })

      expect(res.statusCode).toBe(200)

      // Verify content was updated
      const getRes = await app.inject({
        method: 'GET',
        url: `/api/documents/${docId}/nodes`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      const nodes = getRes.json().nodes
      expect(nodes).toHaveLength(1)
      expect(nodes[0].content).toBe('Updated')
    })

    it('returns400_whenNodesMissing', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })

      const res = await app.inject({
        method: 'POST',
        url: `/api/documents/${docId}/nodes/batch`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({}),
      })

      expect(res.statusCode).toBe(400)
      expect(res.json()).toEqual({ error: 'nodes must be a non-empty array' })
    })

    it('returns400_whenNodesExceed500', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })
      const nodes = Array.from({ length: 501 }, () => makeValidNode())

      const res = await app.inject({
        method: 'POST',
        url: `/api/documents/${docId}/nodes/batch`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ nodes }),
      })

      expect(res.statusCode).toBe(400)
      expect(res.json().error).toContain('500')
    })

    it('returns400_onMalformedNode', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })
      const nodes = [
        makeValidNode(),
        { id: randomUUID(), content: 'No sort_order', note: '', parent_id: null, completed: false, color: 0, collapsed: false },
      ]

      const res = await app.inject({
        method: 'POST',
        url: `/api/documents/${docId}/nodes/batch`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ nodes }),
      })

      expect(res.statusCode).toBe(400)
      expect(res.json().error).toContain('index')
    })

    it('returns400_whenColorOutOfRange', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })
      const nodes = [makeValidNode({ color: 7 })]

      const res = await app.inject({
        method: 'POST',
        url: `/api/documents/${docId}/nodes/batch`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ nodes }),
      })

      expect(res.statusCode).toBe(400)
    })

    it('returns404_whenDocumentNotFound', async () => {
      const res = await app.inject({
        method: 'POST',
        url: `/api/documents/${randomUUID()}/nodes/batch`,
        headers: { 'content-type': 'application/json', authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ nodes: [makeValidNode()] }),
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'Document not found' })
    })

    it('returns401_withNoAuthHeader', async () => {
      const res = await app.inject({
        method: 'POST',
        url: `/api/documents/${randomUUID()}/nodes/batch`,
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ nodes: [makeValidNode()] }),
      })

      expect(res.statusCode).toBe(401)
    })
  })

  // =========================================================================
  // DELETE /api/nodes/:nodeId
  // =========================================================================
  describe('DELETE /api/nodes/:nodeId', () => {
    it('returns200_withDeletedIds_forLeafNode', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })
      const nodeId = seedNode(sqlite, { document_id: docId, user_id: 'user-a', content: 'Leaf' })

      const res = await app.inject({
        method: 'DELETE',
        url: `/api/nodes/${nodeId}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.deleted_ids).toContain(nodeId)
      expect(body.deleted_ids).toHaveLength(1)

      // Verify deleted_at is set
      const row = sqlite.prepare('SELECT deleted_at FROM nodes WHERE id = ?').get(nodeId) as { deleted_at: number }
      expect(row.deleted_at).toBeGreaterThan(0)
    })

    it('recursivelyDeletesChildNodes', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })
      const parentId = seedNode(sqlite, { document_id: docId, user_id: 'user-a', content: 'Parent' })
      const child1 = seedNode(sqlite, { document_id: docId, user_id: 'user-a', content: 'Child 1', parent_id: parentId })
      const child2 = seedNode(sqlite, { document_id: docId, user_id: 'user-a', content: 'Child 2', parent_id: parentId })

      const res = await app.inject({
        method: 'DELETE',
        url: `/api/nodes/${parentId}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.deleted_ids).toHaveLength(3)
      expect(body.deleted_ids).toContain(parentId)
      expect(body.deleted_ids).toContain(child1)
      expect(body.deleted_ids).toContain(child2)

      // All three should have deleted_at set
      for (const id of [parentId, child1, child2]) {
        const row = sqlite.prepare('SELECT deleted_at FROM nodes WHERE id = ?').get(id) as { deleted_at: number }
        expect(row.deleted_at).toBeGreaterThan(0)
      }
    })

    it('returns404_whenNodeNotFound', async () => {
      const res = await app.inject({
        method: 'DELETE',
        url: `/api/nodes/${randomUUID()}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'Node not found' })
    })

    it('returns404_whenNodeBelongsToOtherUser', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-b' })
      const nodeId = seedNode(sqlite, { document_id: docId, user_id: 'user-b', content: 'Other user node' })

      const res = await app.inject({
        method: 'DELETE',
        url: `/api/nodes/${nodeId}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'Node not found' })
    })

    it('returns401_withNoAuthHeader', async () => {
      const res = await app.inject({
        method: 'DELETE',
        url: `/api/nodes/${randomUUID()}`,
      })

      expect(res.statusCode).toBe(401)
    })
  })

  // =========================================================================
  // POST /api/nodes/:nodeId/convert-to-document
  // =========================================================================
  describe('POST /api/nodes/:nodeId/convert-to-document', () => {
    it('returns200_withNewDocument', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })
      const nodeId = seedNode(sqlite, { document_id: docId, user_id: 'user-a', content: 'My Note', sort_order: 'b' })

      const res = await app.inject({
        method: 'POST',
        url: `/api/nodes/${nodeId}/convert-to-document`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.document.title).toBe('My Note')
      expect(body.document.type).toBe('document')
      expect(body.document.parent_id).toBeNull()
      expect(body.document.sort_order).toBe('b')
    })

    it('childNodes_areMigrated_toNewDocument', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })
      const parentId = seedNode(sqlite, { document_id: docId, user_id: 'user-a', content: 'Parent Node' })
      const child1 = seedNode(sqlite, { document_id: docId, user_id: 'user-a', content: 'Child 1', parent_id: parentId })
      const child2 = seedNode(sqlite, { document_id: docId, user_id: 'user-a', content: 'Child 2', parent_id: parentId })

      const res = await app.inject({
        method: 'POST',
        url: `/api/nodes/${parentId}/convert-to-document`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const newDocId = res.json().document.id

      // Verify children were migrated to new document
      const child1Row = sqlite.prepare('SELECT document_id, parent_id FROM nodes WHERE id = ?').get(child1) as { document_id: string; parent_id: string | null }
      const child2Row = sqlite.prepare('SELECT document_id, parent_id FROM nodes WHERE id = ?').get(child2) as { document_id: string; parent_id: string | null }

      expect(child1Row.document_id).toBe(newDocId)
      expect(child2Row.document_id).toBe(newDocId)
      // Direct children should have parent_id set to NULL (they become roots of the new doc)
      expect(child1Row.parent_id).toBeNull()
      expect(child2Row.parent_id).toBeNull()
    })

    it('originalNode_isSoftDeleted', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })
      const nodeId = seedNode(sqlite, { document_id: docId, user_id: 'user-a', content: 'To Convert' })

      await app.inject({
        method: 'POST',
        url: `/api/nodes/${nodeId}/convert-to-document`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      const row = sqlite.prepare('SELECT deleted_at FROM nodes WHERE id = ?').get(nodeId) as { deleted_at: number }
      expect(row.deleted_at).toBeGreaterThan(0)
    })

    it('returns409_whenNodeContentIsEmpty', async () => {
      const docId = seedDocument(sqlite, { user_id: 'user-a' })
      const nodeId = seedNode(sqlite, { document_id: docId, user_id: 'user-a', content: '' })

      const res = await app.inject({
        method: 'POST',
        url: `/api/nodes/${nodeId}/convert-to-document`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(409)
      expect(res.json()).toEqual({ error: 'Node has no content' })
    })

    it('returns404_whenNodeNotFound', async () => {
      const res = await app.inject({
        method: 'POST',
        url: `/api/nodes/${randomUUID()}/convert-to-document`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'Node not found' })
    })

    it('returns401_withNoAuthHeader', async () => {
      const res = await app.inject({
        method: 'POST',
        url: `/api/nodes/${randomUUID()}/convert-to-document`,
      })

      expect(res.statusCode).toBe(401)
    })
  })
})
