import type Database from 'better-sqlite3'

/**
 * Purge refresh tokens older than 90 days.
 * Uses created_at (epoch milliseconds) — same convention as all other tables.
 * Called once at server startup and every 24 hours via setInterval in startServer().
 */
export function purgeStaleRefreshTokens(sqlite: InstanceType<typeof Database>): void {
  const cutoff = Date.now() - 90 * 24 * 60 * 60 * 1000
  const result = sqlite
    .prepare('DELETE FROM refresh_tokens WHERE created_at < ?')
    .run(cutoff)
  if (result.changes > 0) {
    console.log(`Purged ${result.changes} stale refresh tokens`)
  }
}
