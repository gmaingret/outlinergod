import { createRequire } from 'module'
import type { FastifyInstance } from 'fastify'
import type Database from 'better-sqlite3'

const require = createRequire(import.meta.url)
const pkg = require('../../package.json') as { version: string }

export function createHealthRoute(sqlite: InstanceType<typeof Database>) {
  return async function healthRoute(fastify: FastifyInstance) {
    fastify.get('/health', async (_request, reply) => {
      let dbStatus: 'ok' | 'error'
      try {
        sqlite.prepare('SELECT 1').get()
        dbStatus = 'ok'
      } catch {
        dbStatus = 'error'
      }
      return reply.send({
        status: 'ok',
        version: pkg.version,
        db: dbStatus,
        uptime: process.uptime(),
      })
    })
  }
}
