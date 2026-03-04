package com.gmaingret.outlinergod.search

/**
 * Parsed representation of a raw search query.
 *
 * @param ftsTerms The plain text terms to pass to FTS5 MATCH (may include trailing '*' for prefix match).
 * @param isCompleted Filter by completion status: true = completed only, false = incomplete only, null = no filter.
 * @param color Filter by color integer (1=red, 2=orange, 3=yellow, 4=green, 5=blue, 6=purple), null = no filter.
 * @param inNote When true, post-filter results to only those matching in the note field.
 * @param inTitle When true, post-filter results to only those matching in the content/title field.
 */
data class ParsedQuery(
    val ftsTerms: String,
    val isCompleted: Boolean?,
    val color: Int?,
    val inNote: Boolean,
    val inTitle: Boolean
)

/**
 * Parses a raw user search query string into structured filters and FTS5 terms.
 *
 * Supported operators (case-insensitive):
 *  - is:completed / is:not-completed  -> isCompleted filter
 *  - color:red/orange/yellow/green/blue/purple -> color filter (1-6)
 *  - in:note  -> inNote post-filter
 *  - in:title -> inTitle post-filter
 *
 * All remaining text (after operator extraction) becomes ftsTerms. A trailing '*' is appended
 * to the last word for prefix matching if ftsTerms is non-empty and does not already end with '*'.
 */
object SearchQueryParser {

    private val COLOR_MAP = mapOf(
        "red" to 1,
        "orange" to 2,
        "yellow" to 3,
        "green" to 4,
        "blue" to 5,
        "purple" to 6
    )

    fun parse(rawQuery: String): ParsedQuery {
        var remaining = rawQuery.trim()

        var isCompleted: Boolean? = null
        var color: Int? = null
        var inNote = false
        var inTitle = false

        // Extract is:completed / is:not-completed
        val isCompletedRegex = Regex("""is:(completed|not-completed)""", RegexOption.IGNORE_CASE)
        isCompletedRegex.find(remaining)?.let { match ->
            isCompleted = match.groupValues[1].lowercase() == "completed"
            remaining = remaining.removeRange(match.range).trim()
        }

        // Extract color:X
        val colorRegex = Regex("""color:(red|orange|yellow|green|blue|purple)""", RegexOption.IGNORE_CASE)
        colorRegex.find(remaining)?.let { match ->
            color = COLOR_MAP[match.groupValues[1].lowercase()]
            remaining = remaining.removeRange(match.range).trim()
        }

        // Extract in:note
        val inNoteRegex = Regex("""in:note""", RegexOption.IGNORE_CASE)
        inNoteRegex.find(remaining)?.let { match ->
            inNote = true
            remaining = remaining.removeRange(match.range).trim()
        }

        // Extract in:title
        val inTitleRegex = Regex("""in:title""", RegexOption.IGNORE_CASE)
        inTitleRegex.find(remaining)?.let { match ->
            inTitle = true
            remaining = remaining.removeRange(match.range).trim()
        }

        // Normalise remaining text: collapse extra whitespace
        val ftsBase = remaining.trim().replace(Regex("""\s+"""), " ")

        // Append '*' to last word for prefix matching
        val ftsTerms = if (ftsBase.isNotEmpty() && !ftsBase.endsWith('*')) {
            "$ftsBase*"
        } else {
            ftsBase
        }

        return ParsedQuery(
            ftsTerms = ftsTerms,
            isCompleted = isCompleted,
            color = color,
            inNote = inNote,
            inTitle = inTitle
        )
    }
}
