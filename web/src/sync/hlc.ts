/**
 * Hybrid Logical Clock (HLC) — module-level functional API
 *
 * Format: <13-digit-ms>-<5-digit-zero-padded-counter>-<nodeId>
 * Example: 1772370135754-00003-device_abc123
 *
 * Lexicographically sortable — a higher HLC string always means a more recent event.
 */

// Module-level mutable state (NOT exported)
let lastMs = 0
let lastCounter = 0

function format(ms: number, counter: number, nodeId: string): string {
  return `${String(ms).padStart(13, '0')}-${String(counter).padStart(5, '0')}-${nodeId}`
}

/**
 * Generate a new HLC for an outgoing event on this device.
 * Guarantees the returned HLC is strictly greater than any previously generated HLC.
 */
export function hlcGenerate(nodeId: string): string {
  const pt = Date.now()
  if (pt > lastMs) {
    lastMs = pt
    lastCounter = 0
  } else {
    // Clock did not advance (same ms or backwards) — bump counter to stay monotonic
    lastCounter++
  }
  return format(lastMs, lastCounter, nodeId)
}

/**
 * Receive an incoming HLC from a remote event.
 * Advances the local clock past both the local state and the incoming HLC.
 */
export function hlcReceive(remoteHlc: string, nodeId: string): string {
  const parts = remoteHlc.split('-')
  const remoteMs = parseInt(parts[0])
  const remoteCounter = parseInt(parts[1])
  const pt = Date.now()
  const newMs = Math.max(pt, lastMs, remoteMs)

  let newCounter: number
  if (newMs === lastMs && newMs === remoteMs) {
    newCounter = Math.max(lastCounter, remoteCounter) + 1
  } else if (newMs === lastMs) {
    newCounter = lastCounter + 1
  } else if (newMs === remoteMs) {
    newCounter = remoteCounter + 1
  } else {
    newCounter = 0
  }

  lastMs = newMs
  lastCounter = newCounter
  return format(lastMs, lastCounter, nodeId)
}

/**
 * Parse an HLC string into its three components.
 * Splits only on the first two dashes so nodeId may itself contain dashes.
 */
export function hlcParse(hlc: string): { ms: number; counter: number; nodeId: string } {
  const firstDash = hlc.indexOf('-')
  const secondDash = hlc.indexOf('-', firstDash + 1)
  return {
    ms: parseInt(hlc.substring(0, firstDash)),
    counter: parseInt(hlc.substring(firstDash + 1, secondDash)),
    nodeId: hlc.substring(secondDash + 1),
  }
}

/**
 * Compare two HLC strings.
 * Returns negative if a < b, 0 if equal, positive if a > b.
 * Correct via simple string comparison — the format is lexicographically sortable by design.
 */
export function hlcCompare(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0
}

/**
 * Reset module-level clock state to zero.
 * ONLY call this in tests to isolate test cases from each other.
 */
export function resetHlcForTesting(): void {
  lastMs = 0
  lastCounter = 0
}
