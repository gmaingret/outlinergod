import { describe, it, expect } from 'vitest'
import { getTableConfig } from 'drizzle-orm/sqlite-core'
import { drizzle } from 'drizzle-orm/better-sqlite3'
import Database from 'better-sqlite3'
import { settings } from './settings.js'

function makeInMemoryDb() {
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
    CREATE TABLE settings (
      id TEXT PRIMARY KEY NOT NULL,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      theme TEXT NOT NULL DEFAULT 'system',
      theme_hlc TEXT NOT NULL,
      font_size INTEGER NOT NULL DEFAULT 14,
      font_size_hlc TEXT NOT NULL,
      device_id TEXT NOT NULL,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    )
  `)
  sqlite.prepare(`INSERT INTO users VALUES (?, ?, ?, ?, ?, ?, ?)`).run(
    'user-1',
    'google-sub-1',
    'test@example.com',
    'Test User',
    '',
    Date.now(),
    Date.now(),
  )
  return { sqlite, db: drizzle(sqlite) }
}

const BASE_SETTING = {
  user_id: 'user-1',
  theme_hlc: '0000000000000:0:device-1',
  font_size_hlc: '0000000000000:0:device-1',
  device_id: 'device-1',
  created_at: 1000,
  updated_at: 1000,
}

describe('settings schema', () => {
  it('settings_tableName', () => {
    const config = getTableConfig(settings)
    expect(config.name).toBe('settings')
  })

  it('settings_hasTwoHlcColumns', () => {
    const config = getTableConfig(settings)
    const columnNames = config.columns.map((c) => c.name)
    expect(columnNames).toContain('theme_hlc')
    expect(columnNames).toContain('font_size_hlc')
  })

  it('settings_themeDefaultsToSystem', () => {
    const { sqlite } = makeInMemoryDb()
    sqlite
      .prepare(
        `INSERT INTO settings (id, user_id, theme_hlc, font_size_hlc, device_id, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
      )
      .run('setting-1', 'user-1', '0000000000000:0:device-1', '0000000000000:0:device-1', 'device-1', 1000, 1000)
    const row = sqlite.prepare('SELECT * FROM settings WHERE id = ?').get('setting-1') as {
      theme: string
    }
    expect(row.theme).toBe('system')
  })

  it('settings_fontSizeDefaultsTo14', () => {
    const { sqlite } = makeInMemoryDb()
    sqlite
      .prepare(
        `INSERT INTO settings (id, user_id, theme_hlc, font_size_hlc, device_id, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
      )
      .run('setting-2', 'user-1', '0000000000000:0:device-1', '0000000000000:0:device-1', 'device-1', 1000, 1000)
    const row = sqlite.prepare('SELECT * FROM settings WHERE id = ?').get('setting-2') as {
      font_size: number
    }
    expect(row.font_size).toBe(14)
  })

  it('settings_cascadeDelete_onUserDelete', () => {
    const { sqlite, db } = makeInMemoryDb()
    db.insert(settings)
      .values({
        ...BASE_SETTING,
        id: 'setting-cascade-test',
      })
      .run()
    const before = db.select().from(settings).all()
    expect(before).toHaveLength(1)
    sqlite.prepare('DELETE FROM users WHERE id = ?').run('user-1')
    const after = db.select().from(settings).all()
    expect(after).toHaveLength(0)
  })
})
