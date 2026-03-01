/**
 * P2-20 / P2-21: Settings route tests
 *
 * Uses createTestDb() for in-memory SQLite and @fastify/inject for HTTP.
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import Database from 'better-sqlite3'
import { SignJWT } from 'jose'
import { buildApp } from '../index.js'
import { createTestDb } from '../test-helpers/createTestDb.js'

const TEST_SECRET = 'test-jwt-secret-for-settings-route-tests-must-be-long!!'

async function signTestJwt(userId: string): Promise<string> {
  const secret = new TextEncoder().encode(TEST_SECRET)
  return new SignJWT({ email: 'test@example.com', name: 'Test', picture: '' })
    .setProtectedHeader({ alg: 'HS256' })
    .setSubject(userId)
    .setIssuedAt()
    .setExpirationTime('1h')
    .sign(secret)
}

function seedUser(sqlite: InstanceType<typeof Database>, id: string): void {
  const now = Date.now()
  sqlite
    .prepare(
      'INSERT INTO users (id, google_sub, email, name, picture, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)',
    )
    .run(id, `gsub-${id}`, `${id}@example.com`, 'User', '', now, now)
}

function seedSettings(
  sqlite: InstanceType<typeof Database>,
  overrides: {
    id?: string
    user_id: string
    theme?: string
    density?: string
    show_guide_lines?: number
    show_backlink_badge?: number
  },
): void {
  const now = Date.now()
  sqlite
    .prepare(
      `INSERT INTO settings (id, user_id, theme, theme_hlc, density, density_hlc, show_guide_lines, show_guide_lines_hlc, show_backlink_badge, show_backlink_badge_hlc, device_id, created_at, updated_at)
       VALUES (?, ?, ?, '', ?, '', ?, '', ?, '', 'server', ?, ?)`,
    )
    .run(
      overrides.id ?? 'settings-1',
      overrides.user_id,
      overrides.theme ?? 'dark',
      overrides.density ?? 'cozy',
      overrides.show_guide_lines ?? 1,
      overrides.show_backlink_badge ?? 1,
      now,
      now,
    )
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('Settings routes', () => {
  let app: ReturnType<typeof buildApp>
  let sqlite: InstanceType<typeof Database>
  let tokenA: string

  beforeEach(async () => {
    process.env.JWT_SECRET = TEST_SECRET

    const testDb = createTestDb()
    sqlite = testDb.sqlite
    app = buildApp(sqlite)
    await app.ready()

    seedUser(sqlite, 'user-a')
    tokenA = await signTestJwt('user-a')
  })

  afterEach(async () => {
    await app.close()
    sqlite.close()
  })

  // =========================================================================
  // GET /api/settings
  // =========================================================================
  describe('GET /api/settings', () => {
    it('returns200_withDefaults_whenNoSettingsRow', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/api/settings',
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.theme).toBe('dark')
      expect(body.density).toBe('cozy')
      expect(body.show_guide_lines).toBe(true)
      expect(body.show_backlink_badge).toBe(true)
    })

    it('returns200_withStoredSettings_whenRowExists', async () => {
      seedSettings(sqlite, {
        user_id: 'user-a',
        theme: 'light',
        density: 'compact',
      })

      const res = await app.inject({
        method: 'GET',
        url: '/api/settings',
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.theme).toBe('light')
      expect(body.density).toBe('compact')
    })

    it('showGuideLines_isReturnedAsBoolean', async () => {
      seedSettings(sqlite, {
        user_id: 'user-a',
        show_guide_lines: 0,
      })

      const res = await app.inject({
        method: 'GET',
        url: '/api/settings',
        headers: { authorization: `Bearer ${tokenA}` },
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.show_guide_lines).toBe(false)
    })

    it('returns401_withNoAuthHeader', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/api/settings',
      })

      expect(res.statusCode).toBe(401)
    })
  })

  // =========================================================================
  // PUT /api/settings
  // =========================================================================
  describe('PUT /api/settings', () => {
    it('returns200_withUpdatedSettings', async () => {
      const res = await app.inject({
        method: 'PUT',
        url: '/api/settings',
        headers: {
          authorization: `Bearer ${tokenA}`,
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          theme: 'light',
          density: 'compact',
          show_guide_lines: false,
          show_backlink_badge: true,
        }),
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.theme).toBe('light')
      expect(body.density).toBe('compact')
      expect(body.show_guide_lines).toBe(false)
      expect(body.show_backlink_badge).toBe(true)
    })

    it('createsSettingsRow_whenNoneExists', async () => {
      const res = await app.inject({
        method: 'PUT',
        url: '/api/settings',
        headers: {
          authorization: `Bearer ${tokenA}`,
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          theme: 'dark',
          density: 'cozy',
          show_guide_lines: true,
          show_backlink_badge: true,
        }),
      })

      expect(res.statusCode).toBe(200)

      const row = sqlite
        .prepare('SELECT * FROM settings WHERE user_id = ?')
        .get('user-a')
      expect(row).toBeDefined()
    })

    it('replacesExistingSettings_fully', async () => {
      seedSettings(sqlite, {
        user_id: 'user-a',
        theme: 'dark',
      })

      const res = await app.inject({
        method: 'PUT',
        url: '/api/settings',
        headers: {
          authorization: `Bearer ${tokenA}`,
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          theme: 'light',
          density: 'comfortable',
          show_guide_lines: false,
          show_backlink_badge: false,
        }),
      })

      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.theme).toBe('light')
    })

    it('hlcColumns_arePopulated_afterPut', async () => {
      await app.inject({
        method: 'PUT',
        url: '/api/settings',
        headers: {
          authorization: `Bearer ${tokenA}`,
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          theme: 'dark',
          density: 'cozy',
          show_guide_lines: true,
          show_backlink_badge: true,
        }),
      })

      const row = sqlite
        .prepare('SELECT * FROM settings WHERE user_id = ?')
        .get('user-a') as {
        theme_hlc: string
        density_hlc: string
        show_guide_lines_hlc: string
        show_backlink_badge_hlc: string
      }

      expect(row.theme_hlc).toMatch(/^\d{13}-\d{5}-/)
      expect(row.density_hlc).toMatch(/^\d{13}-\d{5}-/)
      expect(row.show_guide_lines_hlc).toMatch(/^\d{13}-\d{5}-/)
      expect(row.show_backlink_badge_hlc).toMatch(/^\d{13}-\d{5}-/)
    })

    it('returns400_whenThemeMissing', async () => {
      const res = await app.inject({
        method: 'PUT',
        url: '/api/settings',
        headers: {
          authorization: `Bearer ${tokenA}`,
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          density: 'cozy',
          show_guide_lines: true,
          show_backlink_badge: true,
        }),
      })

      expect(res.statusCode).toBe(400)
    })

    it('returns400_whenThemeInvalid', async () => {
      const res = await app.inject({
        method: 'PUT',
        url: '/api/settings',
        headers: {
          authorization: `Bearer ${tokenA}`,
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          theme: 'sepia',
          density: 'cozy',
          show_guide_lines: true,
          show_backlink_badge: true,
        }),
      })

      expect(res.statusCode).toBe(400)
    })

    it('returns400_whenDensityInvalid', async () => {
      const res = await app.inject({
        method: 'PUT',
        url: '/api/settings',
        headers: {
          authorization: `Bearer ${tokenA}`,
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          theme: 'dark',
          density: 'sparse',
          show_guide_lines: true,
          show_backlink_badge: true,
        }),
      })

      expect(res.statusCode).toBe(400)
    })

    it('returns401_withNoAuthHeader', async () => {
      const res = await app.inject({
        method: 'PUT',
        url: '/api/settings',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          theme: 'dark',
          density: 'cozy',
          show_guide_lines: true,
          show_backlink_badge: true,
        }),
      })

      expect(res.statusCode).toBe(401)
    })
  })
})
