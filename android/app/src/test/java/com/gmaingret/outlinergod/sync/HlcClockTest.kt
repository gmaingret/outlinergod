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
        assertTrue(hlc.matches(Regex("^\\d{13}-\\d{5}-d1$")))
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
        assertEquals("00000", firstCounter)
        assertEquals("00001", secondCounter)
    }

    "receive_advancesWallPastIncoming" {
        val t = 1000L
        val incomingWall = t + 5000L
        val incomingHlc = "${incomingWall.toString().padStart(13, '0')}-00000-other"
        val clock = HlcClock(clock = { t })
        val result = clock.receive(incomingHlc, "d")
        val resultWall = result.split("-")[0].toLong()
        assertTrue(resultWall >= incomingWall)
    }

    "receive_resetsCounter_whenWallAdvances" {
        // Build up a non-zero counter at wall=1000
        var now = 1000L
        val clock = HlcClock(clock = { now })
        clock.generate("d") // counter = 00000
        clock.generate("d") // counter = 00001
        // Advance physical clock past both local wall and incoming wall
        now = 3000L
        val incomingHlc = "${2000L.toString().padStart(13, '0')}-00000-other"
        val result = clock.receive(incomingHlc, "d")
        val counter = result.split("-")[1]
        assertEquals("00000", counter)
    }

    "compare_higherTimestamp_returnsPositive" {
        val t1 = 1000L
        val t2 = 2000L
        val hlcT1 = "${t1.toString().padStart(13, '0')}-00000-d"
        val hlcT2 = "${t2.toString().padStart(13, '0')}-00000-d"
        val clock = HlcClock()
        assertTrue(clock.compare(hlcT2, hlcT1) > 0)
    }

    "compare_higherCounter_returnsPositive" {
        val wall = "0000000000001"
        val hlcLow = "$wall-00000-d"
        val hlcHigh = "$wall-00001-d"
        val clock = HlcClock()
        assertTrue(clock.compare(hlcHigh, hlcLow) > 0)
    }

    "wallDecimal_isExactlyThirteenChars (property-based)" {
        // 13-digit decimal range: 1_000_000_000_000L..9_999_999_999_999L
        checkAll(Arb.long(1_000_000_000_000L..9_999_999_999_999L)) { wallMs ->
            val clock = HlcClock(clock = { wallMs })
            val hlc = clock.generate("d")
            val wallPart = hlc.split("-")[0]
            assertEquals(13, wallPart.length)
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
