/**
 * Server entry point. Configures the DB connection, runs migrations,
 * then starts listening. This file is NOT imported by tests — use
 * buildApp() from index.ts instead.
 */
import Database from 'better-sqlite3'
import { buildApp } from './index.js'
import { migrate } from './db/migrate.js'

const PORT = parseInt(process.env.PORT ?? '3000', 10)
const DB_PATH = process.env.DATABASE_PATH ?? '/data/outlinergod.db'

const sqlite = new Database(DB_PATH)
sqlite.pragma('journal_mode = WAL')
sqlite.pragma('foreign_keys = ON')

migrate(sqlite)

const app = buildApp(sqlite)

try {
  await app.listen({ port: PORT, host: '0.0.0.0' })
} catch (err) {
  app.log.error(err)
  process.exit(1)
}
