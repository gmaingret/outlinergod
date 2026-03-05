import { promises as fs } from 'node:fs'
import path from 'node:path'
import type { FastifyInstance } from 'fastify'
import type Database from 'better-sqlite3'
import JSZip from 'jszip'
import { requireAuth } from '../middleware/auth.js'

export function createExportRoutes(sqlite: InstanceType<typeof Database>) {
  return async function exportRoutes(fastify: FastifyInstance) {
    // -----------------------------------------------------------------------
    // GET /export — stream a ZIP archive of the user's entire data
    // -----------------------------------------------------------------------
    fastify.get('/export', { preHandler: requireAuth }, async (req, reply) => {
      const userId = req.user!.id

      // 1. Gather all non-deleted documents for this user
      const documents = sqlite
        .prepare('SELECT * FROM documents WHERE user_id = ? AND deleted_at IS NULL')
        .all(userId)

      // 2. Build ZIP archive
      const zip = new JSZip()
      zip.file('documents.json', JSON.stringify(documents, null, 2))

      // 3. For each document, gather its non-deleted nodes
      for (const doc of documents as { id: string }[]) {
        const nodes = sqlite
          .prepare('SELECT * FROM nodes WHERE document_id = ? AND deleted_at IS NULL')
          .all(doc.id)
        zip.file(`nodes/${doc.id}.json`, JSON.stringify(nodes, null, 2))
      }

      // 4. Include attachments owned by this user
      try {
        const uploadsPath = process.env.UPLOADS_PATH
        if (uploadsPath) {
          const userFiles = sqlite
            .prepare('SELECT filename FROM files WHERE user_id = ?')
            .all(userId) as { filename: string }[]
          for (const { filename } of userFiles) {
            try {
              const data = await fs.readFile(path.join(uploadsPath, filename))
              zip.file(`attachments/${filename}`, data)
            } catch {
              // File may not exist on disk; skip it
            }
          }
        }
      } catch {
        // Ignore if uploads dir doesn't exist
      }

      // 5. Generate ZIP buffer and send
      const zipBuffer = await zip.generateAsync({ type: 'nodebuffer' })

      return reply
        .header('Content-Type', 'application/zip')
        .header(
          'Content-Disposition',
          `attachment; filename="outlinergod-export-${Date.now()}.zip"`,
        )
        .send(zipBuffer)
    })
  }
}
