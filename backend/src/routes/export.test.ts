/**
 * P2-26: GET /api/export — ZIP archive of user data
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { randomUUID } from 'node:crypto'
import Database from 'better-sqlite3'
import { SignJWT } from 'jose'
import { buildApp } from '../index.js'
import { createTestDb } from '../test-helpers/createTestDb.js'

const TEST_SECRET = 'test-jwt-secret-for-export-route-tests-must-be-long-enough!!'

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
  }> = {},
): string {
  const id = overrides.id ?? randomUUID()
  const now = Date.now()
  sqlite
    .prepare(
      `INSERT INTO documents (id, user_id, title, title_hlc, type, parent_id, parent_id_hlc, sort_order, sort_order_hlc, collapsed, collapsed_hlc, deleted_at, deleted_hlc, device_id, created_at, updated_at)
       VALUES (?, ?, ?, '', 'document', NULL, '', 'a', '', 0, '', NULL, '', '', ?, ?)`,
    )
    .run(id, overrides.user_id ?? 'user-a', overrides.title ?? 'Test Doc', now, now)
  return id
}

function seedNode(
  sqlite: InstanceType<typeof Database>,
  overrides: Partial<{
    id: string
    document_id: string
    user_id: string
    content: string
  }> = {},
): string {
  const id = overrides.id ?? randomUUID()
  const now = Date.now()
  sqlite
    .prepare(
      `INSERT INTO nodes (id, document_id, user_id, content, content_hlc, note, note_hlc, parent_id, parent_id_hlc, sort_order, sort_order_hlc, completed, completed_hlc, color, color_hlc, collapsed, collapsed_hlc, deleted_at, deleted_hlc, device_id, created_at, updated_at)
       VALUES (?, ?, ?, ?, '', '', '', NULL, '', 'a', '', 0, '', 0, '', 0, '', NULL, '', '', ?, ?)`,
    )
    .run(
      id,
      overrides.document_id ?? 'doc-1',
      overrides.user_id ?? 'user-a',
      overrides.content ?? 'Node content',
      now,
      now,
    )
  return id
}

describe('GET /api/export', () => {
  let app: ReturnType<typeof buildApp>
  let sqlite: InstanceType<typeof Database>
  let tokenA: string

  beforeEach(async () => {
    process.env.JWT_SECRET = TEST_SECRET
    delete process.env.UPLOADS_PATH

    const testDb = createTestDb()
    sqlite = testDb.sqlite
    app = buildApp(sqlite)
    await app.ready()

    seedUser(sqlite, 'user-a')
    tokenA = await signTestJwt('user-a')
  })

  afterEach(async () => {
    await app.close()
    sqlite.close()
  })

  it('returns200_withZipContentType', async () => {
    const docId = seedDocument(sqlite, { user_id: 'user-a' })
    seedNode(sqlite, { document_id: docId, user_id: 'user-a', content: 'Node 1' })
    seedNode(sqlite, { document_id: docId, user_id: 'user-a', content: 'Node 2' })

    const res = await app.inject({
      method: 'GET',
      url: '/api/export',
      headers: { authorization: `Bearer ${tokenA}` },
    })

    expect(res.statusCode).toBe(200)
    expect(res.headers['content-type']).toContain('application/zip')
  })

  it('returns_contentDisposition_withTimestamp', async () => {
    seedDocument(sqlite, { user_id: 'user-a' })

    const res = await app.inject({
      method: 'GET',
      url: '/api/export',
      headers: { authorization: `Bearer ${tokenA}` },
    })

    expect(res.headers['content-disposition']).toMatch(
      /^attachment; filename="outlinegod-export-\d+\.zip"$/,
    )
  })

  it('response_isNonEmptyBuffer', async () => {
    seedDocument(sqlite, { user_id: 'user-a' })

    const res = await app.inject({
      method: 'GET',
      url: '/api/export',
      headers: { authorization: `Bearer ${tokenA}` },
    })

    expect(res.rawPayload.byteLength).toBeGreaterThan(0)
  })

  it('returns401_withNoAuthHeader', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/api/export',
    })

    expect(res.statusCode).toBe(401)
  })
})
