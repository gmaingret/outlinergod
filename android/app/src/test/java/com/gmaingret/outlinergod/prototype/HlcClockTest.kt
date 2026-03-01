package com.gmaingret.outlinergod.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-3 Android HLC tests — mirrors the backend HLC test cases.
 */
class HlcClockTest {

    /**
     * Test 1: generate() is monotonically increasing.
     * Two successive calls must produce HLC strings where the second is
     * lexicographically greater than the first.
     */
    @Test
    fun hlcClock_generate_monotonic() {
        val clock = HlcClock()
        val h1 = clock.generate("dev-1")
        val h2 = clock.generate("dev-1")
        assertTrue("Second HLC must be > first HLC (lexicographic)", h2 > h1)
    }

    /**
     * Test 2: receive() with a future HLC advances the local clock wall past
     * the incoming value.
     */
    @Test
    fun hlcClock_receive_advancesPastIncoming() {
        val clock = HlcClock()
        val futureWall = System.currentTimeMillis() + 100L
        // Construct a fake "incoming" HLC whose wall component is 100 ms in the future
        val incomingHlc = HlcClock.format(futureWall, 0, "remote-device")
        val result = clock.receive(incomingHlc, "local-device")
        val (resultWall, _, _) = HlcClock.parse(result)
        assertTrue(
            "Clock wall after receive ($resultWall) must be >= incoming future wall ($futureWall)",
            resultWall >= futureWall,
        )
    }

    // Sanity check: parse ∘ format is identity
    @Test
    fun hlcClock_parseFormatRoundtrip() {
        val wallMs = 1_700_000_000_000L
        val counter = 42
        val deviceId = "a1b2c3d4-5678-90ab-cdef-0123456789ab"
        val hlc = HlcClock.format(wallMs, counter, deviceId)
        val (parsedWall, parsedCounter, parsedDevice) = HlcClock.parse(hlc)
        assertEquals(wallMs, parsedWall)
        assertEquals(counter, parsedCounter)
        assertEquals(deviceId, parsedDevice)
    }
}
