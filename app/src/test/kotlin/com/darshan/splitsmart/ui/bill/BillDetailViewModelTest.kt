package com.darshan.splitsmart.ui.bill

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.darshan.splitsmart.testutil.FakeBillRepository
import com.darshan.splitsmart.testutil.FakeGroupRepository
import com.darshan.splitsmart.testutil.MainDispatcherRule
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.LoadState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BillDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val mainRule = MainDispatcherRule(dispatcher)
    private val bills = FakeBillRepository()
    private val groups = FakeGroupRepository()

    private fun vm(billId: String = "b1") =
        BillDetailViewModel(SavedStateHandle(mapOf("billId" to billId)), bills, groups, TestDispatcherProvider(dispatcher))

    @Test
    fun `shows the bill with items and payer name`() = runTest(dispatcher) {
        bills.bills.value = listOf(testBill(paidByMemberId = "m1"))
        groups.members.value = listOf(testMember())
        vm().uiState.test {
            assertThat(awaitItem().bill).isEqualTo(LoadState.Loading)
            val content = awaitItem().bill as LoadState.Content
            assertThat(content.data.bill.items).hasSize(1)
            assertThat(content.data.payerName).isEqualTo("Asha")
        }
    }

    @Test
    fun `missing bill is not found`() = runTest(dispatcher) {
        vm("nope").uiState.test {
            awaitItem()
            assertThat(awaitItem().bill).isEqualTo(LoadState.Error(ErrorReason.NOT_FOUND))
        }
    }

    @Test
    fun `delete emits Deleted`() = runTest(dispatcher) {
        bills.bills.value = listOf(testBill())
        val vm = vm()
        vm.uiState.test { awaitItem(); awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.events.test {
            vm.deleteBill()
            assertThat(awaitItem()).isEqualTo(BillDetailEvent.Deleted)
        }
        assertThat(bills.bills.value).isEmpty()
    }
}
