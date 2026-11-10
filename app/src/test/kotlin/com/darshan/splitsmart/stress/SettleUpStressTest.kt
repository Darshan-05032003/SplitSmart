package com.darshan.splitsmart.stress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.MemberShare
import com.darshan.splitsmart.domain.model.Settlement
import com.darshan.splitsmart.domain.model.SettlementMethod
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.split.DebtSimplifier
import com.darshan.splitsmart.domain.split.SplitCheck
import com.darshan.splitsmart.testutil.FakeBillRepository
import com.darshan.splitsmart.testutil.FakeGroupRepository
import com.darshan.splitsmart.testutil.FakeSettlementRepository
import com.darshan.splitsmart.testutil.FakeUpiAppLauncher
import com.darshan.splitsmart.testutil.MainDispatcherRule
import com.darshan.splitsmart.testutil.RecordingAnalyticsTracker
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.assertInside
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.recompositionCycles
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.history.HistoryContent
import com.darshan.splitsmart.ui.history.HistoryLogic
import com.darshan.splitsmart.ui.history.HistoryScreen
import com.darshan.splitsmart.ui.history.HistoryTags
import com.darshan.splitsmart.ui.history.HistoryUiState
import com.darshan.splitsmart.ui.history.InsightsLogic
import com.darshan.splitsmart.ui.history.HistoryCallbacks
import com.darshan.splitsmart.ui.settleup.SettleUpCallbacks
import com.darshan.splitsmart.ui.settleup.SettleUpContent
import com.darshan.splitsmart.ui.settleup.SettleUpItem
import com.darshan.splitsmart.ui.settleup.SettleUpLogic
import com.darshan.splitsmart.ui.settleup.SettleUpScreen
import com.darshan.splitsmart.ui.settleup.SettleUpTags
import com.darshan.splitsmart.ui.settleup.SettleUpUiState
import com.darshan.splitsmart.ui.settleup.SettleUpViewModel
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Load for settle-up, the settled payments, the history and the insights. Prints `STRESS [...]` lines for docs/LOAD_TEST_REPORT.md. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SettleUpStressTest {
    @get:Rule val rule = createComposeRule()
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val mainRule = MainDispatcherRule(dispatcher)

    private val people = (1..200).map { testMember("p$it", name = "Guest $it", vpa = "guest$it@okfake") }

    /** Random split bills between [people]; every share is exact, so the group's balances always sum to zero. */
    private fun bills(count: Int, seed: Int, members: List<String> = people.map { it.id }): List<Bill> {
        val random = Random(seed)
        return (1..count).map { n ->
            val payer = members[random.nextInt(members.size)]
            val sharers = (1..random.nextInt(2, 6)).map { members[random.nextInt(members.size)] }.distinct()
            val each = random.nextLong(100, 50_000)
            testBill(id = "b$n", title = TITLES[n % TITLES.size], totalPaise = each * sharers.size, items = emptyList(), paidByMemberId = payer, status = BillStatus.SPLIT, createdAt = Instant.parse("2026-11-01T00:00:00Z").minusSeconds(n * 3_600L))
                .copy(split = BillSplit(SplitConfig(SplitMode.EQUAL, sharers), sharers.map { MemberShare(it, each, 0, 0, 0) }))
        }
    }

    @Test
    fun `200 people settle every payment through the view model until all square`() = runTest(dispatcher) {
        val groups = FakeGroupRepository().apply {
            this.groups.value = listOf(testGroup())
            members.value = people
        }
        val billRepo = FakeBillRepository().apply { this.bills.value = bills(2_000, seed = 3) }
        val settlements = FakeSettlementRepository()
        val launcher = FakeUpiAppLauncher()
        val vm = SettleUpViewModel(SavedStateHandle(mapOf("groupId" to "g1")), groups, billRepo, settlements, launcher, RecordingAnalyticsTracker(), TestDispatcherProvider(dispatcher))
        var payments = 0
        var square = false
        val ms = measureTimeMillis {
            vm.uiState.test {
                var state = awaitItem()
                while (state.content !is LoadState.Content) state = awaitItem()
                payments = (state.content as LoadState.Content).data.items.size
                var latest = state
                while (true) {
                    dispatcher.scheduler.advanceUntilIdle()
                    latest = runCatching { expectMostRecentItem() }.getOrDefault(latest)
                    val content = (latest.content as LoadState.Content).data
                    if (content.items.isEmpty()) {
                        square = content.allSquare && latest.settled?.allSquare == true
                        break
                    }
                    val next = content.items.first()
                    if (next.link != null) {
                        vm.pay(next)
                        vm.onResumed()
                    } else {
                        vm.markPaid(next)
                    }
                    vm.confirmPaid()
                }
                cancelAndIgnoreRemainingEvents()
            }
        }
        println("STRESS [settle-all] 200 people, 2,000 bills: $payments payments settled one by one in ${ms}ms (${settlements.recordCalls} records, ${launcher.opened.size} UPI links opened), all square = $square")
        assertThat(square).isTrue()
        assertThat(settlements.recordCalls).isEqualTo(payments)
        assertThat(payments).isAtMost(199)
        // Nothing keeps running after the last payment: the virtual clock has no work left.
        val before = dispatcher.scheduler.currentTime
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(dispatcher.scheduler.currentTime - before).isAtMost(5_000L)
    }

    @Test
    fun `1,000 mark-and-undo cycles leave no payment behind`() = runTest(dispatcher) {
        val two = listOf(people[0], people[1])
        val groups = FakeGroupRepository().apply {
            this.groups.value = listOf(testGroup())
            members.value = two
        }
        val billRepo = FakeBillRepository().apply { this.bills.value = bills(10, seed = 5, members = two.map { it.id }) }
        val settlements = FakeSettlementRepository()
        val vm = SettleUpViewModel(SavedStateHandle(mapOf("groupId" to "g1")), groups, billRepo, settlements, FakeUpiAppLauncher(), RecordingAnalyticsTracker(), TestDispatcherProvider(dispatcher))
        val ms = measureTimeMillis {
            vm.uiState.test {
                var state = awaitItem()
                while (state.content !is LoadState.Content) state = awaitItem()
                val item = (state.content as LoadState.Content).data.items.single()
                repeat(1_000) {
                    vm.markPaid(item)
                    vm.confirmPaid()
                    dispatcher.scheduler.advanceUntilIdle()
                    vm.undo(settlements.settlements.value.single().id)
                    dispatcher.scheduler.advanceUntilIdle()
                }
                cancelAndIgnoreRemainingEvents()
            }
        }
        println("STRESS [mark-undo] 1,000 mark + undo cycles in ${ms}ms: ${settlements.recordCalls} records, ${settlements.settlements.value.size} left")
        assertThat(settlements.recordCalls).isEqualTo(1_000)
        assertThat(settlements.settlements.value).isEmpty()
    }

    @Test
    fun `balances with 10,000 bills and 10,000 settled payments across 200 people`() {
        val bills = bills(10_000, seed = 7)
        val random = Random(8)
        val paid = (1..10_000).map { n ->
            val from = people[random.nextInt(200)]
            var to = people[random.nextInt(200)]
            if (to == from) to = people[(people.indexOf(from) + 1) % 200]
            Settlement("s$n", "g1", from.id, to.id, from.name, to.name, random.nextLong(100, 20_000), SettlementMethod.UPI_APP, Instant.EPOCH.plusSeconds(n.toLong()))
        }
        var debts = SettleUpLogic.debts(bills, paid)
        val ms = measureTimeMillis { repeat(10) { debts = SettleUpLogic.debts(bills, paid) } }
        val check = measureTimeMillis { repeat(10) { SplitCheck.countable(bills, people) } }
        val balances = DebtSimplifier.balances(bills, paid)
        println("STRESS [settle-balances] 10,000 bills + 10,000 payments, 200 people: ${debts.size} payments, ${ms / 10} ms per pass; split check ${check / 10} ms per pass")
        assertThat(balances.values.sum()).isEqualTo(0L)
        assertThat(debts.size).isAtMost(199)
        val settled = balances.toMutableMap()
        debts.forEach { settled[it.fromMemberId] = settled[it.fromMemberId]!! + it.amountPaise; settled[it.toMemberId] = settled[it.toMemberId]!! - it.amountPaise }
        assertThat(settled.values.all { it == 0L }).isTrue()
    }

    @Test
    fun `the largest amount fits the hero and a card at 1_3x on a narrow phone`() {
        val max = com.darshan.splitsmart.ui.designsystem.components.AmountFormat.MAX_DISPLAY_PAISE
        val item = SettleUpItem(com.darshan.splitsmart.domain.model.Debt("p1", "p2", max), people[0], people[1].copy(name = "‮Guest with a very long name that goes on and on 🎉🎉"), null)
        val state = SettleUpUiState(LoadState.Content(SettleUpContent(testGroup(), listOf(item), countedBills = 1)))
        rule.setDs(fontScale = 1.3f, reduceMotion = true) {
            Box(Modifier.width(com.darshan.splitsmart.ui.designsystem.tokens.Sizes.debtGraph + com.darshan.splitsmart.ui.designsystem.tokens.Spacing.md).testTag("narrow")) {
                SettleUpScreen(state, SsSnackbarState(), SettleUpCallbacks({}, {}, {}, {}, {}, {}, {}, {}, {}))
            }
        }
        rule.waitForIdle()
        val card = rule.onNodeWithTag(SplitSmartTestTags.SETTLE_ITEM)
        rule.onNode(hasContentDescription("₹99,99,99,999.99") and hasAnyAncestor(hasTestTag(SplitSmartTestTags.SETTLE_ITEM))).assertInside(card)
        rule.onNode(hasContentDescription("₹99,99,99,999.99") and hasAnyAncestor(hasTestTag(SettleUpTags.HERO))).assertInside(rule.onNodeWithTag(SettleUpTags.HERO))
        rule.onNodeWithTag(SettleUpTags.MARK_PAID).assertInside(card)
        assertThat(rule.onAllNodes(androidx.compose.ui.test.hasText("\u202E", substring = true)).fetchSemanticsNodes()).isEmpty()
        assertThat(rule.onAllNodes(androidx.compose.ui.test.hasText("Guest with a very long name", substring = true)).fetchSemanticsNodes()).isNotEmpty()
        println("STRESS [settle-max] ₹99,99,99,999.99 at 1.3x in a 316dp column: inside the hero and the card; bidi override stripped from the name")
    }

    @Test
    fun `history - 10,000 bills and 10,000 payments, insights over 100,000 bills`() {
        val bills = bills(10_000, seed = 13)
        val random = Random(14)
        val paid = (1..10_000).map { n ->
            Settlement("s$n", "g1", people[n % 200].id, people[(n + 1) % 200].id, people[n % 200].name, people[(n + 1) % 200].name, random.nextLong(100, 20_000),
                if (n % 2 == 0) SettlementMethod.UPI_APP else SettlementMethod.OTHER, Instant.parse("2026-11-01T00:00:00Z").minusSeconds(n * 3_000L))
        }
        var months = HistoryLogic.timeline(bills, paid, people, ZoneOffset.UTC)
        val timelineMs = measureTimeMillis { repeat(5) { months = HistoryLogic.timeline(bills, paid, people, ZoneOffset.UTC) } } / 5
        val many = bills(100_000, seed = 15)
        var insights = InsightsLogic.of(many, people, paid)
        val insightsMs = measureTimeMillis { repeat(3) { insights = InsightsLogic.of(many, people, paid) } } / 3
        val rows = months.sumOf { it.entries.size }
        assertThat(rows).isEqualTo(20_000)
        assertThat(insights.billCount).isEqualTo(100_000)
        assertThat(insights.spentPaise).isEqualTo(many.sumOf { it.totalPaise })
        val content = HistoryContent(testGroup(), months, InsightsLogic.of(bills, people, paid))
        val first = measureTimeMillis {
            rule.setDs(reduceMotion = true) { HistoryScreen(HistoryUiState(LoadState.Content(content)), SsSnackbarState(), HistoryCallbacks({})) }
            rule.waitForIdle()
        }
        val before = recompositionCycles()
        val random2 = Random(2)
        val total = rows + months.size + 2
        val jumps = measureTimeMillis { repeat(300) { rule.onNodeWithTag(HistoryTags.LIST).performScrollToIndex(random2.nextInt(total)) } }
        val cycles = recompositionCycles() - before
        println(
            "STRESS [history] $rows rows in ${months.size} months: timeline ${timelineMs}ms, first frame ${first}ms, 300 jumps ${jumps}ms, $cycles recomposer cycles; " +
                "insights over 100,000 bills ${insightsMs}ms (${insights.topCategories.size} categories, ${insights.topPayers.size} payers)",
        )
    }

    private companion object {
        val TITLES = listOf("Dinner", "Taxi", "Groceries", "Hotel", "Coffee", "Museum tickets", "Lunch", "Fuel", "Electricity", "Mystery box")
    }
}
