import { describe, it, expect, beforeEach, vi } from 'vitest'
import {
  hlcGenerate,
  hlcReceive,
  hlcParse,
  hlcCompare,
  resetHlcForTesting,
} from './hlc.js'

beforeEach(() => {
  resetHlcForTesting()
})

describe('hlcGenerate', () => {
  it('hlc_generate_formatIsValid', () => {
    const result = hlcGenerate('dev1')
    expect(result).toMatch(/^\d{13}-\d{5}-dev1$/)
  })

  it('hlc_generate_isMonotonic', () => {
    const results: string[] = []
    for (let i = 0; i < 100; i++) {
      results.push(hlcGenerate('dev1'))
    }
    for (let i = 1; i < results.length; i++) {
      expect(results[i] > results[i - 1]).toBe(true)
    }
  })

  it('hlc_generate_counterIncrementsWithinSameMs', () => {
    const fixedMs = 1700000000000
    const spy = vi.spyOn(Date, 'now').mockReturnValue(fixedMs)
    try {
      const r1 = hlcGenerate('dev1')
      const r2 = hlcGenerate('dev1')
      const { counter: c1 } = hlcParse(r1)
      const { counter: c2 } = hlcParse(r2)
      expect(c2).toBe(c1 + 1)
    } finally {
      spy.mockRestore()
    }
  })
})

describe('hlcReceive', () => {
  it('hlc_receive_advancesClockWhenRemoteIsAhead', () => {
    const futureMs = Date.now() + 1_000_000
    const remoteHlc = `${String(futureMs).padStart(13, '0')}-00005-remote`
    const result = hlcReceive(remoteHlc, 'local')
    const { ms } = hlcParse(result)
    expect(ms).toBeGreaterThanOrEqual(futureMs)
  })

  it('hlc_receive_incrementsCounter_whenSameMs', () => {
    const fixedMs = 1700000000000
    const spy = vi.spyOn(Date, 'now').mockReturnValue(fixedMs)
    try {
      // Advance local clock to fixedMs
      hlcGenerate('dev1')
      // Create a remote HLC with the same ms and a known counter
      const remoteCounter = 3
      const remoteHlc = `${String(fixedMs).padStart(13, '0')}-${String(remoteCounter).padStart(5, '0')}-remote`
      const result = hlcReceive(remoteHlc, 'local')
      const { counter } = hlcParse(result)
      expect(counter).toBeGreaterThan(remoteCounter)
    } finally {
      spy.mockRestore()
    }
  })

  it('hlc_receive_formatIsValid', () => {
    const remoteMs = Date.now() - 1000
    const remoteHlc = `${String(remoteMs).padStart(13, '0')}-00000-remote`
    const result = hlcReceive(remoteHlc, 'local-node')
    expect(result).toMatch(/^\d{13}-\d{5}-.+$/)
  })
})

describe('hlcParse', () => {
  it('hlc_parse_extractsAllThreeParts', () => {
    const { ms, counter, nodeId } = hlcParse('1772370135754-00003-device_abc123')
    expect(ms).toBe(1772370135754)
    expect(counter).toBe(3)
    expect(nodeId).toBe('device_abc123')
  })

  it('hlc_parse_nodeIdWithDash', () => {
    const { nodeId } = hlcParse('1772370135754-00003-device-abc-123')
    expect(nodeId).toBe('device-abc-123')
  })
})

describe('hlcCompare', () => {
  it('hlc_compare_orderIsCorrect', () => {
    const older = '1772370135754-00003-dev1'
    const newer = '1772370135755-00000-dev1'
    expect(hlcCompare(older, newer)).toBeLessThan(0)
    expect(hlcCompare(newer, older)).toBeGreaterThan(0)
    expect(hlcCompare(older, older)).toBe(0)
  })

  it('hlc_compare_stringAndLogicAgree', () => {
    const pairs: [string, string][] = [
      ['1772370135754-00003-dev1', '1772370135755-00000-dev1'],
      ['1772370135754-00003-dev1', '1772370135754-00004-dev1'],
      ['1772370135754-00003-dev1', '1772370135754-00003-dev1'],
      ['1772370135754-00004-dev1', '1772370135754-00003-dev1'],
      ['1772370135755-00000-dev1', '1772370135754-00003-dev1'],
    ]
    for (const [a, b] of pairs) {
      const expected = Math.sign(a < b ? -1 : a > b ? 1 : 0)
      const actual = Math.sign(hlcCompare(a, b))
      expect(actual).toBe(expected)
    }
  })
})
