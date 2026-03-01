import { describe, it, expect } from 'vitest'
import { getTableConfig } from 'drizzle-orm/sqlite-core'
import { drizzle } from 'drizzle-orm/better-sqlite3'
import Database from 'better-sqlite3'
import { refreshTokens } from './refresh_tokens.js'

describe('refresh_tokens schema', () => {
  it('refreshTokens_tableName', () => {
    const config = getTableConfig(refreshTokens)
    expect(config.name).toBe('refresh_tokens')
  })

  it('refreshTokens_tokenIsPrimaryKey', () => {
    const config = getTableConfig(refreshTokens)
    const tokenCol = config.columns.find((c) => c.name === 'token')
    expect(tokenCol?.primary).toBe(true)
  })

  it('refreshTokens_revokedDefaultsToZero', () => {
    const sqlite = new Database(':memory:')
    sqlite.pragma('foreign_keys = ON')
    sqlite.exec(`
      CREATE TABLE users (
        id TEXT PRIMARY KEY NOT NULL,
        google_sub TEXT NOT NULL UNIQUE,
        email TEXT NOT NULL,
        name TEXT NOT NULL,
        picture TEXT NOT NULL DEFAULT '',
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL
      )
    `)
    sqlite.exec(`
      CREATE TABLE refresh_tokens (
        token TEXT PRIMARY KEY NOT NULL,
        user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        device_id TEXT NOT NULL,
        expires_at INTEGER NOT NULL,
        created_at INTEGER NOT NULL,
        revoked INTEGER NOT NULL DEFAULT 0
      )
    `)
    const db = drizzle(sqlite)

    const now = Date.now()
    sqlite.prepare(`INSERT INTO users VALUES (?, ?, ?, ?, ?, ?, ?)`).run(
      'user-1',
      'google-sub-1',
      'test@example.com',
      'Test User',
      '',
      now,
      now,
    )

    db.insert(refreshTokens)
      .values({
        token: 'abc123',
        user_id: 'user-1',
        device_id: 'device-1',
        expires_at: now + 3_600_000,
        created_at: now,
        // revoked intentionally omitted — should default to 0
      })
      .run()

    const rows = db.select().from(refreshTokens).all()
    expect(rows).toHaveLength(1)
    expect(rows[0].revoked).toBe(0)
  })

  it('refreshTokens_cascadeDeleteOnUser', () => {
    const sqlite = new Database(':memory:')
    sqlite.pragma('foreign_keys = ON')
    sqlite.exec(`
      CREATE TABLE users (
        id TEXT PRIMARY KEY NOT NULL,
        google_sub TEXT NOT NULL UNIQUE,
        email TEXT NOT NULL,
        name TEXT NOT NULL,
        picture TEXT NOT NULL DEFAULT '',
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL
      )
    `)
    sqlite.exec(`
      CREATE TABLE refresh_tokens (
        token TEXT PRIMARY KEY NOT NULL,
        user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        device_id TEXT NOT NULL,
        expires_at INTEGER NOT NULL,
        created_at INTEGER NOT NULL,
        revoked INTEGER NOT NULL DEFAULT 0
      )
    `)
    const db = drizzle(sqlite)

    const now = Date.now()
    sqlite.prepare(`INSERT INTO users VALUES (?, ?, ?, ?, ?, ?, ?)`).run(
      'user-2',
      'google-sub-2',
      'other@example.com',
      'Other User',
      '',
      now,
      now,
    )

    db.insert(refreshTokens)
      .values({
        token: 'tok-xyz',
        user_id: 'user-2',
        device_id: 'device-2',
        expires_at: now + 3_600_000,
        created_at: now,
        revoked: 0,
      })
      .run()

    // delete the user — cascade should remove the token
    sqlite.prepare(`DELETE FROM users WHERE id = ?`).run('user-2')

    const rows = db.select().from(refreshTokens).all()
    expect(rows).toHaveLength(0)
  })
})
