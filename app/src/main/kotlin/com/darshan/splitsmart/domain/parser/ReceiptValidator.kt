package com.darshan.splitsmart.domain.parser

import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillItem
import com.darshan.splitsmart.domain.model.ExtractedItem
import com.darshan.splitsmart.domain.model.ExtractedReceipt
import com.darshan.splitsmart.domain.model.ExtractionWarning

data class ValidatedReceipt(val receipt: ExtractedReceipt, val warnings: Set<ExtractionWarning>)

/**
 * Makes an extracted receipt satisfy the domain invariants: drops negative / absurd amounts, fills names,
 * clamps quantities, repairs `qty × unit = total`, caps the item count, derives a missing total and flags a total
 * that does not add up. Returns null when nothing usable is left (the caller then falls back or asks the user).
 */
object ReceiptValidator {
    fun validate(input: ExtractedReceipt): ValidatedReceipt? {
        val warnings = mutableSetOf<ExtractionWarning>()
        val items = input.items.mapIndexedNotNull { index, item -> sanitizeItem(item, index + 1, warnings) }
            .let { if (it.size > Bill.MAX_ITEMS) { warnings += ExtractionWarning.ITEMS_CAPPED; it.take(Bill.MAX_ITEMS) } else it }
        val tax = sanitizeAmount(input.taxPaise, warnings)
        val tip = sanitizeAmount(input.tipPaise, warnings)
        var total = sanitizeAmount(input.totalPaise, warnings)
        val subtotal = items.sumOf { it.totalPaise }
        if (total == 0L) {
            total = subtotal + tax + tip
            if (total == 0L) return null
            if (total > Money.MAX_PAISE) return null
            warnings += ExtractionWarning.TOTAL_DERIVED
        } else if (items.isNotEmpty() && total != subtotal + tax + tip) {
            warnings += ExtractionWarning.SUM_MISMATCH
        }
        return ValidatedReceipt(ExtractedReceipt(items, tax, tip, total), warnings)
    }

    private fun sanitizeAmount(paise: Long, warnings: MutableSet<ExtractionWarning>): Long = when {
        paise < 0 -> { warnings += ExtractionWarning.NEGATIVE_DROPPED; 0 }
        paise > Money.MAX_PAISE -> { warnings += ExtractionWarning.ABSURD_DROPPED; 0 }
        else -> paise
    }

    private fun sanitizeItem(item: ExtractedItem, position: Int, warnings: MutableSet<ExtractionWarning>): ExtractedItem? {
        if (item.unitPaise < 0 || item.totalPaise < 0) { warnings += ExtractionWarning.NEGATIVE_DROPPED; return null }
        if (item.unitPaise > Money.MAX_PAISE || item.totalPaise > Money.MAX_PAISE) { warnings += ExtractionWarning.ABSURD_DROPPED; return null }
        val name = item.name.trim().ifEmpty { warnings += ExtractionWarning.NAME_FILLED; "Item $position" }
        val qty = when {
            item.qty < 1 -> { warnings += ExtractionWarning.QTY_FIXED; 1 }
            item.qty > BillItem.MAX_QTY -> { warnings += ExtractionWarning.QTY_FIXED; BillItem.MAX_QTY }
            else -> item.qty
        }
        var unit = item.unitPaise
        var total = item.totalPaise
        when {
            unit > 0 && total == 0L -> { total = unit * qty; warnings += ExtractionWarning.ITEM_MATH_FIXED }
            unit == 0L && total > 0 -> if (total % qty == 0L) unit = total / qty
            unit > 0 && unit * qty != total -> { unit = 0; warnings += ExtractionWarning.ITEM_MATH_FIXED }
        }
        if (total > Money.MAX_PAISE) { warnings += ExtractionWarning.ABSURD_DROPPED; return null }
        return ExtractedItem(name, qty, unit, total)
    }
}
