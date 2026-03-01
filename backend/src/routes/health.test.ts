import { describe, it, expect } from 'vitest'
import { readFileSync } from 'fs'
import { fileURLToPath } from 'url'
import { join, dirname } from 'path'
import type Database from 'better-sqlite3'
import { buildApp } from '../index.js'
import { createTestDb } from '../test-helpers/createTestDb.js'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)
const pkg = JSON.parse(
  readFileSync(join(__dirname, '../../package.json'), 'utf-8'),
) as { version: string }

describe('GET /health', () => {
  it('GET_health_returns200', async () => {
    const { sqlite } = createTestDb()
    const app = buildApp(sqlite)
    await app.ready()

    const response = await app.inject({ method: 'GET', url: '/health' })
    expect(response.statusCode).toBe(200)
  })

  it('GET_health_statusIsOk', async () => {
    const { sqlite } = createTestDb()
    const app = buildApp(sqlite)
    await app.ready()

    const response = await app.inject({ method: 'GET', url: '/health' })
    const body = response.json<{ status: string }>()
    expect(body.status).toBe('ok')
  })

  it('GET_health_dbIsOk', async () => {
    const { sqlite } = createTestDb()
    const app = buildApp(sqlite)
    await app.ready()

    const response = await app.inject({ method: 'GET', url: '/health' })
    const body = response.json<{ db: string }>()
    expect(body.db).toBe('ok')
  })

  it('GET_health_versionMatchesPackageJson', async () => {
    const { sqlite } = createTestDb()
    const app = buildApp(sqlite)
    await app.ready()

    const response = await app.inject({ method: 'GET', url: '/health' })
    const body = response.json<{ version: string }>()
    expect(body.version).toBe(pkg.version)
  })

  it('GET_health_uptimeIsPositiveNumber', async () => {
    const { sqlite } = createTestDb()
    const app = buildApp(sqlite)
    await app.ready()

    const response = await app.inject({ method: 'GET', url: '/health' })
    const body = response.json<{ uptime: unknown }>()
    expect(typeof body.uptime).toBe('number')
    expect(body.uptime as number).toBeGreaterThan(0)
  })

  it('GET_health_dbIsError_whenDbThrows', async () => {
    const badSqlite = {
      prepare: () => ({
        get: () => {
          throw new Error('db connection error')
        },
      }),
    } as unknown as InstanceType<typeof Database>

    const app = buildApp(badSqlite)
    await app.ready()

    const response = await app.inject({ method: 'GET', url: '/health' })
    expect(response.statusCode).toBe(200)
    const body = response.json<{ db: string }>()
    expect(body.db).toBe('error')
  })
})
