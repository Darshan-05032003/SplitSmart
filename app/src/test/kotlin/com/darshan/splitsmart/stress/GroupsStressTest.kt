package com.darshan.splitsmart.stress

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.GroupSummary
import com.darshan.splitsmart.domain.model.MemberShare
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.usecase.AddMemberUseCase
import com.darshan.splitsmart.domain.usecase.CreateGroupUseCase
import com.darshan.splitsmart.domain.usecase.CreateManualBillUseCase
import com.darshan.splitsmart.testutil.FakeBillRepository
import com.darshan.splitsmart.testutil.FakeGroupRepository
import com.darshan.splitsmart.testutil.MainDispatcherRule
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.fixedClock
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testItem
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.recompositionCycles
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.groupdetail.GroupDetail
import com.darshan.splitsmart.ui.groupdetail.GroupDetailCallbacks
import com.darshan.splitsmart.ui.groupdetail.GroupDetailEvent
import com.darshan.splitsmart.ui.groupdetail.GroupDetailScreen
import com.darshan.splitsmart.ui.groupdetail.GroupDetailUiState
import com.darshan.splitsmart.ui.groupdetail.GroupDetailViewModel
import com.darshan.splitsmart.ui.groups.GroupCard
import com.darshan.splitsmart.ui.groups.GroupsCallbacks
import com.darshan.splitsmart.ui.groups.GroupsScreen
import com.darshan.splitsmart.ui.groups.GroupsTags
import com.darshan.splitsmart.ui.groups.GroupsUiState
import com.darshan.splitsmart.ui.groups.GroupsViewModel
import com.darshan.splitsmart.ui.groups.HomeOverview
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Load for the groups home and the group screen. Prints `STRESS [...]` lines for docs/LOAD_TEST_REPORT.md. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class GroupsStressTest {
    @get:Rule val rule = createComposeRule()
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val mainRule = MainDispatcherRule(dispatcher)

    private fun split(id: String, groupId: String, payer: String, other: String, total: Long, at: Instant) =
        testBill(id = id, groupId = groupId, totalPaise = total, items = listOf(testItem(id = "i$id", billId = id, unitPaise = total)), paidByMemberId = payer, createdAt = at)
            .copy(status = BillStatus.SPLIT, split = BillSplit(SplitConfig(SplitMode.EQUAL, listOf(payer, other)), listOf(MemberShare(payer, total / 2, 0, 0, 0), MemberShare(other, total - total / 2, 0, 0, 0))))

    @Test
    fun `10,000 groups with 40,000 members and 100,000 bills build their cards off the main thread in bounded time`() {
        val groups = List(10_000) { testGroup("g$it", "Group $it", createdAt = Instant.EPOCH.plusSeconds(it.toLong())) }
        val members = groups.flatMap { g -> List(4) { testMember("${g.id}-m$it", g.id, if (it == 0) "Darshan" else "Friend $it") } }
        val bills = ArrayList<Bill>(100_000)
        for (i in 0 until 100_000) {
            val g = i % 10_000
            bills += split("b$i", "g$g", "g$g-m${i % 4}", "g$g-m${(i + 1) % 4}", 1_000L + i % 977, Instant.EPOCH.plusSeconds(i.toLong()))
        }
        lateinit var cards: List<GroupCard>
        val ms = measureTimeMillis { cards = GroupsViewModel.buildCards(groups.map { GroupSummary(it, 4, 10) }, members, bills, "Darshan") }
        val overview = GroupsViewModel.overviewOf(cards, "Darshan")
        println("STRESS [home cards] 10,000 groups / 40,000 members / 100,000 bills -> ${cards.size} cards + overview in $ms ms (groupsWithYou ${overview.groupsWithYou})")
        assertThat(cards).hasSize(10_000)
        assertThat(overview.groupsWithYou).isEqualTo(10_000)
        assertThat(ms).isLessThan(10_000)
    }

    @Test
    fun `scrolling a 10,000-group home jumps without recomposing what is already on screen`() {
        val cards = List(10_000) { GroupCard(GroupSummary(testGroup("g$it", "Group $it"), 4, 3), listOf("Asha", "Ravi", "Meera", "Kabir"), outstandingPaise = 1_000L * it, yourBalancePaise = if (it % 2 == 0) 500L else -500L) }
        rule.mainClock.autoAdvance = false
        rule.setDs(reduceMotion = true) {
            GroupsScreen(GroupsUiState(LoadState.Content(cards), overview = HomeOverview("Asha", 1_000, 500, 10_000, 10_000, 10_000)), GroupsCallbacks())
        }
        rule.mainClock.advanceTimeBy(1_000)
        val list = rule.onAllNodes(hasScrollToIndexAction()).onFirst()
        val before = recompositionCycles()
        val ms = measureTimeMillis {
            for (jump in 0 until 300) {
                list.performScrollToIndex((jump * 7_919) % 10_000)
                rule.mainClock.advanceTimeByFrame()
            }
        }
        val cycles = recompositionCycles() - before
        val composed = rule.onAllNodesWithTag(SplitSmartTestTags.GROUP_ITEM).fetchSemanticsNodes().size
        println("STRESS [home scroll] 300 jumps over 10,000 cards in $ms ms; recomposer cycles $cycles; cards composed at the end $composed")
        assertThat(composed).isLessThan(12)
    }

    @Test
    fun `1,000 taps on a menu action between frames run it once`() {
        var scans = 0
        rule.mainClock.autoAdvance = false
        rule.setDs(reduceMotion = true) {
            GroupsScreen(GroupsUiState(LoadState.Content(listOf(GroupCard(GroupSummary(testGroup(), 1, 0))))), GroupsCallbacks(onScan = { scans++ }))
        }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag(SplitSmartTestTags.FAB_ADD_GROUP).performClick()
        rule.mainClock.advanceTimeBy(500)
        var landed = 0
        val ms = measureTimeMillis {
            while (landed < 1_000 && rule.onAllNodesWithTag(GroupsTags.MENU_SCAN).fetchSemanticsNodes().isNotEmpty()) {
                rule.onNodeWithTag(GroupsTags.MENU_SCAN).performClick()
                landed++
            }
        }
        println("STRESS [fab menu] $landed taps landed on Scan before it left -> $scans scan in $ms ms")
        assertThat(scans).isEqualTo(1)
    }

    @Test
    fun `1,000 archive and undo cycles through the view model leave one consistent group and no stuck writes`() = runTest(dispatcher) {
        val repo = FakeGroupRepository()
        repo.groups.value = listOf(testGroup("goa", "Goa trip"))
        val vm = GroupsViewModel(repo, FakeBillRepository(), com.darshan.splitsmart.data.settings.InMemoryProfileRepository(), CreateGroupUseCase(repo), TestDispatcherProvider(dispatcher))
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        var archived = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { archived++ } }
        dispatcher.scheduler.advanceUntilIdle()
        val ms = measureTimeMillis {
            repeat(1_000) {
                repeat(3) { vm.archive("goa") } // a swipe and two stray taps
                dispatcher.scheduler.advanceUntilIdle()
                vm.unarchive("goa")
                dispatcher.scheduler.advanceUntilIdle()
            }
        }
        val state = vm.uiState.value
        println("STRESS [archive/undo] 1,000 cycles (3,000 archive requests) -> $archived archive events in $ms ms; archived now ${state.archived.size}")
        assertThat(archived).isEqualTo(1_000)
        assertThat(repo.groups.value.single().isArchived).isFalse()
        assertThat((state.groups as LoadState.Content).data.map { it.group.id }).containsExactly("goa")
        collector.cancel()
    }

    @Test
    fun `200 members on the group screen compose a screenful of faces`() {
        val members = List(200) { testMember("m$it", "goa", "Member $it", vpa = if (it % 3 == 0) "m$it@upi" else null) }
        val detail = GroupDetail(testGroup("goa", "Goa trip"), members, List(500) { testBill(id = "b$it", groupId = "goa", title = "Bill $it") })
        rule.mainClock.autoAdvance = false
        var layoutMs = 0L
        layoutMs = measureTimeMillis {
            rule.setDs(reduceMotion = true) { GroupDetailScreen(GroupDetailUiState(LoadState.Content(detail)), SsSnackbarState(), noop) }
            rule.mainClock.advanceTimeBy(500)
        }
        val faces = rule.onAllNodesWithTag(SplitSmartTestTags.MEMBER_ITEM).fetchSemanticsNodes().size
        val bills = rule.onAllNodesWithTag(SplitSmartTestTags.BILL_ITEM).fetchSemanticsNodes().size
        println("STRESS [group screen] 200 members + 500 bills -> $faces faces and $bills bill rows composed; first frame in $layoutMs ms")
        assertThat(faces).isLessThan(12)
        assertThat(bills).isLessThan(10)
    }

    @Test
    fun `1,000 delete confirmations through the view model delete once`() = runTest(dispatcher) {
        val repo = object : FakeGroupRepository() {
            var deletes = 0
            override suspend fun deleteGroup(groupId: String) {
                deletes++
                super.deleteGroup(groupId)
            }
        }
        repo.groups.value = listOf(testGroup())
        val bills = FakeBillRepository()
        val vm = GroupDetailViewModel(SavedStateHandle(mapOf("groupId" to "g1")), repo, bills, AddMemberUseCase(repo), CreateManualBillUseCase(bills, fixedClock), TestDispatcherProvider(dispatcher))
        val events = mutableListOf<GroupDetailEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        val ms = measureTimeMillis {
            repeat(1_000) { vm.deleteGroup() }
            dispatcher.scheduler.advanceUntilIdle()
        }
        println("STRESS [delete] 1,000 confirmations -> ${repo.deletes} delete, ${events.size} event in $ms ms")
        assertThat(repo.deletes).isEqualTo(1)
        assertThat(events).containsExactly(GroupDetailEvent.GroupDeleted)
    }

    private val noop = GroupDetailCallbacks(
        onBack = {}, onRetry = {}, onOpenBill = {}, onScan = {}, onSettleUp = {}, onDeleteGroup = {}, onAddMember = {}, onDismissMember = {},
        onMemberName = {}, onMemberVpa = {}, onSubmitMember = {}, onRemoveMember = {}, onAddBill = {}, onDismissBill = {}, onBillTitle = {},
        onBillAmount = {}, onBillPayer = {}, onSubmitBill = {},
    )
}
