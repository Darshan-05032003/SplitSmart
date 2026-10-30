package com.darshan.splitsmart.core

/**
 * Money is always `Long` paise (1 rupee = 100 paise). Never `Double`.
 * Formatting uses the Indian grouping (12,34,567.89); parsing is strict so hostile input is rejected rather than guessed.
 */
object Money {
    /** Hard cap: ₹1 crore. Anything larger is a scan/LLM error or an attack, never a dinner bill. */
    const val MAX_PAISE: Long = 1_000_000_000L

    private val INPUT = Regex("""^(?:₹|rs\.?|inr)?\s*(\d{1,3}(?:,\d{2,3})*|\d*)(?:\.(\d{1,2}))?$""", RegexOption.IGNORE_CASE)

    fun formatInr(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val abs = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(paise)
        val rupees = abs / 100
        val p = abs % 100
        return "$sign₹${groupIndian(rupees)}.${p.toString().padStart(2, '0')}"
    }

    /** `am=` value for `upi://pay`: plain rupees with exactly two decimals and no grouping. */
    fun toUpiAmount(paise: Long): String {
        require(paise >= 0) { "UPI amount must not be negative" }
        return "${paise / 100}.${(paise % 100).toString().padStart(2, '0')}"
    }

    /** Parses user input such as "1,234.50", "₹99", "Rs. 10.5" into paise. Returns null for anything malformed. */
    fun parseInrToPaise(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 24) return null
        val match = INPUT.matchEntire(trimmed) ?: return null
        val whole = match.groupValues[1].replace(",", "")
        val frac = match.groupValues[2]
        if (whole.isEmpty() && frac.isEmpty()) return null
        if (whole.length > 10) return null
        val rupees = if (whole.isEmpty()) 0L else whole.toLongOrNull() ?: return null
        val paise = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 10; else -> frac.toLong() }
        val total = rupees * 100 + paise
        return total.takeIf { it <= MAX_PAISE }
    }

    private fun groupIndian(rupees: Long): String {
        val s = rupees.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3)
        val grouped = rest.reversed().chunked(2).joinToString(",").reversed()
        return "$grouped,$last3"
    }
}
