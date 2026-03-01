import { randomUUID } from 'node:crypto'
import type { FastifyInstance } from 'fastify'
import type Database from 'better-sqlite3'
import { requireAuth } from '../middleware/auth.js'

interface BookmarkRow {
  id: string
  user_id: string
  title: string
  title_hlc: string
  target_type: string
  target_type_hlc: string
  target_document_id: string | null
  target_document_id_hlc: string
  target_node_id: string | null
  target_node_id_hlc: string
  query: string | null
  query_hlc: string
  sort_order: string
  sort_order_hlc: string
  deleted_at: number | null
  deleted_hlc: string
  device_id: string
  created_at: number
  updated_at: number
}

function toBookmarkResponse(row: BookmarkRow) {
  return {
    id: row.id,
    title: row.title,
    target_type: row.target_type,
    target_document_id: row.target_document_id,
    target_node_id: row.target_node_id,
    query: row.query,
    sort_order: row.sort_order,
    deleted_at: row.deleted_at,
    created_at: row.created_at,
    updated_at: row.updated_at,
  }
}

const VALID_TARGET_TYPES = ['document', 'node', 'search'] as const

export function createBookmarkRoutes(sqlite: InstanceType<typeof Database>) {
  return async function bookmarkRoutes(fastify: FastifyInstance) {
    // -----------------------------------------------------------------------
    // GET /bookmarks — list all active bookmarks for the authenticated user
    // -----------------------------------------------------------------------
    fastify.get('/bookmarks', { preHandler: requireAuth }, async (req) => {
      const rows = sqlite
        .prepare('SELECT * FROM bookmarks WHERE user_id = ? AND deleted_at IS NULL')
        .all(req.user!.id) as BookmarkRow[]

      return { bookmarks: rows.map(toBookmarkResponse) }
    })

    // -----------------------------------------------------------------------
    // POST /bookmarks — create a new bookmark
    // -----------------------------------------------------------------------
    fastify.post('/bookmarks', { preHandler: requireAuth }, async (req, reply) => {
      const body = req.body as {
        title?: string
        target_type?: string
        target_document_id?: string | null
        target_node_id?: string | null
        query?: string | null
        sort_order?: string
      }

      if (
        !body?.title ||
        typeof body.title !== 'string' ||
        !body.target_type ||
        !VALID_TARGET_TYPES.includes(body.target_type as typeof VALID_TARGET_TYPES[number]) ||
        !body.sort_order ||
        typeof body.sort_order !== 'string'
      ) {
        return reply.status(400).send({ error: 'Missing required fields' })
      }

      // Validate target-type consistency
      if (body.target_type === 'document' && !body.target_document_id) {
        return reply.status(400).send({ error: 'Invalid target combination' })
      }
      if (body.target_type === 'node' && (!body.target_document_id || !body.target_node_id)) {
        return reply.status(400).send({ error: 'Invalid target combination' })
      }
      if (body.target_type === 'search' && !body.query) {
        return reply.status(400).send({ error: 'Invalid target combination' })
      }

      const id = randomUUID()
      const now = Date.now()

      sqlite
        .prepare(
          `INSERT INTO bookmarks (id, user_id, title, title_hlc, target_type, target_type_hlc,
            target_document_id, target_document_id_hlc, target_node_id, target_node_id_hlc,
            query, query_hlc, sort_order, sort_order_hlc, deleted_at, deleted_hlc,
            device_id, created_at, updated_at)
           VALUES (?, ?, ?, '', ?, '', ?, '', ?, '', ?, '', ?, '', NULL, '', '', ?, ?)`,
        )
        .run(
          id,
          req.user!.id,
          body.title,
          body.target_type,
          body.target_document_id ?? null,
          body.target_node_id ?? null,
          body.query ?? null,
          body.sort_order,
          now,
          now,
        )

      const row = sqlite.prepare('SELECT * FROM bookmarks WHERE id = ?').get(id) as BookmarkRow

      return reply.status(201).send(toBookmarkResponse(row))
    })

    // -----------------------------------------------------------------------
    // PATCH /bookmarks/:id — update mutable fields on a bookmark
    // -----------------------------------------------------------------------
    fastify.patch('/bookmarks/:id', { preHandler: requireAuth }, async (req, reply) => {
      const { id } = req.params as { id: string }

      const row = sqlite
        .prepare('SELECT * FROM bookmarks WHERE id = ? AND user_id = ? AND deleted_at IS NULL')
        .get(id, req.user!.id) as BookmarkRow | undefined

      if (!row) {
        return reply.status(404).send({ error: 'Bookmark not found' })
      }

      const body = req.body as {
        title?: string
        sort_order?: string
        query?: string | null
      }

      const updatableKeys = ['title', 'sort_order', 'query'] as const
      const hasUpdatable = updatableKeys.some((k) => body?.[k] !== undefined)

      if (!hasUpdatable) {
        return reply.status(400).send({ error: 'No updatable fields supplied' })
      }

      const setClauses: string[] = []
      const values: unknown[] = []

      if (body.title !== undefined) {
        setClauses.push('title = ?')
        values.push(body.title)
      }
      if (body.sort_order !== undefined) {
        setClauses.push('sort_order = ?')
        values.push(body.sort_order)
      }
      if (body.query !== undefined) {
        setClauses.push('query = ?')
        values.push(body.query)
      }

      const now = Date.now()
      setClauses.push('updated_at = ?')
      values.push(now)

      values.push(id)

      sqlite
        .prepare(`UPDATE bookmarks SET ${setClauses.join(', ')} WHERE id = ?`)
        .run(...values)

      const updated = sqlite.prepare('SELECT * FROM bookmarks WHERE id = ?').get(id) as BookmarkRow

      return toBookmarkResponse(updated)
    })

    // -----------------------------------------------------------------------
    // DELETE /bookmarks/:id — soft-delete a bookmark
    // -----------------------------------------------------------------------
    fastify.delete('/bookmarks/:id', { preHandler: requireAuth }, async (req, reply) => {
      const { id } = req.params as { id: string }

      const row = sqlite
        .prepare('SELECT * FROM bookmarks WHERE id = ? AND user_id = ? AND deleted_at IS NULL')
        .get(id, req.user!.id) as BookmarkRow | undefined

      if (!row) {
        return reply.status(404).send({ error: 'Bookmark not found' })
      }

      const now = Date.now()
      sqlite
        .prepare('UPDATE bookmarks SET deleted_at = ? WHERE id = ?')
        .run(now, id)

      return { deleted: true }
    })
  }
}
