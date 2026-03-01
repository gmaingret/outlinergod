import { drizzle } from 'drizzle-orm/better-sqlite3'
import { migrate as drizzleMigrate } from 'drizzle-orm/better-sqlite3/migrator'
import type Database from 'better-sqlite3'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

export function migrate(sqlite: InstanceType<typeof Database>): void {
  const db = drizzle(sqlite)
  const migrationsFolder = join(dirname(fileURLToPath(import.meta.url)), '../../drizzle')
  drizzleMigrate(db, { migrationsFolder })
}
