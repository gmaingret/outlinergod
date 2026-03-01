import fp from 'fastify-plugin'
import type { FastifyPluginAsync, FastifyRequest, FastifyReply } from 'fastify'
import { jwtVerify } from 'jose'

export interface AuthUser {
  id: string
  email: string
  name: string
  picture: string
}

declare module 'fastify' {
  interface FastifyRequest {
    user: AuthUser | null
  }
}

/**
 * Global auth plugin — registered on the Fastify instance via fastify-plugin so
 * the decorateRequest and preHandler hook are not scoped to a child context.
 *
 * Sets req.user from the Bearer JWT on every request.
 * On any failure (missing, invalid, expired), sets req.user = null without throwing.
 * Routes that require authentication must use the requireAuth preHandler.
 */
const authPlugin: FastifyPluginAsync = async (fastify) => {
  fastify.decorateRequest('user', null)

  fastify.addHook('preHandler', async (req: FastifyRequest) => {
    const authHeader = req.headers.authorization
    if (!authHeader?.startsWith('Bearer ')) {
      req.user = null
      return
    }

    const token = authHeader.slice(7)
    try {
      const secret = new TextEncoder().encode(process.env.JWT_SECRET!)
      const { payload } = await jwtVerify(token, secret, { algorithms: ['HS256'] })
      req.user = {
        id: payload.sub!,
        email: payload['email'] as string,
        name: payload['name'] as string,
        picture: payload['picture'] as string,
      }
    } catch {
      req.user = null
    }
  })
}

export default fp(authPlugin)

/**
 * Route-level preHandler that enforces authentication.
 * Returns 401 { error: 'Unauthorized' } if req.user is null.
 */
export async function requireAuth(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  if (!req.user) {
    await reply.status(401).send({ error: 'Unauthorized' })
  }
}
