package com.darshan.splitsmart.ui.groupdetail

import androidx.compose.runtime.Immutable
import com.darshan.splitsmart.ui.designsystem.components.AmountFormat

/** The group screen's one main action: what to do next in this group. */
enum class PrimaryAction { Scan, SettleUp }

@Immutable
data class GroupSummaryFigures(val spentPaise: Long, val outstandingPaise: Long, val payments: Int)

object GroupDetailLogic {
    const val MAX_SHOWN_PAISE = AmountFormat.MAX_DISPLAY_PAISE

    /** Settle up when anyone owes anyone; otherwise the next bill is the useful step, so Scan. */
    fun primaryAction(detail: GroupDetail): PrimaryAction = if (detail.debts.isNotEmpty()) PrimaryAction.SettleUp else PrimaryAction.Scan

    /** Saturating sums: a corrupt row can make the figure wrong, never wrap it negative. */
    fun summary(detail: GroupDetail): GroupSummaryFigures = GroupSummaryFigures(
        spentPaise = saturatingSum(detail.bills.map { it.totalPaise }),
        outstandingPaise = saturatingSum(detail.debts.map { it.amountPaise }),
        payments = detail.debts.size,
    )

    private fun saturatingSum(values: List<Long>): Long {
        var total = 0L
        for (v in values) {
            total = if (v > 0 && total > MAX_SHOWN_PAISE - v) MAX_SHOWN_PAISE else (total + v.coerceAtLeast(0)).coerceAtMost(MAX_SHOWN_PAISE)
        }
        return total
    }
}
