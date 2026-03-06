import { randomUUID } from 'node:crypto'
import { writeFile, stat, readFile, rm } from 'node:fs/promises'
import { join, extname } from 'node:path'
import type { FastifyInstance } from 'fastify'
import type Database from 'better-sqlite3'
import multipart from '@fastify/multipart'
import { requireAuth } from '../middleware/auth.js'

const FILENAME_PATTERN = /^[0-9a-f-]{36}\.[a-z0-9]+$/

const MIME_MAP: Record<string, string> = {
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.gif': 'image/gif',
  '.pdf': 'application/pdf',
  '.txt': 'text/plain',
}

const CONTENT_TYPE_EXT: Record<string, string> = {
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/gif': 'gif',
  'application/pdf': 'pdf',
  'text/plain': 'txt',
}

function getMimeType(filename: string): string {
  const ext = extname(filename).toLowerCase()
  return MIME_MAP[ext] ?? 'application/octet-stream'
}

interface UploadResult {
  url: string
  uuid: string
  filename: string
  size: number
  mime_type: string
}

async function handleFileUpload(
  sqlite: InstanceType<typeof Database>,
  req: { file: () => ReturnType<any>; user: { id: string } | null },
  reply: { status: (n: number) => { send: (body: unknown) => unknown } },
  uploadsPath: string,
): Promise<UploadResult | null> {
  let file
  try {
    file = await (req as any).file()
  } catch {
    reply.status(400).send({ error: 'Missing file' })
    return null
  }

  if (!file) {
    reply.status(400).send({ error: 'Missing file' })
    return null
  }

  let buffer: Buffer
  try {
    buffer = await file.toBuffer()
  } catch {
    reply.status(413).send({ error: 'File too large' })
    return null
  }

  const maxBytes = Number(process.env.MAX_UPLOAD_BYTES ?? 52428800)
  if (buffer.length > maxBytes) {
    reply.status(413).send({ error: 'File too large' })
    return null
  }

  let ext = extname(file.filename).toLowerCase().replace('.', '')
  if (!ext && file.mimetype) {
    ext = CONTENT_TYPE_EXT[file.mimetype] ?? ''
  }
  if (!ext) {
    ext = 'bin'
  }

  const uuid = randomUUID()
  const storedFilename = `${uuid}.${ext}`

  if (!FILENAME_PATTERN.test(storedFilename)) {
    reply.status(400).send({ error: 'Invalid filename' })
    return null
  }

  const fullPath = join(uploadsPath, storedFilename)
  await writeFile(fullPath, buffer)

  const mimeType = file.mimetype || getMimeType(storedFilename)
  const now = Date.now()

  // Parse and validate node_id ownership
  let nodeId: string | null = null
  if (file.fields) {
    const nodeIdField = file.fields['node_id']
    if (nodeIdField && 'value' in nodeIdField && typeof nodeIdField.value === 'string') {
      nodeId = nodeIdField.value
    }
  }

  if (nodeId) {
    const node = sqlite
      .prepare('SELECT id FROM nodes WHERE id = ? AND user_id = ? AND deleted_at IS NULL')
      .get(nodeId, req.user!.id) as { id: string } | undefined
    if (!node) {
      reply.status(404).send({ error: 'Node not found' })
      return null
    }
  }

  sqlite
    .prepare(
      'INSERT INTO files (filename, user_id, node_id, mime_type, size, created_at) VALUES (?, ?, ?, ?, ?, ?)',
    )
    .run(storedFilename, req.user!.id, nodeId, mimeType, buffer.length, now)

  return {
    url: `/api/files/${storedFilename}`,
    uuid,
    filename: storedFilename,
    size: buffer.length,
    mime_type: mimeType,
  }
}

