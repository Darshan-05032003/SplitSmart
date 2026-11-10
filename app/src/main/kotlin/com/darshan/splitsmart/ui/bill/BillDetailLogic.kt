package com.darshan.splitsmart.ui.bill

import androidx.compose.runtime.Immutable
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.MemberShare
import com.darshan.splitsmart.ui.split.AdjustmentKind
import com.darshan.splitsmart.ui.split.SplitLogic

/** A line of the receipt below the items: tax, tip, or what makes the lines add up to the printed total. */
sealed interface ReceiptExtra {
    val paise: Long

    data class Tax(override val paise: Long) : ReceiptExtra
    data class Tip(override val paise: Long) : ReceiptExtra
    data class Adjustment(override val paise: Long, val kind: AdjustmentKind) : ReceiptExtra
}

/** How one person stands on this bill. */
sealed interface ShareRelation {
    /** The payer: they paid the whole bill and get back everyone else's part. */
    data class Paid(val getsBackPaise: Long) : ShareRelation

    data class Owes(val payerName: String) : ShareRelation

    /** No payer recorded yet: nobody owes anyone. */
    data object None : ShareRelation
}

@Immutable
data class ShareRow(val memberId: String, val name: String, val share: MemberShare, val relation: ShareRelation)

/** Pure rules behind the bill screen. */
object BillDetailLogic {
    /** Tax, tip and the adjustment, in receipt order; nothing for a bill typed in without lines. */
    fun extras(bill: Bill): List<ReceiptExtra> = buildList {
        if (bill.taxPaise > 0) add(ReceiptExtra.Tax(bill.taxPaise))
        if (bill.tipPaise > 0) add(ReceiptExtra.Tip(bill.tipPaise))
        if (bill.items.isNotEmpty()) {
            val adjustment = bill.totalPaise - bill.itemsSubtotalPaise - bill.taxPaise - bill.tipPaise
            SplitLogic.adjustmentKind(adjustment)?.let { add(ReceiptExtra.Adjustment(adjustment, it)) }
        }
    }

    /** Every share in split order, with who owes the payer; empty when the bill is not split. */
    fun shareRows(detail: BillDetail, unknownName: String = ""): List<ShareRow> {
        val split = detail.bill.split ?: return emptyList()
        val names = detail.members.associate { it.id to it.name }
        val payer = detail.bill.paidByMemberId
        val payerName = payer?.let { names[it] ?: unknownName }
        return split.shares.map { share ->
            val relation = when {
                payer == null -> ShareRelation.None
                share.memberId == payer -> ShareRelation.Paid(detail.bill.totalPaise - share.totalPaise)
                else -> ShareRelation.Owes(payerName.orEmpty())
            }
            ShareRow(share.memberId, names[share.memberId] ?: unknownName, share, relation)
        }
    }
}
