/**
 * P0-3 Prototype: Sync routes
 *
 * GET  /api/sync/changes  — pull changes since a given HLC
 * POST /api/sync/changes  — push locally pending changes (LWW merge)
 *
 * Auth: stub middleware accepts any Authorization header (no JWT verification
 * in this prototype — real auth is Phase 1).
 */
import Fastify, { type FastifyInstance } from 'fastify'
import Database from 'better-sqlite3'
import { HlcClock } from '../hlc.js'
import { mergeNodes, type NodeSyncRecord } from '../merge.js'

// ---------------------------------------------------------------------------
// Schema
// ---------------------------------------------------------------------------

const CREATE_NODES_TABLE = `
  CREATE TABLE IF NOT EXISTS nodes (
    id             TEXT PRIMARY KEY,
    document_id    TEXT NOT NULL DEFAULT 'default',
    content        TEXT NOT NULL DEFAULT '',
    content_hlc    TEXT NOT NULL DEFAULT '',
    note           TEXT NOT NULL DEFAULT '',
    note_hlc       TEXT NOT NULL DEFAULT '',
    parent_id      TEXT,
    parent_id_hlc  TEXT NOT NULL DEFAULT '',
    sort_order     TEXT NOT NULL DEFAULT '',
    sort_order_hlc TEXT NOT NULL DEFAULT '',
    completed      INTEGER NOT NULL DEFAULT 0,
    completed_hlc  TEXT NOT NULL DEFAULT '',
    color          INTEGER NOT NULL DEFAULT 0,
    color_hlc      TEXT NOT NULL DEFAULT '',
    collapsed      INTEGER NOT NULL DEFAULT 0,
    collapsed_hlc  TEXT NOT NULL DEFAULT '',
    deleted_at     INTEGER,
    deleted_hlc    TEXT NOT NULL DEFAULT '',
    device_id      TEXT NOT NULL DEFAULT ''
  )
`

function initSchema(sqlite: InstanceType<typeof Database>): void {
  sqlite.pragma('journal_mode = WAL')
  sqlite.pragma('foreign_keys = ON')
  sqlite.exec(CREATE_NODES_TABLE)
}

// ---------------------------------------------------------------------------
// DB helpers
// ---------------------------------------------------------------------------

function rowToRecord(row: Record<string, unknown>): NodeSyncRecord {
  return row as unknown as NodeSyncRecord
}

function upsertNode(sqlite: InstanceType<typeof Database>, rec: NodeSyncRecord): void {
  sqlite
    .prepare(
      `INSERT INTO nodes (
        id, document_id, content, content_hlc, note, note_hlc,
        parent_id, parent_id_hlc, sort_order, sort_order_hlc,
        completed, completed_hlc, color, color_hlc,
        collapsed, collapsed_hlc, deleted_at, deleted_hlc, device_id
      ) VALUES (
        @id, @document_id, @content, @content_hlc, @note, @note_hlc,
        @parent_id, @parent_id_hlc, @sort_order, @sort_order_hlc,
        @completed, @completed_hlc, @color, @color_hlc,
        @collapsed, @collapsed_hlc, @deleted_at, @deleted_hlc, @device_id
      )
      ON CONFLICT(id) DO UPDATE SET
        document_id    = excluded.document_id,
        content        = excluded.content,
        content_hlc    = excluded.content_hlc,
        note           = excluded.note,
        note_hlc       = excluded.note_hlc,
        parent_id      = excluded.parent_id,
        parent_id_hlc  = excluded.parent_id_hlc,
        sort_order     = excluded.sort_order,
        sort_order_hlc = excluded.sort_order_hlc,
        completed      = excluded.completed,
        completed_hlc  = excluded.completed_hlc,
        color          = excluded.color,
        color_hlc      = excluded.color_hlc,
        collapsed      = excluded.collapsed,
        collapsed_hlc  = excluded.collapsed_hlc,
        deleted_at     = excluded.deleted_at,
        deleted_hlc    = excluded.deleted_hlc,
        device_id      = excluded.device_id`,
    )
    .run(rec)
}

