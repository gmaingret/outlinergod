package com.gmaingret.outlinergod.ui.common

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
import com.gmaingret.outlinergod.prototype.MarkdownFormatType
import com.gmaingret.outlinergod.prototype.detectMarkdownSpans

/**
 * [VisualTransformation] that applies live inline Markdown styling.
 *
 * Phase 1 behaviour: markers remain visible, cursor is always correct.
 * Delegates span detection to the prototype pure function [detectMarkdownSpans].
 */
object MarkdownVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text.text)
        for (span in detectMarkdownSpans(text.text)) {
            val style = when (span.type) {
                MarkdownFormatType.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                MarkdownFormatType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                MarkdownFormatType.CODE -> SpanStyle(fontFamily = FontFamily.Monospace)
                MarkdownFormatType.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                MarkdownFormatType.HIGHLIGHT -> SpanStyle(background = Color(0xFFFFFF00))
            }
            builder.addStyle(style, span.start, span.end)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
