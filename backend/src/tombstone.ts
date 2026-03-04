import type Database from 'better-sqlite3'

/**
 * Purge soft-deleted records older than 90 days from nodes, documents, and bookmarks.
 * Called once at server startup after migrations run.
 *
 * Records with deleted_at IS NULL are active and never touched.
 * Records with deleted_at set to a timestamp within the last 90 days are retained
 * (needed for sync — other devices may not have received the tombstone yet).
 * Records with deleted_at older than 90 days are safe to hard-delete.
 */
export function purgeTombstones(sqlite: InstanceType<typeof Database>): void {
  const cutoff = Date.now() - 90 * 24 * 60 * 60 * 1000
  const tables = ['nodes', 'documents', 'bookmarks'] as const
  for (const table of tables) {
    const result = sqlite
      .prepare(`DELETE FROM ${table} WHERE deleted_at IS NOT NULL AND deleted_at < ?`)
      .run(cutoff)
    if (result.changes > 0) {
      console.log(`Purged ${result.changes} tombstones from ${table}`)
    }
  }
}
