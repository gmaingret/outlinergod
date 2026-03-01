/**
 * P1-3 Auth route tests (13 cases)
 *
 * Uses dependency-injected verifyGoogle to avoid real network calls to Google.
 * DB: in-memory SQLite via createTestDb() — never touches /data.
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import Database from 'better-sqlite3'
import { jwtVerify, SignJWT } from 'jose'
import { buildApp } from '../index.js'
import { createTestDb } from '../test-helpers/createTestDb.js'
import type { GooglePayload } from './auth.js'
import type { RefreshToken } from '../db/schema.js'

const TEST_SECRET = 'test-jwt-secret-for-auth-route-tests-must-be-long!!'
const MOCK_GOOGLE_PAYLOAD: GooglePayload = {
  sub: 'google-sub-abc123',
  email: 'testuser@example.com',
  name: 'Test User',
  picture: 'https://example.com/photo.jpg',
}

/** Succeeds — returns a fixed Google payload */
const mockVerifyGoogle = async (_idToken: string): Promise<GooglePayload> => MOCK_GOOGLE_PAYLOAD

/** Fails — simulates a Google token verification error */
const failingVerifyGoogle = async (_idToken: string): Promise<GooglePayload> => {
  throw new Error('Google token verification failed')
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('Auth routes', () => {
  let app: ReturnType<typeof buildApp>
  let sqlite: InstanceType<typeof Database>

  beforeEach(async () => {
    process.env.JWT_SECRET = TEST_SECRET
    process.env.GOOGLE_CLIENT_ID = 'test-google-client-id'

    const testDb = createTestDb()
    sqlite = testDb.sqlite
    app = buildApp(sqlite, mockVerifyGoogle)
    await app.ready()
  })

  afterEach(async () => {
    await app.close()
    sqlite.close()
  })

  // --- POST /api/auth/google ---

  it('google_200_validToken_createsNewUser', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/google',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id_token: 'valid-google-token' }),
    })

    expect(res.statusCode).toBe(200)
    const body = res.json()
    expect(typeof body.token).toBe('string')
    expect(typeof body.refresh_token).toBe('string')
    expect(body.is_new_user).toBe(true)
    expect(body.user.google_sub).toBe('google-sub-abc123')
    expect(body.user.email).toBe('testuser@example.com')
    expect(body.user.name).toBe('Test User')
  })

  it('google_200_existingUser_isNewUserFalse', async () => {
    // First call creates the user
    await app.inject({
      method: 'POST',
      url: '/api/auth/google',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id_token: 'valid-google-token' }),
    })

    // Second call — same google_sub should return is_new_user=false
    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/google',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id_token: 'valid-google-token' }),
    })

    expect(res.statusCode).toBe(200)
    expect(res.json().is_new_user).toBe(false)
  })

  it('google_400_missingIdToken', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/google',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({}),
    })

    expect(res.statusCode).toBe(400)
  })

  it('google_400_emptyStringIdToken', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/google',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id_token: '' }),
    })

    expect(res.statusCode).toBe(400)
  })

  it('google_401_invalidGoogleToken', async () => {
    // Rebuild app with a failing verifyGoogle
    await app.close()
    app = buildApp(sqlite, failingVerifyGoogle)
    await app.ready()

    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/google',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id_token: 'bad-google-token' }),
    })

    expect(res.statusCode).toBe(401)
  })

  it('google_refreshToken_isStoredInDb', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/google',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id_token: 'valid-google-token' }),
    })

    const { refresh_token } = res.json()
    const row = sqlite
      .prepare('SELECT * FROM refresh_tokens WHERE token = ?')
      .get(refresh_token) as RefreshToken | undefined

    expect(row).toBeDefined()
    expect(row!.revoked).toBe(0)
    expect(row!.expires_at).toBeGreaterThan(Date.now())
  })

  it('google_jwt_canBeVerifiedWithJwtSecret', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/google',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id_token: 'valid-google-token' }),
    })

    const { token, user } = res.json()
    const secret = new TextEncoder().encode(TEST_SECRET)
    const { payload } = await jwtVerify(token, secret)

    expect(payload.sub).toBe(user.id)
  })

  // --- POST /api/auth/refresh ---

  it('refresh_200_validToken_returnsNewJwt', async () => {
    // Get a refresh token via google auth
    const loginRes = await app.inject({
      method: 'POST',
      url: '/api/auth/google',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id_token: 'valid-google-token' }),
    })
    const { refresh_token: originalRefreshToken } = loginRes.json()

    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ refresh_token: originalRefreshToken }),
    })

    expect(res.statusCode).toBe(200)
    const body = res.json()
    expect(typeof body.token).toBe('string')
    expect(typeof body.refresh_token).toBe('string')
    // Refresh token must be rotated (new value)
    expect(body.refresh_token).not.toBe(originalRefreshToken)
  })

  it('refresh_oldToken_isRevoked_afterRotation', async () => {
    // Login to get tokens
    const loginRes = await app.inject({
      method: 'POST',
      url: '/api/auth/google',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id_token: 'valid-google-token' }),
    })
    const { refresh_token: originalToken } = loginRes.json()

    // Rotate once (should succeed)
    const refreshRes = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ refresh_token: originalToken }),
    })
    expect(refreshRes.statusCode).toBe(200)

    // Try old token again — should be revoked
    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ refresh_token: originalToken }),
    })
    expect(res.statusCode).toBe(401)
  })

  it('refresh_401_expiredToken', async () => {
    // Seed a user first
    const loginRes = await app.inject({
      method: 'POST',
      url: '/api/auth/google',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id_token: 'valid-google-token' }),
    })
    const { user } = loginRes.json()

    // Insert an expired refresh token directly
    const expiredToken = 'expired-refresh-token-abc'
    sqlite
      .prepare(
        'INSERT INTO refresh_tokens (token, user_id, device_id, expires_at, created_at, revoked) VALUES (?, ?, ?, ?, ?, 0)',
      )
      .run(expiredToken, user.id, 'test-device', Date.now() - 1, Date.now() - 100000)

    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ refresh_token: expiredToken }),
    })
    expect(res.statusCode).toBe(401)
  })

  it('refresh_400_missingToken', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({}),
    })

    expect(res.statusCode).toBe(400)
  })

  it('refresh_401_invalidToken', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ refresh_token: 'nonexistent-token-that-is-not-in-db' }),
    })

    expect(res.statusCode).toBe(401)
  })

  it('refresh_401_revokedToken', async () => {
    // Login to get tokens
    const loginRes = await app.inject({
      method: 'POST',
      url: '/api/auth/google',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id_token: 'valid-google-token' }),
    })
    const { token, refresh_token } = loginRes.json()

    // Logout to revoke the refresh token
    await app.inject({
      method: 'POST',
      url: '/api/auth/logout',
      headers: { 'content-type': 'application/json', authorization: `Bearer ${token}` },
      body: JSON.stringify({ refresh_token }),
    })

    // Try to use the now-revoked refresh token
    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/refresh',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ refresh_token }),
    })

    expect(res.statusCode).toBe(401)
  })

  // --- GET /api/auth/me ---

  it('me_200_withValidJwt', async () => {
    // Login to get a JWT
    const loginRes = await app.inject({
      method: 'POST',
      url: '/api/auth/google',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id_token: 'valid-google-token' }),
    })
    const { token } = loginRes.json()

    const res = await app.inject({
      method: 'GET',
      url: '/api/auth/me',
      headers: { authorization: `Bearer ${token}` },
    })

    expect(res.statusCode).toBe(200)
    const body = res.json()
    expect(typeof body.id).toBe('string')
    expect(body.google_sub).toBe('google-sub-abc123')
    expect(body.email).toBe('testuser@example.com')
    expect(body.name).toBe('Test User')
    expect(body.picture).toBe('https://example.com/photo.jpg')
  })

  it('me_401_withoutJwt', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/api/auth/me',
    })

    expect(res.statusCode).toBe(401)
    expect(res.json()).toEqual({ error: 'Unauthorized' })
  })

  it('me_401_withExpiredJwt', async () => {
    // Seed a user
    const loginRes = await app.inject({
      method: 'POST',
      url: '/api/auth/google',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id_token: 'valid-google-token' }),
    })
    const { user } = loginRes.json()

    // Sign an expired JWT
    const secret = new TextEncoder().encode(TEST_SECRET)
    const expiredToken = await new SignJWT({ email: user.email, name: user.name, picture: user.picture })
      .setProtectedHeader({ alg: 'HS256' })
      .setSubject(user.id)
      .setExpirationTime('-1s')
      .sign(secret)

    const res = await app.inject({
      method: 'GET',
      url: '/api/auth/me',
      headers: { authorization: `Bearer ${expiredToken}` },
    })
    expect(res.statusCode).toBe(401)
  })

  it('me_401_withMalformedToken', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/api/auth/me',
      headers: { authorization: 'Bearer not-a-jwt' },
    })
    expect(res.statusCode).toBe(401)
  })

  // --- POST /api/auth/logout ---

  it('logout_200_revokesRefreshToken', async () => {
    const loginRes = await app.inject({
      method: 'POST',
      url: '/api/auth/google',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id_token: 'valid-google-token' }),
    })
    const { token, refresh_token } = loginRes.json()

    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/logout',
      headers: { 'content-type': 'application/json', authorization: `Bearer ${token}` },
      body: JSON.stringify({ refresh_token }),
    })

    expect(res.statusCode).toBe(200)
    expect(res.json().ok).toBe(true)
  })

  it('logout_200_ok_whenTokenNotFound', async () => {
    // Login to get a valid JWT
    const loginRes = await app.inject({
      method: 'POST',
      url: '/api/auth/google',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id_token: 'valid-google-token' }),
    })
    const { token } = loginRes.json()

    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/logout',
      headers: { 'content-type': 'application/json', authorization: `Bearer ${token}` },
      body: JSON.stringify({ refresh_token: 'nonexistent-token' }),
    })

    expect(res.statusCode).toBe(200)
    expect(res.json()).toEqual({ ok: true })
  })

  it('logout_400_missingRefreshToken', async () => {
    const loginRes = await app.inject({
      method: 'POST',
      url: '/api/auth/google',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id_token: 'valid-google-token' }),
    })
    const { token } = loginRes.json()

    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/logout',
      headers: { 'content-type': 'application/json', authorization: `Bearer ${token}` },
      body: JSON.stringify({}),
    })

    expect(res.statusCode).toBe(400)
  })

  it('logout_401_withoutJwt', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/logout',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ refresh_token: 'anything' }),
    })

    expect(res.statusCode).toBe(401)
    expect(res.json()).toEqual({ error: 'Unauthorized' })
  })
})