export function createFileRoutes(sqlite: InstanceType<typeof Database>) {
  return async function fileRoutes(fastify: FastifyInstance) {
    await fastify.register(multipart, {
      limits: {
        fileSize: Number(process.env.MAX_UPLOAD_BYTES ?? 52428800),
      },
    })

    // -----------------------------------------------------------------------
    // GET /files — list all file attachments for the authenticated user
    // -----------------------------------------------------------------------
    fastify.get('/files', { preHandler: requireAuth }, async (req, reply) => {
      const rows = sqlite
        .prepare(
          'SELECT filename, mime_type, size, created_at, node_id FROM files WHERE user_id = ? ORDER BY created_at DESC',
        )
        .all(req.user!.id) as {
          filename: string
          mime_type: string
          size: number
          created_at: number
          node_id: string | null
        }[]
      return { files: rows }
    })

    // -----------------------------------------------------------------------
    // POST /files — upload a file attachment
    // -----------------------------------------------------------------------
    fastify.post('/files', { preHandler: requireAuth }, async (req, reply) => {
      const uploadsPath = process.env.UPLOADS_PATH ?? '/tmp/uploads'
      const result = await handleFileUpload(sqlite, req as any, reply as any, uploadsPath)
      if (!result) return
      return reply.status(201).send(result)
    })

    // -----------------------------------------------------------------------
    // POST /files/upload — alias for POST /files (used by Android client)
    // -----------------------------------------------------------------------
    fastify.post('/files/upload', { preHandler: requireAuth }, async (req, reply) => {
      const uploadsPath = process.env.UPLOADS_PATH ?? '/tmp/uploads'
      const result = await handleFileUpload(sqlite, req as any, reply as any, uploadsPath)
      if (!result) return
      return reply.status(201).send(result)
    })

    // -----------------------------------------------------------------------
    // GET /files/:filename — serve a stored file
    // -----------------------------------------------------------------------
    fastify.get('/files/:filename', { preHandler: requireAuth }, async (req, reply) => {
      const { filename } = req.params as { filename: string }
      const uploadsPath = process.env.UPLOADS_PATH ?? '/tmp/uploads'

      if (!FILENAME_PATTERN.test(filename)) {
        return reply.status(400).send({ error: 'Invalid filename' })
      }

      // Check DB for ownership
      const row = sqlite
        .prepare('SELECT * FROM files WHERE filename = ?')
        .get(filename) as { filename: string; user_id: string } | undefined

      if (row && row.user_id !== req.user!.id) {
        return reply.status(403).send({ error: 'Forbidden' })
      }

      const fullPath = join(uploadsPath, filename)

      try {
        await stat(fullPath)
      } catch {
        return reply.status(404).send({ error: 'File not found' })
      }

      if (!row) {
        return reply.status(404).send({ error: 'File not found' })
      }

      const buffer = await readFile(fullPath)
      const mimeType = getMimeType(filename)

      void reply.header('cache-control', 'max-age=2592000, immutable')
      return reply.type(mimeType).send(buffer)
    })

    // -----------------------------------------------------------------------
    // DELETE /files/:filename — permanently delete a file
    // -----------------------------------------------------------------------
    fastify.delete('/files/:filename', { preHandler: requireAuth }, async (req, reply) => {
      const { filename } = req.params as { filename: string }
      const uploadsPath = process.env.UPLOADS_PATH ?? '/tmp/uploads'

      if (!FILENAME_PATTERN.test(filename)) {
        return reply.status(400).send({ error: 'Invalid filename' })
      }

      // Ownership check: look up DB record before any disk operation.
      const row = sqlite
        .prepare('SELECT * FROM files WHERE filename = ?')
        .get(filename) as { filename: string; user_id: string } | undefined

      if (row && row.user_id !== req.user!.id) {
        return reply.status(403).send({ error: 'Forbidden' })
      }

      const fullPath = join(uploadsPath, filename)

      try {
        await stat(fullPath)
      } catch {
        return reply.status(404).send({ error: 'File not found' })
      }

      // Orphaned file (exists on disk but no DB record) — reject without deleting.
      if (!row) {
        return reply.status(404).send({ error: 'File not found' })
      }

      await rm(fullPath)

      sqlite.prepare('DELETE FROM files WHERE filename = ?').run(filename)

      return reply.status(200).send({ deleted: true })
    })
  }
}
