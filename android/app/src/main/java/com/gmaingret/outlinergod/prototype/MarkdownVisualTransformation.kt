package com.gmaingret.outlinergod.prototype

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration

// ── Pure Kotlin (no Compose dependency) — fully testable on JVM ──────────────

/** The format type for a detected Markdown span. */
enum class MarkdownFormatType { BOLD, ITALIC, CODE, STRIKETHROUGH, HIGHLIGHT }

/**
 * A detected inline Markdown span.
 * [start] is inclusive, [end] is exclusive (matches AnnotatedString.Builder.addStyle convention).
 */
data class MarkdownSpan(val start: Int, val end: Int, val type: MarkdownFormatType)

// Regexes compiled once at top-level — never on recomposition.
val BOLD_REGEX          = Regex("""\*\*(.*?)\*\*""")
val ITALIC_REGEX        = Regex("""_(.*?)_""")
val CODE_REGEX          = Regex("""`(.*?)`""")
val STRIKETHROUGH_REGEX = Regex("""~~(.*?)~~""")
val HIGHLIGHT_REGEX     = Regex("""==(.*?)==""")

/**
 * Scans [text] with all five Markdown regexes and returns every detected span.
 *
 * This is a pure function with no Compose dependency — safe to call in JVM unit tests.
 */
fun detectMarkdownSpans(text: String): List<MarkdownSpan> {
    val spans = mutableListOf<MarkdownSpan>()

    for (match in BOLD_REGEX.findAll(text)) {
        val inner = match.groups[1]!!
        spans.add(MarkdownSpan(inner.range.first, inner.range.last + 1, MarkdownFormatType.BOLD))
    }
    for (match in ITALIC_REGEX.findAll(text)) {
        val inner = match.groups[1]!!
        spans.add(MarkdownSpan(inner.range.first, inner.range.last + 1, MarkdownFormatType.ITALIC))
    }
    for (match in CODE_REGEX.findAll(text)) {
        val inner = match.groups[1]!!
        spans.add(MarkdownSpan(inner.range.first, inner.range.last + 1, MarkdownFormatType.CODE))
    }
    for (match in STRIKETHROUGH_REGEX.findAll(text)) {
        val inner = match.groups[1]!!
        spans.add(MarkdownSpan(inner.range.first, inner.range.last + 1, MarkdownFormatType.STRIKETHROUGH))
    }
    for (match in HIGHLIGHT_REGEX.findAll(text)) {
        val inner = match.groups[1]!!
        spans.add(MarkdownSpan(inner.range.first, inner.range.last + 1, MarkdownFormatType.HIGHLIGHT))
    }

    return spans
}

// ── Compose-specific layer (not tested directly in JVM unit tests) ────────────

/**
 * [VisualTransformation] that applies live inline Markdown styling.
 *
 * Phase 1 behaviour: markers remain visible, cursor is always correct.
 * [OffsetMapping.Identity] is used — no offset math needed because styling
 * only adds spans without altering text length.
 *
 * Phase 2 (marker-hiding via OutputTransformation) is explicitly deferred:
 * OutputTransformation does not yet support AnnotatedString styling as of early 2026.
 */
object MarkdownVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text.text)
        for (span in detectMarkdownSpans(text.text)) {
            val style = when (span.type) {
                MarkdownFormatType.BOLD          -> SpanStyle(fontWeight = FontWeight.Bold)
                MarkdownFormatType.ITALIC        -> SpanStyle(fontStyle = FontStyle.Italic)
                MarkdownFormatType.CODE          -> SpanStyle(fontFamily = FontFamily.Monospace)
                MarkdownFormatType.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                MarkdownFormatType.HIGHLIGHT     -> SpanStyle(background = Color(0xFFFFFF00))
            }
            builder.addStyle(style, span.start, span.end)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
