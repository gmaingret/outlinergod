import { describe, it, expect } from 'vitest'
import { getTableConfig } from 'drizzle-orm/sqlite-core'
import { drizzle } from 'drizzle-orm/better-sqlite3'
import Database from 'better-sqlite3'
import { documents } from './documents.js'

const HLC_COLUMNS = ['title_hlc', 'parent_id_hlc', 'sort_order_hlc', 'collapsed_hlc', 'deleted_hlc']

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

const BASE_DOC = {
  user_id: 'user-1',
  title: 'My Document',
  title_hlc: '0000000000000:0:device-1',
  type: 'document',
  parent_id: null,
  parent_id_hlc: '0000000000000:0:device-1',
  sort_order: 'a0',
  sort_order_hlc: '0000000000000:0:device-1',
  collapsed_hlc: '0000000000000:0:device-1',
  deleted_hlc: '0000000000000:0:device-1',
  device_id: 'device-1',
  created_at: 1000,
  updated_at: 1000,
}

describe('documents schema', () => {
  it('documents_tableName', () => {
    const config = getTableConfig(documents)
    expect(config.name).toBe('documents')
  })

  it('documents_hasAllFiveHlcColumns', () => {
    const config = getTableConfig(documents)
    const columnNames = config.columns.map((c) => c.name)
    for (const hlcCol of HLC_COLUMNS) {
      expect(columnNames).toContain(hlcCol)
    }
  })

  it('documents_collapsedDefaultsToZero', () => {
    const { db } = makeInMemoryDb()
    db.insert(documents)
      .values({
        ...BASE_DOC,
        id: 'doc-collapsed-test',
        // collapsed intentionally omitted — should default to 0
      })
      .run()
    const rows = db.select().from(documents).all()
    expect(rows).toHaveLength(1)
    expect(rows[0].collapsed).toBe(0)
  })

  it('documents_parentId_selfReference_works', () => {
    const { db } = makeInMemoryDb()

    db.insert(documents)
      .values({
        ...BASE_DOC,
        id: 'root-doc',
        parent_id: null,
      })
      .run()

    db.insert(documents)
      .values({
        ...BASE_DOC,
        id: 'child-doc',
        parent_id: 'root-doc',
        sort_order: 'a1',
      })
      .run()

    const rows = db.select().from(documents).all()
    expect(rows).toHaveLength(2)
    expect(rows.find((r) => r.id === 'root-doc')?.parent_id).toBeNull()
    expect(rows.find((r) => r.id === 'child-doc')?.parent_id).toBe('root-doc')
  })

  it('documents_deletedAt_isNullable', () => {
    const { db } = makeInMemoryDb()
    db.insert(documents)
      .values({
        ...BASE_DOC,
        id: 'doc-nullable-test',
        // deleted_at intentionally omitted — should be null
      })
      .run()
    const rows = db.select().from(documents).all()
    expect(rows).toHaveLength(1)
    expect(rows[0].deleted_at).toBeNull()
  })
})
