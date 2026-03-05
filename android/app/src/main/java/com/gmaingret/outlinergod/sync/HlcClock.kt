package com.gmaingret.outlinergod.sync

/**
 * Hybrid Logical Clock (HLC) -- per-field LWW timestamp for sync.
 *
 * Format: <13-digit-decimal-wall>-<5-digit-decimal-counter>-<device_id>
 * Example: 1772370135754-00000-a1b2c3d4-5678-90ab-cdef-12345678abcd
 *
 * Mirrors the backend src/hlc.ts implementation exactly.
 */
class HlcClock(private val clock: () -> Long = { System.currentTimeMillis() }) {

    private var wallMs: Long = 0L
    private var counter: Int = 0

    /**
     * Generate a new HLC for an outgoing event on this device.
     * Guarantees the returned HLC > any previously generated HLC.
     */
    @Synchronized
    fun generate(deviceId: String): String {
        val now = clock()
        if (now > wallMs) {
            wallMs = now
            counter = 0
        } else {
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
        val now = clock()

        when {
            now > inWall && now > wallMs -> {
                wallMs = now
                counter = 0
            }
            inWall > wallMs -> {
                wallMs = inWall
                counter = inCounter + 1
            }
            inWall == wallMs -> {
                counter = maxOf(counter, inCounter) + 1
            }
            else -> {
                counter++
            }
        }
        return format(wallMs, counter, deviceId)
    }

    /**
     * Lexicographic comparison of two HLC strings.
     */
    fun compare(a: String, b: String): Int = a.compareTo(b)

    companion object {
        fun format(wallMs: Long, counter: Int, deviceId: String): String {
            val wallDecimal = wallMs.toString().padStart(13, '0')
            val counterDecimal = counter.toString().padStart(5, '0')
            return "$wallDecimal-$counterDecimal-$deviceId"
        }

        /**
         * Parse an HLC string into its components.
         * Returns Triple(wallMs, counter, deviceId).
         * DeviceId may contain dashes (UUID format) so we split only on the first two dashes.
         */
        fun parse(hlc: String): Triple<Long, Int, String> {
            val firstDash = hlc.indexOf('-')
            val secondDash = hlc.indexOf('-', firstDash + 1)
            val wallMs = hlc.substring(0, firstDash).toLong()
            val counter = hlc.substring(firstDash + 1, secondDash).toInt()
            val deviceId = hlc.substring(secondDash + 1)
            return Triple(wallMs, counter, deviceId)
        }
    }
}
