import { randomUUID } from 'node:crypto'
import type { FastifyInstance } from 'fastify'
import type Database from 'better-sqlite3'
import { requireAuth } from '../middleware/auth.js'

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

export function createDocumentRoutes(sqlite: InstanceType<typeof Database>) {
  return async function documentRoutes(fastify: FastifyInstance) {
    // -----------------------------------------------------------------------
    // GET /documents — list all active documents for the authenticated user
    // -----------------------------------------------------------------------
    fastify.get('/documents', { preHandler: requireAuth }, async (req) => {
      const rows = sqlite
        .prepare('SELECT * FROM documents WHERE user_id = ? AND deleted_at IS NULL')
        .all(req.user!.id) as DocumentRow[]

      return { items: rows.map(toDocumentResponse) }
    })

    // -----------------------------------------------------------------------
    // POST /documents — create a new document or folder
    // -----------------------------------------------------------------------
    fastify.post('/documents', { preHandler: requireAuth }, async (req, reply) => {
      const body = req.body as {
        title?: string
        type?: string
        sort_order?: string
        parent_id?: string | null
      }

      if (
        !body?.title ||
        typeof body.title !== 'string' ||
        !body.type ||
        (body.type !== 'document' && body.type !== 'folder') ||
        !body.sort_order ||
        typeof body.sort_order !== 'string'
      ) {
        return reply.status(400).send({ error: 'Missing required fields' })
      }

      // Validate parent_id if provided
      if (body.parent_id != null) {
        const parent = sqlite
          .prepare('SELECT id FROM documents WHERE id = ? AND user_id = ?')
          .get(body.parent_id, req.user!.id) as { id: string } | undefined

        if (!parent) {
          return reply.status(404).send({ error: 'Parent not found' })
        }
      }

      const id = randomUUID()
      const now = Date.now()

      sqlite
        .prepare(
          `INSERT INTO documents (id, user_id, title, title_hlc, type, parent_id, parent_id_hlc, sort_order, sort_order_hlc, collapsed, collapsed_hlc, deleted_at, deleted_hlc, device_id, created_at, updated_at)
           VALUES (?, ?, ?, '', ?, ?, '', ?, '', 0, '', NULL, '', '', ?, ?)`,
        )
        .run(id, req.user!.id, body.title, body.type, body.parent_id ?? null, body.sort_order, now, now)

      const row = sqlite.prepare('SELECT * FROM documents WHERE id = ?').get(id) as DocumentRow

      return reply.status(201).send(toDocumentResponse(row))
    })

    // -----------------------------------------------------------------------
    // GET /documents/:id — fetch a single document by ID
    // -----------------------------------------------------------------------
    fastify.get('/documents/:id', { preHandler: requireAuth }, async (req, reply) => {
      const { id } = req.params as { id: string }

      const row = sqlite
        .prepare('SELECT * FROM documents WHERE id = ? AND user_id = ?')
        .get(id, req.user!.id) as DocumentRow | undefined

      if (!row || row.deleted_at != null) {
        return reply.status(404).send({ error: 'Document not found' })
      }

      return toDocumentResponse(row)
    })

    // -----------------------------------------------------------------------
    // PATCH /documents/:id — update mutable fields on a document
    // -----------------------------------------------------------------------
    fastify.patch('/documents/:id', { preHandler: requireAuth }, async (req, reply) => {
      const { id } = req.params as { id: string }

      const row = sqlite
        .prepare('SELECT * FROM documents WHERE id = ? AND user_id = ?')
        .get(id, req.user!.id) as DocumentRow | undefined

      if (!row || row.deleted_at != null) {
        return reply.status(404).send({ error: 'Document not found' })
      }

      const body = req.body as {
        title?: string
        parent_id?: string | null
        sort_order?: string
        collapsed?: boolean
      }

      const updatableKeys = ['title', 'parent_id', 'sort_order', 'collapsed'] as const
      const hasUpdatable = updatableKeys.some((k) => body?.[k] !== undefined)

      if (!hasUpdatable) {
        return reply.status(400).send({ error: 'No updatable fields supplied' })
      }

      // Circular reference check
      if (body.parent_id !== undefined && body.parent_id !== null) {
        let current = body.parent_id
        for (let i = 0; i < 100; i++) {
          if (current === id) {
            return reply.status(400).send({ error: 'Circular reference detected' })
          }
          const ancestor = sqlite
            .prepare('SELECT parent_id FROM documents WHERE id = ? AND user_id = ?')
            .get(current, req.user!.id) as { parent_id: string | null } | undefined
          if (!ancestor || ancestor.parent_id == null) break
          current = ancestor.parent_id
        }
      }

      // Build dynamic UPDATE
      const setClauses: string[] = []
      const values: unknown[] = []

      if (body.title !== undefined) {
        setClauses.push('title = ?')
        values.push(body.title)
      }
      if (body.parent_id !== undefined) {
        setClauses.push('parent_id = ?')
        values.push(body.parent_id)
      }
      if (body.sort_order !== undefined) {
        setClauses.push('sort_order = ?')
        values.push(body.sort_order)
      }
      if (body.collapsed !== undefined) {
        setClauses.push('collapsed = ?')
        values.push(body.collapsed ? 1 : 0)
      }

      const now = Date.now()
      setClauses.push('updated_at = ?')
      values.push(now)

      values.push(id)

      sqlite
        .prepare(`UPDATE documents SET ${setClauses.join(', ')} WHERE id = ?`)
        .run(...values)

      const updated = sqlite.prepare('SELECT * FROM documents WHERE id = ?').get(id) as DocumentRow

      return toDocumentResponse(updated)
    })

    // -----------------------------------------------------------------------
    // POST /documents/:id/convert-to-node — fold document into a node
    // -----------------------------------------------------------------------
    fastify.post('/documents/:id/convert-to-node', { preHandler: requireAuth }, async (req, reply) => {
      const { id } = req.params as { id: string }

      const body = req.body as {
        target_document_id?: string
        target_parent_id?: string
        sort_order?: string
      }

      if (
        !body?.target_document_id ||
        typeof body.target_document_id !== 'string' ||
        body.target_parent_id === undefined ||
        (body.target_parent_id !== null && typeof body.target_parent_id !== 'string') ||
        !body.sort_order ||
        typeof body.sort_order !== 'string'
      ) {
        return reply.status(400).send({ error: 'Missing required fields' })
      }

      // Fetch source document
      const sourceDoc = sqlite
        .prepare('SELECT * FROM documents WHERE id = ? AND user_id = ? AND deleted_at IS NULL')
        .get(id, req.user!.id) as DocumentRow | undefined

      if (!sourceDoc) {
        return reply.status(404).send({ error: 'Document not found' })
      }

      // Fetch target document
      const targetDoc = sqlite
        .prepare('SELECT * FROM documents WHERE id = ? AND user_id = ? AND deleted_at IS NULL')
        .get(body.target_document_id, req.user!.id) as DocumentRow | undefined

      if (!targetDoc) {
        return reply.status(404).send({ error: 'Target document not found' })
      }

      // Circular reference guard: cannot convert into itself
      if (body.target_document_id === id) {
        return reply.status(400).send({ error: 'Circular reference detected' })
      }

      const newNodeId = randomUUID()
      const now = Date.now()

      const transaction = sqlite.transaction(() => {
        // 1. Create new node in target document with source doc title as content
        sqlite
          .prepare(
            `INSERT INTO nodes (id, document_id, user_id, content, content_hlc, note, note_hlc, parent_id, parent_id_hlc, sort_order, sort_order_hlc, completed, completed_hlc, color, color_hlc, collapsed, collapsed_hlc, deleted_at, deleted_hlc, device_id, created_at, updated_at)
             VALUES (?, ?, ?, ?, '', '', '', ?, '', ?, '', 0, '', 0, '', 0, '', NULL, '', '', ?, ?)`,
          )
          .run(newNodeId, body.target_document_id, req.user!.id, sourceDoc.title, body.target_parent_id ?? null, body.sort_order, now, now)

        // 2. Migrate top-level nodes (parent_id IS NULL) — re-parent to newNode
        sqlite
          .prepare(
            'UPDATE nodes SET document_id = ?, parent_id = ? WHERE document_id = ? AND deleted_at IS NULL AND parent_id IS NULL',
          )
          .run(body.target_document_id, newNodeId, id)

        // 3. Migrate non-root nodes — just update document_id, keep parent_id
        sqlite
          .prepare(
            'UPDATE nodes SET document_id = ? WHERE document_id = ? AND deleted_at IS NULL AND parent_id IS NOT NULL',
          )
          .run(body.target_document_id, id)

        // 4. Soft-delete source document
        sqlite
          .prepare('UPDATE documents SET deleted_at = ? WHERE id = ?')
          .run(now, id)
      })

      transaction()

      const nodeRow = sqlite.prepare('SELECT * FROM nodes WHERE id = ?').get(newNodeId) as {
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
        created_at: number
        updated_at: number
      }

      return {
        node: {
          id: nodeRow.id,
          document_id: nodeRow.document_id,
          content: nodeRow.content,
          note: nodeRow.note,
          parent_id: nodeRow.parent_id,
          sort_order: nodeRow.sort_order,
          completed: nodeRow.completed === 1,
          color: nodeRow.color,
          collapsed: nodeRow.collapsed === 1,
          deleted_at: nodeRow.deleted_at,
          created_at: nodeRow.created_at,
          updated_at: nodeRow.updated_at,
        },
      }
    })

    // -----------------------------------------------------------------------
    // DELETE /documents/:id — soft-delete document and all descendants
    // -----------------------------------------------------------------------
    fastify.delete('/documents/:id', { preHandler: requireAuth }, async (req, reply) => {
      const { id } = req.params as { id: string }

      const row = sqlite
        .prepare('SELECT * FROM documents WHERE id = ? AND user_id = ?')
        .get(id, req.user!.id) as DocumentRow | undefined

      if (!row || row.deleted_at != null) {
        return reply.status(404).send({ error: 'Document not found' })
      }

      // BFS to collect all descendant document IDs
      const deletedIds: string[] = [id]
      const queue: string[] = [id]

      while (queue.length > 0) {
        const current = queue.shift()!
        const children = sqlite
          .prepare('SELECT id FROM documents WHERE parent_id = ? AND user_id = ? AND deleted_at IS NULL')
          .all(current, req.user!.id) as { id: string }[]

        for (const child of children) {
          deletedIds.push(child.id)
          queue.push(child.id)
        }
      }

      const now = Date.now()

      // Soft-delete all collected documents
      const docPlaceholders = deletedIds.map(() => '?').join(', ')
      sqlite
        .prepare(`UPDATE documents SET deleted_at = ? WHERE id IN (${docPlaceholders})`)
        .run(now, ...deletedIds)

      // Soft-delete all nodes within deleted documents
      sqlite
        .prepare(`UPDATE nodes SET deleted_at = ? WHERE document_id IN (${docPlaceholders}) AND deleted_at IS NULL`)
        .run(now, ...deletedIds)

      // TODO: file cleanup — skip filesystem operations (no files table integration yet)

      return { deleted_ids: deletedIds }
    })
  }
}
