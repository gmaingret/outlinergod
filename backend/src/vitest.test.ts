/// <reference types="vitest/globals" />

// These tests use globals directly (no import) to confirm globals: true is working.

describe('vitest config', () => {
  it('vitest_canRunTests', () => {
    expect(1 + 1).toBe(2)
  })

  it('vitest_globals_areAvailable', () => {
    expect(typeof describe).toBe('function')
    expect(typeof it).toBe('function')
  })
})
