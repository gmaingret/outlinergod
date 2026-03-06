import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import Database from 'better-sqlite3'
import { createTestDb } from './test-helpers/createTestDb.js'
import { purgeStaleRefreshTokens } from './refresh-token-purge.js'

function seedUser(sqlite: InstanceType<typeof Database>, id: string): void {
  const now = Date.now()
  sqlite
    .prepare(
      'INSERT INTO users (id, google_sub, email, name, picture, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)',
    )
    .run(id, `gsub-${id}`, `${id}@example.com`, 'User', '', now, now)
}

function insertRefreshToken(
  sqlite: InstanceType<typeof Database>,
  id: string,
  userId: string,
  createdAt: number,
): void {
  const expiresAt = createdAt + 30 * 24 * 60 * 60 * 1000
  sqlite
    .prepare(
      'INSERT INTO refresh_tokens (token, user_id, device_id, expires_at, created_at, revoked) VALUES (?, ?, ?, ?, ?, ?)',
    )
    .run(`token-value-${id}`, userId, 'device-1', expiresAt, createdAt, 0)
}

describe('purgeStaleRefreshTokens', () => {
  let sqlite: InstanceType<typeof Database>

  beforeEach(() => {
    const testDb = createTestDb()
    sqlite = testDb.sqlite
    seedUser(sqlite, 'user-1')
  })

  afterEach(() => {
    sqlite.close()
  })

  it('deletes tokens older than 90 days', () => {
    const oldCreatedAt = Date.now() - 91 * 24 * 60 * 60 * 1000
    insertRefreshToken(sqlite, 'token-old', 'user-1', oldCreatedAt)

    purgeStaleRefreshTokens(sqlite)

    const row = sqlite
      .prepare('SELECT token FROM refresh_tokens WHERE token = ?')
      .get('token-value-token-old')
    expect(row).toBeUndefined()
  })

  it('retains tokens younger than 90 days', () => {
    const recentCreatedAt = Date.now() - 1 * 24 * 60 * 60 * 1000
    insertRefreshToken(sqlite, 'token-recent', 'user-1', recentCreatedAt)

    purgeStaleRefreshTokens(sqlite)

    const row = sqlite
      .prepare('SELECT token FROM refresh_tokens WHERE token = ?')
      .get('token-value-token-recent')
    expect(row).toBeDefined()
  })

  it('deletes old token but retains recent token when both exist', () => {
    const oldCreatedAt = Date.now() - 91 * 24 * 60 * 60 * 1000
    const recentCreatedAt = Date.now() - 1 * 24 * 60 * 60 * 1000
    insertRefreshToken(sqlite, 'token-old', 'user-1', oldCreatedAt)
    insertRefreshToken(sqlite, 'token-recent', 'user-1', recentCreatedAt)

    purgeStaleRefreshTokens(sqlite)

    const oldRow = sqlite
      .prepare('SELECT token FROM refresh_tokens WHERE token = ?')
      .get('token-value-token-old')
    const recentRow = sqlite
      .prepare('SELECT token FROM refresh_tokens WHERE token = ?')
      .get('token-value-token-recent')

    expect(oldRow).toBeUndefined()
    expect(recentRow).toBeDefined()
  })

  it('retains token at exactly the 90-day cutoff boundary (strictly less-than)', () => {
    // The WHERE clause uses created_at < cutoff (strictly less-than).
    // A token inserted at exactly the cutoff moment should survive because
    // the freshly-computed cutoff in purgeStaleRefreshTokens() will be
    // a few milliseconds later than the stored value.
    const now = Date.now()
    const cutoff = now - 90 * 24 * 60 * 60 * 1000
    insertRefreshToken(sqlite, 'token-boundary', 'user-1', cutoff)

    purgeStaleRefreshTokens(sqlite)

    const row = sqlite
      .prepare('SELECT token FROM refresh_tokens WHERE token = ?')
      .get('token-value-token-boundary')
    expect(row).toBeDefined()
  })
})
