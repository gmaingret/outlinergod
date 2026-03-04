/**
 * Tombstone purge tests
 *
 * Verifies that purgeTombstones() deletes soft-deleted records older than 90 days
 * from nodes, documents, and bookmarks, while retaining:
 *   - active records (deleted_at IS NULL)
 *   - recent tombstones (deleted_at within last 90 days)
 */
import { describe, it, expect, beforeEach } from 'vitest'
import type Database from 'better-sqlite3'
import { createTestDb } from './test-helpers/createTestDb.js'
import { purgeTombstones } from './tombstone.js'

const NOW = Date.now()
const OLD_TOMBSTONE = NOW - 91 * 24 * 60 * 60 * 1000 // 91 days ago — should be purged
const RECENT_TOMBSTONE = NOW - 30 * 24 * 60 * 60 * 1000 // 30 days ago — should be retained

/** Insert a test user and return their id */
function insertUser(sqlite: InstanceType<typeof Database>, id: string): void {
  sqlite
    .prepare(
      'INSERT INTO users (id, google_sub, email, name, picture, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)',
    )
    .run(id, `sub-${id}`, `${id}@example.com`, 'Test User', '', NOW, NOW)
}

/** Insert a test document and return its id */
function insertDocument(
  sqlite: InstanceType<typeof Database>,
  id: string,
  userId: string,
  deletedAt: number | null,
): void {
  sqlite
    .prepare(
      `INSERT INTO documents
        (id, user_id, title, title_hlc, type, parent_id_hlc, sort_order, sort_order_hlc,
         collapsed_hlc, deleted_at, deleted_hlc, device_id, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    )
    .run(id, userId, 'Test Doc', 'hlc1', 'document', '', 'aV', '', '', deletedAt, '', 'test-device', NOW, NOW)
}

/** Insert a test node and return its id */
function insertNode(
  sqlite: InstanceType<typeof Database>,
  id: string,
  userId: string,
  documentId: string,
  deletedAt: number | null,
): void {
  sqlite
    .prepare(
      `INSERT INTO nodes
        (id, document_id, user_id, content_hlc, note_hlc, parent_id_hlc,
         sort_order, sort_order_hlc, completed_hlc, color_hlc, collapsed_hlc,
         deleted_at, deleted_hlc, device_id, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    )
    .run(id, documentId, userId, '', '', '', 'aV', '', '', '', '', deletedAt, '', 'test-device', NOW, NOW)
}

/** Insert a test bookmark */
function insertBookmark(
  sqlite: InstanceType<typeof Database>,
  id: string,
  userId: string,
  deletedAt: number | null,
): void {
  sqlite
    .prepare(
      `INSERT INTO bookmarks
        (id, user_id, title, title_hlc, target_type, target_type_hlc,
         target_document_id_hlc, target_node_id_hlc, query_hlc,
         sort_order, sort_order_hlc, deleted_at, deleted_hlc, device_id, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    )
    .run(
      id, userId, 'Test Bookmark', 'hlc1', 'document', '',
      '', '', '', 'aV', '', deletedAt, '', 'test-device', NOW, NOW,
    )
}

function countRows(sqlite: InstanceType<typeof Database>, table: string): number {
  const row = sqlite.prepare(`SELECT COUNT(*) as cnt FROM ${table}`).get() as { cnt: number }
  return row.cnt
}

function countRowsWhere(
  sqlite: InstanceType<typeof Database>,
  table: string,
  id: string,
): number {
  const row = sqlite.prepare(`SELECT COUNT(*) as cnt FROM ${table} WHERE id = ?`).get(id) as { cnt: number }
  return row.cnt
}

describe('purgeTombstones', () => {
  let sqlite: InstanceType<typeof Database>

  beforeEach(() => {
    const testDb = createTestDb()
    sqlite = testDb.sqlite

    // Seed a user (required by all tables via FK)
    insertUser(sqlite, 'user-1')

    // Documents: old tombstone, recent tombstone, active
    insertDocument(sqlite, 'doc-old', 'user-1', OLD_TOMBSTONE)
    insertDocument(sqlite, 'doc-recent', 'user-1', RECENT_TOMBSTONE)
    insertDocument(sqlite, 'doc-active', 'user-1', null)

    // Nodes: old tombstone, recent tombstone, active (all under doc-active to avoid cascade delete)
    insertNode(sqlite, 'node-old', 'user-1', 'doc-active', OLD_TOMBSTONE)
    insertNode(sqlite, 'node-recent', 'user-1', 'doc-active', RECENT_TOMBSTONE)
    insertNode(sqlite, 'node-active', 'user-1', 'doc-active', null)

    // Bookmarks: old tombstone, recent tombstone, active
    insertBookmark(sqlite, 'bm-old', 'user-1', OLD_TOMBSTONE)
    insertBookmark(sqlite, 'bm-recent', 'user-1', RECENT_TOMBSTONE)
    insertBookmark(sqlite, 'bm-active', 'user-1', null)
  })

  it('purges old document tombstones (>90 days)', () => {
    purgeTombstones(sqlite)
    expect(countRowsWhere(sqlite, 'documents', 'doc-old')).toBe(0)
  })

  it('retains recent document tombstones (30 days)', () => {
    purgeTombstones(sqlite)
    expect(countRowsWhere(sqlite, 'documents', 'doc-recent')).toBe(1)
  })

  it('retains active documents (deleted_at IS NULL)', () => {
    purgeTombstones(sqlite)
    expect(countRowsWhere(sqlite, 'documents', 'doc-active')).toBe(1)
  })

  it('purges old node tombstones (>90 days)', () => {
    purgeTombstones(sqlite)
    expect(countRowsWhere(sqlite, 'nodes', 'node-old')).toBe(0)
  })

  it('retains recent node tombstones (30 days)', () => {
    purgeTombstones(sqlite)
    expect(countRowsWhere(sqlite, 'nodes', 'node-recent')).toBe(1)
  })

  it('retains active nodes (deleted_at IS NULL)', () => {
    purgeTombstones(sqlite)
    expect(countRowsWhere(sqlite, 'nodes', 'node-active')).toBe(1)
  })

  it('purges old bookmark tombstones (>90 days)', () => {
    purgeTombstones(sqlite)
    expect(countRowsWhere(sqlite, 'bookmarks', 'bm-old')).toBe(0)
  })

  it('retains recent bookmark tombstones (30 days)', () => {
    purgeTombstones(sqlite)
    expect(countRowsWhere(sqlite, 'bookmarks', 'bm-recent')).toBe(1)
  })

  it('retains active bookmarks (deleted_at IS NULL)', () => {
    purgeTombstones(sqlite)
    expect(countRowsWhere(sqlite, 'bookmarks', 'bm-active')).toBe(1)
  })

  it('handles empty tables without error', () => {
    // Clear all seeded data
    sqlite.exec("DELETE FROM bookmarks; DELETE FROM nodes; DELETE FROM documents; DELETE FROM users;")
    expect(() => purgeTombstones(sqlite)).not.toThrow()
  })
})
