package com.darshan.splitsmart.ui.bill

import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.MemberShare
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testItem
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.split.AdjustmentKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BillDetailLogicTest {
    private val members = listOf(testMember("a", name = "Asha"), testMember("r", name = "Ravi"), testMember("m", name = "Meera"))
    private val items = listOf(testItem("i1", unitPaise = 50_000), testItem("i2", unitPaise = 30_000, sortOrder = 1))

    @Test
    fun `the receipt's extra lines are tax, tip and whatever makes up the printed total`() {
        val discounted = testBill(totalPaise = 85_000, taxPaise = 4_000, tipPaise = 6_000, items = items)
        assertThat(BillDetailLogic.extras(discounted)).containsExactly(
            ReceiptExtra.Tax(4_000), ReceiptExtra.Tip(6_000), ReceiptExtra.Adjustment(-5_000, AdjustmentKind.Discount),
        ).inOrder()
        val charged = testBill(totalPaise = 92_000, items = items)
        assertThat(BillDetailLogic.extras(charged)).containsExactly(ReceiptExtra.Adjustment(12_000, AdjustmentKind.Charges))
        val exact = testBill(totalPaise = 80_000, items = items)
        assertThat(BillDetailLogic.extras(exact)).isEmpty()
        // A bill typed in by hand has no lines: the total is not a "charge".
        assertThat(BillDetailLogic.extras(testBill(totalPaise = 50_000, items = emptyList()))).isEmpty()
    }

    @Test
    fun `each share says whether that person paid, gets money back or owes the payer`() {
        val bill = testBill(totalPaise = 90_000, items = emptyList(), paidByMemberId = "a", status = BillStatus.SPLIT).copy(
            split = BillSplit(SplitConfig(SplitMode.EQUAL, listOf("a", "r", "m")), listOf(MemberShare("a", 30_000, 0, 0, 0), MemberShare("r", 30_000, 0, 0, 0), MemberShare("m", 30_000, 0, 0, 0))),
        )
        val rows = BillDetailLogic.shareRows(BillDetail(bill, members))
        assertThat(rows.map { it.name }).containsExactly("Asha", "Ravi", "Meera").inOrder()
        assertThat(rows[0].relation).isEqualTo(ShareRelation.Paid(getsBackPaise = 60_000))
        assertThat(rows[1].relation).isEqualTo(ShareRelation.Owes("Asha"))
        assertThat(rows.sumOf { it.share.totalPaise }).isEqualTo(90_000)
    }

    @Test
    fun `without a payer nobody owes anyone yet, and a removed member still has a name`() {
        val bill = testBill(totalPaise = 10_000, items = emptyList(), status = BillStatus.SPLIT).copy(
            split = BillSplit(SplitConfig(SplitMode.EQUAL, listOf("a", "gone")), listOf(MemberShare("a", 5_000, 0, 0, 0), MemberShare("gone", 5_000, 0, 0, 0))),
        )
        val rows = BillDetailLogic.shareRows(BillDetail(bill, members), unknownName = "Former member")
        assertThat(rows.map { it.relation }).containsExactly(ShareRelation.None, ShareRelation.None)
        assertThat(rows[1].name).isEqualTo("Former member")
        assertThat(BillDetailLogic.shareRows(BillDetail(testBill(), members))).isEmpty()
    }
}
