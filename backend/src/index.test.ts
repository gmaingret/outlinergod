import { describe, it, expect, afterEach } from 'vitest'
import { buildApp } from './index.js'
import type { FastifyInstance } from 'fastify'

describe('buildApp', () => {
  let apps: FastifyInstance[] = []

  afterEach(async () => {
    await Promise.all(apps.map((a) => a.close()))
    apps = []
  })

  it('buildApp_returnsInstance', () => {
    const app = buildApp()
    apps.push(app)
    expect(app).toBeTruthy()
    expect(typeof app.inject).toBe('function')
  })

  it('buildApp_isReadyAfterAwait', async () => {
    const app = buildApp()
    apps.push(app)
    await expect(app.ready()).resolves.not.toThrow()
  })

  it('buildApp_twoCallsReturnIndependentInstances', () => {
    const app1 = buildApp()
    const app2 = buildApp()
    apps.push(app1, app2)
    expect(app1).not.toBe(app2)
  })
})
