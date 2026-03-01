import { randomUUID } from 'node:crypto'
import type { FastifyInstance } from 'fastify'
import type Database from 'better-sqlite3'
import { requireAuth } from '../middleware/auth.js'

interface NodeRow {
  id: string
  document_id: string
  user_id: string
  content: string
  content_hlc: string
  note: string
  note_hlc: string
  parent_id: string | null
  parent_id_hlc: string
  sort_order: string
  sort_order_hlc: string
  completed: number
  completed_hlc: string
  color: number
  color_hlc: string
  collapsed: number
  collapsed_hlc: string
  deleted_at: number | null
  deleted_hlc: string
  device_id: string
  created_at: number
  updated_at: number
}

interface DocumentRow {
  id: string
  user_id: string
  title: string
  title_hlc: string
  type: string
  parent_id: string | null
  parent_id_hlc: string
  sort_order: string
  sort_order_hlc: string
  collapsed: number
  collapsed_hlc: string
  deleted_at: number | null
  deleted_hlc: string
  device_id: string
  created_at: number
  updated_at: number
}

function toNodeResponse(row: NodeRow) {
  return {
    id: row.id,
    document_id: row.document_id,
    content: row.content,
    note: row.note,
    parent_id: row.parent_id,
    sort_order: row.sort_order,
    completed: row.completed === 1,
    color: row.color,
    collapsed: row.collapsed === 1,
    deleted_at: row.deleted_at,
    created_at: row.created_at,
    updated_at: row.updated_at,
  }
}

function toDocumentResponse(row: DocumentRow) {
  return {
    id: row.id,
    title: row.title,
    type: row.type,
    parent_id: row.parent_id,
    sort_order: row.sort_order,
    collapsed: row.collapsed === 1,
    deleted_at: row.deleted_at,
    created_at: row.created_at,
    updated_at: row.updated_at,
  }
}

