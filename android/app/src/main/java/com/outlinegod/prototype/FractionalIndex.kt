package com.outlinegod.prototype

/**
 * Pure-Kotlin fractional-indexing implementation (Rocicorp-compatible algorithm).
 *
 * Every key has the form "a" + fractional-digits where digits come from BASE62.
 * Keys compare correctly with plain string (lexicographic) comparison.
 */
object FractionalIndex {

    // 62-char ordered alphabet: digits → uppercase → lowercase
    const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private val MID_CHAR = DIGITS[DIGITS.length / 2] // index 31 → 'P'

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns a key K such that before < K < after (lexicographically).
     * Either bound may be null (meaning "no lower/upper bound").
     */
    fun generateKeyBetween(before: String?, after: String?): String {
        return when {
            before == null && after == null -> "a$MID_CHAR"
            before == null -> keyBefore(after!!)
            after == null -> keyAfter(before)
            else -> {
                require(before < after) { "Keys out of order: $before >= $after" }
                keyBetween(before, after)
            }
        }
    }

    /**
     * Generate [n] evenly-distributed keys between [before] and [after].
     */
    fun generateNKeysBetween(before: String?, after: String?, n: Int): List<String> {
        if (n == 0) return emptyList()
        if (n == 1) return listOf(generateKeyBetween(before, after))
        val result = mutableListOf<String>()
        var lo = before
        for (i in 0 until n) {
            val hi = if (i == n - 1) after else null
            val key = generateKeyBetween(lo, hi)
            result.add(key)
            lo = key
        }
        return result
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    /** A key strictly less than [b]. */
    private fun keyBefore(b: String): String {
        val bFrac = b.drop(1)
        return "a${midpointStr("", bFrac)}"
    }

    /** A key strictly greater than [a]. */
    private fun keyAfter(a: String): String {
        val aFrac = a.drop(1)
        return "a${aFrac}$MID_CHAR"
    }

    /** A key strictly between [a] and [b] (both must be valid keys, a < b). */
    private fun keyBetween(a: String, b: String): String {
        val aFrac = a.drop(1)
        val bFrac = b.drop(1)
        return "a${midpointStr(aFrac, bFrac)}"
    }

    /**
     * Compute the lexicographic midpoint between fractional strings [a] and [b].
     * Both strings use characters from DIGITS.
     * Contract: result R satisfies a ≤ R < b (or a ≤ R when b is null).
     */
    internal fun midpointStr(a: String, b: String?): String {
        if (b != null && a >= b) {
            throw IllegalArgumentException("midpointStr: a=$a must be < b=$b")
        }
        val result = StringBuilder()
        var i = 0
        while (true) {
            val lo = if (i < a.length) DIGITS.indexOf(a[i]) else 0
            val hi = when {
                b == null -> DIGITS.length          // open upper bound
                i < b.length -> DIGITS.indexOf(b[i])
                else -> DIGITS.length               // b exhausted → treat as max+1
            }
            val diff = hi - lo
            when {
                diff > 1 -> {
                    // Room for a midpoint character at this position
                    result.append(DIGITS[(lo + hi) / 2])
                    return result.toString()
                }
                diff == 0 -> {
                    // Same digit — copy and go deeper
                    result.append(DIGITS[lo])
                    i++
                }
                diff == 1 -> {
                    // Adjacent digits: take lo, then append something > a[i+1:]
                    result.append(DIGITS[lo])
                    val aSuffix = if (i + 1 < a.length) a.substring(i + 1) else ""
                    result.append(aSuffix)
                    result.append(MID_CHAR)
                    return result.toString()
                }
                else -> throw IllegalStateException(
                    "hi < lo at i=$i: lo=$lo hi=$hi a=$a b=$b"
                )
            }
        }
    }
}
