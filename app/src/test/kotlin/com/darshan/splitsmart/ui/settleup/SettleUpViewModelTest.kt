package com.darshan.splitsmart.ui.settleup

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.darshan.splitsmart.data.upi.UpiLaunchResult
import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.SettlementMethod
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.model.VpaError
import com.darshan.splitsmart.domain.split.BillProblem
import com.darshan.splitsmart.domain.split.SplitCalculator
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
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.designsystem.effects.HapticEvent
import com.darshan.splitsmart.ui.designsystem.effects.UiEffect
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettleUpViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val mainRule = MainDispatcherRule(dispatcher)
    private val groups = FakeGroupRepository()
    private val bills = FakeBillRepository()
    private val settlements = FakeSettlementRepository()
    private val launcher = FakeUpiAppLauncher()
    private val analytics = RecordingAnalyticsTracker()

    @Before
    fun seed() {
        groups.groups.value = listOf(testGroup(name = "Goa trip"))
        groups.members.value = listOf(testMember("a", name = "Asha", vpa = "asha@okfake"), testMember("b", name = "Ravi"), testMember("c", name = "Meera", vpa = "meera@fakebank"))
        val config = SplitConfig(SplitMode.EQUAL, listOf("a", "b", "c"))
        val bill = testBill(totalPaise = 90_000, items = emptyList(), paidByMemberId = "a", status = BillStatus.SPLIT)
        bills.bills.value = listOf(bill.copy(split = BillSplit(config, SplitCalculator.split(bill, config).shares)))
        // a paid 900, each owes 300 → b owes a 300, c owes a 300
    }

    private fun vm(groupId: String = "g1", handle: SavedStateHandle = SavedStateHandle(mapOf("groupId" to groupId))) =
        SettleUpViewModel(handle, groups, bills, settlements, launcher, analytics, TestDispatcherProvider(dispatcher))

    private suspend fun SettleUpViewModel.content(): SettleUpContent {
        uiState.test { while (awaitItem().content !is LoadState.Content) { }; cancelAndIgnoreRemainingEvents() }
        dispatcher.scheduler.advanceUntilIdle()
        return (uiState.value.content as LoadState.Content).data
    }

    private fun SettleUpUiState.data() = (content as LoadState.Content).data

    /** The state as a new subscriber sees it now (`uiState.value` is stale once WhileSubscribed has stopped). */
    private suspend fun SettleUpViewModel.fresh(): SettleUpUiState {
        var latest: SettleUpUiState? = null
        uiState.test {
            dispatcher.scheduler.advanceUntilIdle()
            latest = expectMostRecentItem()
            cancelAndIgnoreRemainingEvents()
        }
        return latest!!
    }

    private fun <T> CoroutineScope.launchCollect(flow: Flow<T>, into: MutableList<T>): Job =
        launch(UnconfinedTestDispatcher(dispatcher.scheduler)) { flow.collect { into += it } }

    @Test
    fun `builds one settle-up item per debt with a upi link to the payee`() = runTest(dispatcher) {
        val content = vm().content()
        assertThat(content.group.name).isEqualTo("Goa trip")
        assertThat(content.items).hasSize(2)
        val item = content.items.first { it.payer.id == "b" }
        assertThat(item.payee.id).isEqualTo("a")
        assertThat(item.debt.amountPaise).isEqualTo(30_000)
        assertThat(item.link!!.uri).isEqualTo("upi://pay?pa=asha%40okfake&pn=Asha&am=300.00&cu=INR&tn=Goa%20trip%20via%20SplitSmart")
        assertThat(item.missingVpa).isFalse()
        assertThat(item.key).isEqualTo("b>a")
        assertThat(content.totalPaise).isEqualTo(60_000)
        assertThat(content.allSquare).isFalse()
    }

    @Test
    fun `payments come largest first`() = runTest(dispatcher) {
        val config = SplitConfig(SplitMode.BY_SHARE, listOf("a", "b", "c"), shares = mapOf("a" to 1, "b" to 1, "c" to 3))
        val bill = testBill("b2", totalPaise = 50_000, items = emptyList(), paidByMemberId = "a", status = BillStatus.SPLIT)
        bills.bills.value = listOf(bill.copy(split = BillSplit(config, SplitCalculator.split(bill, config).shares)))
        val amounts = vm().content().items.map { it.debt.amountPaise }
        assertThat(amounts).isInOrder(Comparator.reverseOrder<Long>())
    }

    @Test
    fun `a payee without a upi id has no link and is flagged`() = runTest(dispatcher) {
        // make b the creditor: b paid a bill
        val config = SplitConfig(SplitMode.EQUAL, listOf("a", "b"))
        val bill = testBill("b2", totalPaise = 200_000, items = emptyList(), paidByMemberId = "b", status = BillStatus.SPLIT)
        bills.bills.value = listOf(bill.copy(split = BillSplit(config, SplitCalculator.split(bill, config).shares)))
        val item = vm().content().items.single()
        assertThat(item.payee.id).isEqualTo("b")
        assertThat(item.link).isNull()
        assertThat(item.missingVpa).isTrue()
    }

    @Test
    fun `pay opens the link through the launcher and reports a missing upi app`() = runTest(dispatcher) {
        val vm = vm()
        val item = vm.content().items.first()
        vm.pay(item)
        assertThat(launcher.opened).containsExactly(item.link!!.uri)
        assertThat(analytics.last("settle_up_tap")).containsExactly("method", "upi", "payee_has_id", 1)
        vm.notYet()
        launcher.result = UpiLaunchResult.NoUpiApp
        vm.events.test {
            vm.pay(item)
            assertThat(awaitItem()).isEqualTo(SettleUpEvent.NoUpiApp)
        }
    }

    @Test
    fun `after pay the screen waits, and coming back asks whether it went through without recording anything`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            var state = awaitItem()
            while (state.content !is LoadState.Content) state = awaitItem()
            val item = state.data().items.first()
            vm.pay(item)
            val away = awaitItem().check!!
            assertThat(away.key).isEqualTo(item.key)
            assertThat(away.amountPaise).isEqualTo(item.debt.amountPaise)
            assertThat(away.method).isEqualTo(SettlementMethod.UPI_APP)
            assertThat(away.asking).isFalse()
            vm.onResumed()
            assertThat(awaitItem().check!!.asking).isTrue()
            // Coming back is not a payment: nothing is recorded until the person says so.
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(settlements.recordCalls).isEqualTo(0)
            assertThat(settlements.settlements.value).isEmpty()
        }
    }

    @Test
    fun `resuming while nothing is pending changes nothing, and a missing upi app leaves no question`() = runTest(dispatcher) {
        val vm = vm()
        vm.content()
        vm.onResumed()
        assertThat(vm.fresh().check).isNull()
        launcher.result = UpiLaunchResult.NoUpiApp
        vm.pay(vm.content().items.first())
        vm.onResumed()
        assertThat(vm.fresh().check).isNull()
    }

    @Test
    fun `1000 rapid taps on pay open the upi app once`() = runTest(dispatcher) {
        val vm = vm()
        val items = vm.content().items
        repeat(1_000) { vm.pay(items[it % items.size]) }
        assertThat(launcher.opened).hasSize(1)
    }

    @Test
    fun `yes it went through records one payment for the amount that was opened, with a confirm haptic and undo`() = runTest(dispatcher) {
        val vm = vm()
        val effects = mutableListOf<UiEffect>()
        val events = mutableListOf<SettleUpEvent>()
        val collectEffects = backgroundScope.launchCollect(vm.effects, effects)
        val collectEvents = backgroundScope.launchCollect(vm.events, events)
        vm.uiState.test {
            var state = awaitItem()
            while (state.content !is LoadState.Content) state = awaitItem()
            val item = state.data().items.first { it.payer.id == "b" }
            vm.pay(item)
            vm.onResumed()
            repeat(1_000) { vm.confirmPaid() }
            dispatcher.scheduler.advanceUntilIdle()
            val after = expectMostRecentItem()
            assertThat(settlements.recordCalls).isEqualTo(1)
            val paid = settlements.settlements.value.single()
            assertThat(paid.fromMemberId).isEqualTo("b")
            assertThat(paid.toMemberId).isEqualTo("a")
            assertThat(paid.amountPaise).isEqualTo(30_000)
            assertThat(paid.method).isEqualTo(SettlementMethod.UPI_APP)
            assertThat(after.check).isNull()
            assertThat(after.settled!!.settlementId).isEqualTo(paid.id)
            assertThat(after.settled!!.allSquare).isFalse()
            assertThat(after.data().items.map { it.key }).containsExactly("c>a")
            assertThat(after.data().history).isEqualTo(1)
        }
        assertThat(effects).containsExactly(UiEffect.Haptic(HapticEvent.Confirm))
        assertThat(events.filterIsInstance<SettleUpEvent.Recorded>().single().settlementId).isEqualTo(settlements.settlements.value.single().id)
        assertThat(analytics.last("settlement_marked")).containsExactly("method", "upi_app", "all_square", 0)
        collectEffects.cancel()
        collectEvents.cancel()
    }

    @Test
    fun `confirming while still away in the upi app does nothing`() = runTest(dispatcher) {
        val vm = vm()
        vm.pay(vm.content().items.first())
        vm.confirmPaid()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(settlements.recordCalls).isEqualTo(0)
    }

    @Test
    fun `not yet clears the question and records nothing`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            var state = awaitItem()
            while (state.content !is LoadState.Content) state = awaitItem()
            vm.pay(state.data().items.first())
            vm.onResumed()
            vm.notYet()
            dispatcher.scheduler.advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(vm.fresh().check).isNull()
        assertThat(settlements.recordCalls).isEqualTo(0)
    }

    @Test
    fun `paid another way asks first, then records it as other`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            var state = awaitItem()
            while (state.content !is LoadState.Content) state = awaitItem()
            val item = state.data().items.first()
            vm.markPaid(item)
            val asking = awaitItem().check!!
            assertThat(asking.asking).isTrue()
            assertThat(asking.method).isEqualTo(SettlementMethod.OTHER)
            assertThat(launcher.opened).isEmpty()
            vm.confirmPaid()
            dispatcher.scheduler.advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(settlements.settlements.value.single().method).isEqualTo(SettlementMethod.OTHER)
    }

    @Test
    fun `settling the last payment makes the group all square, and undo brings the debt back`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            var state = awaitItem()
            while (state.content !is LoadState.Content) state = awaitItem()
            for (item in state.data().items) {
                vm.markPaid(item)
                vm.confirmPaid()
                dispatcher.scheduler.advanceUntilIdle()
            }
            val square = expectMostRecentItem()
            assertThat(square.data().items).isEmpty()
            assertThat(square.data().allSquare).isTrue()
            assertThat(square.settled!!.allSquare).isTrue()
            vm.undo(square.settled!!.settlementId)
            dispatcher.scheduler.advanceUntilIdle()
            val back = expectMostRecentItem()
            assertThat(back.settled).isNull()
            assertThat(back.data().items).hasSize(1)
        }
    }

    @Test
    fun `a failed save keeps the question open and says so`() = runTest(dispatcher) {
        val vm = vm()
        val effects = mutableListOf<UiEffect>()
        val job = backgroundScope.launchCollect(vm.effects, effects)
        vm.events.test {
            vm.markPaid(vm.content().items.first())
            settlements.failWith = IllegalStateException("disk full")
            vm.confirmPaid()
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(SettleUpEvent.Failed)
        }
        val after = vm.fresh()
        assertThat(after.check).isNotNull()
        assertThat(after.recording).isFalse()
        assertThat(effects).containsExactly(UiEffect.Haptic(HapticEvent.Reject))
        job.cancel()
    }

    @Test
    fun `a person who left while the question was open is not paid, the question goes`() = runTest(dispatcher) {
        val vm = vm()
        vm.events.test {
            vm.markPaid(vm.content().items.first { it.payer.id == "b" })
            groups.members.value = groups.members.value.filterNot { it.id == "b" }
            dispatcher.scheduler.advanceUntilIdle()
            vm.confirmPaid()
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(SettleUpEvent.Failed)
        }
        assertThat(settlements.recordCalls).isEqualTo(0)
        assertThat(vm.fresh().check).isNull()
    }

    @Test
    fun `the open question survives process death and asks once the app is back`() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("groupId" to "g1"))
        val first = vm(handle = handle)
        first.pay(first.content().items.first { it.payer.id == "b" })
        val restored = vm(handle = handle)
        restored.uiState.test {
            var state = awaitItem()
            while (state.content !is LoadState.Content) state = awaitItem()
            assertThat(state.check!!.key).isEqualTo("b>a")
            restored.onResumed()
            assertThat(awaitItem().check!!.asking).isTrue()
            restored.confirmPaid()
            dispatcher.scheduler.advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(settlements.settlements.value.single().amountPaise).isEqualTo(30_000)
    }

    @Test
    fun `bills that lost a member or a payer are named and left out of the payments`() = runTest(dispatcher) {
        val config = SplitConfig(SplitMode.EQUAL, listOf("a", "b", "z"))
        val withStranger = testBill("bz", title = "Ferry", totalPaise = 30_000, items = emptyList(), paidByMemberId = "a", status = BillStatus.SPLIT)
        val dropped = testBill("bd", title = "Villa", totalPaise = 10_000, items = emptyList(), paidByMemberId = "a", status = BillStatus.SPLIT)
        val draft = testBill("bn", title = "Snacks", totalPaise = 5_000, items = emptyList())
        bills.bills.value = bills.bills.value + withStranger.copy(split = BillSplit(config, SplitCalculator.split(withStranger, config).shares)) + dropped + draft
        val content = vm().content()
        assertThat(content.toFix.map { it.bill.title to it.problem }).containsExactly(
            "Ferry" to BillProblem.MEMBER_LEFT, "Villa" to BillProblem.MEMBER_LEFT, "Snacks" to BillProblem.NOT_SPLIT,
        )
        // Only the bill everyone is still in moves money.
        assertThat(content.items.map { it.key to it.debt.amountPaise }).containsExactly("b>a" to 30_000L, "c>a" to 30_000L)
    }

    @Test
    fun `share sends the given text and reports failure`() = runTest(dispatcher) {
        val vm = vm()
        vm.content()
        vm.share("Hi Ravi, you owe Asha ₹300.00")
        assertThat(launcher.shared).containsExactly("Hi Ravi, you owe Asha ₹300.00")
        assertThat(analytics.last("settle_up_tap")["method"]).isEqualTo("share")
        analytics.events.forEach { (_, p) -> p.values.forEach { assertThat(it.toString()).doesNotContain("Asha") } }
        launcher.shareSucceeds = false
        vm.events.test {
            vm.share("x")
            assertThat(awaitItem()).isEqualTo(SettleUpEvent.ShareFailed)
        }
    }

    @Test
    fun `qr sheet state`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            var state = awaitItem()
            while (state.content !is LoadState.Content) state = awaitItem()
            val item = state.data().items.first()
            vm.showQr(item)
            assertThat(awaitItem().qrItem).isEqualTo(item)
            assertThat(analytics.last("settle_up_tap")["method"]).isEqualTo("qr")
            vm.dismissQr()
            assertThat(awaitItem().qrItem).isNull()
        }
    }

    @Test
    fun `adding a upi id for a payee validates and updates the member, links appear`() = runTest(dispatcher) {
        val config = SplitConfig(SplitMode.EQUAL, listOf("a", "b"))
        val bill = testBill("b2", totalPaise = 200_000, items = emptyList(), paidByMemberId = "b", status = BillStatus.SPLIT)
        bills.bills.value = listOf(bill.copy(split = BillSplit(config, SplitCalculator.split(bill, config).shares)))
        val vm = vm()
        vm.uiState.test {
            var state = awaitItem()
            while (state.content !is LoadState.Content) state = awaitItem()
            vm.editVpa("b")
            assertThat(awaitItem().vpaDraft!!.memberId).isEqualTo("b")
            vm.onVpaChange("not valid@upi")
            awaitItem()
            vm.saveVpa()
            assertThat(awaitItem().vpaDraft!!.error).isEqualTo(VpaError.INVALID_CHARS)
            vm.onVpaChange("Ravi@upi")
            awaitItem()
            vm.saveVpa()
            dispatcher.scheduler.advanceUntilIdle()
            val latest = expectMostRecentItem()
            assertThat(latest.vpaDraft).isNull()
            assertThat(groups.members.value.first { it.id == "b" }.vpa).isEqualTo("ravi@upi")
            assertThat((latest.content as LoadState.Content).data.items.single().link!!.payeeVpa).isEqualTo("ravi@upi")
        }
    }

    @Test
    fun `no bills is the empty state and a missing group is not found`() = runTest(dispatcher) {
        bills.bills.value = emptyList()
        vm().uiState.test {
            awaitItem()
            assertThat(awaitItem().content).isEqualTo(LoadState.Empty)
        }
        vm("nope").uiState.test {
            awaitItem()
            assertThat(awaitItem().content).isEqualTo(LoadState.Error(ErrorReason.NOT_FOUND))
        }
    }
}
