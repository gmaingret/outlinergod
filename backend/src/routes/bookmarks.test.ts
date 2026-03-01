/**
 * P2-22 through P2-25: Bookmarks CRUD route tests
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

const TEST_SECRET = 'test-jwt-secret-for-bookmarks-route-tests-must-be-long!!'

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

function seedBookmark(
  sqlite: InstanceType<typeof Database>,
  overrides: Partial<{
    id: string
    user_id: string
    title: string
    target_type: string
    target_document_id: string | null
    target_node_id: string | null
    query: string | null
    sort_order: string
    deleted_at: number | null
  }> = {},
): string {
  const id = overrides.id ?? randomUUID()
  const now = Date.now()
  sqlite
    .prepare(
      `INSERT INTO bookmarks (id, user_id, title, title_hlc, target_type, target_type_hlc,
        target_document_id, target_document_id_hlc, target_node_id, target_node_id_hlc,
        query, query_hlc, sort_order, sort_order_hlc, deleted_at, deleted_hlc,
        device_id, created_at, updated_at)
       VALUES (?, ?, ?, '', ?, '', ?, '', ?, '', ?, '', ?, '', ?, '', '', ?, ?)`,
    )
    .run(
      id,
      overrides.user_id ?? 'user-a',
      overrides.title ?? 'Test Bookmark',
      overrides.target_type ?? 'document',
      overrides.target_document_id ?? 'some-doc-id',
      overrides.target_node_id ?? null,
      overrides.query ?? null,
      overrides.sort_order ?? 'a',
      overrides.deleted_at ?? null,
      now,
      now,
    )
  return id
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('Bookmark routes', () => {
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
  // GET /api/bookmarks
  // =========================================================================
  describe('GET /api/bookmarks', () => {
    it('returns200_withBookmarks', async () => {
      seedBookmark(sqlite, { user_id: 'user-a', title: 'Bookmark 1' })
      seedBookmark(sqlite, { user_id: 'user-a', title: 'Bookmark 2' })

      const res = await app.inject({
        method: 'GET',
        url: '/api/bookmarks',
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.bookmarks).toHaveLength(2)
    })

    it('excludes_softDeletedBookmarks', async () => {
      seedBookmark(sqlite, { user_id: 'user-a', title: 'Active' })
      seedBookmark(sqlite, { user_id: 'user-a', title: 'Deleted', deleted_at: Date.now() })

      const res = await app.inject({
        method: 'GET',
        url: '/api/bookmarks',
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.bookmarks).toHaveLength(1)
      expect(body.bookmarks[0].title).toBe('Active')
    })

    it('excludes_otherUsersBookmarks', async () => {
      seedBookmark(sqlite, { user_id: 'user-b', title: 'B bookmark' })

      const res = await app.inject({
        method: 'GET',
        url: '/api/bookmarks',
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.bookmarks).toHaveLength(0)
    })

    it('returns200_emptyArray_whenNoBookmarks', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/api/bookmarks',
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.bookmarks).toEqual([])
    })

    it('returns401_withNoAuthHeader', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/api/bookmarks',
      })

      expect(res.statusCode).toBe(401)
    })
  })

  // =========================================================================
  // POST /api/bookmarks
  // =========================================================================
  describe('POST /api/bookmarks', () => {
    it('returns201_forDocumentBookmark', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/bookmarks',
        headers: { authorization: `Bearer ${tokenA}` },
        payload: {
          title: 'B',
          target_type: 'document',
          target_document_id: 'some-uuid',
          sort_order: 'V',
        },
      })

      expect(res.statusCode).toBe(201)
      const body = res.json()
      expect(body.id).toBeDefined()
      expect(body.target_type).toBe('document')
      expect(body.target_document_id).toBe('some-uuid')
      expect(body.title).toBe('B')
    })

    it('returns201_forNodeBookmark', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/bookmarks',
        headers: { authorization: `Bearer ${tokenA}` },
        payload: {
          title: 'Node BM',
          target_type: 'node',
          target_document_id: 'doc-uuid',
          target_node_id: 'node-uuid',
          sort_order: 'a',
        },
      })

      expect(res.statusCode).toBe(201)
      const body = res.json()
      expect(body.target_type).toBe('node')
      expect(body.target_document_id).toBe('doc-uuid')
      expect(body.target_node_id).toBe('node-uuid')
    })

    it('returns201_forSearchBookmark', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/bookmarks',
        headers: { authorization: `Bearer ${tokenA}` },
        payload: {
          title: 'Search BM',
          target_type: 'search',
          query: 'hello',
          sort_order: 'b',
        },
      })

      expect(res.statusCode).toBe(201)
      const body = res.json()
      expect(body.target_type).toBe('search')
      expect(body.query).toBe('hello')
    })

    it('returns400_whenTitleMissing', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/bookmarks',
        headers: { authorization: `Bearer ${tokenA}` },
        payload: {
          target_type: 'document',
          target_document_id: 'some-uuid',
          sort_order: 'a',
        },
      })

      expect(res.statusCode).toBe(400)
    })

    it('returns400_whenTargetTypeInvalid', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/bookmarks',
        headers: { authorization: `Bearer ${tokenA}` },
        payload: {
          title: 'Bad',
          target_type: 'tag',
          sort_order: 'a',
        },
      })

      expect(res.statusCode).toBe(400)
    })

    it('returns400_forDocumentType_withoutTargetDocumentId', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/bookmarks',
        headers: { authorization: `Bearer ${tokenA}` },
        payload: {
          title: 'Missing doc id',
          target_type: 'document',
          sort_order: 'a',
        },
      })

      expect(res.statusCode).toBe(400)
      const body = res.json()
      expect(body.error).toBe('Invalid target combination')
    })

    it('returns400_forNodeType_withoutTargetNodeId', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/bookmarks',
        headers: { authorization: `Bearer ${tokenA}` },
        payload: {
          title: 'Missing node id',
          target_type: 'node',
          target_document_id: 'doc-uuid',
          sort_order: 'a',
        },
      })

      expect(res.statusCode).toBe(400)
      const body = res.json()
      expect(body.error).toBe('Invalid target combination')
    })

    it('returns400_forSearchType_withoutQuery', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/bookmarks',
        headers: { authorization: `Bearer ${tokenA}` },
        payload: {
          title: 'Missing query',
          target_type: 'search',
          sort_order: 'a',
        },
      })

      expect(res.statusCode).toBe(400)
      const body = res.json()
      expect(body.error).toBe('Invalid target combination')
    })

    it('returns401_withNoAuthHeader', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/bookmarks',
        payload: { title: 'X', target_type: 'document', target_document_id: 'd', sort_order: 'a' },
      })

      expect(res.statusCode).toBe(401)
    })
  })

  // =========================================================================
  // PATCH /api/bookmarks/:id
  // =========================================================================
  describe('PATCH /api/bookmarks/:id', () => {
    it('returns200_updatesTitle', async () => {
      const bmId = seedBookmark(sqlite, { user_id: 'user-a', title: 'Old Title' })

      const res = await app.inject({
        method: 'PATCH',
        url: `/api/bookmarks/${bmId}`,
        headers: { authorization: `Bearer ${tokenA}` },
        payload: { title: 'New Title' },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.title).toBe('New Title')
    })

    it('returns200_updatesSortOrder', async () => {
      const bmId = seedBookmark(sqlite, { user_id: 'user-a' })

      const res = await app.inject({
        method: 'PATCH',
        url: `/api/bookmarks/${bmId}`,
        headers: { authorization: `Bearer ${tokenA}` },
        payload: { sort_order: 'b' },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.sort_order).toBe('b')
    })

    it('targetType_isImmutable', async () => {
      const bmId = seedBookmark(sqlite, {
        user_id: 'user-a',
        target_type: 'document',
        target_document_id: 'doc-1',
      })

      const res = await app.inject({
        method: 'PATCH',
        url: `/api/bookmarks/${bmId}`,
        headers: { authorization: `Bearer ${tokenA}` },
        payload: { title: 'X', target_type: 'search' },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.target_type).toBe('document')
    })

    it('returns400_whenNoUpdatableFields', async () => {
      const bmId = seedBookmark(sqlite, { user_id: 'user-a' })

      const res = await app.inject({
        method: 'PATCH',
        url: `/api/bookmarks/${bmId}`,
        headers: { authorization: `Bearer ${tokenA}` },
        payload: {},
      })

      expect(res.statusCode).toBe(400)
      const body = res.json()
      expect(body.error).toBe('No updatable fields supplied')
    })

    it('returns404_whenBookmarkNotFound', async () => {
      const res = await app.inject({
        method: 'PATCH',
        url: `/api/bookmarks/${randomUUID()}`,
        headers: { authorization: `Bearer ${tokenA}` },
        payload: { title: 'New Title' },
      })

      expect(res.statusCode).toBe(404)
    })

    it('returns404_whenBookmarkBelongsToOtherUser', async () => {
      const bmId = seedBookmark(sqlite, { user_id: 'user-b' })

      const res = await app.inject({
        method: 'PATCH',
        url: `/api/bookmarks/${bmId}`,
        headers: { authorization: `Bearer ${tokenA}` },
        payload: { title: 'Stolen' },
      })

      expect(res.statusCode).toBe(404)
    })

    it('returns401_withNoAuthHeader', async () => {
      const bmId = seedBookmark(sqlite, { user_id: 'user-a' })

      const res = await app.inject({
        method: 'PATCH',
        url: `/api/bookmarks/${bmId}`,
        payload: { title: 'X' },
      })

      expect(res.statusCode).toBe(401)
    })
  })

  // =========================================================================
  // DELETE /api/bookmarks/:id
  // =========================================================================
  describe('DELETE /api/bookmarks/:id', () => {
    it('returns200_deletedTrue_andSetsTombstone', async () => {
      const bmId = seedBookmark(sqlite, { user_id: 'user-a' })

      const res = await app.inject({
        method: 'DELETE',
        url: `/api/bookmarks/${bmId}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.deleted).toBe(true)

      // Verify tombstone in DB
      const row = sqlite
        .prepare('SELECT deleted_at FROM bookmarks WHERE id = ?')
        .get(bmId) as { deleted_at: number | null }
      expect(row.deleted_at).not.toBeNull()
    })

    it('tombstone_isExcluded_fromGetBookmarks', async () => {
      const bmId = seedBookmark(sqlite, { user_id: 'user-a' })

      // Delete it
      await app.inject({
        method: 'DELETE',
        url: `/api/bookmarks/${bmId}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      // Verify it's excluded from GET
      const res = await app.inject({
        method: 'GET',
        url: '/api/bookmarks',
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.bookmarks).toHaveLength(0)
    })

    it('returns404_whenBookmarkNotFound', async () => {
      const res = await app.inject({
        method: 'DELETE',
        url: `/api/bookmarks/${randomUUID()}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(404)
    })

    it('returns404_whenBookmarkBelongsToOtherUser', async () => {
      const bmId = seedBookmark(sqlite, { user_id: 'user-b' })

      const res = await app.inject({
        method: 'DELETE',
        url: `/api/bookmarks/${bmId}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(404)
    })

    it('returns401_withNoAuthHeader', async () => {
      const bmId = seedBookmark(sqlite, { user_id: 'user-a' })

      const res = await app.inject({
        method: 'DELETE',
        url: `/api/bookmarks/${bmId}`,
      })

      expect(res.statusCode).toBe(401)
    })
  })
})
