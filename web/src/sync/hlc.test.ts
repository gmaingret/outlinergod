import { describe, it, expect, beforeEach } from 'vitest'
import { hlcGenerate, hlcReceive, hlcParse, hlcCompare, resetHlcForTesting } from './hlc'

beforeEach(() => {
  resetHlcForTesting()
})

describe('HLC module', () => {
  it('hlcGenerate returns string matching format /^\\d{13}-\\d{5}-.+$/', () => {
    const hlc = hlcGenerate('device_abc123')
    expect(hlc).toMatch(/^\d{13}-\d{5}-.+$/)
  })

  it('hlcGenerate with same deviceId twice produces strictly increasing HLCs', () => {
    const first = hlcGenerate('device_abc')
    const second = hlcGenerate('device_abc')
    // String comparison is correct since format is lexicographically sortable
    expect(second > first).toBe(true)
  })

  it('hlcGenerate with different deviceIds produces valid HLCs', () => {
    const hlc1 = hlcGenerate('device_a')
    const hlc2 = hlcGenerate('device_b')
    expect(hlc1).toMatch(/^\d{13}-\d{5}-device_a$/)
    expect(hlc2).toMatch(/^\d{13}-\d{5}-device_b$/)
  })

  it('hlcParse correctly splits a known HLC string into {ms, counter, nodeId}', () => {
    const parsed = hlcParse('1772370135754-00003-device_abc123')
    expect(parsed.ms).toBe(1772370135754)
    expect(parsed.counter).toBe(3)
    expect(parsed.nodeId).toBe('device_abc123')
  })

  it('hlcParse handles nodeId containing dashes', () => {
    const parsed = hlcParse('1772370135754-00001-device-with-dashes')
    expect(parsed.ms).toBe(1772370135754)
    expect(parsed.counter).toBe(1)
    expect(parsed.nodeId).toBe('device-with-dashes')
  })

  it('hlcCompare returns negative for a < b, 0 for equal, positive for a > b', () => {
    const a = '1772370135754-00001-dev'
    const b = '1772370135754-00002-dev'
    const c = '1772370135754-00001-dev'
    expect(hlcCompare(a, b)).toBeLessThan(0)
    expect(hlcCompare(b, a)).toBeGreaterThan(0)
    expect(hlcCompare(a, c)).toBe(0)
  })

  it('hlcReceive advances clock past both local and remote state', () => {
    // Generate a local HLC first
    const local = hlcGenerate('local-device')
    const localParsed = hlcParse(local)

    // Build a remote HLC with a counter higher than local
    const remoteMs = localParsed.ms
    const remoteCounter = localParsed.counter + 5
    const remoteHlc = `${String(remoteMs).padStart(13, '0')}-${String(remoteCounter).padStart(5, '0')}-remote-device`

    const received = hlcReceive(remoteHlc, 'local-device')
    const receivedParsed = hlcParse(received)

    // Received must be strictly greater than both local and remote
    expect(hlcCompare(received, local)).toBeGreaterThan(0)
    expect(hlcCompare(received, remoteHlc)).toBeGreaterThan(0)
    expect(receivedParsed.nodeId).toBe('local-device')
  })

  it('resetHlcForTesting resets state so next generate returns counter 0', () => {
    // Generate several HLCs to advance the counter
    hlcGenerate('device')
    hlcGenerate('device')
    hlcGenerate('device')

    // Reset and generate again — counter should be 0 again (unless wall clock advanced)
    resetHlcForTesting()
    const afterReset = hlcGenerate('device')
    const parsed = hlcParse(afterReset)
    expect(parsed.counter).toBe(0)
  })
})
