package com.darshan.splitsmart.ui.groupdetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.NameError
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
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.LoadState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val mainRule = MainDispatcherRule(dispatcher)
    private val groups = FakeGroupRepository()
    private val bills = FakeBillRepository()

    private fun vm(handle: SavedStateHandle = SavedStateHandle(mapOf("groupId" to "g1"))) = GroupDetailViewModel(
        savedStateHandle = handle,
        groupRepository = groups,
        billRepository = bills,
        addMember = AddMemberUseCase(groups),
        createManualBill = CreateManualBillUseCase(bills, fixedClock),
        dispatchers = TestDispatcherProvider(dispatcher),
    )

    @Test
    fun `loads group, members and bills`() = runTest(dispatcher) {
        groups.groups.value = listOf(testGroup())
        groups.members.value = listOf(testMember())
        bills.bills.value = listOf(testBill())
        vm().uiState.test {
            assertThat(awaitItem().detail).isEqualTo(LoadState.Loading)
            val content = awaitItem().detail as LoadState.Content
            assertThat(content.data.group.name).isEqualTo("Goa trip")
            assertThat(content.data.members.map { it.name }).containsExactly("Asha")
            assertThat(content.data.bills.map { it.title }).containsExactly("Dinner")
        }
    }

    @Test
    fun `missing group is a not found error`() = runTest(dispatcher) {
        vm().uiState.test {
            awaitItem()
            assertThat(awaitItem().detail).isEqualTo(LoadState.Error(ErrorReason.NOT_FOUND))
        }
    }

    @Test
    fun `add member validates and stores the draft in saved state`() = runTest(dispatcher) {
        groups.groups.value = listOf(testGroup())
        val handle = SavedStateHandle(mapOf("groupId" to "g1"))
        val vm = vm(handle)
        vm.uiState.test { awaitItem(); awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.openAddMember()
        vm.onMemberNameChange("Ravi")
        vm.onMemberVpaChange("ravi@upi")
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(handle.get<String>("memberName")).isEqualTo("Ravi")
        // process death: a new VM with the same handle restores the open dialog and its text
        val restored = vm(handle)
        restored.uiState.test { awaitItem(); awaitItem(); cancelAndIgnoreRemainingEvents() }
        assertThat(restored.uiState.value.memberDraft?.name).isEqualTo("Ravi")
        assertThat(restored.uiState.value.memberDraft?.vpa).isEqualTo("ravi@upi")

        restored.submitMember()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(groups.members.value.single().vpa).isEqualTo("ravi@upi")
        assertThat(restored.uiState.value.memberDraft).isNull()
    }

    @Test
    fun `invalid member name shows an error in the dialog`() = runTest(dispatcher) {
        groups.groups.value = listOf(testGroup())
        val vm = vm()
        vm.uiState.test { awaitItem(); awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.openAddMember()
        vm.onMemberNameChange("")
        vm.submitMember()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.uiState.value.memberDraft?.nameError).isEqualTo(NameError.BLANK)
    }

    @Test
    fun `manual bill is created with parsed paise and emits navigation event`() = runTest(dispatcher) {
        groups.groups.value = listOf(testGroup())
        groups.members.value = listOf(testMember())
        val vm = vm()
        vm.uiState.test { awaitItem(); awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.openAddBill()
        vm.onBillTitleChange("Lunch")
        vm.onBillAmountChange("1,250.50")
        vm.onBillPayerChange("m1")
        vm.events.test {
            vm.submitBill()
            val event = awaitItem() as GroupDetailEvent.BillCreated
            val bill = bills.bills.value.single()
            assertThat(event.billId).isEqualTo(bill.id)
            assertThat(bill.totalPaise).isEqualTo(125_050)
            assertThat(bill.paidByMemberId).isEqualTo("m1")
            assertThat(bill.source).isEqualTo(BillSource.MANUAL)
        }
    }

    @Test
    fun `manual bill with bad amount shows field error`() = runTest(dispatcher) {
        groups.groups.value = listOf(testGroup())
        val vm = vm()
        vm.uiState.test { awaitItem(); awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.openAddBill()
        vm.onBillTitleChange("Lunch")
        vm.onBillAmountChange("-5")
        vm.submitBill()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.uiState.value.billDraft?.amountError).isTrue()
        assertThat(bills.bills.value).isEmpty()
    }

    @Test
    fun `remove member and delete group`() = runTest(dispatcher) {
        groups.groups.value = listOf(testGroup())
        groups.members.value = listOf(testMember())
        val vm = vm()
        vm.uiState.test { awaitItem(); awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.removeMember("m1")
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(groups.members.value).isEmpty()
        vm.events.test {
            vm.deleteGroup()
            assertThat(awaitItem()).isEqualTo(GroupDetailEvent.GroupDeleted)
        }
        assertThat(groups.groups.value).isEmpty()
    }
}
