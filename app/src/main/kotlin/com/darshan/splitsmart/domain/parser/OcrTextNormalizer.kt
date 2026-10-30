package com.darshan.splitsmart.domain.parser

/**
 * Cleans raw OCR output before it reaches the LLM or the regex parser: strips invisible/bidi characters, collapses
 * whitespace, normalises Indian currency markers and repairs the classic OCR confusions (O→0, l/I→1, S→5) but only
 * inside tokens that already contain a digit, so words such as "Oil" are never touched. Idempotent and length-capped.
 */
object OcrTextNormalizer {
    const val MAX_CHARS = 20_000

    private val INVISIBLE = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u2066-\\u2069\\uFEFF]")
    private val WHITESPACE = Regex("[\\t\\p{Zs}]+")
    private val CURRENCY = Regex("(?i)(?:₹|₨|\\brs\\.?|\\binr)\\s*(?=\\d)")
    private val RUPEE_SUFFIX = Regex("(?<=\\d)\\s*/-")
    private val DECIMAL_COMMA = Regex("(?<=\\d),(\\d{2})(?![\\d,])")
    private val THOUSANDS_COMMA = Regex("(?<=\\d),(?=\\d)")
    private val SPACED_DOT_BEFORE = Regex("(?<=\\d) \\.(?=\\d{2}\\b)")
    private val SPACED_DOT_AFTER = Regex("(?<=\\d)\\. (?=\\d{2}\\b)")
    private val NUMERIC_TOKEN = Regex("^[0-9OoIlS.]+$")
    private val HAS_DIGIT = Regex("\\d")
    private val HAS_CONTENT = Regex("[\\p{L}\\p{N}]")

    fun normalize(raw: String): String {
        val cleaned = INVISIBLE.replace(raw.take(MAX_CHARS * 2), "")
        val lines = cleaned.split('\n', '\r').asSequence()
            .map { line -> WHITESPACE.replace(line, " ").trim() }
            .filter { HAS_CONTENT.containsMatchIn(it) }
            .map(::normalizeLine)
            .filter { it.isNotEmpty() }
        return lines.joinToString("\n").take(MAX_CHARS)
    }

    private fun normalizeLine(line: String): String {
        var s = CURRENCY.replace(line, "")
        s = RUPEE_SUFFIX.replace(s, "")
        s = SPACED_DOT_BEFORE.replace(s, ".")
        s = SPACED_DOT_AFTER.replace(s, ".")
        s = DECIMAL_COMMA.replace(s, ".$1")
        s = THOUSANDS_COMMA.replace(s, "")
        return s.split(' ').joinToString(" ") { token ->
            if (NUMERIC_TOKEN.matches(token) && HAS_DIGIT.containsMatchIn(token)) fixDigits(token) else token
        }.trim()
    }

    private fun fixDigits(token: String): String = buildString(token.length) {
        for (c in token) append(
            when (c) {
                'O', 'o' -> '0'
                'I', 'l' -> '1'
                'S' -> '5'
                else -> c
            },
        )
    }
}
