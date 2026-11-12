package com.darshan.splitsmart.ui.groupdetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.Debt
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.split.SplitCalculator
import com.darshan.splitsmart.domain.usecase.AddMemberUseCase
import com.darshan.splitsmart.domain.usecase.CreateManualBillUseCase
import com.darshan.splitsmart.testutil.FakeBillRepository
import com.darshan.splitsmart.testutil.FakeGroupRepository
import com.darshan.splitsmart.testutil.MainDispatcherRule
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.fixedClock
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.LoadState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupBalancesTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val mainRule = MainDispatcherRule(dispatcher)
    private val groups = FakeGroupRepository()
    private val bills = FakeBillRepository()

    @Test
    fun `group detail exposes simplified debts and the number of bills not yet split`() = runTest(dispatcher) {
        groups.groups.value = listOf(testGroup())
        groups.members.value = listOf(testMember("a", name = "Asha"), testMember("b", name = "Ravi"))
        val config = SplitConfig(SplitMode.EQUAL, listOf("a", "b"))
        val b1 = testBill("b1", totalPaise = 1_000, items = emptyList(), paidByMemberId = "a")
        val b2 = testBill("b2", totalPaise = 400, items = emptyList(), paidByMemberId = "b")
        bills.bills.value = listOf(
            b1.copy(split = BillSplit(config, SplitCalculator.split(b1, config).shares)),
            b2.copy(split = BillSplit(config, SplitCalculator.split(b2, config).shares)),
            testBill("b3", totalPaise = 999, items = emptyList()),
        )
        val vm = GroupDetailViewModel(
            SavedStateHandle(mapOf("groupId" to "g1")), groups, bills, com.darshan.splitsmart.testutil.FakeSettlementRepository(), AddMemberUseCase(groups),
            CreateManualBillUseCase(bills, fixedClock), TestDispatcherProvider(dispatcher), com.darshan.splitsmart.testutil.FakeTrashRepository(groups, bills), com.darshan.splitsmart.ui.undo.UndoCenter(),
        )
        vm.uiState.test {
            awaitItem()
            val detail = (awaitItem().detail as LoadState.Content).data
            assertThat(detail.debts).containsExactly(Debt("b", "a", 300)) // a paid 1000 (owed 500), b paid 400 (owed 200) → b owes a 300
            assertThat(detail.unsettledBills).isEqualTo(1)
        }
    }

    @Test
    fun `a payment marked as settled takes its debt off the group and stays in the detail`() = runTest(dispatcher) {
        groups.groups.value = listOf(testGroup())
        groups.members.value = listOf(testMember("a", name = "Asha"), testMember("b", name = "Ravi"))
        val config = SplitConfig(SplitMode.EQUAL, listOf("a", "b"))
        val b1 = testBill("b1", totalPaise = 1_000, items = emptyList(), paidByMemberId = "a")
        bills.bills.value = listOf(b1.copy(split = BillSplit(config, SplitCalculator.split(b1, config).shares)))
        val settlements = com.darshan.splitsmart.testutil.FakeSettlementRepository()
        settlements.settlements.value = listOf(com.darshan.splitsmart.testutil.testSettlement(from = "b", to = "a", amountPaise = 200))
        val vm = GroupDetailViewModel(
            SavedStateHandle(mapOf("groupId" to "g1")), groups, bills, settlements, AddMemberUseCase(groups),
            CreateManualBillUseCase(bills, fixedClock), TestDispatcherProvider(dispatcher), com.darshan.splitsmart.testutil.FakeTrashRepository(groups, bills), com.darshan.splitsmart.ui.undo.UndoCenter(),
        )
        vm.uiState.test {
            awaitItem()
            val detail = (awaitItem().detail as LoadState.Content).data
            assertThat(detail.debts).containsExactly(Debt("b", "a", 300))
            assertThat(detail.settlements).hasSize(1)
            settlements.settlements.value = settlements.settlements.value + com.darshan.splitsmart.testutil.testSettlement("s2", from = "b", to = "a", amountPaise = 300)
            assertThat((awaitItem().detail as LoadState.Content).data.debts).isEmpty()
        }
    }
}
