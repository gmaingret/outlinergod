import Fastify from 'fastify'
import { TypeBoxTypeProvider } from '@fastify/type-provider-typebox'
import Database from 'better-sqlite3'
import authPlugin from './middleware/auth.js'
import { createAuthRoutes, type GooglePayload } from './routes/auth.js'

/**
 * Application factory. Builds and returns a Fastify instance without calling
 * listen() — that lives in server.ts so tests can call buildApp() in isolation.
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

  // Auth routes at /api/auth/*
  void app.register(createAuthRoutes(sqliteInstance, verifyGoogle), { prefix: '/api' })

  // Placeholder root route
  app.get('/', async (_req, reply) => {
    return reply.send({ message: 'OutlinerGod API' })
  })

  return app
}
