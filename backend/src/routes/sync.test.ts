/**
 * P0-3 Backend sync tests — 10 cases required by PLAN_PHASE0.md
 *
 * HLC & merge tests (1–6) operate on the modules directly.
 * Route tests (7–10) use Fastify inject (no real HTTP port needed).
 *
 * DB: in-memory SQLite (:memory:) — never touches the real /data database.
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import Database from 'better-sqlite3'
import { HlcClock } from '../hlc.js'
import { mergeNodes, type NodeSyncRecord } from '../merge.js'
import { buildApp } from './sync.js'
import type { FastifyInstance } from 'fastify'

// ---------------------------------------------------------------------------
// Test helpers
// ---------------------------------------------------------------------------

function makeNode({ id, ...rest }: Partial<NodeSyncRecord> & { id: string }): NodeSyncRecord {
  return {
    id,
    document_id: 'doc-1',
    content: 'hello',
    content_hlc: '',
    note: '',
    note_hlc: '',
    parent_id: null,
    parent_id_hlc: '',
    sort_order: 'a0',
    sort_order_hlc: '',
    completed: 0,
    completed_hlc: '',
    color: 0,
    color_hlc: '',
    collapsed: 0,
    collapsed_hlc: '',
    deleted_at: null,
    deleted_hlc: '',
    device_id: 'device-a',
    ...rest,
  }
}

// ---------------------------------------------------------------------------
// 1 & 2: HLC clock tests
// ---------------------------------------------------------------------------

describe('HLC clock', () => {
  it('hlc_generate_isMonotonicallyIncreasing', () => {
    const clock = new HlcClock()
    const h1 = clock.generate('dev-1')
    const h2 = clock.generate('dev-1')
    // Lexicographic comparison: h2 must be strictly greater than h1
    expect(h2 > h1).toBe(true)
  })

  it('hlc_receive_advancesClockPastIncoming', () => {
    const clock = new HlcClock()
    const futureWall = Date.now() + 100
    // Construct an HLC whose wall component is 100 ms in the future
    const incomingHlc = HlcClock.format(futureWall, 0, 'remote-device')
    const result = clock.receive(incomingHlc, 'local-device')
    const { wallMs } = HlcClock.parse(result)
    // The returned clock wall must be at least the incoming future wall
    expect(wallMs).toBeGreaterThanOrEqual(futureWall)
  })
})

// ---------------------------------------------------------------------------
// 3–6: LWW merge tests
// ---------------------------------------------------------------------------

describe('LWW merge', () => {
  it('lww_merge_higherHlcWins', () => {
    const lowHlc = '0000017b05a3a1be-0000-device-a'
    const highHlc = '0000017b05a3a1be-0001-device-b'

    const a = makeNode({ id: 'n1', content: 'old content', content_hlc: lowHlc })
    const b = makeNode({ id: 'n1', content: 'new content', content_hlc: highHlc })

    const merged = mergeNodes(a, b)
    expect(merged.content).toBe('new content')
    expect(merged.content_hlc).toBe(highHlc)
  })

  it('lww_merge_idempotent', () => {
    const hlcA = '0000017b05a3a1be-0001-device-a'
    const hlcB = '0000017b05a3a1be-0000-device-b'

    const a = makeNode({ id: 'n1', content: 'A content', content_hlc: hlcA, note: 'A note', note_hlc: hlcA })
    const b = makeNode({ id: 'n1', content: 'B content', content_hlc: hlcB, note: 'B note', note_hlc: hlcB })

    const mergeAB = mergeNodes(a, b)
    const mergeA_AB = mergeNodes(a, mergeAB)

    // merge(A, merge(A, B)) must equal merge(A, B) field-by-field
    expect(mergeA_AB.content).toBe(mergeAB.content)
    expect(mergeA_AB.content_hlc).toBe(mergeAB.content_hlc)
    expect(mergeA_AB.note).toBe(mergeAB.note)
    expect(mergeA_AB.note_hlc).toBe(mergeAB.note_hlc)
    expect(mergeA_AB.deleted_at).toBe(mergeAB.deleted_at)
    expect(mergeA_AB.deleted_hlc).toBe(mergeAB.deleted_hlc)
    expect(mergeA_AB.device_id).toBe(mergeAB.device_id)
  })

  it('lww_merge_commutative', () => {
    const hlcA = '0000017b05a3a1be-0002-device-a'
    const hlcB = '0000017b05a3a1be-0001-device-b'
    const hlcC = '0000017b05a3a1be-0003-device-c'

    // Record A wins on content; Record B wins on note; Record A wins on sort_order
    const a = makeNode({
      id: 'n1',
      content: 'A content',
      content_hlc: hlcA,
      note: 'A note',
      note_hlc: hlcB, // A has the lower note HLC
      sort_order: 'a2',
      sort_order_hlc: hlcC,
      device_id: 'device-a',
    })
    const b = makeNode({
      id: 'n1',
      content: 'B content',
      content_hlc: hlcB, // B has lower content HLC
      note: 'B note',
      note_hlc: hlcC, // B has higher note HLC
      sort_order: 'a1',
      sort_order_hlc: hlcA,
      device_id: 'device-b',
    })

    const ab = mergeNodes(a, b)
    const ba = mergeNodes(b, a)

    // Every field must be equal
    expect(ab.content).toBe(ba.content)
    expect(ab.content_hlc).toBe(ba.content_hlc)
    expect(ab.note).toBe(ba.note)
    expect(ab.note_hlc).toBe(ba.note_hlc)
    expect(ab.sort_order).toBe(ba.sort_order)
    expect(ab.sort_order_hlc).toBe(ba.sort_order_hlc)
    expect(ab.deleted_at).toBe(ba.deleted_at)
    expect(ab.deleted_hlc).toBe(ba.deleted_hlc)
    expect(ab.device_id).toBe(ba.device_id)
  })

  it('lww_merge_deleteWins', () => {
    // B deletes the node with a very high deleted_hlc — must win over A's edits
    const editHlc = '0000017b05a3a1be-0001-device-a'
    const deleteHlc = 'ffffffffffffffff-ffff-device-b' // lexicographically higher than any edit

    const a = makeNode({
      id: 'n1',
      content: 'edited by A',
      content_hlc: editHlc,
      deleted_at: null,
      deleted_hlc: editHlc, // A has not deleted — lower deleted_hlc
      device_id: 'device-a',
    })
    const b = makeNode({
      id: 'n1',
      content: 'B original',
      content_hlc: '0000017b05a3a1be-0000-device-b', // B has older content
      deleted_at: Date.now(),
      deleted_hlc: deleteHlc, // B deleted with highest possible HLC
      device_id: 'device-b',
    })

    const merged = mergeNodes(a, b)

    // deleted_hlc of B wins → deleted_at must be set
    expect(merged.deleted_at).not.toBeNull()
    expect(merged.deleted_hlc).toBe(deleteHlc)
    // A's content edit still wins for the content field (content_hlc A > B)
    expect(merged.content).toBe('edited by A')
  })
})

// ---------------------------------------------------------------------------
// 7–10: Sync route tests (Fastify inject, in-memory SQLite)
// ---------------------------------------------------------------------------

describe('Sync routes', () => {
  let sqlite: InstanceType<typeof Database>
  let app: FastifyInstance

  const DEVICE_A = 'aaaaaaaa-0000-0000-0000-000000000001'
  const DEVICE_B = 'bbbbbbbb-0000-0000-0000-000000000002'

  beforeEach(() => {
    sqlite = new Database(':memory:')
    app = buildApp(sqlite)
  })

  afterEach(async () => {
    await app.close()
    sqlite.close()
  })

  it('POST /sync/changes — push single node, accepted and retrievable via GET', async () => {
    const clock = new HlcClock()
    const hlc = clock.generate(DEVICE_A)

    const node = makeNode({
      id: 'node-push-test',
      content: 'pushed content',
      content_hlc: hlc,
      note_hlc: hlc,
      parent_id_hlc: hlc,
      sort_order_hlc: hlc,
      completed_hlc: hlc,
      color_hlc: hlc,
      collapsed_hlc: hlc,
      deleted_hlc: hlc,
      device_id: DEVICE_A,
    })

    const postRes = await app.inject({
      method: 'POST',
      url: '/api/sync/changes',
      headers: { 'content-type': 'application/json', authorization: 'Bearer stub' },
      body: JSON.stringify({ device_id: DEVICE_A, nodes: [node] }),
    })
    expect(postRes.statusCode).toBe(200)
    const postBody = postRes.json()
    expect(postBody.accepted_node_ids).toContain('node-push-test')

    // Verify retrievable via GET from a different device
    const getRes = await app.inject({
      method: 'GET',
      url: `/api/sync/changes?since=0&device_id=${DEVICE_B}`,
      headers: { authorization: 'Bearer stub' },
    })
    expect(getRes.statusCode).toBe(200)
    const getBody = getRes.json()
    const ids = (getBody.nodes as NodeSyncRecord[]).map((n) => n.id)
    expect(ids).toContain('node-push-test')
  })

  it('GET /sync/changes — node pushed by A appears when B pulls', async () => {
    const clock = new HlcClock()
    const hlc = clock.generate(DEVICE_A)

    const node = makeNode({
      id: 'echo-node',
      content_hlc: hlc,
      note_hlc: hlc,
      parent_id_hlc: hlc,
      sort_order_hlc: hlc,
      completed_hlc: hlc,
      color_hlc: hlc,
      collapsed_hlc: hlc,
      deleted_hlc: hlc,
      device_id: DEVICE_A,
    })

    await app.inject({
      method: 'POST',
      url: '/api/sync/changes',
      headers: { 'content-type': 'application/json', authorization: 'Bearer stub' },
      body: JSON.stringify({ device_id: DEVICE_A, nodes: [node] }),
    })

    const res = await app.inject({
      method: 'GET',
      url: `/api/sync/changes?since=0&device_id=${DEVICE_B}`,
      headers: { authorization: 'Bearer stub' },
    })
    expect(res.statusCode).toBe(200)
    const body = res.json()
    const ids = (body.nodes as NodeSyncRecord[]).map((n) => n.id)
    expect(ids).toContain('echo-node')
  })

  it('GET /sync/changes — echo suppression: node pushed by A NOT returned when A pulls', async () => {
    const clock = new HlcClock()
    const hlc = clock.generate(DEVICE_A)

    const node = makeNode({
      id: 'suppressed-node',
      content_hlc: hlc,
      note_hlc: hlc,
      parent_id_hlc: hlc,
      sort_order_hlc: hlc,
      completed_hlc: hlc,
      color_hlc: hlc,
      collapsed_hlc: hlc,
      deleted_hlc: hlc,
      device_id: DEVICE_A,
    })

    await app.inject({
      method: 'POST',
      url: '/api/sync/changes',
      headers: { 'content-type': 'application/json', authorization: 'Bearer stub' },
      body: JSON.stringify({ device_id: DEVICE_A, nodes: [node] }),
    })

    // A pulls its own changes — should be suppressed
    const res = await app.inject({
      method: 'GET',
      url: `/api/sync/changes?since=0&device_id=${DEVICE_A}`,
      headers: { authorization: 'Bearer stub' },
    })
    expect(res.statusCode).toBe(200)
    const body = res.json()
    const ids = (body.nodes as NodeSyncRecord[]).map((n) => n.id)
    expect(ids).not.toContain('suppressed-node')
  })

  it('conflict_serverVersionWins — B pushes older HLC; conflicts array contains A version', async () => {
    const clockA = new HlcClock()
    const clockB = new HlcClock()

    // A pushes first with a higher HLC (simulate A's edit happened later)
    const hlcA = clockA.generate(DEVICE_A)
    // B generates an HLC but it will be lower than A's because A already advanced
    const hlcB = clockB.generate(DEVICE_B)

    // Ensure hlcA > hlcB by making A's timestamp a bit later
    const futureWallA = Date.now() + 50
    const highHlcA = HlcClock.format(futureWallA, 0, DEVICE_A)
    const lowHlcB = HlcClock.format(Date.now(), 0, DEVICE_B)

    const nodeA = makeNode({
      id: 'conflict-node',
      content: 'A version',
      content_hlc: highHlcA,
      note_hlc: highHlcA,
      parent_id_hlc: highHlcA,
      sort_order_hlc: highHlcA,
      completed_hlc: highHlcA,
      color_hlc: highHlcA,
      collapsed_hlc: highHlcA,
      deleted_hlc: highHlcA,
      device_id: DEVICE_A,
    })

    // A pushes first
    const pushA = await app.inject({
      method: 'POST',
      url: '/api/sync/changes',
      headers: { 'content-type': 'application/json', authorization: 'Bearer stub' },
      body: JSON.stringify({ device_id: DEVICE_A, nodes: [nodeA] }),
    })
    expect(pushA.statusCode).toBe(200)
    expect(pushA.json().accepted_node_ids).toContain('conflict-node')

    // B pushes the same node with an OLDER HLC — server version should win
    const nodeB = makeNode({
      id: 'conflict-node',
      content: 'B version',
      content_hlc: lowHlcB,
      note_hlc: lowHlcB,
      parent_id_hlc: lowHlcB,
      sort_order_hlc: lowHlcB,
      completed_hlc: lowHlcB,
      color_hlc: lowHlcB,
      collapsed_hlc: lowHlcB,
      deleted_hlc: lowHlcB,
      device_id: DEVICE_B,
    })

    const pushB = await app.inject({
      method: 'POST',
      url: '/api/sync/changes',
      headers: { 'content-type': 'application/json', authorization: 'Bearer stub' },
      body: JSON.stringify({ device_id: DEVICE_B, nodes: [nodeB] }),
    })
    expect(pushB.statusCode).toBe(200)
    const bBody = pushB.json()

    // B's node must NOT be in accepted_node_ids (A's version won)
    expect(bBody.accepted_node_ids).not.toContain('conflict-node')

    // The conflict response must contain 'conflict-node' with A's winning content
    const conflictNode = (bBody.conflicts.nodes as NodeSyncRecord[]).find(
      (n) => n.id === 'conflict-node',
    )
    expect(conflictNode).toBeDefined()
    expect(conflictNode!.content).toBe('A version')
    expect(conflictNode!.content_hlc).toBe(highHlcA)
  })
})
