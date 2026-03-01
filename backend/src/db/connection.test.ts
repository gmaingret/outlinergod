import { describe, it, expect, afterAll } from 'vitest'
import { createConnection } from './connection.js'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { unlinkSync, existsSync } from 'node:fs'

// WAL mode is unsupported on in-memory SQLite — requires a real file.
// Use a temp file for WAL-specific tests only.
const TMP_DB = join(tmpdir(), `outlinergod-connection-test-${Date.now()}.db`)

describe('createConnection', () => {
  afterAll(() => {
    for (const path of [TMP_DB, `${TMP_DB}-wal`, `${TMP_DB}-shm`]) {
      if (existsSync(path)) unlinkSync(path)
    }
  })

  it('createConnection_opens_inMemoryDb', () => {
    const sqlite = createConnection(':memory:')
    expect(sqlite).toBeTruthy()
    expect(sqlite.open).toBe(true)
    sqlite.close()
  })

  it('walMode_isEnabled', () => {
    // WAL pragma is ignored on :memory: databases — test with a real file.
    const sqlite = createConnection(TMP_DB)
    const row = sqlite.prepare('PRAGMA journal_mode').get() as { journal_mode: string }
    expect(row.journal_mode).toBe('wal')
    sqlite.close()
  })

  it('foreignKeys_areEnabled', () => {
    const sqlite = createConnection(':memory:')
    const row = sqlite.prepare('PRAGMA foreign_keys').get() as { foreign_keys: number }
    expect(row.foreign_keys).toBe(1)
    sqlite.close()
  })

  it('twoConnections_areIndependentInstances', () => {
    const db1 = createConnection(':memory:')
    const db2 = createConnection(':memory:')
    expect(db1).not.toBe(db2)
    db1.close()
    db2.close()
  })
})
