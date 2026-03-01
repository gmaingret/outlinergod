/**
 * P1-11 migrate.test.ts — 4 test cases
 *
 * Each test creates its own fresh in-memory SQLite DB.
 * Never imports the singleton db from src/db/index.ts.
 */
import { describe, it, expect } from 'vitest'
import Database from 'better-sqlite3'
import { runMigrations } from './migrate.js'

describe('runMigrations', () => {
  // 1. The function is exported
  it('migrate_functionIsExported', () => {
    expect(typeof runMigrations).toBe('function')
  })

  // 2. Runs without throwing against a fresh in-memory DB
  it('migrate_runsWithoutError', async () => {
    const sqlite = new Database(':memory:')
    try {
      await expect(runMigrations(sqlite)).resolves.toBeUndefined()
    } finally {
      sqlite.close()
    }
  })

  // 3. Running migrations twice on the same DB is safe (idempotent)
  it('migrate_isIdempotent', async () => {
    const sqlite = new Database(':memory:')
    try {
      await runMigrations(sqlite)
      await expect(runMigrations(sqlite)).resolves.toBeUndefined()
    } finally {
      sqlite.close()
    }
  })

  // 4. All 6 application tables exist after migration
  it('migrate_allTablesExist_afterMigration', async () => {
    const sqlite = new Database(':memory:')
    try {
      await runMigrations(sqlite)

      const rows = sqlite
        .prepare("SELECT name FROM sqlite_master WHERE type='table'")
        .all() as { name: string }[]
      const tableNames = rows.map((r) => r.name)

      expect(tableNames).toContain('users')
      expect(tableNames).toContain('refresh_tokens')
      expect(tableNames).toContain('documents')
      expect(tableNames).toContain('nodes')
      expect(tableNames).toContain('bookmarks')
      expect(tableNames).toContain('settings')
    } finally {
      sqlite.close()
    }
  })
})
