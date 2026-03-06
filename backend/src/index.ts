import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'
import Fastify from 'fastify'
import fastifyStatic from '@fastify/static'
import { TypeBoxTypeProvider } from '@fastify/type-provider-typebox'
import Database from 'better-sqlite3'
import authPlugin from './middleware/auth.js'
import { createAuthRoutes, type GooglePayload } from './routes/auth.js'
import { createDocumentRoutes } from './routes/documents.js'
import { createNodeRoutes } from './routes/nodes.js'
import { createSyncRoutes } from './routes/sync.js'
import { createFileRoutes } from './routes/files.js'
import { createSettingsRoutes } from './routes/settings.js'
import { createBookmarkRoutes } from './routes/bookmarks.js'
import { createExportRoutes } from './routes/export.js'
import { createHealthRoute } from './routes/health.js'
import { runMigrations } from './db/migrate.js'
import { createConnection } from './db/connection.js'
import { purgeTombstones } from './tombstone.js'
import { purgeStaleRefreshTokens } from './refresh-token-purge.js'

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

  // Override the built-in JSON body parser so that empty bodies (e.g. DELETE
  // requests sent with Content-Type: application/json but no payload) are
  // treated as undefined rather than causing a 400 parse error.  This ensures
  // requireAuth preHandlers can fire and return 401 for unauthenticated calls.
  app.addContentTypeParser('application/json', { parseAs: 'string' }, (_req, body, done) => {
    const str = body as string
    if (!str || str.trim() === '') {
      done(null, undefined)
      return
    }
    try {
      done(null, JSON.parse(str))
    } catch (err) {
      done(err as Error, undefined)
    }
  })

  // Global JWT middleware — sets req.user on every request.
  void app.register(authPlugin)

  // Health check — unprotected, no /api prefix
  void app.register(createHealthRoute(sqliteInstance))

  // Auth routes at /api/auth/*
  void app.register(createAuthRoutes(sqliteInstance, verifyGoogle), { prefix: '/api' })

  // Document routes at /api/documents/*
  void app.register(createDocumentRoutes(sqliteInstance), { prefix: '/api' })

  // Node routes at /api/nodes/*
  void app.register(createNodeRoutes(sqliteInstance), { prefix: '/api/nodes' })

  // Sync routes at /api/sync/*
  void app.register(createSyncRoutes(sqliteInstance), { prefix: '/api' })

  // File routes at /api/files/*
  void app.register(createFileRoutes(sqliteInstance), { prefix: '/api' })

  // Settings routes at /api/settings
  void app.register(createSettingsRoutes(sqliteInstance), { prefix: '/api' })

  // Bookmark routes at /api/bookmarks/*
  void app.register(createBookmarkRoutes(sqliteInstance), { prefix: '/api' })

  // Export route at /api/export
  void app.register(createExportRoutes(sqliteInstance), { prefix: '/api' })

  // Serve React SPA from web/dist.
  // In production Docker: /app/web/dist (set WEB_DIST_PATH env var).
  // In dev (tsx src/server.ts): computed relative to src/ → ../../web/dist.
  const __dirname = fileURLToPath(new URL('.', import.meta.url))
  const webDistPath = process.env.WEB_DIST_PATH ?? join(__dirname, '..', '..', 'web', 'dist')

  void app.register(fastifyStatic, {
    root: webDistPath,
    wildcard: false,  // CRITICAL: do not use wildcard: true — it creates GET /* that shadows /api routes
  })

  // SPA fallback: any GET that doesn't match an API route or static file returns index.html.
  // This makes deep links work (e.g. /documents/123 loads the app, React Router handles routing).
  // /api/* paths that are not registered return 404 as expected (not the SPA).
  app.setNotFoundHandler((req, reply) => {
    if (req.url.startsWith('/api/')) {
      return reply.code(404).send({ error: 'Not Found' })
    }
    return reply.sendFile('index.html')
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
  // Ensure the uploads directory exists inside the Docker volume.
  // The Dockerfile creates it at image build time, but a pre-existing named
  // volume shadows that layer, so we create it here at every startup.
  const uploadsPath = process.env.UPLOADS_PATH ?? '/data/uploads'
  mkdirSync(uploadsPath, { recursive: true })

  const sqlite = createConnection(dbPath)

  try {
    await runMigrations(sqlite)
  } catch (err) {
    console.error('Migration failed — cannot start server:', err)
    process.exit(1)
  }

  // JWT_SECRET guard — must be before buildApp so server refuses to start with weak secret.
  const jwtSecret = process.env.JWT_SECRET ?? ''
  if (jwtSecret.length < 32) {
    console.error('FATAL: JWT_SECRET must be at least 32 characters')
    process.exit(1)
  }

  // Purge tombstones older than 90 days. Keeps the SQLite database size
  // manageable over time without requiring manual maintenance.
  purgeTombstones(sqlite)

  // Purge stale refresh tokens once at startup, then every 24 hours.
  purgeStaleRefreshTokens(sqlite)
  setInterval(() => purgeStaleRefreshTokens(sqlite), 24 * 60 * 60 * 1000)

  // Routes are registered inside buildApp() — migrations have already run above.
  const app = buildApp(sqlite)

  try {
    await app.listen({ port, host: '0.0.0.0' })
  } catch (err) {
    app.log.error(err)
    process.exit(1)
  }
}
