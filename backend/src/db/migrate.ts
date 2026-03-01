import { drizzle } from 'drizzle-orm/better-sqlite3'
import { migrate as drizzleMigrate } from 'drizzle-orm/better-sqlite3/migrator'
import type Database from 'better-sqlite3'
import { fileURLToPath } from 'node:url'
import { dirname, join, resolve } from 'node:path'

/**
 * Async migration runner. Applies all pending Drizzle migrations to the given
 * better-sqlite3 connection, then logs success.
 *
 * Used by startServer() in src/index.ts for production startup.
 * Export: both named and default.
 */
export async function runMigrations(sqlite: InstanceType<typeof Database>): Promise<void> {
  const db = drizzle(sqlite)
  const migrationsFolder = resolve(fileURLToPath(import.meta.url), '..', '..', '..', 'drizzle')
  drizzleMigrate(db, { migrationsFolder })
  console.log('Migrations applied successfully')
}

export default runMigrations

/**
 * Synchronous migration runner kept for backward-compatibility.
 * Used by createTestDb() in test-helpers — new code should use runMigrations().
 */
export function migrate(sqlite: InstanceType<typeof Database>): void {
  const db = drizzle(sqlite)
  const migrationsFolder = join(dirname(fileURLToPath(import.meta.url)), '../../drizzle')
  drizzleMigrate(db, { migrationsFolder })
}
