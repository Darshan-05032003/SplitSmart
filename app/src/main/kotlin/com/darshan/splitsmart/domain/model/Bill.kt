package com.darshan.splitsmart.domain.model

import com.darshan.splitsmart.core.Money
import java.time.Instant

enum class BillStatus { DRAFT, SPLIT, SETTLED }

/** Where the bill came from; drives analytics and the fallback label in the UI. */
enum class BillSource { MANUAL, SCAN_LLM, SCAN_REGEX }

data class BillItem(
    val id: String,
    val billId: String,
    val name: String,
    val qty: Int,
    /** Unit price in paise; 0 when the receipt only printed a line total. */
    val unitPaise: Long,
    val totalPaise: Long,
    val sortOrder: Int,
) {
    init {
        require(name.isNotBlank()) { "item name is blank" }
        require(qty in 1..MAX_QTY) { "qty out of range: $qty" }
        require(unitPaise in 0..Money.MAX_PAISE) { "unit price out of range: $unitPaise" }
        require(totalPaise in 0..Money.MAX_PAISE) { "item total out of range: $totalPaise" }
        require(unitPaise == 0L || totalPaise == unitPaise * qty) { "item total $totalPaise != $qty × $unitPaise" }
    }

    companion object {
        const val MAX_QTY = 999
    }
}

data class Bill(
    val id: String,
    val groupId: String,
    val title: String,
    val totalPaise: Long,
    val taxPaise: Long,
    val tipPaise: Long,
    val paidByMemberId: String?,
    val source: BillSource,
    val status: BillStatus,
    val createdAt: Instant,
    val items: List<BillItem>,
    /** Present once the bill has been split; shares always sum exactly to [totalPaise]. */
    val split: BillSplit? = null,
) {
    init {
        require(title.isNotBlank()) { "title is blank" }
        require(totalPaise in 0..Money.MAX_PAISE) { "total out of range: $totalPaise" }
        require(taxPaise in 0..Money.MAX_PAISE) { "tax out of range: $taxPaise" }
        require(tipPaise in 0..Money.MAX_PAISE) { "tip out of range: $tipPaise" }
        require(items.size <= MAX_ITEMS) { "too many items: ${items.size}" }
        if (split != null) {
            require(split.shares.isNotEmpty()) { "split without shares" }
            require(split.shares.map { it.memberId }.toSet().size == split.shares.size) { "duplicate member in split" }
            require(split.shares.sumOf { it.totalPaise } == totalPaise) { "shares ${split.shares.sumOf { it.totalPaise }} != total $totalPaise" }
        }
    }

    val itemsSubtotalPaise: Long get() = items.sumOf { it.totalPaise }

    companion object {
        const val MAX_ITEMS = 200
        const val MAX_TITLE_LENGTH = 80
    }
}
