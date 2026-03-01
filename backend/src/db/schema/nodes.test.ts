import { describe, it, expect } from 'vitest'
import { getTableConfig } from 'drizzle-orm/sqlite-core'
import { drizzle } from 'drizzle-orm/better-sqlite3'
import Database from 'better-sqlite3'
import { nodes } from './nodes.js'

const HLC_COLUMNS = [
  'content_hlc',
  'note_hlc',
  'parent_id_hlc',
  'sort_order_hlc',
  'completed_hlc',
  'color_hlc',
  'collapsed_hlc',
  'deleted_hlc',
]

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
    .prepare(
      `INSERT INTO documents VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    )
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
  return { sqlite, db: drizzle(sqlite) }
}

const BASE_NODE = {
  document_id: 'doc-1',
  user_id: 'user-1',
  content_hlc: '0000000000000:0:device-1',
  note_hlc: '0000000000000:0:device-1',
  parent_id: null,
  parent_id_hlc: '0000000000000:0:device-1',
  sort_order: 'a0',
  sort_order_hlc: '0000000000000:0:device-1',
  completed_hlc: '0000000000000:0:device-1',
  color_hlc: '0000000000000:0:device-1',
  collapsed_hlc: '0000000000000:0:device-1',
  deleted_hlc: '0000000000000:0:device-1',
  device_id: 'device-1',
  created_at: 1000,
  updated_at: 1000,
}

describe('nodes schema', () => {
  it('nodes_tableName', () => {
    const config = getTableConfig(nodes)
    expect(config.name).toBe('nodes')
  })

  it('nodes_hasAllEightHlcColumns', () => {
    const config = getTableConfig(nodes)
    const columnNames = config.columns.map((c) => c.name)
    for (const hlcCol of HLC_COLUMNS) {
      expect(columnNames).toContain(hlcCol)
    }
  })

  it('nodes_noChildrenColumn', () => {
    const config = getTableConfig(nodes)
    const columnNames = config.columns.map((c) => c.name)
    expect(columnNames).not.toContain('children')
  })

  it('nodes_rootNode_hasNullParentId', () => {
    const { db } = makeInMemoryDb()
    db.insert(nodes)
      .values({
        ...BASE_NODE,
        id: 'node-root',
        parent_id: null,
      })
      .run()
    const rows = db.select().from(nodes).all()
    expect(rows).toHaveLength(1)
    expect(rows[0].parent_id).toBeNull()
  })

  it('nodes_cascadeDelete_onDocumentDelete', () => {
    const { sqlite, db } = makeInMemoryDb()
    db.insert(nodes)
      .values({
        ...BASE_NODE,
        id: 'node-cascade-test',
      })
      .run()
    const before = db.select().from(nodes).all()
    expect(before).toHaveLength(1)
    sqlite.prepare('DELETE FROM documents WHERE id = ?').run('doc-1')
    const after = db.select().from(nodes).all()
    expect(after).toHaveLength(0)
  })

  it('nodes_colorDefaultsToZero', () => {
    const { db } = makeInMemoryDb()
    db.insert(nodes)
      .values({
        ...BASE_NODE,
        id: 'node-color-test',
        // color intentionally omitted — should default to 0
      })
      .run()
    const rows = db.select().from(nodes).all()
    expect(rows).toHaveLength(1)
    expect(rows[0].color).toBe(0)
  })
})
