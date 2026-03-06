import { randomBytes, randomUUID } from 'node:crypto'
import type { FastifyInstance } from 'fastify'
import { SignJWT } from 'jose'
import { OAuth2Client } from 'google-auth-library'
import type Database from 'better-sqlite3'
import rateLimit from '@fastify/rate-limit'
import { requireAuth } from '../middleware/auth.js'
import type { User, RefreshToken } from '../db/schema.js'

const JWT_EXPIRY = '1h'
const REFRESH_TOKEN_MS = 30 * 24 * 60 * 60 * 1000 // 30 days

export type GooglePayload = {
  sub: string
  email: string
  name: string
  picture: string
}

/** Default Google verifier — calls the real OAuth2Client. */
async function defaultVerifyGoogle(idToken: string): Promise<GooglePayload> {
  const client = new OAuth2Client(process.env.GOOGLE_CLIENT_ID)
  const ticket = await client.verifyIdToken({
    idToken,
    audience: [process.env.GOOGLE_CLIENT_ID, process.env.GOOGLE_WEB_CLIENT_ID].filter(
      (id): id is string => Boolean(id),
    ),
  })
  const p = ticket.getPayload()!
  return {
    sub: p.sub,
    email: p.email ?? '',
    name: p.name ?? '',
    picture: p.picture ?? '',
  }
}

async function signJwt(
  user: Pick<User, 'id' | 'email' | 'name' | 'picture'>,
): Promise<string> {
  const secret = new TextEncoder().encode(process.env.JWT_SECRET!)
  return new SignJWT({ email: user.email, name: user.name, picture: user.picture })
    .setProtectedHeader({ alg: 'HS256' })
    .setSubject(user.id)
    .setIssuedAt()
    .setExpirationTime(JWT_EXPIRY)
    .sign(secret)
}

/**
 * Auth routes plugin factory.
 *
 * @param sqlite  - The better-sqlite3 Database instance (already configured with WAL/FK pragmas).
 * @param verifyGoogle - Optional override for Google token verification (used in tests to
 *                       avoid real network calls to Google's JWKS endpoint).
 */
export function createAuthRoutes(
  sqlite: InstanceType<typeof Database>,
  verifyGoogle: (idToken: string) => Promise<GooglePayload> = defaultVerifyGoogle,
) {
  return async function authRoutes(fastify: FastifyInstance) {
    // Rate-limit auth routes only (10 requests per minute per IP).
    // Scoped to this plugin — sync/document routes remain unlimited.
    await fastify.register(rateLimit, {
      max: 10,
      timeWindow: '1 minute',
    })

    // -----------------------------------------------------------------------
    // POST /auth/google
    // Exchange a Google ID token for a backend JWT + refresh token.
    // -----------------------------------------------------------------------
    fastify.post('/auth/google', async (req, reply) => {
      const body = req.body as { id_token?: string; device_id?: string }

      if (!body?.id_token) {
        return reply.status(400).send({ error: 'Missing id_token' })
      }

      let googlePayload: GooglePayload
      try {
        googlePayload = await verifyGoogle(body.id_token)
      } catch {
        return reply.status(401).send({ error: 'Google token verification failed' })
      }

      const now = Date.now()
      const existing = sqlite
        .prepare('SELECT * FROM users WHERE google_sub = ?')
        .get(googlePayload.sub) as User | undefined

      let user: User
      const is_new_user = !existing

      if (existing) {
        user = existing
      } else {
        const id = randomUUID()
        sqlite
          .prepare(
            'INSERT INTO users (id, google_sub, email, name, picture, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)',
          )
          .run(id, googlePayload.sub, googlePayload.email, googlePayload.name, googlePayload.picture, now, now)
        user = {
          id,
          google_sub: googlePayload.sub,
          email: googlePayload.email,
          name: googlePayload.name,
          picture: googlePayload.picture,
          created_at: now,
          updated_at: now,
        }
      }

      const token = await signJwt(user)
      const refreshToken = randomBytes(32).toString('hex')

      sqlite
        .prepare(
          'INSERT INTO refresh_tokens (token, user_id, device_id, expires_at, created_at, revoked) VALUES (?, ?, ?, ?, ?, 0)',
        )
        .run(refreshToken, user.id, body.device_id ?? 'web-default', now + REFRESH_TOKEN_MS, now)

      return reply.send({
        token,
        refresh_token: refreshToken,
        user: {
          id: user.id,
          google_sub: user.google_sub,
          email: user.email,
          name: user.name,
          picture: user.picture,
        },
        is_new_user,
      })
    })

    // -----------------------------------------------------------------------
    // POST /auth/refresh
    // Exchange a valid refresh token for a new JWT + rotated refresh token.
    // -----------------------------------------------------------------------
    fastify.post('/auth/refresh', async (req, reply) => {
      const body = req.body as { refresh_token?: string }

      if (!body?.refresh_token) {
        return reply.status(400).send({ error: 'Missing refresh_token' })
      }

      const rt = sqlite
        .prepare('SELECT * FROM refresh_tokens WHERE token = ?')
        .get(body.refresh_token) as RefreshToken | undefined

      if (!rt || rt.revoked === 1 || rt.expires_at < Date.now()) {
        return reply.status(401).send({ error: 'Invalid or expired refresh token' })
      }

      // Revoke old token (rotation)
      sqlite.prepare('UPDATE refresh_tokens SET revoked = 1 WHERE token = ?').run(body.refresh_token)

      const user = sqlite.prepare('SELECT * FROM users WHERE id = ?').get(rt.user_id) as
        | User
        | undefined
      if (!user) {
        return reply.status(401).send({ error: 'User not found' })
      }

      const now = Date.now()
      const token = await signJwt(user)
      const newRefreshToken = randomBytes(32).toString('hex')

      sqlite
        .prepare(
          'INSERT INTO refresh_tokens (token, user_id, device_id, expires_at, created_at, revoked) VALUES (?, ?, ?, ?, ?, 0)',
        )
        .run(newRefreshToken, user.id, rt.device_id, now + REFRESH_TOKEN_MS, now)

      return reply.send({ token, refresh_token: newRefreshToken })
    })

    // -----------------------------------------------------------------------
    // GET /auth/me
    // Validate the current JWT and return the authenticated user's profile.
    // -----------------------------------------------------------------------
    fastify.get('/auth/me', { preHandler: requireAuth }, async (req) => {
      const user = sqlite
        .prepare('SELECT id, google_sub, email, name, picture FROM users WHERE id = ?')
        .get(req.user!.id) as Pick<User, 'id' | 'google_sub' | 'email' | 'name' | 'picture'> | undefined
      if (!user) {
        return { error: 'User not found' }
      }
      return user
    })

    // -----------------------------------------------------------------------
    // POST /auth/logout
    // Invalidate the current refresh token for this device.
    // -----------------------------------------------------------------------
    fastify.post('/auth/logout', { preHandler: requireAuth }, async (req, reply) => {
      const body = req.body as { refresh_token?: string }

      if (!body?.refresh_token) {
        return reply.status(400).send({ error: 'Missing refresh_token' })
      }

      sqlite.prepare('UPDATE refresh_tokens SET revoked = 1 WHERE token = ?').run(body.refresh_token)

      return reply.send({ ok: true })
    })
  }
}
