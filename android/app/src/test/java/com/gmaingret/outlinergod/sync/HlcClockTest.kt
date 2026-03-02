package com.gmaingret.outlinergod.sync

import io.kotest.core.spec.style.StringSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.runner.RunWith
import io.kotest.runner.junit4.KotestTestRunner

@RunWith(KotestTestRunner::class)
class HlcClockTest : StringSpec({

    "generate_matchesFormat" {
        val clock = HlcClock()
        val hlc = clock.generate("d1")
        assertTrue(hlc.matches(Regex("^[0-9a-f]{16}-[0-9a-f]{4}-d1$")))
    }

    "generate_isMonotonicallyIncreasing" {
        val clock = HlcClock()
        val results = (1..100).map { clock.generate("d") }
        for (i in 1 until results.size) {
            assertTrue(
                "HLC should be monotonically increasing",
                results[i] > results[i - 1]
            )
        }
    }

    "generate_incrementsCounter_onSameMillisecond" {
        val clock = HlcClock(clock = { 1000L })
        val first = clock.generate("d")
        val second = clock.generate("d")
        val firstCounter = first.split("-")[1]
        val secondCounter = second.split("-")[1]
        assertEquals("0000", firstCounter)
        assertEquals("0001", secondCounter)
    }

    "receive_advancesWallPastIncoming" {
        val t = 1000L
        val incomingWall = t + 5000L
        val incomingHlc = "${incomingWall.toString(16).padStart(16, '0')}-0000-other"
        val clock = HlcClock(clock = { t })
        val result = clock.receive(incomingHlc, "d")
        val resultWall = result.split("-")[0].toLong(16)
        assertTrue(resultWall >= incomingWall)
    }

    "receive_resetsCounter_whenWallAdvances" {
        // Build up a non-zero counter at wall=1000
        var now = 1000L
        val clock = HlcClock(clock = { now })
        clock.generate("d") // counter = 0000
        clock.generate("d") // counter = 0001
        // Advance physical clock past both local wall and incoming wall
        now = 3000L
        val incomingHlc = "${2000L.toString(16).padStart(16, '0')}-0000-other"
        val result = clock.receive(incomingHlc, "d")
        val counter = result.split("-")[1]
        assertEquals("0000", counter)
    }

    "compare_higherTimestamp_returnsPositive" {
        val t1 = 1000L
        val t2 = 2000L
        val hlcT1 = "${t1.toString(16).padStart(16, '0')}-0000-d"
        val hlcT2 = "${t2.toString(16).padStart(16, '0')}-0000-d"
        val clock = HlcClock()
        assertTrue(clock.compare(hlcT2, hlcT1) > 0)
    }

    "compare_higherCounter_returnsPositive" {
        val wall = "0000000000000001"
        val hlcLow = "$wall-0000-d"
        val hlcHigh = "$wall-0001-d"
        val clock = HlcClock()
        assertTrue(clock.compare(hlcHigh, hlcLow) > 0)
    }

    "wallHex_isExactlySixteenChars (property-based)" {
        checkAll(Arb.long(0L..Long.MAX_VALUE / 2)) { wallMs ->
            val clock = HlcClock(clock = { wallMs })
            val hlc = clock.generate("d")
            val wallPart = hlc.split("-")[0]
            assertEquals(16, wallPart.length)
        }
    }

    "sortLexicographic_matchesGenerationOrder (property-based)" {
        checkAll(Arb.int(2..50)) { n ->
            val clock = HlcClock()
            val generated = (1..n).map { clock.generate("d") }
            val sorted = generated.sortedWith(Comparator { a, b -> a.compareTo(b) })
            assertEquals(generated, sorted)
        }
    }
})
