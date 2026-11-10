package com.darshan.splitsmart.stress

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.MemberShare
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.split.SplitCalculator
import com.darshan.splitsmart.domain.usecase.SaveSplitUseCase
import com.darshan.splitsmart.testutil.FakeBillRepository
import com.darshan.splitsmart.testutil.FakeGroupRepository
import com.darshan.splitsmart.testutil.MainDispatcherRule
import com.darshan.splitsmart.testutil.RecordingAnalyticsTracker
import com.darshan.splitsmart.testutil.RecordingCrashReporter
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testItem
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.bill.BillDetail
import com.darshan.splitsmart.ui.bill.BillDetailScreen
import com.darshan.splitsmart.ui.bill.BillDetailTags
import com.darshan.splitsmart.ui.bill.BillDetailUiState
import com.darshan.splitsmart.ui.bill.BillDetailViewModel
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.effects.HapticEvent
import com.darshan.splitsmart.ui.designsystem.effects.UiEffect
import com.darshan.splitsmart.ui.designsystem.recompositionCycles
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.simplify.DebtGraphLogic
import com.darshan.splitsmart.ui.split.DonutLogic
import com.darshan.splitsmart.ui.split.SplitCallbacks
import com.darshan.splitsmart.ui.split.SplitEditor
import com.darshan.splitsmart.ui.split.SplitLogic
import com.darshan.splitsmart.ui.split.SplitModeOption
import com.darshan.splitsmart.ui.split.SplitScreen
import com.darshan.splitsmart.ui.split.SplitTags
import com.darshan.splitsmart.ui.split.SplitUiState
import com.darshan.splitsmart.ui.split.SplitViewModel
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Load for the split screen, the bill screen and the simplify picture. Prints `STRESS [...]` lines for docs/LOAD_TEST_REPORT.md. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SplitUiStressTest {
    @get:Rule val rule = createComposeRule()
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val mainRule = MainDispatcherRule(dispatcher)
    private val dispatchers = TestDispatcherProvider(dispatcher)

    private val people = (1..200).map { testMember("p$it", name = "Guest $it") }
    private val bigBill = testBill(
        title = "Wedding caterer",
        totalPaise = Money.MAX_PAISE,
        taxPaise = 5_00_000_00,
        tipPaise = 1_00_000_00,
        items = (1..200).map { testItem("i$it", name = "Dish $it", unitPaise = 3_00_000, sortOrder = it) },
    )

    private fun vm(bills: FakeBillRepository, groups: FakeGroupRepository, handle: SavedStateHandle = SavedStateHandle(mapOf("billId" to bigBill.id))) =
        SplitViewModel(handle, bills, groups, SaveSplitUseCase(bills, RecordingAnalyticsTracker(), RecordingCrashReporter()), dispatchers)

    private fun repos(): Pair<FakeBillRepository, FakeGroupRepository> {
        val groups = FakeGroupRepository().apply { members.value = people }
        var writes = 0
        val bills = object : FakeBillRepository() {
            override suspend fun saveBill(bill: Bill) = super.saveBill(bill).also { writes++ }
        }
        bills.bills.value = listOf(bigBill)
        return bills to groups
    }

    @Test
    fun `1,000 rapid saves write one split and confirm once`() = runTest(dispatcher) {
        var writes = 0
        val groups = FakeGroupRepository().apply { members.value = people }
        val bills = object : FakeBillRepository() {
            override suspend fun saveBill(bill: Bill) = super.saveBill(bill).also { writes++ }
        }
        bills.bills.value = listOf(bigBill)
        val vm = vm(bills, groups)
        vm.uiState.test { while (awaitItem().editor !is LoadState.Content) Unit; cancelAndIgnoreRemainingEvents() }
        vm.effects.test {
            val ms = measureTimeMillis { repeat(1_000) { vm.save() } }
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(UiEffect.Haptic(HapticEvent.Confirm))
            expectNoEvents()
            println("STRESS [split] 1,000 save calls in $ms ms -> $writes write, 1 confirm haptic")
        }
        assertThat(writes).isEqualTo(1)
        assertThat(bills.bills.value.single().split!!.shares.sumOf { it.totalPaise }).isEqualTo(Money.MAX_PAISE)
    }

    @Test
    fun `200 people x 200 lines - 1,000 drops and mode changes stay paise-exact with bounded effects`() = runTest(dispatcher) {
        val (bills, groups) = repos()
        val vm = vm(bills, groups)
        vm.uiState.test { while (awaitItem().editor !is LoadState.Content) Unit; cancelAndIgnoreRemainingEvents() }
        vm.setModeOption(SplitModeOption.ByItem)
        val random = Random(12)
        var worstMs = 0.0
        val total = measureTimeMillis {
            repeat(1_000) { n ->
                val ns = measureNanoTime {
                    when (n % 10) {
                        9 -> vm.setModeOption(SplitModeOption.entries[random.nextInt(4)])
                        else -> {
                            if (n % 10 == 0) vm.setModeOption(SplitModeOption.ByItem)
                            vm.assignItems(listOf("i${random.nextInt(1, 201)}"), "p${random.nextInt(1, 201)}")
                        }
                    }
                }
                worstMs = maxOf(worstMs, ns / 1e6)
            }
            dispatcher.scheduler.advanceUntilIdle()
        }
        val editor = (vm.uiState.value.editor as LoadState.Content).data
        editor.preview?.let { assertThat(it.totalPaise).isEqualTo(Money.MAX_PAISE) }
        // Effects are a bounded channel: a thousand light haptics nobody collected cannot grow memory.
        var drained = 0
        vm.effects.test {
            while (true) { runCatching { awaitItem() }.getOrNull() ?: break; drained++; if (drained > 100) break }
            cancelAndIgnoreRemainingEvents()
        }
        println("STRESS [split] 200 people x 200 lines, 1,000 drops/mode changes in $total ms (worst op ${"%.1f".format(worstMs)} ms), $drained effects buffered (cap 16)")
        assertThat(drained).isAtMost(16)
    }

    @Test
    fun `odd paise across 3 and 7 people - 10,000 random bills through the editor's maths add up exactly`() {
        val random = Random(3)
        var worst = 0L
        val ms = measureTimeMillis {
            repeat(10_000) {
                val total = random.nextLong(1, Money.MAX_PAISE + 1)
                val n = if (it % 2 == 0) 3 else 7
                val ids = (1..n).map { i -> "m$i" }
                val bill = testBill(totalPaise = total, items = listOf(testItem(unitPaise = total)))
                val equal = SplitCalculator.split(bill, SplitConfig(SplitMode.EQUAL, ids)).shares.map { s -> s.totalPaise }
                val percent = SplitCalculator.split(bill, SplitConfig(SplitMode.BY_SHARE, ids, shares = ids.zip(SplitLogic.evenPercents(ids).values.map { v -> v.toInt() }).toMap(), percent = true)).shares.map { s -> s.totalPaise }
                assertThat(equal.sum()).isEqualTo(total)
                assertThat(percent.sum()).isEqualTo(total)
                worst = maxOf(worst, equal.max() - equal.min())
            }
        }
        println("STRESS [split] 10,000 random bills over 3 and 7 people (equal + even percent) in $ms ms: every one exact, equal shares at most $worst paisa apart")
        assertThat(worst).isAtMost(1L)
    }

    @Test
    fun `200 people x 200 lines by item - first frame, 300 jumps through the list and the donut`() {
        val config = SplitConfig(SplitMode.BY_ITEM, people.map { it.id }, itemAssignments = (1..200).associate { "i$it" to setOf("p$it") })
        val editor = SplitEditor(bigBill, people, config, "p1", emptyMap(), SplitCalculator.split(bigBill, config), null)
        val state = mutableStateOf(SplitUiState(LoadState.Content(editor)))
        rule.mainClock.autoAdvance = false
        val firstFrame = measureTimeMillis {
            rule.setDs(reduceMotion = false) { SplitScreen(state.value, SsSnackbarState(), SplitCallbacks({}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {})) }
            rule.mainClock.advanceTimeByFrame()
        }
        rule.mainClock.advanceTimeBy(2_000)
        val before = recompositionCycles()
        val jumps = measureTimeMillis {
            repeat(300) { n ->
                rule.onNodeWithTag(SplitTags.LIST).performScrollToIndex((n * 37) % 400)
                rule.mainClock.advanceTimeByFrame()
            }
        }
        val cycles = recompositionCycles() - before
        val donut = measureNanoTime {
            val a = DonutLogic.align(DonutLogic.slices(people.map { it.id to 1L }), DonutLogic.slices(people.map { it.id to it.id.drop(1).toLong() }))
            val out = FloatArray(a.keys.size * 2)
            repeat(1_000) { DonutLogic.layout(a.from, a.to, it / 1_000f, 3f, out) }
        }
        println("STRESS [split] 200 people x 200 lines: first frame $firstFrame ms, 300 jumps in $jumps ms with $cycles recomposer cycles, 1,000 donut layouts in ${"%.2f".format(donut / 1e6)} ms")
        assertThat(firstFrame).isLessThan(5_000L)
    }

    @Test
    fun `100 drags in a row - 100 assignments, and nothing left hovering`() {
        val members = people.take(5) // five faces fit across a phone
        val bill = testBill(items = (1..10).map { testItem("i$it", name = "Dish $it", unitPaise = 1_000, sortOrder = it) }, totalPaise = 10_000)
        val config = SplitConfig(SplitMode.BY_ITEM, members.map { it.id })
        val editor = SplitEditor(bill, members, config, null, emptyMap(), SplitCalculator.split(bill, config), null)
        val dropped = mutableListOf<String>()
        rule.setDs(reduceMotion = true) {
            SplitScreen(SplitUiState(LoadState.Content(editor)), SsSnackbarState(), SplitCallbacks({}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, onAssign = { _, m -> dropped += m }))
        }
        rule.onNodeWithTag(SplitTags.LIST).performScrollToIndex(4)
        val ms = measureTimeMillis {
            repeat(100) { n ->
                val face = rule.onNode(hasContentDescription("Guest ${n % 5 + 1}, 0 lines")).fetchSemanticsNode().boundsInRoot.center
                val handle = rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, SplitTags.handle("i1")))
                val from = handle.fetchSemanticsNode().boundsInRoot.center
                handle.performTouchInput {
                    down(center)
                    repeat(8) { moveBy((face - from) / 8f) }
                    up()
                }
                rule.waitForIdle()
            }
        }
        println("STRESS [split] 100 drags in $ms ms -> ${dropped.size} assignments, ghost left: ${runCatching { rule.onNodeWithTag(SplitTags.GHOST).fetchSemanticsNode() }.isSuccess}")
        assertThat(dropped).hasSize(100)
        assertThat(dropped.first()).isEqualTo("p1")
        rule.onNodeWithTag(SplitTags.GHOST).assertDoesNotExist()
    }

    @Test
    fun `bill screen - 200 lines and 200 shares compose a screenful, 1,000 delete taps delete once`() = runTest(dispatcher) {
        val config = SplitConfig(SplitMode.EQUAL, people.map { it.id })
        val split = bigBill.copy(paidByMemberId = "p1", status = BillStatus.SPLIT, split = BillSplit(config, SplitCalculator.split(bigBill, config).shares))
        var deletes = 0
        val bills = object : FakeBillRepository() {
            override suspend fun deleteBill(billId: String) = super.deleteBill(billId).also { deletes++ }
        }
        bills.bills.value = listOf(split)
        val vm = BillDetailViewModel(SavedStateHandle(mapOf("billId" to split.id)), bills, FakeGroupRepository().apply { members.value = people }, dispatchers)
        val ms = measureTimeMillis { repeat(1_000) { vm.deleteBill() } }
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(deletes).isEqualTo(1)

        val firstFrame = measureTimeMillis {
            rule.setDs(reduceMotion = true) { BillDetailScreen(BillDetailUiState(LoadState.Content(BillDetail(split, people))), SsSnackbarState(), {}, {}, {}, {}) }
            rule.waitForIdle()
        }
        rule.onNodeWithTag(BillDetailTags.LIST).performScrollToIndex(300)
        println("STRESS [bill] 1,000 delete calls in $ms ms -> $deletes delete; 200 shares + 200 lines first frame $firstFrame ms")
        assertThat(split.split!!.shares.sumOf { it.totalPaise }).isEqualTo(Money.MAX_PAISE)
    }

    @Test
    fun `simplify picture for 200 people and 10,000 bills`() {
        val random = Random(9)
        val bills = (1..10_000).map { n ->
            val payer = "p${random.nextInt(1, 201)}"
            val sharers = (1..random.nextInt(2, 6)).map { "p${random.nextInt(1, 201)}" }.distinct()
            val each = random.nextLong(100, 50_000)
            testBill(id = "b$n", totalPaise = each * sharers.size, items = emptyList(), paidByMemberId = payer, status = BillStatus.SPLIT).copy(
                split = BillSplit(SplitConfig(SplitMode.EQUAL, sharers), sharers.map { MemberShare(it, each, 0, 0, 0) }),
            )
        }
        var graph = DebtGraphLogic.graph(people, bills)
        val ms = measureTimeMillis { repeat(10) { graph = DebtGraphLogic.graph(people, bills) } }
        println("STRESS [simplify] 200 people, 10,000 bills: ${graph.before.size} IOUs -> ${graph.after.size} payments, ${graph.nodes.size} faces (+${graph.hiddenPeople} folded), ${ms / 10} ms per graph")
        assertThat(graph.nodes).hasSize(DebtGraphLogic.MAX_NODES)
        assertThat(graph.after.size).isAtMost(199)
        rule.setDs(reduceMotion = true) { com.darshan.splitsmart.ui.simplify.DebtGraphView(graph, playKey = 1) }
        rule.waitForIdle()
    }
}
