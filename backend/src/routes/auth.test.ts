/**
 * P1-3 Auth route tests (13 cases)
 *
 * Uses dependency-injected verifyGoogle to avoid real network calls to Google.
 * DB: in-memory SQLite via createTestDb() — never touches /data.
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import Database from 'better-sqlite3'
import { buildApp } from '../index.js'
import { createTestDb } from '../test-helpers/createTestDb.js'
import type { GooglePayload } from './auth.js'

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
    expect(body.email).toBe('testuser@example.com')
    expect(body.name).toBe('Test User')
  })

  it('me_401_withoutJwt', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/api/auth/me',
    })

    expect(res.statusCode).toBe(401)
    expect(res.json()).toEqual({ error: 'Unauthorized' })
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
