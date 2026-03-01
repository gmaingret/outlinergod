import Database from 'better-sqlite3'
import { drizzle } from 'drizzle-orm/better-sqlite3'
import { migrate } from '../db/migrate.js'

/**
 * Creates a fresh in-memory SQLite DB with migrations applied.
 * Use this in every test file that needs DB access — never import the
 * singleton db from src/db/index.ts in tests.
 */
export function createTestDb() {
  const sqlite = new Database(':memory:')
  sqlite.pragma('journal_mode = WAL')
  sqlite.pragma('foreign_keys = ON')
  migrate(sqlite)
  const db = drizzle(sqlite)
  return { sqlite, db }
}
