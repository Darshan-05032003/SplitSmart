package com.darshan.splitsmart.ui.groupdetail

import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.domain.model.Debt
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testMember
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GroupDetailLogicTest {
    private val group = testGroup()

    @Test
    fun `the one primary action is the next useful step`() {
        val fresh = GroupDetail(group, members = emptyList(), bills = emptyList())
        assertThat(GroupDetailLogic.primaryAction(fresh)).isEqualTo(PrimaryAction.Scan)
        val owing = GroupDetail(group, listOf(testMember()), listOf(testBill()), debts = listOf(Debt("m2", "m1", 15_000)))
        assertThat(GroupDetailLogic.primaryAction(owing)).isEqualTo(PrimaryAction.SettleUp)
        val square = GroupDetail(group, listOf(testMember()), listOf(testBill()))
        assertThat(GroupDetailLogic.primaryAction(square)).isEqualTo(PrimaryAction.Scan)
    }

    @Test
    fun `the summary adds up what was spent and what is still to move`() {
        val detail = GroupDetail(
            group,
            listOf(testMember()),
            listOf(testBill(id = "a", totalPaise = 30_000), testBill(id = "b", totalPaise = 20_050)),
            debts = listOf(Debt("m2", "m1", 15_000), Debt("m3", "m1", 5_000)),
        )
        val summary = GroupDetailLogic.summary(detail)
        assertThat(summary.spentPaise).isEqualTo(50_050)
        assertThat(summary.outstandingPaise).isEqualTo(20_000)
        assertThat(summary.payments).isEqualTo(2)
    }

    @Test
    fun `thousands of maximum bills stop at the odometer's ceiling instead of overflowing`() {
        // 2,000 bills at the ₹1 crore cap add up past ₹99,99,99,999.99.
        val huge = GroupDetail(group, emptyList(), List(2_000) { testBill(id = "b$it", totalPaise = Money.MAX_PAISE, items = emptyList()) })
        assertThat(GroupDetailLogic.summary(huge).spentPaise).isEqualTo(GroupDetailLogic.MAX_SHOWN_PAISE)
        val debts = GroupDetail(group, emptyList(), emptyList(), debts = List(2_000) { Debt("a$it", "b", Money.MAX_PAISE) })
        assertThat(GroupDetailLogic.summary(debts).outstandingPaise).isEqualTo(GroupDetailLogic.MAX_SHOWN_PAISE)
    }
}
