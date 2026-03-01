package com.gmaingret.outlinergod.prototype

/**
 * Hybrid Logical Clock (HLC) — per-field LWW timestamp for sync.
 *
 * Format: <wall_ms_16hex>-<counter_4hex>-<device_id>
 * Example: 0000017b05a3a1be-0000-a1b2c3d4-5678-90ab-cdef-12345678abcd
 *
 * Mirrors the backend src/hlc.ts implementation exactly.
 * All public methods are @Synchronized for safe use from background threads.
 */
class HlcClock {
    private var wallMs: Long = 0L
    private var counter: Int = 0

    /**
     * Generate a new HLC for an outgoing event on this device.
     * Guarantees the returned HLC > any previously generated HLC.
     */
    @Synchronized
    fun generate(deviceId: String): String {
        val now = System.currentTimeMillis()
        if (now > wallMs) {
            wallMs = now
            counter = 0
        } else {
            // Clock tie or backward — bump counter to stay monotonic
            counter++
        }
        return format(wallMs, counter, deviceId)
    }

    /**
     * Receive an incoming HLC from a remote event.
     * Advances the local clock so future generate() calls produce a higher value.
     */
    @Synchronized
    fun receive(incoming: String, deviceId: String): String {
        val (inWall, inCounter, _) = parse(incoming)
        val now = System.currentTimeMillis()

        when {
            now > inWall && now > wallMs -> {
                // Physical clock ahead of everything — use it
                wallMs = now
                counter = 0
            }
            inWall > wallMs -> {
                // Incoming is from the future relative to our local clock
                wallMs = inWall
                counter = inCounter + 1
            }
            inWall == wallMs -> {
                // Same wall — take max counter and bump
                counter = maxOf(counter, inCounter) + 1
            }
            else -> {
                // Local clock is ahead of incoming — just bump our counter
                counter++
            }
        }
        return format(wallMs, counter, deviceId)
    }

    companion object {
        fun format(wallMs: Long, counter: Int, deviceId: String): String {
            val wallHex = wallMs.toString(16).padStart(16, '0')
            val counterHex = counter.toString(16).padStart(4, '0')
            return "$wallHex-$counterHex-$deviceId"
        }

        /**
         * Parse an HLC string into its components.
         * Returns Triple(wallMs, counter, deviceId).
         * DeviceId may contain dashes (UUID format) so we split only on the first two dashes.
         */
        fun parse(hlc: String): Triple<Long, Int, String> {
            val firstDash = hlc.indexOf('-')
            val secondDash = hlc.indexOf('-', firstDash + 1)
            val wallMs = hlc.substring(0, firstDash).toLong(16)
            val counter = hlc.substring(firstDash + 1, secondDash).toInt(16)
            val deviceId = hlc.substring(secondDash + 1)
            return Triple(wallMs, counter, deviceId)
        }
    }
}
