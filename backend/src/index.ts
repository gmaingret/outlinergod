import Fastify from 'fastify'
import { TypeBoxTypeProvider } from '@fastify/type-provider-typebox'

/**
 * Application factory. Builds and returns a Fastify instance without calling
 * listen() — that lives in server.ts so tests can call buildApp() in isolation.
 */
export function buildApp() {
  const app = Fastify({ logger: true }).withTypeProvider<TypeBoxTypeProvider>()

  // Placeholder root route
  app.get('/', async (_req, reply) => {
    return reply.send({ message: 'OutlinerGod API' })
  })

  return app
}
