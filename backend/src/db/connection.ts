import Database from 'better-sqlite3'

/**
 * Opens a better-sqlite3 connection at the given path and immediately
 * enables WAL journal mode and foreign key enforcement.
 *
 * Use this factory everywhere a DB connection is needed — in server.ts,
 * in createTestDb.ts, and directly in tests via createConnection(':memory:').
 * Never import the singleton from src/db/index.ts in tests.
 */
export function createConnection(dbPath: string): InstanceType<typeof Database> {
  const sqlite = new Database(dbPath)
  sqlite.pragma('journal_mode = WAL')
  sqlite.pragma('foreign_keys = ON')
  return sqlite
}
