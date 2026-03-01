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
    CREATE TABLE bookmarks (
      id TEXT PRIMARY KEY NOT NULL,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      title TEXT NOT NULL,
      title_hlc TEXT NOT NULL,
      target_type TEXT NOT NULL,
      target_type_hlc TEXT NOT NULL,
      target_document_id TEXT,
      target_document_id_hlc TEXT NOT NULL,
      target_node_id TEXT,
      target_node_id_hlc TEXT NOT NULL,
      query TEXT,
      query_hlc TEXT NOT NULL,
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
  return { sqlite, db: drizzle(sqlite) }
}

const BASE_BOOKMARK = {
  user_id: 'user-1',
  title: 'Test Bookmark',
  title_hlc: '0000000000000:0:device-1',
  target_type: 'document',
  target_type_hlc: '0000000000000:0:device-1',
  target_document_id: 'doc-1',
  target_document_id_hlc: '0000000000000:0:device-1',
  target_node_id_hlc: '0000000000000:0:device-1',
  query_hlc: '0000000000000:0:device-1',
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

  it('bookmarks_hasAllHlcColumns', () => {
    const config = getTableConfig(bookmarks)
    const columnNames = config.columns.map((c) => c.name)
    expect(columnNames).toContain('title_hlc')
    expect(columnNames).toContain('target_type_hlc')
    expect(columnNames).toContain('target_document_id_hlc')
    expect(columnNames).toContain('target_node_id_hlc')
    expect(columnNames).toContain('query_hlc')
    expect(columnNames).toContain('sort_order_hlc')
    expect(columnNames).toContain('deleted_hlc')
  })

  it('bookmarks_sortOrder_isText', () => {
    const config = getTableConfig(bookmarks)
    const sortOrderCol = config.columns.find((c) => c.name === 'sort_order')
    expect(sortOrderCol).toBeDefined()
    expect(sortOrderCol!.dataType).toBe('string')
  })

  it('bookmarks_cascadeDelete_onUserDelete', () => {
    const { sqlite, db } = makeInMemoryDb()
    db.insert(bookmarks)
      .values({
        ...BASE_BOOKMARK,
        id: 'bookmark-cascade-test',
      })
      .run()
    const before = db.select().from(bookmarks).all()
    expect(before).toHaveLength(1)
    sqlite.prepare('DELETE FROM users WHERE id = ?').run('user-1')
    const after = db.select().from(bookmarks).all()
    expect(after).toHaveLength(0)
  })

  it('bookmarks_deletedAt_isNullable', () => {
    const { db } = makeInMemoryDb()
    db.insert(bookmarks)
      .values({
        ...BASE_BOOKMARK,
        id: 'bookmark-nullable-test',
      })
      .run()
    const rows = db.select().from(bookmarks).all()
    expect(rows).toHaveLength(1)
    expect(rows[0].deleted_at).toBeNull()
  })
})
