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

export function createFileRoutes(sqlite: InstanceType<typeof Database>) {
  return async function fileRoutes(fastify: FastifyInstance) {
    await fastify.register(multipart, {
      limits: {
        fileSize: Number(process.env.MAX_UPLOAD_BYTES ?? 52428800),
      },
    })

    // -----------------------------------------------------------------------
    // POST /files — upload a file attachment
    // -----------------------------------------------------------------------
    fastify.post('/files', { preHandler: requireAuth }, async (req, reply) => {
      const uploadsPath = process.env.UPLOADS_PATH ?? '/tmp/uploads'

      let file
      try {
        file = await req.file()
      } catch {
        return reply.status(400).send({ error: 'Missing file' })
      }

      if (!file) {
        return reply.status(400).send({ error: 'Missing file' })
      }

      // Consume file buffer — this may throw if file exceeds limit
      let buffer: Buffer
      try {
        buffer = await file.toBuffer()
      } catch {
        return reply.status(413).send({ error: 'File too large' })
      }

      // Check size against limit (belt-and-suspenders for cases where
      // the multipart limit didn't trigger, e.g. chunked encoding)
      const maxBytes = Number(process.env.MAX_UPLOAD_BYTES ?? 52428800)
      if (buffer.length > maxBytes) {
        return reply.status(413).send({ error: 'File too large' })
      }

      // Determine extension
      let ext = extname(file.filename).toLowerCase().replace('.', '')
      if (!ext && file.mimetype) {
        ext = CONTENT_TYPE_EXT[file.mimetype] ?? ''
      }
      if (!ext) {
        ext = 'bin'
      }

      const uuid = randomUUID()
      const storedFilename = `${uuid}.${ext}`

      // Validate constructed filename
      if (!FILENAME_PATTERN.test(storedFilename)) {
        return reply.status(400).send({ error: 'Invalid filename' })
      }

      const fullPath = join(uploadsPath, storedFilename)

      await writeFile(fullPath, buffer)

      const mimeType = file.mimetype || getMimeType(storedFilename)
      const now = Date.now()

      // Parse node_id from multipart fields if present
      let nodeId: string | null = null
      if (file.fields) {
        const nodeIdField = file.fields['node_id']
        if (nodeIdField && 'value' in nodeIdField && typeof nodeIdField.value === 'string') {
          nodeId = nodeIdField.value
        }
      }

      sqlite
        .prepare(
          'INSERT INTO files (filename, user_id, node_id, mime_type, size, created_at) VALUES (?, ?, ?, ?, ?, ?)',
        )
        .run(storedFilename, req.user!.id, nodeId, mimeType, buffer.length, now)

      return reply.status(201).send({
        url: `/api/files/${storedFilename}`,
        uuid,
        filename: storedFilename,
        size: buffer.length,
        mime_type: mimeType,
      })
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

      const fullPath = join(uploadsPath, filename)

      try {
        await stat(fullPath)
      } catch {
        return reply.status(404).send({ error: 'File not found' })
      }

      await rm(fullPath)

      // Also remove DB record if it exists
      sqlite.prepare('DELETE FROM files WHERE filename = ?').run(filename)

      return reply.status(200).send({ deleted: true })
    })
  }
}
