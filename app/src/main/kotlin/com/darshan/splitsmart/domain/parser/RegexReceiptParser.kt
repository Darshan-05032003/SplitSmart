package com.darshan.splitsmart.domain.parser

import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.domain.model.ExtractedItem
import com.darshan.splitsmart.domain.model.ExtractedReceipt

/**
 * On-device fallback when the LLM is unavailable: a line-oriented parser for normalised receipt text
 * (`OcrTextNormalizer` output). Items are "name [qty] amount" lines; totals, taxes and tips are recognised by keyword.
 * It never throws and returns [EMPTY] when nothing looks like a receipt.
 */
object RegexReceiptParser {
    val EMPTY = ExtractedReceipt(emptyList(), 0, 0, 0)

    private val AMOUNT_AT_END = Regex("""(?<![\d.])(-?\d{1,9}(?:\.\d{1,2})?)\s*(?:/-|rs\.?|inr)?\s*$""", RegexOption.IGNORE_CASE)
    private val SKIP = Regex("""(?i)\b(gstin|fssai|tin|bill\s*no|invoice|receipt\s*no|order\s*(no|id)|token|table|date|time|ph|phone|mob|mobile|tel|cashier|server|thank|visit|welcome|discount|round\s*off|change|cash|card|upi|paid\s*by|tender)\b""")
    private val SUBTOTAL = Regex("""(?i)^\s*sub\s*-?\s*total\b""")
    private val TOTAL_GRAND = Regex("""(?i)^\s*(grand\s*total|net\s*(amount|total|payable)|total\s*(amount\s*)?(payable|due))\b""")
    private val TOTAL_PLAIN = Regex("""(?i)^\s*(total|bill\s*amount|amount\s*(payable|due|paid)|payable|net)\b""")
    private val TAX = Regex("""(?i)\b(cgst|sgst|igst|ugst|gst|vat|tax|cess|service\s*charge|svc|s\.c\.)\b""")
    private val TIP = Regex("""(?i)\b(tip|tips|gratuity)\b""")
    private val QTY_PREFIX = Regex("""^(\d{1,3})\s*[xX×*]\s*(.+)$""")
    private val QTY_SUFFIX = Regex("""^(.+?)\s*[xX×*]\s*(\d{1,3})$""")
    private val QTY_AT_RATE = Regex("""^(.+?)\s+(\d{1,3})\s*@\s*(\d{1,9}(?:\.\d{1,2})?)$""")
    private val QTY_RATE_COLUMNS = Regex("""^(.+?)\s+(\d{1,3})\s+(\d{1,9}(?:\.\d{1,2})?)$""")
    private val HAS_LETTER = Regex("""\p{L}""")
    private val TRAILING_PUNCT = Regex("""[\s\-:.,]+$""")

    fun parse(text: String): ExtractedReceipt {
        if (text.isBlank()) return EMPTY
        val items = mutableListOf<ExtractedItem>()
        var tax = 0L
        var tip = 0L
        var total: Long? = null
        var totalWeight = -1
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val match = AMOUNT_AT_END.find(line) ?: continue
            val amountText = match.groupValues[1]
            if (amountText.startsWith("-")) continue
            val amount = Money.parseInrToPaise(amountText) ?: continue
            val head = line.substring(0, match.range.first).replace(TRAILING_PUNCT, "").trim()
            when {
                SUBTOTAL.containsMatchIn(head) -> Unit
                TOTAL_GRAND.containsMatchIn(head) -> if (totalWeight <= 2) { total = amount; totalWeight = 2 }
                TOTAL_PLAIN.containsMatchIn(head) -> if (totalWeight <= 1) { total = amount; totalWeight = 1 }
                TAX.containsMatchIn(head) -> tax = (tax + amount).coerceAtMost(Money.MAX_PAISE)
                TIP.containsMatchIn(head) -> tip = (tip + amount).coerceAtMost(Money.MAX_PAISE)
                SKIP.containsMatchIn(head) -> Unit
                amount > 0 && HAS_LETTER.containsMatchIn(head) -> parseItem(head, amount)?.let(items::add)
            }
        }
        if (items.isEmpty() && total == null && tax == 0L && tip == 0L) return EMPTY
        val finalTotal = total ?: (items.sumOf { it.totalPaise } + tax + tip).coerceAtMost(Money.MAX_PAISE)
        return ExtractedReceipt(items, tax, tip, finalTotal)
    }

    private fun parseItem(head: String, lineTotal: Long): ExtractedItem? {
        var name = head
        var qty = 1
        QTY_AT_RATE.matchEntire(head)?.let { m -> name = m.groupValues[1]; qty = m.groupValues[2].toInt() }
            ?: QTY_RATE_COLUMNS.matchEntire(head)?.let { m ->
                val q = m.groupValues[2].toInt()
                val rate = Money.parseInrToPaise(m.groupValues[3])
                if (q in 1..999 && rate != null && rate * q == lineTotal) { name = m.groupValues[1]; qty = q }
            }
            ?: QTY_PREFIX.matchEntire(head)?.let { m -> qty = m.groupValues[1].toInt(); name = m.groupValues[2] }
            ?: QTY_SUFFIX.matchEntire(head)?.let { m -> name = m.groupValues[1]; qty = m.groupValues[2].toInt() }
        name = name.replace(TRAILING_PUNCT, "").trim().take(60)
        if (name.isEmpty() || !HAS_LETTER.containsMatchIn(name)) return null
        if (qty !in 1..999) qty = 1
        val unit = if (lineTotal % qty == 0L) lineTotal / qty else 0L
        return ExtractedItem(name, qty, unit, lineTotal)
    }
}