// ---------------------------------------------------------------------------
// App factory
// ---------------------------------------------------------------------------

export function buildApp(sqlite: InstanceType<typeof Database>): FastifyInstance {
  initSchema(sqlite)
  const clock = new HlcClock()
  const app = Fastify({ logger: false })

  // Stub auth: accept any request (prototype only)
  app.addHook('preHandler', async (_request, _reply) => {
    // No-op: production will verify JWT here
  })

  // -------------------------------------------------------------------------
  // GET /api/sync/changes
  // Pull all records with any HLC field > since, excluding caller's own echoes.
  // -------------------------------------------------------------------------
  app.get('/api/sync/changes', async (request, reply) => {
    const query = request.query as Record<string, string>
    const since = query.since
    const device_id = query.device_id

    if (!since || !device_id) {
      return reply.status(400).send({ error: 'Missing required query params: since, device_id' })
    }

    // Return nodes where at least one HLC field was updated after `since`,
    // excluding records whose device_id matches the requester (echo suppression).
    const rows = sqlite
      .prepare(
        `SELECT * FROM nodes
         WHERE MAX(content_hlc, note_hlc, parent_id_hlc, sort_order_hlc,
                   completed_hlc, color_hlc, collapsed_hlc, deleted_hlc) > ?
           AND device_id != ?`,
      )
      .all(since, device_id) as NodeSyncRecord[]

    const server_hlc = clock.generate('server')

    return {
      server_hlc,
      nodes: rows,
      documents: [],
      settings: null,
      bookmarks: [],
    }
  })

  // -------------------------------------------------------------------------
  // POST /api/sync/changes
  // Push pending changes. Server applies per-field LWW merge.
  // -------------------------------------------------------------------------
  app.post('/api/sync/changes', async (request, reply) => {
    const body = request.body as {
      device_id?: string
      nodes?: NodeSyncRecord[]
    }

    if (!body.device_id) {
      return reply.status(400).send({ error: 'Missing device_id' })
    }

    const incomingNodes = body.nodes ?? []
    const acceptedNodeIds: string[] = []
    const conflictNodes: NodeSyncRecord[] = []

    for (const incoming of incomingNodes) {
      const existing = sqlite
        .prepare('SELECT * FROM nodes WHERE id = ?')
        .get(incoming.id) as NodeSyncRecord | undefined

      if (!existing) {
        // New node: insert as-is
        upsertNode(sqlite, incoming)
        acceptedNodeIds.push(incoming.id)
      } else {
        // Existing node: per-field LWW merge
        const merged = mergeNodes(existing, incoming)

        // "Fully accepted" means incoming won or tied on every field
        const fullyAccepted =
          incoming.content_hlc >= existing.content_hlc &&
          incoming.note_hlc >= existing.note_hlc &&
          incoming.parent_id_hlc >= existing.parent_id_hlc &&
          incoming.sort_order_hlc >= existing.sort_order_hlc &&
          incoming.completed_hlc >= existing.completed_hlc &&
          incoming.color_hlc >= existing.color_hlc &&
          incoming.collapsed_hlc >= existing.collapsed_hlc &&
          incoming.deleted_hlc >= existing.deleted_hlc

        upsertNode(sqlite, merged)

        if (fullyAccepted) {
          acceptedNodeIds.push(incoming.id)
        } else {
          // Return the server's winning version so the client can apply it
          conflictNodes.push(merged)
        }
      }
    }

    const server_hlc = clock.generate('server')

    return {
      server_hlc,
      accepted_node_ids: acceptedNodeIds,
      accepted_document_ids: [],
      accepted_bookmark_ids: [],
      conflicts: {
        nodes: conflictNodes,
        documents: [],
        bookmarks: [],
        settings: null,
      },
    }
  })

  return app
}