export function createNodeRoutes(sqlite: InstanceType<typeof Database>) {
  return async function nodeRoutes(fastify: FastifyInstance) {
    // -----------------------------------------------------------------------
    // GET / — list all active nodes for a document
    // Registered at: GET /api/nodes?document_id=<id>
    // -----------------------------------------------------------------------
    fastify.get('/', { preHandler: requireAuth }, async (req, reply) => {
      const { document_id } = req.query as { document_id?: string }

      if (!document_id) {
        return reply.status(400).send({ error: 'document_id query param required' })
      }

      // Verify document exists, belongs to user, and is not deleted
      const doc = sqlite
        .prepare('SELECT id FROM documents WHERE id = ? AND user_id = ? AND deleted_at IS NULL')
        .get(document_id, req.user!.id) as { id: string } | undefined

      if (!doc) {
        return reply.status(404).send({ error: 'Document not found' })
      }

      const rows = sqlite
        .prepare('SELECT * FROM nodes WHERE document_id = ? AND deleted_at IS NULL')
        .all(document_id) as NodeRow[]

      return { nodes: rows.map(toNodeResponse) }
    })

    // -----------------------------------------------------------------------
    // POST /batch — batch upsert up to 500 nodes
    // Registered at: POST /api/nodes/batch
    // -----------------------------------------------------------------------
    fastify.post('/batch', { preHandler: requireAuth }, async (req, reply) => {
      const body = req.body as { document_id?: string; nodes?: unknown }

      if (!body?.document_id || typeof body.document_id !== 'string') {
        return reply.status(400).send({ error: 'document_id required' })
      }

      const { document_id } = body

      // Verify document exists, belongs to user, and is not deleted
      const doc = sqlite
        .prepare('SELECT id FROM documents WHERE id = ? AND user_id = ? AND deleted_at IS NULL')
        .get(document_id, req.user!.id) as { id: string } | undefined

      if (!doc) {
        return reply.status(404).send({ error: 'Document not found' })
      }

      if (!body?.nodes || !Array.isArray(body.nodes) || body.nodes.length === 0) {
        return reply.status(400).send({ error: 'nodes must be a non-empty array' })
      }

      if (body.nodes.length > 500) {
        return reply.status(400).send({ error: 'Batch exceeds 500 nodes' })
      }

      // Validate each node
      for (let i = 0; i < body.nodes.length; i++) {
        const n = body.nodes[i] as Record<string, unknown>
        if (
          !n ||
          typeof n.id !== 'string' ||
          typeof n.content !== 'string' ||
          typeof n.note !== 'string' ||
          (n.parent_id !== null && typeof n.parent_id !== 'string') ||
          typeof n.sort_order !== 'string' ||
          typeof n.completed !== 'boolean' ||
          typeof n.color !== 'number' ||
          !Number.isInteger(n.color) ||
          n.color < 0 ||
          n.color > 6 ||
          typeof n.collapsed !== 'boolean'
        ) {
          return reply.status(400).send({ error: `Malformed node at index ${i}` })
        }
      }

      const now = Date.now()

      const upsertStmt = sqlite.prepare(
        `INSERT OR REPLACE INTO nodes
         (id, document_id, user_id, content, content_hlc, note, note_hlc, parent_id, parent_id_hlc, sort_order, sort_order_hlc, completed, completed_hlc, color, color_hlc, collapsed, collapsed_hlc, deleted_at, deleted_hlc, device_id, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, '', '', ?, ?)`,
      )

      const transaction = sqlite.transaction(() => {
        for (const n of body.nodes as Array<Record<string, unknown>>) {
          upsertStmt.run(
            n.id,
            document_id,
            req.user!.id,
            n.content,
            (n.content_hlc as string) ?? '',
            n.note,
            (n.note_hlc as string) ?? '',
            n.parent_id ?? null,
            (n.parent_id_hlc as string) ?? '',
            n.sort_order,
            (n.sort_order_hlc as string) ?? '',
            (n.completed as boolean) ? 1 : 0,
            (n.completed_hlc as string) ?? '',
            n.color,
            (n.color_hlc as string) ?? '',
            (n.collapsed as boolean) ? 1 : 0,
            (n.collapsed_hlc as string) ?? '',
            now,
            now,
          )
        }
      })

      transaction()

      return { upserted: body.nodes.length }
    })

    // -----------------------------------------------------------------------
    // DELETE /:id — soft-delete a node and all its descendants
    // Registered at: DELETE /api/nodes/:id
    // -----------------------------------------------------------------------
    fastify.delete('/:id', { preHandler: requireAuth }, async (req, reply) => {
      const { id } = req.params as { id: string }

      const node = sqlite
        .prepare('SELECT * FROM nodes WHERE id = ? AND user_id = ?')
        .get(id, req.user!.id) as NodeRow | undefined

      if (!node || node.deleted_at != null) {
        return reply.status(404).send({ error: 'Node not found' })
      }

      // BFS to collect all descendant node IDs within the same document
      const deletedIds: string[] = [id]
      const queue: string[] = [id]

      while (queue.length > 0) {
        const current = queue.shift()!
        const children = sqlite
          .prepare('SELECT id FROM nodes WHERE parent_id = ? AND document_id = ? AND deleted_at IS NULL')
          .all(current, node.document_id) as { id: string }[]

        for (const child of children) {
          deletedIds.push(child.id)
          queue.push(child.id)
        }
      }

      const now = Date.now()
      const placeholders = deletedIds.map(() => '?').join(', ')
      sqlite
        .prepare(`UPDATE nodes SET deleted_at = ? WHERE id IN (${placeholders})`)
        .run(now, ...deletedIds)

      return { deleted_ids: deletedIds }
    })

    // -----------------------------------------------------------------------
    // POST /:id/convert — promote a node to a document
    // Registered at: POST /api/nodes/:id/convert
    // -----------------------------------------------------------------------
    fastify.post('/:id/convert', { preHandler: requireAuth }, async (req, reply) => {
      const { id } = req.params as { id: string }

      const node = sqlite
        .prepare('SELECT * FROM nodes WHERE id = ? AND user_id = ?')
        .get(id, req.user!.id) as NodeRow | undefined

      if (!node || node.deleted_at != null) {
        return reply.status(404).send({ error: 'Node not found' })
      }

      if (!node.content || node.content.trim() === '') {
        return reply.status(409).send({ error: 'Node has no content' })
      }

      const newDocId = randomUUID()
      const now = Date.now()

      const transaction = sqlite.transaction(() => {
        // 1. Create new document
        sqlite
          .prepare(
            `INSERT INTO documents (id, user_id, title, title_hlc, type, parent_id, parent_id_hlc, sort_order, sort_order_hlc, collapsed, collapsed_hlc, deleted_at, deleted_hlc, device_id, created_at, updated_at)
             VALUES (?, ?, ?, '', 'document', NULL, '', ?, '', 0, '', NULL, '', '', ?, ?)`,
          )
          .run(newDocId, req.user!.id, node.content, node.sort_order, now, now)

        // 2. Move direct children: update parent_id and document_id
        const directChildren = sqlite
          .prepare('SELECT id FROM nodes WHERE document_id = ? AND parent_id = ? AND deleted_at IS NULL')
          .all(node.document_id, id) as { id: string }[]

        if (directChildren.length > 0) {
          const childIds = directChildren.map((c) => c.id)
          const childPlaceholders = childIds.map(() => '?').join(', ')
          sqlite
            .prepare(`UPDATE nodes SET parent_id = NULL, document_id = ? WHERE id IN (${childPlaceholders})`)
            .run(newDocId, ...childIds)

          // 3. BFS to update all deeper descendants' document_id
          const queue = [...childIds]
          while (queue.length > 0) {
            const current = queue.shift()!
            const grandchildren = sqlite
              .prepare('SELECT id FROM nodes WHERE parent_id = ? AND document_id = ? AND deleted_at IS NULL')
              .all(current, node.document_id) as { id: string }[]

            if (grandchildren.length > 0) {
              const gcIds = grandchildren.map((gc) => gc.id)
              const gcPlaceholders = gcIds.map(() => '?').join(', ')
              sqlite
                .prepare(`UPDATE nodes SET document_id = ? WHERE id IN (${gcPlaceholders})`)
                .run(newDocId, ...gcIds)

              for (const gc of grandchildren) {
                queue.push(gc.id)
              }
            }
          }
        }

        // 4. Soft-delete the original node
        sqlite
          .prepare('UPDATE nodes SET deleted_at = ? WHERE id = ?')
          .run(now, id)
      })

      transaction()

      const docRow = sqlite.prepare('SELECT * FROM documents WHERE id = ?').get(newDocId) as DocumentRow

      return { document: toDocumentResponse(docRow) }
    })
  }
}
