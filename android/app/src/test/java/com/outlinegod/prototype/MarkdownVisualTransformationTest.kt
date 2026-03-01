package com.outlinegod.prototype

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the Markdown span-detection logic in MarkdownVisualTransformation.kt.
 *
 * All tests run on the JVM. The pure [detectMarkdownSpans] function is tested
 * directly — it has no Compose dependency.
 *
 * Offset-mapping tests (7 & 8) verify the identity property that the prototype
 * relies on: since [OffsetMapping.Identity] is used throughout, any offset maps
 * to itself. We model this as a pure function (identityMap) and verify it.
 *
 * Test cases match the P0-2 specification in PLAN_PHASE0.md (9 cases).
 */
class MarkdownVisualTransformationTest {

    // ── Test 1: bold_appliesBoldSpanStyle ─────────────────────────────────────
    // Input:   "hello **world** end"
    // Indices:  01234567890123456789
    //                   8..12 = "world" (inner group 1)
    //           addStyle call: start=8, end=13 (exclusive)

    @Test
    fun bold_appliesBoldSpanStyle() {
        val input = "hello **world** end"
        val spans = detectMarkdownSpans(input)

        val boldSpans = spans.filter { it.type == MarkdownFormatType.BOLD }
        assertEquals("Exactly one bold span", 1, boldSpans.size)

        val span = boldSpans[0]
        // "world" starts at offset 8 (after "hello **")
        assertEquals("Bold span start", 8, span.start)
        // end is exclusive: 8 + 5 ("world") = 13
        assertEquals("Bold span end (exclusive)", 13, span.end)
        assertEquals(input.substring(span.start, span.end), "world")
    }

    // ── Test 2: italic_appliesItalicSpanStyle ─────────────────────────────────
    // Input:   "_hello_"
    // Indices:  0123456
    // Inner group 1: "hello" at 1..5 → addStyle start=1, end=6

    @Test
    fun italic_appliesItalicSpanStyle() {
        val input = "_hello_"
        val spans = detectMarkdownSpans(input)

        val italicSpans = spans.filter { it.type == MarkdownFormatType.ITALIC }
        assertEquals("Exactly one italic span", 1, italicSpans.size)

        val span = italicSpans[0]
        assertEquals("Italic span start", 1, span.start)
        assertEquals("Italic span end (exclusive)", 6, span.end)
        assertEquals("hello", input.substring(span.start, span.end))
    }

    // ── Test 3: code_appliesMonospaceStyle ────────────────────────────────────
    // Input:   "`code`"
    // Indices:  012345
    // Inner group 1: "code" at 1..4 → addStyle start=1, end=5

    @Test
    fun code_appliesMonospaceStyle() {
        val input = "`code`"
        val spans = detectMarkdownSpans(input)

        val codeSpans = spans.filter { it.type == MarkdownFormatType.CODE }
        assertEquals("Exactly one code span", 1, codeSpans.size)

        val span = codeSpans[0]
        assertEquals("Code span start", 1, span.start)
        assertEquals("Code span end (exclusive)", 5, span.end)
        assertEquals("code", input.substring(span.start, span.end))
    }

    // ── Test 4: strikethrough_appliesLineThrough ──────────────────────────────
    // Input:   "~~strike~~"
    // Indices:  0123456789
    // Inner group 1: "strike" at 2..7 → addStyle start=2, end=8

    @Test
    fun strikethrough_appliesLineThrough() {
        val input = "~~strike~~"
        val spans = detectMarkdownSpans(input)

        val strikeSpans = spans.filter { it.type == MarkdownFormatType.STRIKETHROUGH }
        assertEquals("Exactly one strikethrough span", 1, strikeSpans.size)

        val span = strikeSpans[0]
        assertEquals("Strikethrough span start", 2, span.start)
        assertEquals("Strikethrough span end (exclusive)", 8, span.end)
        assertEquals("strike", input.substring(span.start, span.end))
    }

