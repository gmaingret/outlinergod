/**
 * Server entry point. Runs migrations then starts listening.
 * This file is NOT imported by tests — use buildApp() from index.ts instead.
 */
import { buildApp } from './index.js'

const PORT = parseInt(process.env.PORT ?? '3000', 10)

const app = buildApp()

// Migrations are called here (implemented in P1-11 — migrate.ts).
// When migrate.ts is ready: import { migrate } from './db/migrate.js'
// and call: await migrate(sqlite)

try {
  await app.listen({ port: PORT, host: '0.0.0.0' })
} catch (err) {
  app.log.error(err)
  process.exit(1)
}
