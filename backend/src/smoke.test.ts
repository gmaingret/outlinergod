import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildApp } from './index.js'
import { createTestDb } from './test-helpers/createTestDb.js'

/** Recursively collect every *.test.ts file under `dir`. */
function getAllTestFiles(dir: string): string[] {
  const results: string[] = []
  for (const entry of readdirSync(dir)) {
    const fullPath = join(dir, entry)
    if (statSync(fullPath).isDirectory()) {
      results.push(...getAllTestFiles(fullPath))
    } else if (entry.endsWith('.test.ts')) {
      results.push(fullPath)
    }
  }
  return results
}

describe('Phase 1 smoke tests', () => {
  it('smoke_health_returnsFullShape', async () => {
    const { sqlite } = createTestDb()
    const app = buildApp(sqlite)
    await app.ready()

    const response = await app.inject({ method: 'GET', url: '/health' })
    const body = response.json<{
      status: string
      db: string
      version: unknown
      uptime: unknown
    }>()

    expect(body).toMatchObject({
      status: 'ok',
      db: 'ok',
      version: expect.any(String),
      uptime: expect.any(Number),
    })

    await app.close()
  })

  it('smoke_allSevenTables_existAfterMigration', () => {
    const { sqlite } = createTestDb()
    const rows = sqlite
      .prepare("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
      .all() as { name: string }[]
    const tableNames = rows.map((r) => r.name)

    expect(tableNames).toContain('bookmarks')
    expect(tableNames).toContain('documents')
    expect(tableNames).toContain('files')
    expect(tableNames).toContain('nodes')
    expect(tableNames).toContain('refresh_tokens')
    expect(tableNames).toContain('settings')
    expect(tableNames).toContain('users')
  })

  it('smoke_noSkippedTests_inSuite', () => {
    // backend/src/ is the directory containing this file
    const srcDir = fileURLToPath(new URL('.', import.meta.url))
    // Exclude this file: it contains the pattern string as a literal in its source
    const thisFile = fileURLToPath(import.meta.url)
    const testFiles = getAllTestFiles(srcDir).filter((f) => f !== thisFile)

    // Build pattern dynamically so the string literal doesn't self-match
    const skipPattern = new RegExp('\\b(it|test)\\.' + 'skip\\b|\\.' + 'todo\\b', 'g')
    let totalMatches = 0

    for (const file of testFiles) {
      const content = readFileSync(file, 'utf-8')
      const matches = content.match(skipPattern)
      if (matches) totalMatches += matches.length
    }

    expect(totalMatches).toBe(0)
  })
})
