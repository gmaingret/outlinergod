import { drizzle } from 'drizzle-orm/better-sqlite3'
import { createConnection } from '../db/connection.js'
import { migrate } from '../db/migrate.js'

/**
 * Creates a fresh in-memory SQLite DB with migrations applied.
 * Use this in every test file that needs DB access — never import the
 * singleton db from src/db/index.ts in tests.
 */
export function createTestDb() {
  const sqlite = createConnection(':memory:')
  migrate(sqlite)
  const db = drizzle(sqlite)
  return { sqlite, db }
}
