package com.darshan.splitsmart.domain.split

import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.Member

/** Why a bill cannot move money yet. */
enum class BillProblem {
    /** Never split. */
    NOT_SPLIT,

    /** Split, but nobody is recorded as having paid it. */
    NO_PAYER,

    /** Whoever paid it has left the group. */
    PAYER_LEFT,

    /** Someone with a share left the group, so the shares no longer add up to the bill. */
    MEMBER_LEFT,
}

data class BillCheck(val bill: Bill, val problem: BillProblem)

/**
 * Checks each bill's split against the group as it is now. People can be removed after a bill was split: Room then drops their
 * share (the split stops adding up and reads as none) or clears the payer. Such a bill must not quietly vanish from settle-up,
 * so it is named with the reason and left out of the payments until someone splits it again.
 */
object SplitCheck {
    fun problem(bill: Bill, members: List<Member>): BillProblem? = problem(bill, members.mapTo(HashSet()) { it.id })

    fun problems(bills: List<Bill>, members: List<Member>): List<BillCheck> {
        val ids = members.mapTo(HashSet()) { it.id }
        return bills.mapNotNull { bill -> problem(bill, ids)?.let { BillCheck(bill, it) } }
    }

    /** The bills whose money moves: split, with a payer, and everyone in them still in the group. */
    fun countable(bills: List<Bill>, members: List<Member>): List<Bill> {
        val ids = members.mapTo(HashSet()) { it.id }
        return bills.filter { problem(it, ids) == null }
    }

    private fun problem(bill: Bill, ids: Set<String>): BillProblem? {
        val split = bill.split
        val payer = bill.paidByMemberId
        return when {
            split == null && bill.status == BillStatus.SPLIT -> BillProblem.MEMBER_LEFT
            split != null && split.shares.any { it.totalPaise != 0L && it.memberId !in ids } -> BillProblem.MEMBER_LEFT
            payer != null && payer !in ids -> BillProblem.PAYER_LEFT
            payer == null && bill.status == BillStatus.SPLIT -> BillProblem.PAYER_LEFT
            split == null -> BillProblem.NOT_SPLIT
            payer == null -> BillProblem.NO_PAYER
            else -> null
        }
    }
}
