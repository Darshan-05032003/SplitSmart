package com.darshan.splitsmart.domain.split

import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testMember
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SplitCheckTest {
    private val members = listOf(testMember("a", name = "Asha"), testMember("b", name = "Ravi"))

    private fun split(id: String, payer: String?, participants: List<String> = listOf("a", "b"), total: Long = 1_000): Bill {
        val config = SplitConfig(SplitMode.EQUAL, participants)
        val bill = testBill(id, totalPaise = total, items = emptyList(), paidByMemberId = payer, status = BillStatus.SPLIT)
        return bill.copy(split = BillSplit(config, SplitCalculator.split(bill, config).shares))
    }

    @Test
    fun `a split bill with a payer who is still here counts`() {
        val bill = split("b1", payer = "a")
        assertThat(SplitCheck.problem(bill, members)).isNull()
        assertThat(SplitCheck.countable(listOf(bill), members)).containsExactly(bill)
    }

    @Test
    fun `each reason a bill cannot move money yet is named`() {
        val draft = testBill("d1", items = emptyList())
        val draftWithPayer = testBill("d2", items = emptyList(), paidByMemberId = "a")
        // Room drops a removed member's share row, so the stored split no longer adds up and reads as none.
        val shareDropped = testBill("s1", items = emptyList(), paidByMemberId = "a", status = BillStatus.SPLIT)
        // A fake or a corrupt row: a share for someone who is not in the group any more.
        val strangerShare = split("s2", payer = "a", participants = listOf("a", "z"))
        val payerGone = split("p1", payer = "z")
        val payerCleared = split("p2", payer = null)
        assertThat(SplitCheck.problem(draft, members)).isEqualTo(BillProblem.NOT_SPLIT)
        assertThat(SplitCheck.problem(draftWithPayer, members)).isEqualTo(BillProblem.NOT_SPLIT)
        assertThat(SplitCheck.problem(testBill("n1", totalPaise = 1_000, items = emptyList()).copy(split = split("x", "a").split), members)).isEqualTo(BillProblem.NO_PAYER)
        assertThat(SplitCheck.problem(shareDropped, members)).isEqualTo(BillProblem.MEMBER_LEFT)
        assertThat(SplitCheck.problem(strangerShare, members)).isEqualTo(BillProblem.MEMBER_LEFT)
        assertThat(SplitCheck.problem(payerGone, members)).isEqualTo(BillProblem.PAYER_LEFT)
        assertThat(SplitCheck.problem(payerCleared, members)).isEqualTo(BillProblem.PAYER_LEFT)
        val all = listOf(draft, shareDropped, strangerShare, payerGone, payerCleared, split("ok", "b"))
        assertThat(SplitCheck.problems(all, members).map { it.bill.id }).containsExactly("d1", "s1", "s2", "p1", "p2").inOrder()
        assertThat(SplitCheck.countable(all, members).map { it.id }).containsExactly("ok")
    }

    @Test
    fun `a member who left with a zero share does not spoil the split`() {
        // By item, z had no lines: their share is zero, the money is still right.
        val bill = split("b1", payer = "a").let { b -> b.copy(split = b.split!!.copy(config = b.split!!.config.copy(participantIds = listOf("a", "b", "z")))) }
        assertThat(SplitCheck.problem(bill, members)).isNull()
    }
}
