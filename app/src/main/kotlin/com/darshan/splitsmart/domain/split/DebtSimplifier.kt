package com.darshan.splitsmart.domain.split

import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.Debt
import java.util.PriorityQueue

/**
 * Who owes whom. [balances] nets every split bill with a known payer (payer +total, each participant −share);
 * [simplify] turns the net balances into the minimal greedy set of transfers: the largest debtor always pays the
 * largest creditor, which needs at most n − 1 transfers and settles every balance exactly.
 */
object DebtSimplifier {
    private data class Entry(val memberId: String, var amount: Long)

    private val byAmountThenName = compareByDescending<Entry> { it.amount }.thenBy { it.memberId }

    fun simplify(balances: Map<String, Long>): List<Debt> {
        require(balances.values.sum() == 0L) { "balances must sum to zero" }
        val creditors = PriorityQueue(byAmountThenName).apply { balances.filter { it.value > 0 }.forEach { add(Entry(it.key, it.value)) } }
        val debtors = PriorityQueue(byAmountThenName).apply { balances.filter { it.value < 0 }.forEach { add(Entry(it.key, -it.value)) } }
        val debts = mutableListOf<Debt>()
        while (creditors.isNotEmpty() && debtors.isNotEmpty()) {
            val creditor = creditors.poll()
            val debtor = debtors.poll()
            val amount = minOf(creditor.amount, debtor.amount)
            debts += Debt(fromMemberId = debtor.memberId, toMemberId = creditor.memberId, amountPaise = amount)
            creditor.amount -= amount
            debtor.amount -= amount
            if (creditor.amount > 0) creditors.add(creditor)
            if (debtor.amount > 0) debtors.add(debtor)
        }
        return debts
    }

    /** Net balance per member over all split bills with a known payer (positive = is owed money). */
    fun balances(bills: List<Bill>): Map<String, Long> {
        val balances = linkedMapOf<String, Long>()
        for (bill in bills) {
            val split = bill.split ?: continue
            val payer = bill.paidByMemberId ?: continue
            balances[payer] = (balances[payer] ?: 0L) + bill.totalPaise
            for (share in split.shares) balances[share.memberId] = (balances[share.memberId] ?: 0L) - share.totalPaise
        }
        return balances
    }

    /** Bills that cannot be netted yet: not split, or nobody recorded as the payer. */
    fun unsettledCount(bills: List<Bill>): Int = bills.count { it.split == null || it.paidByMemberId == null }
}
