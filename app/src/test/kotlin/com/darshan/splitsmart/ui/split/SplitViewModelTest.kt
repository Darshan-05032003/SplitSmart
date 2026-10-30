package com.darshan.splitsmart.ui.split

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.darshan.splitsmart.domain.model.SplitError
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.usecase.SaveSplitUseCase
import com.darshan.splitsmart.testutil.FakeBillRepository
import com.darshan.splitsmart.testutil.FakeGroupRepository
import com.darshan.splitsmart.testutil.MainDispatcherRule
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testItem
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
class SplitViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val mainRule = MainDispatcherRule(dispatcher)
    private val bills = FakeBillRepository()
    private val groups = FakeGroupRepository()

    @Before
    fun seed() {
        groups.members.value = listOf(testMember("a", name = "Asha"), testMember("b", name = "Ravi"), testMember("c", name = "Meera"))
        bills.bills.value = listOf(testBill(totalPaise = 300, items = listOf(testItem("i1", unitPaise = 200), testItem("i2", unitPaise = 100, sortOrder = 1))))
    }

    private fun vm(handle: SavedStateHandle = SavedStateHandle(mapOf("billId" to "b1"))) =
        SplitViewModel(handle, bills, groups, SaveSplitUseCase(bills), TestDispatcherProvider(dispatcher))

    private suspend fun SplitViewModel.editor(): SplitEditor {
        uiState.test { while (awaitItem().editor !is LoadState.Content) { }; cancelAndIgnoreRemainingEvents() }
        dispatcher.scheduler.advanceUntilIdle()
        return (uiState.value.editor as LoadState.Content).data
    }

    @Test
    fun `defaults to an equal split over all members with a live preview`() = runTest(dispatcher) {
        val e = vm().editor()
        assertThat(e.config.mode).isEqualTo(SplitMode.EQUAL)
        assertThat(e.config.participantIds).containsExactly("a", "b", "c").inOrder()
        assertThat(e.preview!!.shares.map { it.totalPaise }).containsExactly(100L, 100L, 100L)
        assertThat(e.members.map { it.name }).containsExactly("Asha", "Ravi", "Meera")
    }

    @Test
    fun `toggling participants and items updates the preview`() = runTest(dispatcher) {
        val vm = vm()
        vm.editor()
        vm.toggleParticipant("c")
        vm.setMode(SplitMode.BY_ITEM)
        vm.toggleItemAssignment("i1", "a")
        dispatcher.scheduler.advanceUntilIdle()
        val e = (vm.uiState.value.editor as LoadState.Content).data
        assertThat(e.config.participantIds).containsExactly("a", "b")
        assertThat(e.preview!!.shares.associate { it.memberId to it.totalPaise }).containsExactly("a", 250L, "b", 50L)
        vm.toggleItemAssignment("i1", "a") // back to everyone
        dispatcher.scheduler.advanceUntilIdle()
        assertThat((vm.uiState.value.editor as LoadState.Content).data.preview!!.shares.map { it.totalPaise }).containsExactly(150L, 150L)
    }

    @Test
    fun `share text drives weights, bad text is treated as zero and all-zero shows an error`() = runTest(dispatcher) {
        val vm = vm()
        vm.editor()
        vm.setMode(SplitMode.BY_SHARE)
        vm.setShareText("a", "2"); vm.setShareText("b", "x"); vm.setShareText("c", "1")
        dispatcher.scheduler.advanceUntilIdle()
        var e = (vm.uiState.value.editor as LoadState.Content).data
        assertThat(e.preview!!.shares.map { it.totalPaise }).containsExactly(200L, 0L, 100L).inOrder()
        vm.setShareText("a", "0"); vm.setShareText("c", "")
        dispatcher.scheduler.advanceUntilIdle()
        e = (vm.uiState.value.editor as LoadState.Content).data
        assertThat(e.preview).isNull()
        assertThat(e.error).isEqualTo(SplitError.NO_SHARES)
    }

    @Test
    fun `removing every participant shows an error and save is refused`() = runTest(dispatcher) {
        val vm = vm()
        vm.editor()
        listOf("a", "b", "c").forEach { vm.toggleParticipant(it) }
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat((vm.uiState.value.editor as LoadState.Content).data.error).isEqualTo(SplitError.NO_PARTICIPANTS)
        assertThat(bills.bills.value.single().split).isNull()
    }

    @Test
    fun `save stores the split with the payer and emits Saved`() = runTest(dispatcher) {
        val vm = vm()
        vm.editor()
        vm.setPayer("b")
        vm.events.test {
            vm.save()
            assertThat(awaitItem()).isEqualTo(SplitEvent.Saved("b1"))
        }
        val saved = bills.bills.value.single()
        assertThat(saved.paidByMemberId).isEqualTo("b")
        assertThat(saved.split!!.shares.sumOf { it.totalPaise }).isEqualTo(300)
        assertThat(saved.status.name).isEqualTo("SPLIT")
    }

    @Test
    fun `existing split is loaded for editing and config survives process death`() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("billId" to "b1"))
        val vm = vm(handle)
        vm.editor()
        vm.setMode(SplitMode.BY_SHARE)
        vm.setShareText("a", "3")
        dispatcher.scheduler.advanceUntilIdle()
        val restored = vm(handle).editor()
        assertThat(restored.config.mode).isEqualTo(SplitMode.BY_SHARE)
        assertThat(restored.shareTexts["a"]).isEqualTo("3")
        // after saving, a fresh ViewModel (new handle) starts from the stored split
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        val fresh = vm(SavedStateHandle(mapOf("billId" to "b1"))).editor()
        assertThat(fresh.config.mode).isEqualTo(SplitMode.BY_SHARE)
        assertThat(fresh.shareTexts["a"]).isEqualTo("3")
    }

    @Test
    fun `missing bill is not found and double save creates one write`() = runTest(dispatcher) {
        vm(SavedStateHandle(mapOf("billId" to "nope"))).uiState.test {
            awaitItem()
            assertThat(awaitItem().editor).isEqualTo(LoadState.Error(ErrorReason.NOT_FOUND))
        }
        var writes = 0
        val counting = object : FakeBillRepository() {
            override suspend fun saveBill(bill: com.darshan.splitsmart.domain.model.Bill) = super.saveBill(bill).also { writes++ }
        }
        counting.bills.value = bills.bills.value
        val vm = SplitViewModel(SavedStateHandle(mapOf("billId" to "b1")), counting, groups, SaveSplitUseCase(counting), TestDispatcherProvider(dispatcher))
        vm.editor()
        vm.save(); vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(writes).isEqualTo(1)
    }
}
