package com.darshan.splitsmart.ui.settleup

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.darshan.splitsmart.data.upi.UpiLaunchResult
import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.model.VpaError
import com.darshan.splitsmart.domain.split.SplitCalculator
import com.darshan.splitsmart.testutil.FakeBillRepository
import com.darshan.splitsmart.testutil.FakeGroupRepository
import com.darshan.splitsmart.testutil.FakeUpiAppLauncher
import com.darshan.splitsmart.testutil.MainDispatcherRule
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.LoadState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val launcher = FakeUpiAppLauncher()

    @Before
    fun seed() {
        groups.groups.value = listOf(testGroup(name = "Goa trip"))
        groups.members.value = listOf(testMember("a", name = "Asha", vpa = "asha@okicici"), testMember("b", name = "Ravi"), testMember("c", name = "Meera", vpa = "meera@ybl"))
        val config = SplitConfig(SplitMode.EQUAL, listOf("a", "b", "c"))
        val bill = testBill(totalPaise = 90_000, items = emptyList(), paidByMemberId = "a")
        bills.bills.value = listOf(bill.copy(split = BillSplit(config, SplitCalculator.split(bill, config).shares)))
        // a paid 900, each owes 300 → b owes a 300, c owes a 300
    }

    private fun vm(groupId: String = "g1") = SettleUpViewModel(SavedStateHandle(mapOf("groupId" to groupId)), groups, bills, launcher, TestDispatcherProvider(dispatcher))

    private suspend fun SettleUpViewModel.content(): SettleUpContent {
        uiState.test { while (awaitItem().content !is LoadState.Content) { }; cancelAndIgnoreRemainingEvents() }
        dispatcher.scheduler.advanceUntilIdle()
        return (uiState.value.content as LoadState.Content).data
    }

    @Test
    fun `builds one settle-up item per debt with a upi link to the payee`() = runTest(dispatcher) {
        val content = vm().content()
        assertThat(content.group.name).isEqualTo("Goa trip")
        assertThat(content.items).hasSize(2)
        val item = content.items.first { it.payer.id == "b" }
        assertThat(item.payee.id).isEqualTo("a")
        assertThat(item.debt.amountPaise).isEqualTo(30_000)
        assertThat(item.link!!.uri).isEqualTo("upi://pay?pa=asha%40okicici&pn=Asha&am=300.00&cu=INR&tn=Goa%20trip%20via%20SplitSmart")
        assertThat(item.missingVpa).isFalse()
    }

    @Test
    fun `a payee without a upi id has no link and is flagged`() = runTest(dispatcher) {
        // make b the creditor: b paid a bill
        val config = SplitConfig(SplitMode.EQUAL, listOf("a", "b"))
        val bill = testBill("b2", totalPaise = 200_000, items = emptyList(), paidByMemberId = "b")
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
        launcher.result = UpiLaunchResult.NoUpiApp
        vm.events.test {
            vm.pay(item)
            assertThat(awaitItem()).isEqualTo(SettleUpEvent.NoUpiApp)
        }
    }

    @Test
    fun `share sends the given text and reports failure`() = runTest(dispatcher) {
        val vm = vm()
        vm.content()
        vm.share("Hi Ravi, you owe Asha ₹300.00")
        assertThat(launcher.shared).containsExactly("Hi Ravi, you owe Asha ₹300.00")
        launcher.shareSucceeds = false
        vm.events.test {
            vm.share("x")
            assertThat(awaitItem()).isEqualTo(SettleUpEvent.ShareFailed)
        }
    }

    @Test
    fun `qr dialog state`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            var state = awaitItem()
            while (state.content !is LoadState.Content) state = awaitItem()
            val item = (state.content as LoadState.Content).data.items.first()
            vm.showQr(item)
            assertThat(awaitItem().qrItem).isEqualTo(item)
            vm.dismissQr()
            assertThat(awaitItem().qrItem).isNull()
        }
    }

    @Test
    fun `adding a upi id for a payee validates and updates the member, links appear`() = runTest(dispatcher) {
        val config = SplitConfig(SplitMode.EQUAL, listOf("a", "b"))
        val bill = testBill("b2", totalPaise = 200_000, items = emptyList(), paidByMemberId = "b")
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
    fun `no debts is the empty state and a missing group is not found`() = runTest(dispatcher) {
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
