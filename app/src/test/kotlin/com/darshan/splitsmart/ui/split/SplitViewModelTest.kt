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
import com.darshan.splitsmart.testutil.RecordingAnalyticsTracker
import com.darshan.splitsmart.testutil.RecordingCrashReporter
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.darshan.splitsmart.ui.designsystem.effects.HapticEvent
import com.darshan.splitsmart.ui.designsystem.effects.UiEffect
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
        SplitViewModel(handle, bills, groups, SaveSplitUseCase(bills, RecordingAnalyticsTracker(), RecordingCrashReporter()), TestDispatcherProvider(dispatcher))

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
        val vm = SplitViewModel(SavedStateHandle(mapOf("billId" to "b1")), counting, groups, SaveSplitUseCase(counting, RecordingAnalyticsTracker(), RecordingCrashReporter()), TestDispatcherProvider(dispatcher))
        vm.editor()
        vm.save(); vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(writes).isEqualTo(1)
    }
    @Test
    fun `percent mode starts from an even 100 and only splits once the percentages add up to 100`() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("billId" to "b1"))
        val vm = vm(handle)
        vm.editor()
        vm.setModeOption(SplitModeOption.Percent)
        dispatcher.scheduler.advanceUntilIdle()
        var e = (vm.uiState.value.editor as LoadState.Content).data
        assertThat(e.option).isEqualTo(SplitModeOption.Percent)
        assertThat(e.shareTexts).containsExactly("a", "34", "b", "33", "c", "33")
        assertThat(e.percentLeft).isEqualTo(0)
        assertThat(e.preview!!.shares.map { it.totalPaise }).containsExactly(102L, 99L, 99L).inOrder()

        vm.setShareText("a", "50")
        dispatcher.scheduler.advanceUntilIdle()
        e = (vm.uiState.value.editor as LoadState.Content).data
        assertThat(e.percentLeft).isEqualTo(-16)
        assertThat(e.preview).isNull()
        vm.effects.test {
            vm.save()
            assertThat(awaitItem()).isEqualTo(UiEffect.Haptic(HapticEvent.Reject))
        }
        assertThat(bills.bills.value.single().split).isNull()

        vm.setShareText("a", "34")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        val stored = bills.bills.value.single().split!!
        assertThat(stored.config.percent).isTrue()
        assertThat(stored.shares.sumOf { it.totalPaise }).isEqualTo(300)
        val reopened = vm(SavedStateHandle(mapOf("billId" to "b1"))).editor()
        assertThat(reopened.option).isEqualTo(SplitModeOption.Percent)
        assertThat(reopened.shareTexts).containsExactly("a", "34", "b", "33", "c", "33")
    }

    @Test
    fun `leaving percent for shares keeps the numbers as weights`() = runTest(dispatcher) {
        val vm = vm()
        vm.editor()
        vm.setModeOption(SplitModeOption.Percent)
        vm.setModeOption(SplitModeOption.ByShare)
        dispatcher.scheduler.advanceUntilIdle()
        val e = (vm.uiState.value.editor as LoadState.Content).data
        assertThat(e.config.percent).isFalse()
        assertThat(e.percentLeft).isNull()
        assertThat(e.shareTexts).containsExactly("a", "34", "b", "33", "c", "33")
    }

    @Test
    fun `dropping an item on a face assigns it with a light haptic, a repeat drop changes nothing`() = runTest(dispatcher) {
        val vm = vm()
        vm.editor()
        vm.setModeOption(SplitModeOption.ByItem)
        vm.effects.test {
            vm.assignItems(listOf("i1"), "a")
            assertThat(awaitItem()).isEqualTo(UiEffect.Haptic(HapticEvent.Light))
            vm.assignItems(listOf("i1"), "a")
            vm.assignItems(listOf("i1"), "stranger")
            dispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
        val e = (vm.uiState.value.editor as LoadState.Content).data
        assertThat(e.config.itemAssignments["i1"]).containsExactly("a")
        assertThat(e.assignedCounts).containsExactly("a", 1, "b", 0, "c", 0)
        assertThat(e.preview!!.shares.associate { it.memberId to it.totalPaise }).containsExactly("a", 234L, "b", 33L, "c", 33L)
        assertThat(e.preview!!.totalPaise).isEqualTo(300)
    }

    @Test
    fun `the last unassigned item plays the celebration once`() = runTest(dispatcher) {
        val vm = vm()
        vm.editor()
        vm.setModeOption(SplitModeOption.ByItem)
        vm.assignItems(listOf("i1"), "a")
        dispatcher.scheduler.advanceUntilIdle()
        assertThat((vm.uiState.value.editor as LoadState.Content).data.celebrate).isEqualTo(0)
        vm.assignItems(listOf("i2"), "b")
        dispatcher.scheduler.advanceUntilIdle()
        var e = (vm.uiState.value.editor as LoadState.Content).data
        assertThat(e.allAssigned).isTrue()
        assertThat(e.celebrate).isEqualTo(1)
        vm.assignItems(listOf("i2"), "c") // still all assigned: no second party
        dispatcher.scheduler.advanceUntilIdle()
        e = (vm.uiState.value.editor as LoadState.Content).data
        assertThat(e.celebrate).isEqualTo(1)
        // An already fully assigned split that is reopened does not celebrate on load.
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        val reopened = vm(SavedStateHandle(mapOf("billId" to "b1"))).editor()
        assertThat(reopened.allAssigned).isTrue()
        assertThat(reopened.celebrate).isEqualTo(0)
    }

    @Test
    fun `long-press picks survive process death and go to one face together`() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("billId" to "b1"))
        val vm = vm(handle)
        vm.editor()
        vm.setModeOption(SplitModeOption.ByItem)
        vm.toggleSelected("i1"); vm.toggleSelected("i2"); vm.toggleSelected("i2"); vm.toggleSelected("i2")
        dispatcher.scheduler.advanceUntilIdle()
        val restored = vm(handle).editor()
        assertThat(restored.selected).containsExactly("i1", "i2")
        vm.effects.test {
            vm.assignSelected("c")
            assertThat(awaitItem()).isEqualTo(UiEffect.Haptic(HapticEvent.Light))
            cancelAndIgnoreRemainingEvents()
        }
        val e = (vm.uiState.value.editor as LoadState.Content).data
        assertThat(e.selected).isEmpty()
        assertThat(e.config.itemAssignments).containsExactly("i1", setOf("c"), "i2", setOf("c"))
        vm.clearSelection()
        vm.toggleSelected("i1")
        vm.setModeOption(SplitModeOption.Equal) // picks belong to "by item"
        dispatcher.scheduler.advanceUntilIdle()
        assertThat((vm.uiState.value.editor as LoadState.Content).data.selected).isEmpty()
    }

    @Test
    fun `share steppers move one share at a time and never below zero`() = runTest(dispatcher) {
        val vm = vm()
        vm.editor()
        vm.setModeOption(SplitModeOption.ByShare)
        vm.stepShare("a", 1); vm.stepShare("b", -1); vm.stepShare("b", -1)
        dispatcher.scheduler.advanceUntilIdle()
        val e = (vm.uiState.value.editor as LoadState.Content).data
        assertThat(e.shareTexts).containsExactly("a", "2", "b", "0", "c", "1")
        assertThat(e.preview!!.shares.map { it.totalPaise }).containsExactly(200L, 0L, 100L).inOrder()
    }

    @Test
    fun `a saved split confirms with the confirm haptic`() = runTest(dispatcher) {
        val vm = vm()
        vm.editor()
        vm.effects.test {
            vm.save()
            assertThat(awaitItem()).isEqualTo(UiEffect.Haptic(HapticEvent.Confirm))
        }
    }

    @Test
    fun `odd paise across 3 and 7 people still add up to the bill, nobody more than a paisa apart`() = runTest(dispatcher) {
        groups.members.value = (1..7).map { testMember("p$it", name = "Person $it") }
        for ((total, people) in listOf(10_000L to 3, 100_001L to 7, 1L to 3, com.darshan.splitsmart.core.Money.MAX_PAISE - 1 to 7)) {
            bills.bills.value = listOf(testBill(totalPaise = total, items = listOf(testItem("i1", unitPaise = total))))
            val vm = vm()
            vm.editor()
            (people + 1..7).forEach { vm.toggleParticipant("p$it") }
            dispatcher.scheduler.advanceUntilIdle()
            val shares = (vm.uiState.value.editor as LoadState.Content).data.preview!!.shares.map { it.totalPaise }
            assertWithMessage("$total over $people").that(shares).hasSize(people)
            assertWithMessage("$total over $people").that(shares.sum()).isEqualTo(total)
            assertWithMessage("$total over $people").that(shares.max() - shares.min()).isAtMost(1L)
        }
    }
}
