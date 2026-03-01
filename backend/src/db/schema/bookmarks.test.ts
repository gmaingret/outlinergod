import { describe, it, expect } from 'vitest'
import { getTableConfig } from 'drizzle-orm/sqlite-core'
import { drizzle } from 'drizzle-orm/better-sqlite3'
import Database from 'better-sqlite3'
import { bookmarks } from './bookmarks.js'

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
    CREATE TABLE documents (
      id TEXT PRIMARY KEY NOT NULL,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      title TEXT NOT NULL,
      title_hlc TEXT NOT NULL,
      type TEXT NOT NULL,
      parent_id TEXT REFERENCES documents(id),
      parent_id_hlc TEXT NOT NULL,
      sort_order TEXT NOT NULL,
      sort_order_hlc TEXT NOT NULL,
      collapsed INTEGER NOT NULL DEFAULT 0,
      collapsed_hlc TEXT NOT NULL,
      deleted_at INTEGER,
      deleted_hlc TEXT NOT NULL,
      device_id TEXT NOT NULL,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    )
  `)
  sqlite.exec(`
    CREATE TABLE nodes (
      id TEXT PRIMARY KEY NOT NULL,
      document_id TEXT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      content TEXT NOT NULL DEFAULT '',
      content_hlc TEXT NOT NULL,
      note TEXT NOT NULL DEFAULT '',
      note_hlc TEXT NOT NULL,
      parent_id TEXT REFERENCES nodes(id),
      parent_id_hlc TEXT NOT NULL,
      sort_order TEXT NOT NULL,
      sort_order_hlc TEXT NOT NULL,
      completed INTEGER NOT NULL DEFAULT 0,
      completed_hlc TEXT NOT NULL,
      color INTEGER NOT NULL DEFAULT 0,
      color_hlc TEXT NOT NULL,
      collapsed INTEGER NOT NULL DEFAULT 0,
      collapsed_hlc TEXT NOT NULL,
      deleted_at INTEGER,
      deleted_hlc TEXT NOT NULL,
      device_id TEXT NOT NULL,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    )
  `)
  sqlite.exec(`
    CREATE TABLE bookmarks (
      id TEXT PRIMARY KEY NOT NULL,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      node_id TEXT NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
      document_id TEXT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
      sort_order TEXT NOT NULL,
      sort_order_hlc TEXT NOT NULL,
      deleted_at INTEGER,
      deleted_hlc TEXT NOT NULL,
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
  sqlite
    .prepare(`INSERT INTO documents VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`)
    .run(
      'doc-1',
      'user-1',
      'Test Doc',
      '0000000000000:0:device-1',
      'document',
      null,
      '0000000000000:0:device-1',
      'a0',
      '0000000000000:0:device-1',
      0,
      '0000000000000:0:device-1',
      null,
      '0000000000000:0:device-1',
      'device-1',
      1000,
      1000,
    )
  sqlite
    .prepare(
      `INSERT INTO nodes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    )
    .run(
      'node-1',
      'doc-1',
      'user-1',
      '',
      '0000000000000:0:device-1',
      '',
      '0000000000000:0:device-1',
      null,
      '0000000000000:0:device-1',
      'a0',
      '0000000000000:0:device-1',
      0,
      '0000000000000:0:device-1',
      0,
      '0000000000000:0:device-1',
      0,
      '0000000000000:0:device-1',
      null,
      '0000000000000:0:device-1',
      'device-1',
      1000,
      1000,
    )
  return { sqlite, db: drizzle(sqlite) }
}

const BASE_BOOKMARK = {
  user_id: 'user-1',
  node_id: 'node-1',
  document_id: 'doc-1',
  sort_order: 'a0',
  sort_order_hlc: '0000000000000:0:device-1',
  deleted_hlc: '0000000000000:0:device-1',
  device_id: 'device-1',
  created_at: 1000,
  updated_at: 1000,
}

describe('bookmarks schema', () => {
  it('bookmarks_tableName', () => {
    const config = getTableConfig(bookmarks)
    expect(config.name).toBe('bookmarks')
  })

  it('bookmarks_hasTwoHlcColumns', () => {
    const config = getTableConfig(bookmarks)
    const columnNames = config.columns.map((c) => c.name)
    expect(columnNames).toContain('sort_order_hlc')
    expect(columnNames).toContain('deleted_hlc')
  })

  it('bookmarks_sortOrder_isText', () => {
    const config = getTableConfig(bookmarks)
    const sortOrderCol = config.columns.find((c) => c.name === 'sort_order')
    expect(sortOrderCol).toBeDefined()
    expect(sortOrderCol!.dataType).toBe('string')
  })

  it('bookmarks_cascadeDelete_onNodeDelete', () => {
    const { sqlite, db } = makeInMemoryDb()
    db.insert(bookmarks)
      .values({
        ...BASE_BOOKMARK,
        id: 'bookmark-cascade-test',
      })
      .run()
    const before = db.select().from(bookmarks).all()
    expect(before).toHaveLength(1)
    sqlite.prepare('DELETE FROM nodes WHERE id = ?').run('node-1')
    const after = db.select().from(bookmarks).all()
    expect(after).toHaveLength(0)
  })

  it('bookmarks_deletedAt_isNullable', () => {
    const { db } = makeInMemoryDb()
    db.insert(bookmarks)
      .values({
        ...BASE_BOOKMARK,
        id: 'bookmark-nullable-test',
        // deleted_at intentionally omitted — should be null
      })
      .run()
    const rows = db.select().from(bookmarks).all()
    expect(rows).toHaveLength(1)
    expect(rows[0].deleted_at).toBeNull()
  })
})
