/**
 * P2-15 / P2-16: Sync route tests (full Phase 2)
 *
 * Replaces P0-3 prototype tests (10 cases) with 20 proper tests:
 *   - P2-15: GET /api/sync/changes (11 cases)
 *   - P2-16: POST /api/sync/changes (9 cases)
 *
 * Uses createTestDb() for in-memory SQLite, @fastify/inject for HTTP,
 * and real JWT auth via jose.
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { randomUUID } from 'node:crypto'
import Database from 'better-sqlite3'
import { SignJWT } from 'jose'
import { buildApp } from '../index.js'
import { createTestDb } from '../test-helpers/createTestDb.js'
import type { FastifyInstance } from 'fastify'
import { resetHlcForTesting } from '../hlc/hlc.js'

// ---------------------------------------------------------------------------
// JWT helper
// ---------------------------------------------------------------------------

const TEST_SECRET = 'test-jwt-secret-for-sync-route-tests-must-be-long!!'

async function signTestJwt(userId: string): Promise<string> {
  const secret = new TextEncoder().encode(TEST_SECRET)
  return new SignJWT({ email: 'test@example.com', name: 'Test', picture: '' })
    .setProtectedHeader({ alg: 'HS256' })
    .setSubject(userId)
    .setIssuedAt()
    .setExpirationTime('1h')
    .sign(secret)
}

// ---------------------------------------------------------------------------
// Seed helpers
// ---------------------------------------------------------------------------

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
  id: string,
  userId: string,
): void {
  const now = Date.now()
  sqlite
    .prepare(
      `INSERT INTO documents (id, user_id, title, title_hlc, type, parent_id, parent_id_hlc, sort_order, sort_order_hlc, collapsed, collapsed_hlc, deleted_at, deleted_hlc, device_id, created_at, updated_at)
       VALUES (?, ?, 'Test Doc', '', 'document', NULL, '', 'a', '', 0, '', NULL, '', '', ?, ?)`,
    )
    .run(id, userId, now, now)
}

function seedNode(
  sqlite: InstanceType<typeof Database>,
  overrides: {
    id: string
    document_id: string
    user_id: string
    content?: string
    content_hlc?: string
    note_hlc?: string
    parent_id_hlc?: string
    sort_order_hlc?: string
    completed_hlc?: string
    color_hlc?: string
    collapsed_hlc?: string
    deleted_at?: number | null
    deleted_hlc?: string
    device_id?: string
  },
): void {
  const now = Date.now()
  sqlite
    .prepare(
      `INSERT INTO nodes (id, document_id, user_id, content, content_hlc, note, note_hlc, parent_id, parent_id_hlc, sort_order, sort_order_hlc, completed, completed_hlc, color, color_hlc, collapsed, collapsed_hlc, deleted_at, deleted_hlc, device_id, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, '', ?, NULL, ?, 'a0', ?, 0, ?, 0, ?, 0, ?, ?, ?, ?, ?, ?)`,
    )
    .run(
      overrides.id,
      overrides.document_id,
      overrides.user_id,
      overrides.content ?? 'hello',
      overrides.content_hlc ?? '',
      overrides.note_hlc ?? '',
      overrides.parent_id_hlc ?? '',
      overrides.sort_order_hlc ?? '',
      overrides.completed_hlc ?? '',
      overrides.color_hlc ?? '',
      overrides.collapsed_hlc ?? '',
      overrides.deleted_at ?? null,
      overrides.deleted_hlc ?? '',
      overrides.device_id ?? 'deviceA',
      now,
      now,
    )
}

function seedSettings(
  sqlite: InstanceType<typeof Database>,
  overrides: {
    id: string
    user_id: string
    theme?: string
    theme_hlc?: string
    density?: string
    density_hlc?: string
    show_guide_lines?: number
    show_guide_lines_hlc?: string
    show_backlink_badge?: number
    show_backlink_badge_hlc?: string
    device_id?: string
  },
): void {
  const now = Date.now()
  sqlite
    .prepare(
      `INSERT INTO settings (id, user_id, theme, theme_hlc, density, density_hlc, show_guide_lines, show_guide_lines_hlc, show_backlink_badge, show_backlink_badge_hlc, device_id, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    )
    .run(
      overrides.id,
      overrides.user_id,
      overrides.theme ?? 'dark',
      overrides.theme_hlc ?? '',
      overrides.density ?? 'cozy',
      overrides.density_hlc ?? '',
      overrides.show_guide_lines ?? 1,
      overrides.show_guide_lines_hlc ?? '',
      overrides.show_backlink_badge ?? 1,
      overrides.show_backlink_badge_hlc ?? '',
      overrides.device_id ?? 'deviceA',
      now,
      now,
    )
}

// ---------------------------------------------------------------------------
// Test suite
// ---------------------------------------------------------------------------

describe('GET /api/sync/changes', () => {
  let sqlite: InstanceType<typeof Database>
  let app: ReturnType<typeof buildApp>
  const USER_ID = 'sync-test-user'

  beforeEach(async () => {
    process.env.JWT_SECRET = TEST_SECRET
    resetHlcForTesting()
    const testDb = createTestDb()
    sqlite = testDb.sqlite
    app = buildApp(sqlite)
    await app.ready()
    seedUser(sqlite, USER_ID)
    seedDocument(sqlite, 'doc-1', USER_ID)
  })

  afterEach(async () => {
    await app.close()
    sqlite.close()
  })

  it('returns200_withAllEntityArrays', async () => {
    const jwt = await signTestJwt(USER_ID)
    const res = await app.inject({
      method: 'GET',
      url: '/api/sync/changes?since=0&device_id=deviceB',
      headers: { authorization: `Bearer ${jwt}` },
    })

    expect(res.statusCode).toBe(200)
    const body = res.json()
    expect(body).toHaveProperty('server_hlc')
    expect(body).toHaveProperty('nodes')
    expect(body).toHaveProperty('documents')
    expect(body).toHaveProperty('settings')
    expect(body).toHaveProperty('bookmarks')
    expect(Array.isArray(body.nodes)).toBe(true)
    expect(Array.isArray(body.documents)).toBe(true)
    expect(Array.isArray(body.bookmarks)).toBe(true)
  })

  it('returns_nodes_changedAfterSince', async () => {
    seedNode(sqlite, {
      id: 'node-changed',
      document_id: 'doc-1',
      user_id: USER_ID,
      content_hlc: 'AAAA',
      note_hlc: '0',
      parent_id_hlc: '0',
      sort_order_hlc: '0',
      completed_hlc: '0',
      color_hlc: '0',
      collapsed_hlc: '0',
      deleted_hlc: '0',
      device_id: 'deviceA',
    })

    const jwt = await signTestJwt(USER_ID)
    const res = await app.inject({
      method: 'GET',
      url: '/api/sync/changes?since=0&device_id=deviceB',
      headers: { authorization: `Bearer ${jwt}` },
    })

    expect(res.statusCode).toBe(200)
    const body = res.json()
    const ids = body.nodes.map((n: { id: string }) => n.id)
    expect(ids).toContain('node-changed')
  })

  it('excludes_nodes_fromSameDevice', async () => {
    seedNode(sqlite, {
      id: 'node-echo',
      document_id: 'doc-1',
      user_id: USER_ID,
      content_hlc: 'AAAA',
      note_hlc: '0',
      parent_id_hlc: '0',
      sort_order_hlc: '0',
      completed_hlc: '0',
      color_hlc: '0',
      collapsed_hlc: '0',
      deleted_hlc: '0',
      device_id: 'deviceA',
    })

    const jwt = await signTestJwt(USER_ID)
    const res = await app.inject({
      method: 'GET',
      url: '/api/sync/changes?since=0&device_id=deviceA',
      headers: { authorization: `Bearer ${jwt}` },
    })

    expect(res.statusCode).toBe(200)
    const body = res.json()
    const ids = body.nodes.map((n: { id: string }) => n.id)
    expect(ids).not.toContain('node-echo')
  })

  it('excludes_nodes_notChangedAfterSince', async () => {
    seedNode(sqlite, {
      id: 'node-old',
      document_id: 'doc-1',
      user_id: USER_ID,
      content_hlc: '0001',
      note_hlc: '0001',
      parent_id_hlc: '0001',
      sort_order_hlc: '0001',
      completed_hlc: '0001',
      color_hlc: '0001',
      collapsed_hlc: '0001',
      deleted_hlc: '0001',
      device_id: 'deviceA',
    })

    const jwt = await signTestJwt(USER_ID)
    const res = await app.inject({
      method: 'GET',
      url: '/api/sync/changes?since=0002&device_id=deviceB',
      headers: { authorization: `Bearer ${jwt}` },
    })

    expect(res.statusCode).toBe(200)
    const body = res.json()
    expect(body.nodes).toHaveLength(0)
  })

  it('returns_tombstones_forDeletedNodes', async () => {
    seedNode(sqlite, {
      id: 'node-deleted',
      document_id: 'doc-1',
      user_id: USER_ID,
      content_hlc: '0001',
      note_hlc: '0001',
      parent_id_hlc: '0001',
      sort_order_hlc: '0001',
      completed_hlc: '0001',
      color_hlc: '0001',
      collapsed_hlc: '0001',
      deleted_at: Date.now(),
      deleted_hlc: 'ZZZZ',
      device_id: 'deviceA',
    })

    const jwt = await signTestJwt(USER_ID)
    const res = await app.inject({
      method: 'GET',
      url: '/api/sync/changes?since=0&device_id=deviceB',
      headers: { authorization: `Bearer ${jwt}` },
    })

    expect(res.statusCode).toBe(200)
    const body = res.json()
    const node = body.nodes.find((n: { id: string }) => n.id === 'node-deleted')
    expect(node).toBeDefined()
    expect(node.deleted_at).not.toBeNull()
  })

  it('returns_settings_whenChanged', async () => {
    seedSettings(sqlite, {
      id: 'settings-1',
      user_id: USER_ID,
      theme: 'dark',
      theme_hlc: 'AAAA',
      density_hlc: '0',
      show_guide_lines_hlc: '0',
      show_backlink_badge_hlc: '0',
      device_id: 'deviceA',
    })

    const jwt = await signTestJwt(USER_ID)
    const res = await app.inject({
      method: 'GET',
      url: '/api/sync/changes?since=0&device_id=deviceB',
      headers: { authorization: `Bearer ${jwt}` },
    })

    expect(res.statusCode).toBe(200)
    const body = res.json()
    expect(body.settings).not.toBeNull()
    expect(body.settings.theme).toBe('dark')
  })

  it('settings_isNull_whenNoneExist', async () => {
    const jwt = await signTestJwt(USER_ID)
    const res = await app.inject({
      method: 'GET',
      url: '/api/sync/changes?since=0&device_id=deviceB',
      headers: { authorization: `Bearer ${jwt}` },
    })

    expect(res.statusCode).toBe(200)
    const body = res.json()
    expect(body.settings).toBeNull()
  })

  it('returns400_whenSinceMissing', async () => {
    const jwt = await signTestJwt(USER_ID)
    const res = await app.inject({
      method: 'GET',
      url: '/api/sync/changes?device_id=deviceB',
      headers: { authorization: `Bearer ${jwt}` },
    })

    expect(res.statusCode).toBe(400)
  })

  it('returns400_whenDeviceIdMissing', async () => {
    const jwt = await signTestJwt(USER_ID)
    const res = await app.inject({
      method: 'GET',
      url: '/api/sync/changes?since=0',
      headers: { authorization: `Bearer ${jwt}` },
    })

    expect(res.statusCode).toBe(400)
  })

  it('returns401_withNoAuthHeader', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/api/sync/changes?since=0&device_id=deviceB',
    })

    expect(res.statusCode).toBe(401)
  })

  it('server_hlc_isLexicographicallyValid', async () => {
    const jwt = await signTestJwt(USER_ID)
    const res = await app.inject({
      method: 'GET',
      url: '/api/sync/changes?since=0&device_id=deviceB',
      headers: { authorization: `Bearer ${jwt}` },
    })

    expect(res.statusCode).toBe(200)
    const body = res.json()
    // HLC format: 13-digit-ms-zero-padded-5-digit-counter-nodeId
    // The module-level hlc uses decimal padding, so: XXXXXXXXXXXXX-XXXXX-server
    expect(body.server_hlc).toMatch(/^\d{13}-\d{5}-server$/)
  })
})

// ---------------------------------------------------------------------------
// POST /api/sync/push
// ---------------------------------------------------------------------------

describe('POST /api/sync/push', () => {
  let sqlite: InstanceType<typeof Database>
  let app: ReturnType<typeof buildApp>
  const USER_ID = 'sync-push-user'

  beforeEach(async () => {
    process.env.JWT_SECRET = TEST_SECRET
    resetHlcForTesting()
    const testDb = createTestDb()
    sqlite = testDb.sqlite
    app = buildApp(sqlite)
    await app.ready()
    seedUser(sqlite, USER_ID)
    seedDocument(sqlite, 'doc-push-1', USER_ID)
  })

  afterEach(async () => {
    await app.close()
    sqlite.close()
  })

  it('returns401_withNoAuthHeader', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/api/sync/push',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ device_id: 'deviceA', nodes: [] }),
    })

    expect(res.statusCode).toBe(401)
  })

  it('returns200_acceptsNewNode', async () => {
    const jwt = await signTestJwt(USER_ID)
    const nodeId = randomUUID()

    const res = await app.inject({
      method: 'POST',
      url: '/api/sync/push',
      headers: {
        authorization: `Bearer ${jwt}`,
        'content-type': 'application/json',
      },
      body: JSON.stringify({
        device_id: 'deviceA',
        nodes: [
          {
            id: nodeId,
            document_id: 'doc-push-1',
            content: 'pushed node',
            content_hlc: 'AAA',
            note: '',
            note_hlc: 'AAA',
            parent_id: null,
            parent_id_hlc: 'AAA',
            sort_order: 'a0',
            sort_order_hlc: 'AAA',
            completed: 0,
            completed_hlc: 'AAA',
            color: 0,
            color_hlc: 'AAA',
            collapsed: 0,
            collapsed_hlc: 'AAA',
            deleted_at: null,
            deleted_hlc: 'AAA',
            device_id: 'deviceA',
          },
        ],
      }),
    })

    expect(res.statusCode).toBe(200)
    const body = res.json()
    expect(body.accepted_node_ids).toContain(nodeId)
    expect(body).toHaveProperty('server_hlc')
    expect(body).toHaveProperty('conflicts')

    const row = sqlite.prepare('SELECT * FROM nodes WHERE id = ?').get(nodeId) as { id: string; user_id: string } | undefined
    expect(row).toBeDefined()
    expect(row!.user_id).toBe(USER_ID)
  })

  it('returns400_whenDeviceIdMissing', async () => {
    const jwt = await signTestJwt(USER_ID)
    const res = await app.inject({
      method: 'POST',
      url: '/api/sync/push',
      headers: {
        authorization: `Bearer ${jwt}`,
        'content-type': 'application/json',
      },
      body: JSON.stringify({ nodes: [] }),
    })

    expect(res.statusCode).toBe(400)
  })
})

// ---------------------------------------------------------------------------
// POST /api/sync/changes
// ---------------------------------------------------------------------------

describe('POST /api/sync/changes', () => {
  let sqlite: InstanceType<typeof Database>
  let app: ReturnType<typeof buildApp>
  const USER_ID = 'sync-post-user'

  beforeEach(async () => {
    process.env.JWT_SECRET = TEST_SECRET
    resetHlcForTesting()
    const testDb = createTestDb()
    sqlite = testDb.sqlite
    app = buildApp(sqlite)
    await app.ready()
    seedUser(sqlite, USER_ID)
    seedDocument(sqlite, 'doc-1', USER_ID)
  })

  afterEach(async () => {
    await app.close()
    sqlite.close()
  })

  it('returns200_acceptsNewNode', async () => {
    const jwt = await signTestJwt(USER_ID)
    const nodeId = randomUUID()

    const res = await app.inject({
      method: 'POST',
      url: '/api/sync/changes',
      headers: {
        authorization: `Bearer ${jwt}`,
        'content-type': 'application/json',
      },
      body: JSON.stringify({
        device_id: 'deviceA',
        nodes: [
          {
            id: nodeId,
            document_id: 'doc-1',
            content: 'new node',
            content_hlc: 'AAA',
            note: '',
            note_hlc: 'AAA',
            parent_id: null,
            parent_id_hlc: 'AAA',
            sort_order: 'a0',
            sort_order_hlc: 'AAA',
            completed: 0,
            completed_hlc: 'AAA',
            color: 0,
            color_hlc: 'AAA',
            collapsed: 0,
            collapsed_hlc: 'AAA',
            deleted_at: null,
            deleted_hlc: 'AAA',
            device_id: 'deviceA',
          },
        ],
      }),
    })

    expect(res.statusCode).toBe(200)
    const body = res.json()
    expect(body.accepted_node_ids).toContain(nodeId)

    // Verify in DB
    const row = sqlite.prepare('SELECT * FROM nodes WHERE id = ?').get(nodeId) as { id: string; user_id: string } | undefined
    expect(row).toBeDefined()
    expect(row!.user_id).toBe(USER_ID)
  })

  it('returns200_incomingContentWins_whenHigherHlc', async () => {
    const nodeId = 'node-content-wins'
    seedNode(sqlite, {
      id: nodeId,
      document_id: 'doc-1',
      user_id: USER_ID,
      content: 'old',
      content_hlc: 'AAA',
      note_hlc: 'AAA',
      parent_id_hlc: 'AAA',
      sort_order_hlc: 'AAA',
      completed_hlc: 'AAA',
      color_hlc: 'AAA',
      collapsed_hlc: 'AAA',
      deleted_hlc: 'AAA',
      device_id: 'deviceA',
    })

    const jwt = await signTestJwt(USER_ID)
    const res = await app.inject({
      method: 'POST',
      url: '/api/sync/changes',
      headers: {
        authorization: `Bearer ${jwt}`,
        'content-type': 'application/json',
      },
      body: JSON.stringify({
        device_id: 'deviceB',
        nodes: [
          {
            id: nodeId,
            document_id: 'doc-1',
            content: 'new',
            content_hlc: 'ZZZ',
            note: '',
            note_hlc: 'ZZZ',
            parent_id: null,
            parent_id_hlc: 'ZZZ',
            sort_order: 'a0',
            sort_order_hlc: 'ZZZ',
            completed: 0,
            completed_hlc: 'ZZZ',
            color: 0,
            color_hlc: 'ZZZ',
            collapsed: 0,
            collapsed_hlc: 'ZZZ',
            deleted_at: null,
            deleted_hlc: 'ZZZ',
            device_id: 'deviceB',
          },
        ],
      }),
    })

    expect(res.statusCode).toBe(200)
    const body = res.json()
    expect(body.accepted_node_ids).toContain(nodeId)

    // Verify DB has new content
    const row = sqlite.prepare('SELECT content FROM nodes WHERE id = ?').get(nodeId) as { content: string }
    expect(row.content).toBe('new')
  })

  it('serverVersionWins_returnsConflict', async () => {
    const nodeId = 'node-server-wins'
    seedNode(sqlite, {
      id: nodeId,
      document_id: 'doc-1',
      user_id: USER_ID,
      content: 'server version',
      content_hlc: 'ZZZ',
      note_hlc: 'ZZZ',
      parent_id_hlc: 'ZZZ',
      sort_order_hlc: 'ZZZ',
      completed_hlc: 'ZZZ',
      color_hlc: 'ZZZ',
      collapsed_hlc: 'ZZZ',
      deleted_hlc: 'ZZZ',
      device_id: 'deviceA',
    })

    const jwt = await signTestJwt(USER_ID)
    const res = await app.inject({
      method: 'POST',
      url: '/api/sync/changes',
      headers: {
        authorization: `Bearer ${jwt}`,
        'content-type': 'application/json',
      },
      body: JSON.stringify({
        device_id: 'deviceB',
        nodes: [
          {
            id: nodeId,
            document_id: 'doc-1',
            content: 'client version',
            content_hlc: 'AAA',
            note: '',
            note_hlc: 'AAA',
            parent_id: null,
            parent_id_hlc: 'AAA',
            sort_order: 'a0',
            sort_order_hlc: 'AAA',
            completed: 0,
            completed_hlc: 'AAA',
            color: 0,
            color_hlc: 'AAA',
            collapsed: 0,
            collapsed_hlc: 'AAA',
            deleted_at: null,
            deleted_hlc: 'AAA',
            device_id: 'deviceB',
          },
        ],
      }),
    })

    expect(res.statusCode).toBe(200)
    const body = res.json()
    expect(body.accepted_node_ids).not.toContain(nodeId)
    const conflictNode = body.conflicts.nodes.find((n: { id: string }) => n.id === nodeId)
    expect(conflictNode).toBeDefined()
    expect(conflictNode.content).toBe('server version')
  })

  it('deleteWins_overConcurrentEdits', async () => {
    const nodeId = 'node-delete-wins'
    seedNode(sqlite, {
      id: nodeId,
      document_id: 'doc-1',
      user_id: USER_ID,
      content: 'original',
      content_hlc: 'BBB',
      note_hlc: 'BBB',
      parent_id_hlc: 'BBB',
      sort_order_hlc: 'BBB',
      completed_hlc: 'BBB',
      color_hlc: 'BBB',
      collapsed_hlc: 'BBB',
      deleted_at: null,
      deleted_hlc: 'BBB',
      device_id: 'deviceA',
    })

    const jwt = await signTestJwt(USER_ID)
    const deletedAt = Date.now()

    const res = await app.inject({
      method: 'POST',
      url: '/api/sync/changes',
      headers: {
        authorization: `Bearer ${jwt}`,
        'content-type': 'application/json',
      },
      body: JSON.stringify({
        device_id: 'deviceB',
        nodes: [
          {
            id: nodeId,
            document_id: 'doc-1',
            content: 'edited',
            content_hlc: 'AAA', // lower than stored BBB
            note: '',
            note_hlc: 'AAA',
            parent_id: null,
            parent_id_hlc: 'AAA',
            sort_order: 'a0',
            sort_order_hlc: 'AAA',
            completed: 0,
            completed_hlc: 'AAA',
            color: 0,
            color_hlc: 'AAA',
            collapsed: 0,
            collapsed_hlc: 'AAA',
            deleted_at: deletedAt,
            deleted_hlc: 'ZZZ', // higher — delete wins
            device_id: 'deviceB',
          },
        ],
      }),
    })

    expect(res.statusCode).toBe(200)

    // Verify DB has deleted_at set
    const row = sqlite.prepare('SELECT deleted_at FROM nodes WHERE id = ?').get(nodeId) as { deleted_at: number | null }
    expect(row.deleted_at).not.toBeNull()
  })

  it('settings_mergedPerField', async () => {
    seedSettings(sqlite, {
      id: 'settings-merge-test',
      user_id: USER_ID,
      theme: 'dark',
      theme_hlc: 'ZZZ',
      density: 'cozy',
      density_hlc: 'AAA',
      show_guide_lines: 1,
      show_guide_lines_hlc: 'AAA',
      show_backlink_badge: 1,
      show_backlink_badge_hlc: 'AAA',
      device_id: 'deviceA',
    })

    const jwt = await signTestJwt(USER_ID)
    const res = await app.inject({
      method: 'POST',
      url: '/api/sync/changes',
      headers: {
        authorization: `Bearer ${jwt}`,
        'content-type': 'application/json',
      },
      body: JSON.stringify({
        device_id: 'deviceB',
        settings: {
          id: 'settings-merge-test',
          user_id: USER_ID,
          theme: 'light',
          theme_hlc: 'AAA', // lower — stored 'dark' wins
          density: 'compact',
          density_hlc: 'AAA', // same as stored — tiebreaker
          show_guide_lines: 0,
          show_guide_lines_hlc: 'AAA',
          show_backlink_badge: 0,
          show_backlink_badge_hlc: 'AAA',
          device_id: 'deviceB',
          created_at: Date.now(),
          updated_at: Date.now(),
        },
      }),
    })

    expect(res.statusCode).toBe(200)

    // Verify DB still has theme = 'dark' (stored ZZZ > incoming AAA)
    const row = sqlite.prepare('SELECT theme FROM settings WHERE user_id = ?').get(USER_ID) as { theme: string }
    expect(row.theme).toBe('dark')
  })

  it('returns400_whenDeviceIdMissing', async () => {
    const jwt = await signTestJwt(USER_ID)
    const res = await app.inject({
      method: 'POST',
      url: '/api/sync/changes',
      headers: {
        authorization: `Bearer ${jwt}`,
        'content-type': 'application/json',
      },
      body: JSON.stringify({ nodes: [] }),
    })

    expect(res.statusCode).toBe(400)
  })

  it('returns413_whenPayloadTooLarge', async () => {
    const jwt = await signTestJwt(USER_ID)
    const res = await app.inject({
      method: 'POST',
      url: '/api/sync/changes',
      headers: {
        authorization: `Bearer ${jwt}`,
        'content-type': 'application/json',
        'content-length': String(6 * 1024 * 1024), // 6 MB > 5 MB limit
      },
      body: JSON.stringify({ device_id: 'deviceA', nodes: [] }),
    })

    expect(res.statusCode).toBe(413)
  })

  it('returns401_withNoAuthHeader', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/api/sync/changes',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ device_id: 'deviceA', nodes: [] }),
    })

    expect(res.statusCode).toBe(401)
  })

  it('accepted_and_conflict_areMutuallyExclusive', async () => {
    // Node A: incoming wins (higher HLC)
    const nodeAId = 'node-wins'
    seedNode(sqlite, {
      id: nodeAId,
      document_id: 'doc-1',
      user_id: USER_ID,
      content: 'old A',
      content_hlc: 'AAA',
      note_hlc: 'AAA',
      parent_id_hlc: 'AAA',
      sort_order_hlc: 'AAA',
      completed_hlc: 'AAA',
      color_hlc: 'AAA',
      collapsed_hlc: 'AAA',
      deleted_hlc: 'AAA',
      device_id: 'deviceA',
    })

    // Node B: stored wins (higher HLC on server)
    const nodeBId = 'node-loses'
    seedNode(sqlite, {
      id: nodeBId,
      document_id: 'doc-1',
      user_id: USER_ID,
      content: 'server B',
      content_hlc: 'ZZZ',
      note_hlc: 'ZZZ',
      parent_id_hlc: 'ZZZ',
      sort_order_hlc: 'ZZZ',
      completed_hlc: 'ZZZ',
      color_hlc: 'ZZZ',
      collapsed_hlc: 'ZZZ',
      deleted_hlc: 'ZZZ',
      device_id: 'deviceA',
    })

    const jwt = await signTestJwt(USER_ID)
    const res = await app.inject({
      method: 'POST',
      url: '/api/sync/changes',
      headers: {
        authorization: `Bearer ${jwt}`,
        'content-type': 'application/json',
      },
      body: JSON.stringify({
        device_id: 'deviceB',
        nodes: [
          {
            id: nodeAId,
            document_id: 'doc-1',
            content: 'new A',
            content_hlc: 'ZZZ',
            note: '',
            note_hlc: 'ZZZ',
            parent_id: null,
            parent_id_hlc: 'ZZZ',
            sort_order: 'a0',
            sort_order_hlc: 'ZZZ',
            completed: 0,
            completed_hlc: 'ZZZ',
            color: 0,
            color_hlc: 'ZZZ',
            collapsed: 0,
            collapsed_hlc: 'ZZZ',
            deleted_at: null,
            deleted_hlc: 'ZZZ',
            device_id: 'deviceB',
          },
          {
            id: nodeBId,
            document_id: 'doc-1',
            content: 'client B',
            content_hlc: 'AAA',
            note: '',
            note_hlc: 'AAA',
            parent_id: null,
            parent_id_hlc: 'AAA',
            sort_order: 'a0',
            sort_order_hlc: 'AAA',
            completed: 0,
            completed_hlc: 'AAA',
            color: 0,
            color_hlc: 'AAA',
            collapsed: 0,
            collapsed_hlc: 'AAA',
            deleted_at: null,
            deleted_hlc: 'AAA',
            device_id: 'deviceB',
          },
        ],
      }),
    })

    expect(res.statusCode).toBe(200)
    const body = res.json()

    // Node A should be accepted, Node B should be in conflicts
    expect(body.accepted_node_ids).toContain(nodeAId)
    expect(body.accepted_node_ids).not.toContain(nodeBId)

    const conflictIds = body.conflicts.nodes.map((n: { id: string }) => n.id)
    expect(conflictIds).toContain(nodeBId)
    expect(conflictIds).not.toContain(nodeAId)
  })
})
