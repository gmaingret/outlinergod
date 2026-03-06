import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

afterEach(cleanup)

// @testing-library/react's waitFor calls jest.advanceTimersByTime(0) to drain
// the microtask queue when fake timers are active. Vitest does not provide a
// `jest` global, so we shim it here to delegate to vi.
//
// RTL detects fake timers via: setTimeout._isMockFunction === true
// OR Object.prototype.hasOwnProperty.call(setTimeout, 'clock')
// Both are set by vitest's fake-timer implementation, so the detection works
// as long as `jest` is defined.
if (typeof globalThis.jest === 'undefined') {
  ;(globalThis as Record<string, unknown>).jest = {
    advanceTimersByTime: (ms: number) => vi.advanceTimersByTime(ms),
    runAllTimers: () => vi.runAllTimers(),
    runAllTimersAsync: () => vi.runAllTimersAsync(),
  }
}
