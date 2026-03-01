/**
 * Hybrid Logical Clock (HLC) — per-field LWW timestamp
 *
 * Format: <wall_ms_16hex>-<counter_4hex>-<device_id>
 * Example: 0000017b05a3a1be-0000-a1b2c3d4-5678-90ab-cdef-12345678abcd
 *
 * Lexicographically sortable — higher HLC string always means a more recent event.
 */
export class HlcClock {
  private wallMs = 0
  private counter = 0

  /**
   * Generate a new HLC for an outgoing event on this device.
   * Guarantees the returned HLC > any previously generated HLC.
   */
  generate(deviceId: string): string {
    const now = Date.now()
    if (now > this.wallMs) {
      this.wallMs = now
      this.counter = 0
    } else {
      // now <= wallMs: clock tie or backward — bump counter to stay monotonic
      this.counter++
    }
    return HlcClock.format(this.wallMs, this.counter, deviceId)
  }

  /**
   * Receive an incoming HLC from a remote event.
   * Advances the local clock to be strictly greater than both the local state
   * and the incoming HLC, so any future generate() is larger than the incoming.
   */
  receive(incoming: string, deviceId: string): string {
    const { wallMs: inWall, counter: inCounter } = HlcClock.parse(incoming)
    const now = Date.now()

    if (now > inWall && now > this.wallMs) {
      // Physical clock is ahead of everything — use it, reset counter
      this.wallMs = now
      this.counter = 0
    } else if (inWall > this.wallMs) {
      // Incoming is from the future relative to our local clock
      this.wallMs = inWall
      this.counter = inCounter + 1
    } else if (inWall === this.wallMs) {
      // Same wall time — take the max counter and bump
      this.counter = Math.max(this.counter, inCounter) + 1
    } else {
      // Our local clock is ahead of the incoming — just bump our counter
      this.counter++
    }
    return HlcClock.format(this.wallMs, this.counter, deviceId)
  }

  /** Current HLC value without advancing the clock. */
  current(deviceId: string): string {
    return HlcClock.format(this.wallMs, this.counter, deviceId)
  }

  static format(wallMs: number, counter: number, deviceId: string): string {
    const wallHex = wallMs.toString(16).padStart(16, '0')
    const counterHex = counter.toString(16).padStart(4, '0')
    return `${wallHex}-${counterHex}-${deviceId}`
  }

  static parse(hlc: string): { wallMs: number; counter: number; deviceId: string } {
    // Format: 16hex-4hex-<deviceId>
    // deviceId may contain dashes (UUID), so split only on the first two dashes.
    const firstDash = hlc.indexOf('-')
    const secondDash = hlc.indexOf('-', firstDash + 1)
    const wallMs = parseInt(hlc.substring(0, firstDash), 16)
    const counter = parseInt(hlc.substring(firstDash + 1, secondDash), 16)
    const deviceId = hlc.substring(secondDash + 1)
    return { wallMs, counter, deviceId }
  }
}
