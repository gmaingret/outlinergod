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
      theme TEXT NOT NULL DEFAULT 'dark',
      theme_hlc TEXT NOT NULL,
      density TEXT NOT NULL DEFAULT 'cozy',
      density_hlc TEXT NOT NULL,
      show_guide_lines INTEGER NOT NULL DEFAULT 1,
      show_guide_lines_hlc TEXT NOT NULL,
      show_backlink_badge INTEGER NOT NULL DEFAULT 1,
      show_backlink_badge_hlc TEXT NOT NULL,
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
  density_hlc: '0000000000000:0:device-1',
  show_guide_lines_hlc: '0000000000000:0:device-1',
  show_backlink_badge_hlc: '0000000000000:0:device-1',
  device_id: 'device-1',
  created_at: 1000,
  updated_at: 1000,
}

describe('settings schema', () => {
  it('settings_tableName', () => {
    const config = getTableConfig(settings)
    expect(config.name).toBe('settings')
  })

  it('settings_hasAllHlcColumns', () => {
    const config = getTableConfig(settings)
    const columnNames = config.columns.map((c) => c.name)
    expect(columnNames).toContain('theme_hlc')
    expect(columnNames).toContain('density_hlc')
    expect(columnNames).toContain('show_guide_lines_hlc')
    expect(columnNames).toContain('show_backlink_badge_hlc')
  })

  it('settings_themeDefaultsToDark', () => {
    const { sqlite } = makeInMemoryDb()
    sqlite
      .prepare(
        `INSERT INTO settings (id, user_id, theme_hlc, density_hlc, show_guide_lines_hlc, show_backlink_badge_hlc, device_id, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      )
      .run('setting-1', 'user-1', '', '', '', '', 'device-1', 1000, 1000)
    const row = sqlite.prepare('SELECT * FROM settings WHERE id = ?').get('setting-1') as {
      theme: string
    }
    expect(row.theme).toBe('dark')
  })

  it('settings_densityDefaultsToCozy', () => {
    const { sqlite } = makeInMemoryDb()
    sqlite
      .prepare(
        `INSERT INTO settings (id, user_id, theme_hlc, density_hlc, show_guide_lines_hlc, show_backlink_badge_hlc, device_id, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      )
      .run('setting-2', 'user-1', '', '', '', '', 'device-1', 1000, 1000)
    const row = sqlite.prepare('SELECT * FROM settings WHERE id = ?').get('setting-2') as {
      density: string
    }
    expect(row.density).toBe('cozy')
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
