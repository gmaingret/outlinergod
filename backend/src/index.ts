import Fastify from 'fastify'
import { TypeBoxTypeProvider } from '@fastify/type-provider-typebox'
import Database from 'better-sqlite3'
import authPlugin from './middleware/auth.js'
import { createAuthRoutes, type GooglePayload } from './routes/auth.js'
import { createDocumentRoutes } from './routes/documents.js'
import { createNodeRoutes } from './routes/nodes.js'
import { createHealthRoute } from './routes/health.js'
import { runMigrations } from './db/migrate.js'
import { createConnection } from './db/connection.js'

/**
 * Application factory. Builds and returns a Fastify instance without calling
 * listen() — that lives in startServer() so tests can call buildApp() in isolation.
 *
 * @param sqlite       - Pre-configured better-sqlite3 instance (pragmas set, migrations run).
 *                       Defaults to a fresh in-memory DB when omitted (used by unit tests that
 *                       don't exercise routes requiring the schema).
 * @param verifyGoogle - Optional Google ID token verifier override (used in auth route tests
 *                       to avoid real network calls to Google's JWKS endpoint).
 */
export function buildApp(
  sqlite?: InstanceType<typeof Database>,
  verifyGoogle?: (idToken: string) => Promise<GooglePayload>,
) {
  const app = Fastify({ logger: true }).withTypeProvider<TypeBoxTypeProvider>()
  const sqliteInstance = sqlite ?? new Database(':memory:')

  // Global JWT middleware — sets req.user on every request.
  void app.register(authPlugin)

  // Health check — unprotected, no /api prefix
  void app.register(createHealthRoute(sqliteInstance))

  // Auth routes at /api/auth/*
  void app.register(createAuthRoutes(sqliteInstance, verifyGoogle), { prefix: '/api' })

  // Document routes at /api/documents/*
  void app.register(createDocumentRoutes(sqliteInstance), { prefix: '/api' })

  // Node routes at /api/documents/:id/nodes/* and /api/nodes/*
  void app.register(createNodeRoutes(sqliteInstance), { prefix: '/api' })

  // Placeholder root route
  app.get('/', async (_req, reply) => {
    return reply.send({ message: 'OutlinerGod API' })
  })

  return app
}

/**
 * Full server startup: runs migrations before registering any routes, then
 * starts listening. If migrations fail the process exits with code 1 so the
 * container restarts rather than serving a broken app.
 *
 * @param port    - TCP port to listen on (default 3000).
 * @param dbPath  - SQLite file path (default /data/outlinergod.db).
 */
export async function startServer(port: number, dbPath: string): Promise<void> {
  const sqlite = createConnection(dbPath)

  try {
    await runMigrations(sqlite)
  } catch (err) {
    console.error('Migration failed — cannot start server:', err)
    process.exit(1)
  }

  // Routes are registered inside buildApp() — migrations have already run above.
  const app = buildApp(sqlite)

  try {
    await app.listen({ port, host: '0.0.0.0' })
  } catch (err) {
    app.log.error(err)
    process.exit(1)
  }
}
