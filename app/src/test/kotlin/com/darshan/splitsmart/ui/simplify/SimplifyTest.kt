package com.darshan.splitsmart.ui.simplify

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.MemberShare
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.split.DebtSimplifier
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.commitState
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.recompositionCycles
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.groupdetail.GroupDetail
import com.darshan.splitsmart.ui.groupdetail.GroupDetailCallbacks
import com.darshan.splitsmart.ui.groupdetail.GroupDetailScreen
import com.darshan.splitsmart.ui.groupdetail.GroupDetailTags
import com.darshan.splitsmart.ui.groupdetail.GroupDetailUiState
import com.google.common.truth.Truth.assertThat
import androidx.compose.runtime.mutableIntStateOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Fictional trip with a chain and a loop of IOUs, so simplifying saves payments. Shared with the screenshot tests. */
internal object SimplifyFixtures {
    val group = testGroup("goa", "Goa trip")
    val members = listOf(testMember("a", "goa", "Asha"), testMember("r", "goa", "Ravi"), testMember("m", "goa", "Meera"), testMember("d", "goa", "Darshan"))

    private fun bill(id: String, title: String, payer: String, shares: Map<String, Long>): Bill =
        testBill(id = id, groupId = "goa", title = title, totalPaise = shares.values.sum(), items = emptyList(), paidByMemberId = payer, status = BillStatus.SPLIT).copy(
            split = BillSplit(SplitConfig(SplitMode.EQUAL, shares.keys.toList()), shares.map { (m, p) -> MemberShare(m, p, 0, 0, 0) }),
        )

    val bills = listOf(
        bill("b1", "Beach shack dinner", "a", mapOf("a" to 100_00L, "r" to 300_00L)),
        bill("b2", "Scooter rent", "r", mapOf("r" to 100_00L, "m" to 300_00L)),
        bill("b3", "Ferry tickets", "m", mapOf("m" to 100_00L, "a" to 100_00L)),
        bill("b4", "Villa", "d", mapOf("d" to 100_00L, "a" to 100_00L, "r" to 100_00L, "m" to 100_00L)),
    )
    val detail = GroupDetail(group, members, bills, debts = DebtSimplifier.simplify(DebtSimplifier.balances(bills)))
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SimplifyTest {
    @get:Rule val rule = createComposeRule()

    private val noop = GroupDetailCallbacks(
        onBack = {}, onRetry = {}, onOpenBill = {}, onScan = {}, onSettleUp = {}, onDeleteGroup = {}, onAddMember = {}, onDismissMember = {},
        onMemberName = {}, onMemberVpa = {}, onSubmitMember = {}, onRemoveMember = {}, onAddBill = {}, onDismissBill = {}, onBillTitle = {},
        onBillAmount = {}, onBillPayer = {}, onSubmitBill = {},
    )

    private fun show(detail: GroupDetail = SimplifyFixtures.detail, reduceMotion: Boolean = true) =
        rule.setDs(reduceMotion = reduceMotion) { GroupDetailScreen(GroupDetailUiState(LoadState.Content(detail)), SsSnackbarState(), noop) }

    @Test
    fun `the group offers the simplification when it saves payments, and the sheet shows both pictures`() {
        show()
        rule.onNodeWithTag(GroupDetailTags.LIST).performScrollToKey("simplify")
        rule.onNodeWithText("2 payments instead of 6 IOUs").assertIsDisplayed()
        rule.onNodeWithTag(SimplifyTags.CARD).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(SimplifyTags.SHEET).assertIsDisplayed()
        rule.onNodeWithContentDescription("Bill by bill: 6 IOUs between 4 people. Simplified: 2 payments.").assertExists()
        // The payments are listed in words too, the way the group lists them.
        rule.onNodeWithText("Meera owes Darshan").assertExists()
        rule.onNodeWithText("Ravi owes Asha").assertExists()
        // Only the sheet's two: the screen behind an open sheet is hidden from TalkBack.
        rule.onAllNodesWithTag(SplitSmartTestTags.DEBT_ITEM).assertCountEquals(2)
    }

    @Test
    fun `a group where simplifying saves nothing shows no card`() {
        val one = SimplifyFixtures.bills.take(1)
        show(GroupDetail(SimplifyFixtures.group, SimplifyFixtures.members, one, debts = DebtSimplifier.simplify(DebtSimplifier.balances(one))))
        rule.onNodeWithTag(SimplifyTags.CARD).assertDoesNotExist()
        rule.onNodeWithText("Ravi owes Asha").assertExists()
    }

    @Test
    fun `the arrows collapse and redraw in draw, without recomposing every frame`() {
        rule.mainClock.autoAdvance = false
        val play = mutableIntStateOf(1)
        rule.setDs(reduceMotion = false) { DebtGraphView(DebtGraphLogic.graph(SimplifyFixtures.members, SimplifyFixtures.bills), playKey = play.intValue) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        val before = recompositionCycles()
        repeat(60) { rule.mainClock.advanceTimeByFrame() }
        assertThat(recompositionCycles() - before).isAtMost(3L)
        // Play again restarts it from the tangle.
        commitState { play.intValue = 2 }
        rule.mainClock.advanceTimeBy(3_000)
        rule.onNodeWithTag(SimplifyTags.GRAPH).assertExists()
    }

    @Test
    fun `play again replays the picture`() {
        show()
        rule.onNodeWithTag(GroupDetailTags.LIST).performScrollToKey("simplify")
        rule.onNodeWithTag(SimplifyTags.CARD).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(SimplifyTags.REPLAY).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(SimplifyTags.GRAPH).assertIsDisplayed()
    }

    @Test
    fun `200 people still make one picture of eight faces`() {
        val people = (1..200).map { testMember("p$it", "goa", "Guest $it") }
        val bills = (1..100).map { n ->
            testBill(id = "x$n", groupId = "goa", totalPaise = 3_000, items = emptyList(), paidByMemberId = "p$n", status = BillStatus.SPLIT).copy(
                split = BillSplit(SplitConfig(SplitMode.EQUAL, listOf("p$n", "p${n + 100}")), listOf(MemberShare("p$n", 1_500, 0, 0, 0), MemberShare("p${n + 100}", 1_500, 0, 0, 0))),
            )
        }
        rule.setDs(reduceMotion = true) { DebtGraphView(DebtGraphLogic.graph(people, bills), playKey = 1) }
        rule.waitForIdle()
        rule.onAllNodesWithTag(SimplifyTags.FACE).assertCountEquals(DebtGraphLogic.MAX_NODES)
    }
}
