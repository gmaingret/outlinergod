/**
 * P2-17 through P2-19: Files routes tests
 *
 * Uses createTestDb() for in-memory SQLite and @fastify/inject for HTTP.
 * fs operations are mocked via vi.mock('node:fs/promises').
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { randomUUID } from 'node:crypto'
import Database from 'better-sqlite3'
import { SignJWT } from 'jose'
import { buildApp } from '../index.js'
import { createTestDb } from '../test-helpers/createTestDb.js'

// Mock the fs/promises module before any imports that use it
vi.mock('node:fs/promises', () => ({
  writeFile: vi.fn().mockResolvedValue(undefined),
  stat: vi.fn().mockResolvedValue({ size: 100 }),
  readFile: vi.fn().mockResolvedValue(Buffer.from('fake-file-data')),
  rm: vi.fn().mockResolvedValue(undefined),
}))

// Import the mocked module for assertions
import { writeFile, stat, readFile, rm } from 'node:fs/promises'

const TEST_SECRET = 'test-jwt-secret-for-files-route-tests-must-be-long!!'

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

function seedFileRow(
  sqlite: InstanceType<typeof Database>,
  filename: string,
  userId: string,
): void {
  const now = Date.now()
  sqlite
    .prepare(
      'INSERT INTO files (filename, user_id, node_id, mime_type, size, created_at) VALUES (?, ?, ?, ?, ?, ?)',
    )
    .run(filename, userId, null, 'image/jpeg', 100, now)
}

function buildMultipartBody(
  boundary: string,
  opts: { fieldName?: string; filename?: string; contentType?: string; content?: string } = {},
): string {
  const fieldName = opts.fieldName ?? 'file'
  const filename = opts.filename ?? 'test.jpg'
  const contentType = opts.contentType ?? 'image/jpeg'
  const content = opts.content ?? 'fake-image-bytes'

  return [
    `--${boundary}`,
    `Content-Disposition: form-data; name="${fieldName}"; filename="${filename}"`,
    `Content-Type: ${contentType}`,
    '',
    content,
    `--${boundary}--`,
  ].join('\r\n')
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('Files routes', () => {
  let app: ReturnType<typeof buildApp>
  let sqlite: InstanceType<typeof Database>
  let tokenA: string
  let tokenB: string

  beforeEach(async () => {
    process.env.JWT_SECRET = TEST_SECRET
    process.env.UPLOADS_PATH = '/tmp/test-uploads'

    vi.mocked(writeFile).mockResolvedValue(undefined)
    vi.mocked(stat).mockResolvedValue({ size: 100 } as any)
    vi.mocked(readFile).mockResolvedValue(Buffer.from('fake-file-data'))
    vi.mocked(rm).mockResolvedValue(undefined)

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
    vi.restoreAllMocks()
  })

  // =========================================================================
  // GET /api/files
  // =========================================================================
  describe('GET /api/files', () => {
    it('returns200_withFileList_forAuthenticatedUser', async () => {
      const filename = '3fa85f64-5717-4562-b3fc-2c963f66afa6.jpg'
      seedFileRow(sqlite, filename, 'user-a')

      const res = await app.inject({
        method: 'GET',
        url: '/api/files',
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const json = res.json()
      expect(Array.isArray(json.files)).toBe(true)
      expect(json.files).toHaveLength(1)
      expect(json.files[0].filename).toBe(filename)
      expect(json.files[0].mime_type).toBe('image/jpeg')
    })

    it('returns_only_files_for_authenticated_user', async () => {
      seedFileRow(sqlite, '3fa85f64-5717-4562-b3fc-2c963f66afa6.jpg', 'user-a')
      seedFileRow(sqlite, '00000000-0000-0000-0000-000000000001.png', 'user-b')

      const res = await app.inject({
        method: 'GET',
        url: '/api/files',
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const json = res.json()
      expect(json.files).toHaveLength(1)
      expect(json.files[0].filename).toBe('3fa85f64-5717-4562-b3fc-2c963f66afa6.jpg')
    })

    it('returns401_withNoAuthHeader', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/api/files',
      })

      expect(res.statusCode).toBe(401)
      expect(res.json()).toEqual({ error: 'Unauthorized' })
    })
  })

  // =========================================================================
  // POST /api/files
  // =========================================================================
  describe('POST /api/files', () => {
    it('returns201_withFileMetadata_onValidUpload', async () => {
      const boundary = 'test-boundary-201'
      const body = buildMultipartBody(boundary)

      const res = await app.inject({
        method: 'POST',
        url: '/api/files',
        headers: {
          authorization: `Bearer ${tokenA}`,
          'content-type': `multipart/form-data; boundary=${boundary}`,
        },
        body,
      })

      expect(res.statusCode).toBe(201)
      const json = res.json()
      expect(json.url).toMatch(/^\/api\/files\/[0-9a-f-]{36}\.[a-z0-9]+$/)
      expect(typeof json.uuid).toBe('string')
      expect(typeof json.filename).toBe('string')
      expect(typeof json.size).toBe('number')
      expect(typeof json.mime_type).toBe('string')
      expect(vi.mocked(writeFile)).toHaveBeenCalled()
    })

    it('filename_matchesUuidPattern', async () => {
      const boundary = 'test-boundary-uuid'
      const body = buildMultipartBody(boundary)

      const res = await app.inject({
        method: 'POST',
        url: '/api/files',
        headers: {
          authorization: `Bearer ${tokenA}`,
          'content-type': `multipart/form-data; boundary=${boundary}`,
        },
        body,
      })

      expect(res.statusCode).toBe(201)
      const json = res.json()
      expect(json.filename).toMatch(/^[0-9a-f-]{36}\.[a-z0-9]+$/)
    })

    it('returns400_whenFilePartMissing', async () => {
      const boundary = 'test-boundary-400'
      // Send a multipart body with no file part (just a text field)
      const body = [
        `--${boundary}`,
        'Content-Disposition: form-data; name="notafile"',
        '',
        'some-text-value',
        `--${boundary}--`,
      ].join('\r\n')

      const res = await app.inject({
        method: 'POST',
        url: '/api/files',
        headers: {
          authorization: `Bearer ${tokenA}`,
          'content-type': `multipart/form-data; boundary=${boundary}`,
        },
        body,
      })

      expect(res.statusCode).toBe(400)
      expect(res.json()).toEqual({ error: 'Missing file' })
    })

    it('returns413_whenFileTooLarge', async () => {
      // Set a very low limit
      process.env.MAX_UPLOAD_BYTES = '10'

      // Need a fresh app instance with the new limit
      await app.close()
      sqlite.close()

      const testDb = createTestDb()
      sqlite = testDb.sqlite
      app = buildApp(sqlite)
      await app.ready()

      seedUser(sqlite, 'user-a')
      tokenA = await signTestJwt('user-a')

      const boundary = 'test-boundary-413'
      const body = buildMultipartBody(boundary, {
        content: 'this-content-is-definitely-longer-than-10-bytes',
      })

      const res = await app.inject({
        method: 'POST',
        url: '/api/files',
        headers: {
          authorization: `Bearer ${tokenA}`,
          'content-type': `multipart/form-data; boundary=${boundary}`,
        },
        body,
      })

      expect(res.statusCode).toBe(413)

      // Clean up env
      delete process.env.MAX_UPLOAD_BYTES
    })

    it('returns401_withNoAuthHeader', async () => {
      const boundary = 'test-boundary-401'
      const body = buildMultipartBody(boundary)

      const res = await app.inject({
        method: 'POST',
        url: '/api/files',
        headers: {
          'content-type': `multipart/form-data; boundary=${boundary}`,
        },
        body,
      })

      expect(res.statusCode).toBe(401)
      expect(res.json()).toEqual({ error: 'Unauthorized' })
    })

    it('returns404_whenNodeIdDoesNotBelongToUser', async () => {
      // Seed a node belonging to user-b
      const now = Date.now()
      const nodeId = '11111111-1111-1111-1111-111111111111'
      const docId = '22222222-2222-2222-2222-222222222222'
      sqlite
        .prepare(
          `INSERT INTO documents (id, user_id, title, title_hlc, type, parent_id, parent_id_hlc, sort_order, sort_order_hlc, collapsed, collapsed_hlc, deleted_at, deleted_hlc, device_id, created_at, updated_at)
           VALUES (?, ?, 'Doc', '', 'document', NULL, '', 'a', '', 0, '', NULL, '', '', ?, ?)`,
        )
        .run(docId, 'user-b', now, now)
      sqlite
        .prepare(
          `INSERT INTO nodes (id, document_id, user_id, content, content_hlc, note, note_hlc, parent_id, parent_id_hlc, sort_order, sort_order_hlc, completed, completed_hlc, color, color_hlc, collapsed, collapsed_hlc, deleted_at, deleted_hlc, device_id, created_at, updated_at)
           VALUES (?, ?, ?, '', '', '', '', NULL, '', 'a', '', 0, '', 0, '', 0, '', NULL, '', '', ?, ?)`,
        )
        .run(nodeId, docId, 'user-b', now, now)

      // user-a tries to attach a file to user-b's node
      const boundary = 'test-boundary-node-ownership'
      const bodyParts = [
        `--${boundary}`,
        `Content-Disposition: form-data; name="file"; filename="test.jpg"`,
        `Content-Type: image/jpeg`,
        '',
        'fake-image-bytes',
        `--${boundary}`,
        `Content-Disposition: form-data; name="node_id"`,
        '',
        nodeId,
        `--${boundary}--`,
      ].join('\r\n')

      const res = await app.inject({
        method: 'POST',
        url: '/api/files',
        headers: {
          authorization: `Bearer ${tokenA}`,
          'content-type': `multipart/form-data; boundary=${boundary}`,
        },
        body: bodyParts,
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'Node not found' })
    })
  })

  // =========================================================================
  // POST /api/files/upload (alias)
  // =========================================================================
  describe('POST /api/files/upload', () => {
    it('returns201_withFileMetadata_onValidUpload', async () => {
      const boundary = 'test-boundary-upload-alias'
      const body = buildMultipartBody(boundary)

      const res = await app.inject({
        method: 'POST',
        url: '/api/files/upload',
        headers: {
          authorization: `Bearer ${tokenA}`,
          'content-type': `multipart/form-data; boundary=${boundary}`,
        },
        body,
      })

      expect(res.statusCode).toBe(201)
      const json = res.json()
      expect(json.url).toMatch(/^\/api\/files\/[0-9a-f-]{36}\.[a-z0-9]+$/)
    })

    it('returns401_withNoAuthHeader', async () => {
      const boundary = 'test-boundary-upload-alias-401'
      const body = buildMultipartBody(boundary)

      const res = await app.inject({
        method: 'POST',
        url: '/api/files/upload',
        headers: {
          'content-type': `multipart/form-data; boundary=${boundary}`,
        },
        body,
      })

      expect(res.statusCode).toBe(401)
    })
  })

  // =========================================================================
  // GET /api/files/:filename
  // =========================================================================
  describe('GET /api/files/:filename', () => {
    const validFilename = '3fa85f64-5717-4562-b3fc-2c963f66afa6.jpg'

    it('returns200_withFileBytes_onValidFilename', async () => {
      seedFileRow(sqlite, validFilename, 'user-a')

      const res = await app.inject({
        method: 'GET',
        url: `/api/files/${validFilename}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      expect(res.headers['content-type']).toContain('image/jpeg')
    })

    it('returns_cacheControl_immutable', async () => {
      seedFileRow(sqlite, validFilename, 'user-a')

      const res = await app.inject({
        method: 'GET',
        url: `/api/files/${validFilename}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const cacheControl = res.headers['cache-control'] as string
      expect(cacheControl).toContain('max-age=2592000')
      expect(cacheControl).toContain('immutable')
    })

    it('returns400_whenFilenamePatternInvalid', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/api/files/not-a-valid-filename.txt',
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(400)
      expect(res.json()).toEqual({ error: 'Invalid filename' })
    })

    it('returns400_whenFilenameHasNoExtension', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/api/files/3fa85f64-5717-4562-b3fc-2c963f66afa6',
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(400)
      expect(res.json()).toEqual({ error: 'Invalid filename' })
    })

    it('returns404_whenFileNotFound', async () => {
      vi.mocked(stat).mockRejectedValueOnce(
        Object.assign(new Error('ENOENT'), { code: 'ENOENT' }),
      )

      const missingFile = '00000000-0000-0000-0000-000000000000.jpg'
      seedFileRow(sqlite, missingFile, 'user-a')

      const res = await app.inject({
        method: 'GET',
        url: `/api/files/${missingFile}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'File not found' })
    })

    it('returns401_withNoAuthHeader', async () => {
      const res = await app.inject({
        method: 'GET',
        url: `/api/files/${validFilename}`,
      })

      expect(res.statusCode).toBe(401)
      expect(res.json()).toEqual({ error: 'Unauthorized' })
    })
  })

  // =========================================================================
  // DELETE /api/files/:filename
  // =========================================================================
  describe('DELETE /api/files/:filename', () => {
    const validFilename = '3fa85f64-5717-4562-b3fc-2c963f66afa6.jpg'

    it('returns200_deletedTrue_onSuccess', async () => {
      const res = await app.inject({
        method: 'DELETE',
        url: `/api/files/${validFilename}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      expect(res.json()).toEqual({ deleted: true })
      expect(vi.mocked(rm)).toHaveBeenCalledWith(
        expect.stringContaining(validFilename),
      )
    })

    it('returns400_whenFilenamePatternInvalid', async () => {
      const res = await app.inject({
        method: 'DELETE',
        url: '/api/files/not-a-valid-filename.txt',
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(400)
    })

    it('returns404_whenFileNotFound', async () => {
      vi.mocked(stat).mockRejectedValueOnce(
        Object.assign(new Error('ENOENT'), { code: 'ENOENT' }),
      )

      const res = await app.inject({
        method: 'DELETE',
        url: `/api/files/${validFilename}`,
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(404)
      expect(res.json()).toEqual({ error: 'File not found' })
    })

    it('returns401_withNoAuthHeader', async () => {
      const res = await app.inject({
        method: 'DELETE',
        url: `/api/files/${validFilename}`,
      })

      expect(res.statusCode).toBe(401)
      expect(res.json()).toEqual({ error: 'Unauthorized' })
    })
  })
})
