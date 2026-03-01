/**
 * P2-27 Route registration tests (4 cases)
 *
 * Verifies that all Phase 2 route plugins are correctly wired into
 * buildApp() under the /api prefix, and that /health stays outside it.
 */
import { describe, it, expect, afterEach } from 'vitest'
import { buildApp } from '../index.js'
import { createTestDb } from '../test-helpers/createTestDb.js'

describe('Route registration', () => {
  let apps: ReturnType<typeof buildApp>[] = []

  afterEach(async () => {
    await Promise.all(apps.map((a) => a.close()))
    apps = []
  })

  it('buildApp_registersAllApiRoutes', async () => {
    const { sqlite } = createTestDb()
    const app = buildApp(sqlite)
    apps.push(app)
    await app.ready()

    // Inject each critical route and verify it does NOT return 404.
    // Auth-protected routes return 401 (requireAuth fires), which proves
    // the route IS registered. A 404 would mean the route is missing.
    const routesToCheck = [
      // Auth routes
      { method: 'POST' as const, url: '/api/auth/google' },
      { method: 'POST' as const, url: '/api/auth/refresh' },
      { method: 'GET' as const, url: '/api/auth/me' },
      { method: 'POST' as const, url: '/api/auth/logout' },
      // Document routes
      { method: 'GET' as const, url: '/api/documents' },
      { method: 'POST' as const, url: '/api/documents' },
      { method: 'GET' as const, url: '/api/documents/00000000-0000-0000-0000-000000000001' },
      { method: 'PATCH' as const, url: '/api/documents/00000000-0000-0000-0000-000000000001' },
      { method: 'DELETE' as const, url: '/api/documents/00000000-0000-0000-0000-000000000001' },
      { method: 'POST' as const, url: '/api/documents/00000000-0000-0000-0000-000000000001/convert-to-node' },
      // Node routes
      { method: 'GET' as const, url: '/api/documents/00000000-0000-0000-0000-000000000001/nodes' },
      { method: 'POST' as const, url: '/api/documents/00000000-0000-0000-0000-000000000001/nodes/batch' },
      { method: 'DELETE' as const, url: '/api/nodes/00000000-0000-0000-0000-000000000001' },
      { method: 'POST' as const, url: '/api/nodes/00000000-0000-0000-0000-000000000001/convert-to-document' },
      // Sync routes
      { method: 'GET' as const, url: '/api/sync/changes' },
      { method: 'POST' as const, url: '/api/sync/changes' },
      // File routes
      { method: 'POST' as const, url: '/api/files' },
      { method: 'GET' as const, url: '/api/files/test.png' },
      { method: 'DELETE' as const, url: '/api/files/test.png' },
      // Settings routes
      { method: 'GET' as const, url: '/api/settings' },
      { method: 'PUT' as const, url: '/api/settings' },
      // Bookmark routes
      { method: 'GET' as const, url: '/api/bookmarks' },
      { method: 'POST' as const, url: '/api/bookmarks' },
      { method: 'PATCH' as const, url: '/api/bookmarks/00000000-0000-0000-0000-000000000001' },
      { method: 'DELETE' as const, url: '/api/bookmarks/00000000-0000-0000-0000-000000000001' },
      // Export route
      { method: 'GET' as const, url: '/api/export' },
      // Health (outside /api prefix)
      { method: 'GET' as const, url: '/health' },
    ]

    for (const route of routesToCheck) {
      const res = await app.inject({ method: route.method, url: route.url })
      expect(
        res.statusCode,
        `${route.method} ${route.url} should be registered (got ${res.statusCode})`,
      ).not.toBe(404)
    }
  })

  it('buildApp_healthRoute_isOutsideApiPrefix', async () => {
    const { sqlite } = createTestDb()
    const app = buildApp(sqlite)
    apps.push(app)
    await app.ready()

    const response = await app.inject({ method: 'GET', url: '/health' })
    expect(response.statusCode).toBe(200)
  })

  it('buildApp_unknownRoute_returns404', async () => {
    const { sqlite } = createTestDb()
    const app = buildApp(sqlite)
    apps.push(app)
    await app.ready()

    const response = await app.inject({ method: 'GET', url: '/api/nonexistent' })
    expect(response.statusCode).toBe(404)
  })

  it('buildApp_doesNotThrow_onDoubleReady', async () => {
    const { sqlite } = createTestDb()
    const app = buildApp(sqlite)
    apps.push(app)
    await app.ready()
    await expect(app.ready()).resolves.not.toThrow()
  })
})
