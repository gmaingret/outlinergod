/**
 * P1-3 Auth middleware tests (6 cases per PLAN_PHASE1.md P1-14)
 *
 * Tests the JWT verification preHandler and the requireAuth guard.
 * All tests use a minimal Fastify app — no DB required.
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import Fastify from 'fastify'
import { SignJWT } from 'jose'
import authPlugin, { requireAuth } from './auth.js'
import type { FastifyInstance } from 'fastify'

const TEST_SECRET = 'test-jwt-secret-for-middleware-tests-must-be-long-enough!!'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function buildTestApp(): Promise<FastifyInstance> {
  const app = Fastify({ logger: false })
  process.env.JWT_SECRET = TEST_SECRET

  await app.register(authPlugin)

  // Route that echoes req.user back (no auth required)
  app.get('/test', async (req) => ({ user: req.user }))

  // Route that requires authentication
  app.get('/protected', { preHandler: requireAuth }, async () => ({ ok: true }))

  await app.ready()
  return app
}

async function signTestJwt(
  payload: { sub: string; email: string; name: string; picture: string },
  opts: { secret?: string; expSeconds?: number } = {},
): Promise<string> {
  const secret = new TextEncoder().encode(opts.secret ?? TEST_SECRET)
  const builder = new SignJWT({
    email: payload.email,
    name: payload.name,
    picture: payload.picture,
  })
    .setProtectedHeader({ alg: 'HS256' })
    .setSubject(payload.sub)
    .setIssuedAt()

  if (opts.expSeconds !== undefined) {
    builder.setExpirationTime(Math.floor(Date.now() / 1000) + opts.expSeconds)
  } else {
    builder.setExpirationTime('1h')
  }

  return builder.sign(secret)
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('Auth middleware', () => {
  let app: FastifyInstance

  beforeEach(async () => {
    app = await buildTestApp()
  })

  afterEach(async () => {
    await app.close()
  })

  it('auth_setsUser_onValidJwt', async () => {
    const token = await signTestJwt({
      sub: 'user-uuid',
      email: 'test@example.com',
      name: 'Test User',
      picture: 'https://example.com/pic.jpg',
    })

    const res = await app.inject({
      method: 'GET',
      url: '/test',
      headers: { authorization: `Bearer ${token}` },
    })

    expect(res.statusCode).toBe(200)
    const body = res.json()
    expect(body.user).toEqual({
      id: 'user-uuid',
      email: 'test@example.com',
      name: 'Test User',
      picture: 'https://example.com/pic.jpg',
    })
  })

  it('auth_setsNull_onMissingHeader', async () => {
    const res = await app.inject({ method: 'GET', url: '/test' })
    expect(res.statusCode).toBe(200)
    expect(res.json().user).toBeNull()
  })

  it('auth_setsNull_onExpiredToken', async () => {
    // exp set to 1 second in the past
    const token = await signTestJwt(
      { sub: 'u1', email: 'e@e.com', name: 'N', picture: 'p' },
      { expSeconds: -1 },
    )

    const res = await app.inject({
      method: 'GET',
      url: '/test',
      headers: { authorization: `Bearer ${token}` },
    })

    expect(res.statusCode).toBe(200)
    expect(res.json().user).toBeNull()
  })

  it('auth_setsNull_onWrongSecret', async () => {
    const token = await signTestJwt(
      { sub: 'u1', email: 'e@e.com', name: 'N', picture: 'p' },
      { secret: 'wrong-secret-that-will-not-match-the-app-configured-secret-here' },
    )

    const res = await app.inject({
      method: 'GET',
      url: '/test',
      headers: { authorization: `Bearer ${token}` },
    })

    expect(res.statusCode).toBe(200)
    expect(res.json().user).toBeNull()
  })

  it('requireAuth_returns401_whenNoUser', async () => {
    const res = await app.inject({ method: 'GET', url: '/protected' })
    expect(res.statusCode).toBe(401)
    expect(res.json()).toEqual({ error: 'Unauthorized' })
  })

  it('requireAuth_allows_whenUserIsSet', async () => {
    const token = await signTestJwt({
      sub: 'user-uuid',
      email: 'test@example.com',
      name: 'Test User',
      picture: 'https://example.com/pic.jpg',
    })

    const res = await app.inject({
      method: 'GET',
      url: '/protected',
      headers: { authorization: `Bearer ${token}` },
    })

    expect(res.statusCode).toBe(200)
    expect(res.json()).toEqual({ ok: true })
  })
})
