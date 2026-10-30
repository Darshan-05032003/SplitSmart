package com.darshan.splitsmart.domain.split

import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillItem
import com.darshan.splitsmart.domain.model.MemberShare
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitError
import com.darshan.splitsmart.domain.model.SplitException
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.model.SplitResult
import kotlin.math.abs

/**
 * Pure split math. Every component (items, tax, tip, adjustment) is apportioned with [LargestRemainder], so the
 * per-member totals always sum exactly to the bill total.
 *
 * - EQUAL / BY_SHARE: the bill total, items, tax and tip are each distributed by the weights; the adjustment absorbs
 *   rounding and any difference between the printed total and items + tax + tip.
 * - BY_ITEM: each item is shared equally by its assignees (everyone when unassigned); tax, tip and the adjustment are
 *   allocated proportionally to each member's item subtotal.
 */
object SplitCalculator {
    fun split(bill: Bill, config: SplitConfig): SplitResult =
        splitAmounts(bill.items, bill.taxPaise, bill.tipPaise, bill.totalPaise, config)

    fun splitAmounts(items: List<BillItem>, taxPaise: Long, tipPaise: Long, totalPaise: Long, config: SplitConfig): SplitResult {
        val participants = config.participantIds
        if (participants.isEmpty()) throw SplitException(SplitError.NO_PARTICIPANTS)
        if (participants.toSet().size != participants.size) throw SplitException(SplitError.DUPLICATE_PARTICIPANT)
        val n = participants.size
        val subtotal = items.sumOf { it.totalPaise }
        val shares = when (config.mode) {
            SplitMode.EQUAL -> byWeights(participants, List(n) { 1L }, subtotal, taxPaise, tipPaise, totalPaise)
            SplitMode.BY_SHARE -> {
                val weights = participants.map { (config.shares[it] ?: 0).toLong() }
                if (weights.any { it < 0 }) throw SplitException(SplitError.NEGATIVE_SHARE)
                if (weights.all { it == 0L }) throw SplitException(SplitError.NO_SHARES)
                byWeights(participants, weights, subtotal, taxPaise, tipPaise, totalPaise)
            }
            SplitMode.BY_ITEM -> byItem(participants, items, config, subtotal, taxPaise, tipPaise, totalPaise)
        }
        return SplitResult(config.mode, shares)
    }

    private fun byWeights(participants: List<String>, weights: List<Long>, subtotal: Long, tax: Long, tip: Long, total: Long): List<MemberShare> {
        val totals = LargestRemainder.distribute(total, weights)
        val items = LargestRemainder.distribute(subtotal, weights)
        val taxes = LargestRemainder.distribute(tax, weights)
        val tips = LargestRemainder.distribute(tip, weights)
        return participants.indices.map { i ->
            MemberShare(participants[i], items[i], taxes[i], tips[i], adjustmentPaise = totals[i] - items[i] - taxes[i] - tips[i])
        }
    }

    private fun byItem(participants: List<String>, items: List<BillItem>, config: SplitConfig, subtotal: Long, tax: Long, tip: Long, total: Long): List<MemberShare> {
        val index = participants.withIndex().associate { it.value to it.index }
        val itemPaise = LongArray(participants.size)
        val everyone = participants.indices.toList()
        for (item in items) {
            val assignees = config.itemAssignments[item.id].orEmpty().mapNotNull { index[it] }.sorted().ifEmpty { everyone }
            val parts = LargestRemainder.distribute(item.totalPaise, List(assignees.size) { 1L })
            assignees.forEachIndexed { k, memberIndex -> itemPaise[memberIndex] += parts[k] }
        }
        val weights = itemPaise.toList().let { w -> if (w.all { it == 0L }) List(participants.size) { 1L } else w }
        val taxes = LargestRemainder.distribute(tax, weights)
        val tips = LargestRemainder.distribute(tip, weights)
        val difference = total - subtotal - tax - tip
        val adjustments = LargestRemainder.distribute(abs(difference), weights).map { if (difference < 0) -it else it }
        return participants.indices.map { i -> MemberShare(participants[i], itemPaise[i], taxes[i], tips[i], adjustments[i]) }
    }
}