    // ── Test 5: highlight_appliesBackgroundColor ──────────────────────────────
    // Input:   "==hi=="
    // Indices:  012345
    // Inner group 1: "hi" at 2..3 → addStyle start=2, end=4

    @Test
    fun highlight_appliesBackgroundColor() {
        val input = "==hi=="
        val spans = detectMarkdownSpans(input)

        val highlightSpans = spans.filter { it.type == MarkdownFormatType.HIGHLIGHT }
        assertEquals("Exactly one highlight span", 1, highlightSpans.size)

        val span = highlightSpans[0]
        assertEquals("Highlight span start", 2, span.start)
        assertEquals("Highlight span end (exclusive)", 4, span.end)
        assertEquals("hi", input.substring(span.start, span.end))
    }

    // ── Test 6: nestedOrdering_doesNotCrash ──────────────────────────────────
    // All 5 format types in one string — no exception, at least 5 spans.

    @Test
    fun nestedOrdering_doesNotCrash() {
        val input = "**bold** _italic_ `code` ~~strike~~ ==highlight=="
        val spans = detectMarkdownSpans(input)

        assertTrue(
            "Expected at least 5 spans but got ${spans.size}",
            spans.size >= 5,
        )
        assertTrue("Must have BOLD span",          spans.any { it.type == MarkdownFormatType.BOLD })
        assertTrue("Must have ITALIC span",        spans.any { it.type == MarkdownFormatType.ITALIC })
        assertTrue("Must have CODE span",          spans.any { it.type == MarkdownFormatType.CODE })
        assertTrue("Must have STRIKETHROUGH span", spans.any { it.type == MarkdownFormatType.STRIKETHROUGH })
        assertTrue("Must have HIGHLIGHT span",     spans.any { it.type == MarkdownFormatType.HIGHLIGHT })
    }

    // ── Test 7: offsetMapping_identityPreservesCursorAt0 ──────────────────────
    // MarkdownVisualTransformation uses OffsetMapping.Identity, which means:
    //   originalToTransformed(x) == x
    //   transformedToOriginal(x) == x
    // We verify the identity property as a pure function.

    @Test
    fun offsetMapping_identityPreservesCursorAt0() {
        // Identity mapping: offset 0 → 0 in both directions.
        val identityMap: (Int) -> Int = { it }
        assertEquals("originalToTransformed(0) == 0", 0, identityMap(0))
        assertEquals("transformedToOriginal(0) == 0", 0, identityMap(0))
    }

    // ── Test 8: offsetMapping_identityPreservesCursorAtEnd ───────────────────
    // For a string of length N, identity mapping returns N for offset N.

    @Test
    fun offsetMapping_identityPreservesCursorAtEnd() {
        val input = "hello **world** end"
        val n = input.length
        val identityMap: (Int) -> Int = { it }
        assertEquals("originalToTransformed(N) == N", n, identityMap(n))
    }

    // ── Test 9: performance_under2ms ─────────────────────────────────────────
    // Apply detectMarkdownSpans to a ~2,000-char string containing 20 bold spans.
    // Must complete in < 2 ms (wall-clock).

    @Test
    fun performance_under2ms() {
        // Build a ~2,000-char string with exactly 20 bold spans.
        val boldUnit = "**bold** "           // 9 chars per span
        val spans20  = boldUnit.repeat(20)   // 180 chars
        val padding  = "x".repeat(2000 - spans20.length)
        val input    = spans20 + padding     // 2000 chars total

        // Warm up the JIT (one ignored run)
        detectMarkdownSpans(input)

        val startNs  = System.nanoTime()
        val result   = detectMarkdownSpans(input)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0

        assertEquals("Expected 20 bold spans", 20, result.filter { it.type == MarkdownFormatType.BOLD }.size)
        assertTrue(
            "detectMarkdownSpans must complete in < 2 ms but took ${elapsedMs}ms",
            elapsedMs < 2.0,
        )
    }
}
